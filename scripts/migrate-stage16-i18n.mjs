import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const scriptDir = path.dirname(fileURLToPath(import.meta.url))
const repoRoot = path.resolve(scriptDir, '..')
const sourceRoot = path.join(repoRoot, 'hoj-vue', 'src')

const write = process.argv.includes('--write')
const expectedArgument = process.argv.find((argument) => argument.startsWith('--expect='))
const expected = expectedArgument
  ? Number.parseInt(expectedArgument.slice('--expect='.length), 10)
  : null

function findSourceFiles(directory) {
  const files = []
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const entryPath = path.join(directory, entry.name)
    if (entry.isDirectory()) {
      files.push(...findSourceFiles(entryPath))
    } else if (entry.isFile() && /\.(?:js|vue)$/.test(entry.name)) {
      files.push(entryPath)
    }
  }
  return files.sort()
}

function countMatches(source, pattern) {
  return source.match(pattern)?.length ?? 0
}

const summary = {
  legacyComponentTranslations: 0,
  changedFiles: 0
}
const changedFiles = []

for (const filePath of findSourceFiles(sourceRoot)) {
  const source = fs.readFileSync(filePath, 'utf8')
  const legacyNestedTranslations = countMatches(source, /\$i18n\.\$t\(/g)
  const legacyTranslations = countMatches(source, /\$i18n\.t\(/g)
  const fileChanges = legacyNestedTranslations + legacyTranslations

  if (fileChanges === 0) {
    continue
  }

  const updatedSource = source
    .replaceAll('$i18n.$t(', '$t(')
    .replaceAll('$i18n.t(', '$t(')

  if (updatedSource === source) {
    throw new Error('Vue I18n migration produced no change for ' + filePath)
  }

  summary.legacyComponentTranslations += fileChanges
  summary.changedFiles += 1
  changedFiles.push(path.relative(repoRoot, filePath))

  if (write) {
    fs.writeFileSync(filePath, updatedSource, 'utf8')
  }
}

if (expected !== null && summary.legacyComponentTranslations !== expected) {
  throw new Error(
    'Expected ' + expected + ' legacy component translations, found ' +
      summary.legacyComponentTranslations + '.'
  )
}

console.log(JSON.stringify({
  mode: write ? 'write' : 'dry-run',
  summary,
  changedFiles
}, null, 2))
