import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const frontendRoot = path.join(repoRoot, 'hoj-vue')
const sourceRoot = path.join(frontendRoot, 'src')

function walk(directory) {
  const files = []
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const fullPath = path.join(directory, entry.name)
    if (entry.isDirectory()) {
      files.push(...walk(fullPath))
    } else if (/\.(?:vue|m?js|ts)$/.test(entry.name)) {
      files.push(fullPath)
    }
  }
  return files
}

function relative(filePath) {
  return path.relative(repoRoot, filePath).replaceAll('\\', '/')
}

function lineNumberAt(source, index) {
  return source.slice(0, index).split('\n').length
}

const patterns = [
  ['vueCompatImports', /@vue\/compat/g],
  ['compatConfiguration', /\b(?:configureCompat|compatConfig)\b/g],
  ['compatRuntimeReferences', /vue3-compat-runtime/g],
  ['legacyNativeModifiers', /\.native(?==|\s)/g],
  ['legacySyncBindings', /\.sync(?==|\s)/g],
  ['legacyInstanceEventApis', /\.\$(?:on|off|once)\s*\(/g],
  ['legacyGlobalVueApis', /\bVue\.(?:extend|prototype|component|directive|filter|mixin|use|observable|set|delete)\b/g],
  ['legacyLifecycleHooks', /\b(?:beforeDestroy|destroyed)\s*(?:\(|:)/g],
  ['componentModelOptions', /\bmodel\s*:\s*\{/g],
  ['legacySlotScope', /(?:^|[\s<])(?:slot-scope|slot)\s*=/gm],
]

const findings = Object.fromEntries(patterns.map(([name]) => [name, []]))
const sourceFiles = walk(sourceRoot)
for (const filePath of sourceFiles) {
  const source = fs.readFileSync(filePath, 'utf8')
  for (const [name, pattern] of patterns) {
    pattern.lastIndex = 0
    for (const match of source.matchAll(pattern)) {
      findings[name].push(`${relative(filePath)}:${lineNumberAt(source, match.index)}`)
    }
  }
}

const packageJson = JSON.parse(fs.readFileSync(path.join(frontendRoot, 'package.json'), 'utf8'))
const packageLockSource = fs.readFileSync(path.join(frontendRoot, 'package-lock.json'), 'utf8')
const viteSource = fs.readFileSync(path.join(frontendRoot, 'vite.config.mjs'), 'utf8')
const mainSource = fs.readFileSync(path.join(sourceRoot, 'main.js'), 'utf8')

const prohibitedDependencies = [
  '@vue/compat',
  'mavon-editor',
  'muse-ui',
  'vue-calendar-heatmap',
  'vue-katex-auto-render',
  'vue-resize',
]
const dependencySections = ['dependencies', 'devDependencies', 'optionalDependencies']
const prohibitedDependencyFindings = []
for (const section of dependencySections) {
  for (const name of prohibitedDependencies) {
    if (packageJson[section]?.[name]) {
      prohibitedDependencyFindings.push(`${section}.${name}`)
    }
  }
}

const boundaries = {
  exactVueVersion: packageJson.dependencies?.vue === '3.5.39',
  exactCompilerVersion: packageJson.devDependencies?.['@vue/compiler-sfc'] === '3.5.39',
  noCompatDependency: !packageJson.dependencies?.['@vue/compat'] && !packageLockSource.includes('node_modules/@vue/compat'),
  nativeViteCompiler: /\bvue\(\)/.test(viteSource) && !/compatConfig|@vue\/compat|vue3-compat-runtime/.test(viteSource),
  nativeApplicationBootstrap: /createApp\(App\)/.test(mainSource) && !/configureCompat|compatConfig|@vue\/compat/.test(mainSource),
  noCompatRuntimeFile: !fs.existsSync(path.join(sourceRoot, 'common', 'vue3-compat-runtime.mjs')),
  noProhibitedDependencies: prohibitedDependencyFindings.length === 0,
  noLegacySourceApis: Object.values(findings).every((items) => items.length === 0),
}

const summary = {
  sourceFiles: sourceFiles.length,
  ...Object.fromEntries(Object.entries(findings).map(([name, items]) => [name, items.length])),
  prohibitedDependencies: prohibitedDependencyFindings.length,
}

const result = {
  summary,
  boundaries,
  findings: {
    ...findings,
    prohibitedDependencies: prohibitedDependencyFindings,
  },
}

console.log(JSON.stringify(result, null, 2))
if (!Object.values(boundaries).every(Boolean)) {
  process.exitCode = 1
}
