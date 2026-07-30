import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const scriptDir = path.dirname(fileURLToPath(import.meta.url))
const repoRoot = path.resolve(scriptDir, '..')
const frontendRoot = path.join(repoRoot, 'hoj-vue')
const sourceRoot = path.join(frontendRoot, 'src')

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

function lineNumberAt(source, offset) {
  return source.slice(0, offset).split(/\r?\n/).length
}

const checks = {
  legacyComponentTranslations: /\$i18n\.(?:\$t|t)\(/g,
  legacyPluralCalls: /(?:\$tc|\.tc)\s*\(/g,
  legacyDirectiveUsages: /\bv-t(?:\s|=|:|\.)/g,
  legacyModuloMessages: /%\{[^}]+\}/g,
  unwrappedComposerLocales: /\bi18n\.locale(?!\.value)\b/g
}
const summary = Object.fromEntries(Object.keys(checks).map((key) => [key, 0]))
const findings = Object.fromEntries(Object.keys(checks).map((key) => [key, []]))

for (const filePath of findSourceFiles(sourceRoot)) {
  const source = fs.readFileSync(filePath, 'utf8')
  const relativePath = path.relative(repoRoot, filePath)

  for (const [kind, pattern] of Object.entries(checks)) {
    pattern.lastIndex = 0
    for (const match of source.matchAll(pattern)) {
      summary[kind] += 1
      findings[kind].push(relativePath + ':' + lineNumberAt(source, match.index))
    }
  }
}

const packageJson = JSON.parse(
  fs.readFileSync(path.join(frontendRoot, 'package.json'), 'utf8')
)
const i18nSource = fs.readFileSync(path.join(sourceRoot, 'i18n', 'index.js'), 'utf8')

const configuration = {
  dependency: packageJson.dependencies['vue-i18n'],
  compositionMode: /\blegacy\s*:\s*false\b/.test(i18nSource),
  globalInjection: /\bglobalInjection\s*:\s*true\b/.test(i18nSource),
  allowCompositionOption: /\ballowComposition\s*:/.test(i18nSource)
}

console.log(JSON.stringify({ summary, configuration, findings }, null, 2))
