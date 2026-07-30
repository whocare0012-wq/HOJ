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

function hasStaticAttribute(node, name) {
  return node.props.some((property) => property.type === 6 && property.name === name)
}

function getBoundAttribute(node, name) {
  return node.props.find(
    (property) => property.type === 7 &&
      property.name === 'bind' &&
      property.arg?.type === 4 &&
      property.arg.content === name
  )
}

const summary = {
  directTransitionRouterViews: 0,
  legacyRadioValues: 0,
  booleanLinkUnderlines: 0
}
const findings = {
  directTransitionRouterViews: [],
  legacyRadioValues: [],
  booleanLinkUnderlines: []
}

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

  const ast = parseTemplate(templateBlock.content)
  const relativePath = path.relative(repoRoot, filePath)

  function record(kind, node) {
    summary[kind] += 1
    findings[kind].push(relativePath + ':' + node.loc.start.line)
  }

  function walk(node, parent) {
    if (node.type !== 1) {
      return
    }

    if (node.tag === 'router-view' && parent?.tag === 'transition') {
      record('directTransitionRouterViews', node)
    }

    if (node.tag === 'el-radio' &&
        (hasStaticAttribute(node, 'label') || getBoundAttribute(node, 'label'))) {
      record('legacyRadioValues', node)
    }

    if (node.tag === 'el-link') {
      const underlineBinding = getBoundAttribute(node, 'underline')
      if (underlineBinding?.exp?.type === 4 &&
          /^(?:true|false)$/.test(underlineBinding.exp.content.trim())) {
        record('booleanLinkUnderlines', node)
      }
    }

    for (const child of node.children) {
      walk(child, node)
    }
  }

  for (const child of ast.children) {
    walk(child, null)
  }
}

console.log(JSON.stringify({ summary, findings }, null, 2))
