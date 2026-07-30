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
const expectedRadioArgument = process.argv.find((argument) => argument.startsWith('--expect-radio='))
const expectedLinkArgument = process.argv.find((argument) => argument.startsWith('--expect-link='))
const expectedRadio = expectedRadioArgument
  ? Number.parseInt(expectedRadioArgument.slice('--expect-radio='.length), 10)
  : null
const expectedLink = expectedLinkArgument
  ? Number.parseInt(expectedLinkArgument.slice('--expect-link='.length), 10)
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

function applyEdits(content, edits) {
  edits.sort((left, right) => right.start - left.start)
  let result = content
  for (const edit of edits) {
    result = result.slice(0, edit.start) + edit.text + result.slice(edit.end)
  }
  return result
}

const summary = {
  legacyRadioValues: 0,
  booleanLinkUnderlines: 0,
  changedFiles: 0
}
const changedFiles = []

for (const filePath of findVueFiles(sourceRoot)) {
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
  const ast = parseTemplate(content)
  const edits = []

  function walk(node) {
    if (node.type !== 1) {
      return
    }

    if (node.tag === 'el-radio') {
      for (const property of node.props) {
        if (property.type === 6 && property.name === 'label') {
          edits.push({
            start: property.loc.start.offset,
            end: property.loc.start.offset + 'label'.length,
            text: 'value'
          })
          summary.legacyRadioValues += 1
        } else if (property.type === 7 &&
                   property.name === 'bind' &&
                   property.arg?.type === 4 &&
                   property.arg.content === 'label') {
          edits.push({
            start: property.arg.loc.start.offset,
            end: property.arg.loc.end.offset,
            text: 'value'
          })
          summary.legacyRadioValues += 1
        }
      }
    }

    if (node.tag === 'el-link') {
      for (const property of node.props) {
        if (property.type === 7 &&
            property.name === 'bind' &&
            property.arg?.type === 4 &&
            property.arg.content === 'underline' &&
            property.exp?.type === 4 &&
            /^(?:true|false)$/.test(property.exp.content.trim())) {
          const underline = property.exp.content.trim() === 'true' ? 'always' : 'never'
          edits.push({
            start: property.loc.start.offset,
            end: property.loc.end.offset,
            text: 'underline="' + underline + '"'
          })
          summary.booleanLinkUnderlines += 1
        }
      }
    }

    for (const child of node.children) {
      walk(child)
    }
  }

  for (const child of ast.children) {
    walk(child)
  }

  if (edits.length === 0) {
    continue
  }

  const updatedContent = applyEdits(content, edits)
  const updatedSource =
    source.slice(0, templateBlock.loc.start.offset) +
    updatedContent +
    source.slice(templateBlock.loc.end.offset)

  if (updatedSource === source) {
    throw new Error('Element Plus migration produced no change for ' + filePath)
  }

  summary.changedFiles += 1
  changedFiles.push(path.relative(repoRoot, filePath))
  if (write) {
    fs.writeFileSync(filePath, updatedSource, 'utf8')
  }
}

if (expectedRadio !== null && summary.legacyRadioValues !== expectedRadio) {
  throw new Error(
    'Expected ' + expectedRadio + ' legacy radio values, found ' + summary.legacyRadioValues + '.'
  )
}
if (expectedLink !== null && summary.booleanLinkUnderlines !== expectedLink) {
  throw new Error(
    'Expected ' + expectedLink + ' boolean link underlines, found ' + summary.booleanLinkUnderlines + '.'
  )
}

console.log(JSON.stringify({
  mode: write ? 'write' : 'dry-run',
  summary,
  changedFiles
}, null, 2))
