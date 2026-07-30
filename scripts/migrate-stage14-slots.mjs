import fs from 'node:fs'
import path from 'node:path'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'

const scriptDir = path.dirname(fileURLToPath(import.meta.url))
const repoRoot = path.resolve(scriptDir, '..')
const frontendRoot = path.join(repoRoot, 'hoj-vue')
const sourceRoot = path.join(frontendRoot, 'src')
const requireFromFrontend = createRequire(path.join(frontendRoot, 'package.json'))
const { parse: parseSfc } = requireFromFrontend('@vue/compiler-sfc')
const { parse: parseTemplate } = requireFromFrontend('@vue/compiler-dom')

const write = process.argv.includes('--write')
const expectedArgument = process.argv.find((argument) => argument.startsWith('--expect='))
const expectedCount = expectedArgument
  ? Number.parseInt(expectedArgument.slice('--expect='.length), 10)
  : null

function findVueFiles(directory) {
  const files = []
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const entryPath = path.join(directory, entry.name)
    if (entry.isDirectory()) {
      files.push(...findVueFiles(entryPath))
    } else if (entry.isFile() && entry.name.endsWith('.vue')) {
      files.push(entryPath)
    }
  }
  return files.sort()
}

function getAttributeRemovalRange(content, node, attribute) {
  let start = attribute.loc.start.offset
  let end = attribute.loc.end.offset
  const lineStart = content.lastIndexOf('\n', start - 1) + 1
  const lineEnd = content.indexOf('\n', end)
  const before = content.slice(lineStart, start)
  const after = content.slice(end, lineEnd === -1 ? content.length : lineEnd)

  if (before.trim() === '' && after.trim() === '' && lineEnd !== -1) {
    return { start: lineStart, end: lineEnd + 1 }
  }

  while (start > node.loc.start.offset && /[ \t]/.test(content[start - 1])) {
    start -= 1
  }
  return { start, end }
}

function addInsertion(insertions, offset, text) {
  insertions.set(offset, (insertions.get(offset) || '') + text)
}

function applyEdits(content, removals, insertions) {
  const edits = [
    ...removals.map((removal) => ({ ...removal, text: '' })),
    ...Array.from(insertions, ([start, text]) => ({ start, end: start, text }))
  ]

  edits.sort((left, right) => {
    if (left.start !== right.start) {
      return right.start - left.start
    }
    return right.end - left.end
  })

  let result = content
  for (const edit of edits) {
    result = result.slice(0, edit.start) + edit.text + result.slice(edit.end)
  }
  return result
}

const files = findVueFiles(sourceRoot)
const summary = {
  total: 0,
  wrappedComponentSlots: 0,
  promotedConditionalSlots: 0,
  removedIneffectiveNativeSlots: 0,
  changedFiles: 0
}
const slotCounts = new Map()
const changedFiles = []

for (const filePath of files) {
  const source = fs.readFileSync(filePath, 'utf8')
  const parsedSfc = parseSfc(source, { filename: filePath })
  if (parsedSfc.errors.length > 0) {
    throw new Error('Unable to parse SFC ' + filePath + ': ' + parsedSfc.errors.join(', '))
  }

  const templateBlock = parsedSfc.descriptor.template
  if (!templateBlock) {
    continue
  }

  const content = templateBlock.content
  const eol = source.includes('\r\n') ? '\r\n' : '\n'
  const ast = parseTemplate(content)
  const removals = []
  const insertions = new Map()
  let fileSlotCount = 0

  function walk(node, parent) {
    if (node.type !== 1) {
      return
    }

    const slotAttribute = node.props.find(
      (property) => property.type === 6 && property.name === 'slot' && property.value
    )

    if (slotAttribute) {
      const slotName = slotAttribute.value.content
      if (!/^[A-Za-z][\w-]*$/.test(slotName)) {
        throw new Error('Unsupported slot name in ' + filePath + ': ' + slotName)
      }

      summary.total += 1
      fileSlotCount += 1
      slotCounts.set(slotName, (slotCounts.get(slotName) || 0) + 1)
      removals.push(getAttributeRemovalRange(content, node, slotAttribute))

      if (parent && parent.tag === 'template') {
        const templateOpenEnd = content.indexOf('>', parent.loc.start.offset)
        if (templateOpenEnd < 0 || templateOpenEnd > node.loc.start.offset) {
          throw new Error('Unable to promote conditional slot in ' + filePath)
        }
        addInsertion(insertions, templateOpenEnd, ' #' + slotName)
        summary.promotedConditionalSlots += 1
      } else if (parent && parent.tagType === 1) {
        const lineStart = content.lastIndexOf('\n', node.loc.start.offset - 1) + 1
        const indentation = content.slice(lineStart, node.loc.start.offset)
        if (!/^[ \t]*$/.test(indentation)) {
          throw new Error('Inline legacy slot requires manual migration in ' + filePath)
        }

        addInsertion(
          insertions,
          node.loc.start.offset,
          '<template #' + slotName + '>' + eol + indentation + '  '
        )
        addInsertion(
          insertions,
          node.loc.end.offset,
          eol + indentation + '</template>'
        )

        let newlineOffset = content.indexOf('\n', node.loc.start.offset)
        while (newlineOffset !== -1 && newlineOffset < node.loc.end.offset) {
          const nextNewlineOffset = content.indexOf('\n', newlineOffset + 1)
          const lineEndOffset = nextNewlineOffset === -1
            ? node.loc.end.offset
            : Math.min(nextNewlineOffset, node.loc.end.offset)
          const nextLine = content.slice(newlineOffset + 1, lineEndOffset)

          if (nextLine.trim() !== '') {
            addInsertion(insertions, newlineOffset + 1, '  ')
          }
          newlineOffset = content.indexOf('\n', newlineOffset + 1)
        }
        summary.wrappedComponentSlots += 1
      } else if (parent && parent.tagType === 0) {
        summary.removedIneffectiveNativeSlots += 1
      } else {
        throw new Error('Unsupported legacy slot parent in ' + filePath)
      }
    }

    for (const child of node.children) {
      walk(child, node)
    }
  }

  for (const child of ast.children) {
    walk(child, null)
  }

  if (fileSlotCount === 0) {
    continue
  }

  const updatedContent = applyEdits(content, removals, insertions)
  const updatedSource =
    source.slice(0, templateBlock.loc.start.offset) +
    updatedContent +
    source.slice(templateBlock.loc.end.offset)

  if (updatedSource === source) {
    throw new Error('Slot migration produced no change for ' + filePath)
  }

  summary.changedFiles += 1
  changedFiles.push(path.relative(repoRoot, filePath))
  if (write) {
    fs.writeFileSync(filePath, updatedSource, 'utf8')
  }
}

if (expectedCount !== null && summary.total !== expectedCount) {
  throw new Error(
    'Expected ' + expectedCount + ' legacy slots, found ' + summary.total + '. No count assumption was accepted.'
  )
}

console.log(JSON.stringify({
  mode: write ? 'write' : 'dry-run',
  summary,
  slots: Object.fromEntries(Array.from(slotCounts).sort()),
  changedFiles
}, null, 2))
