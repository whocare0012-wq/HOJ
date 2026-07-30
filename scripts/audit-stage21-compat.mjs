import fs from 'node:fs'
import path from 'node:path'
import vm from 'node:vm'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'

const scriptDir = path.dirname(fileURLToPath(import.meta.url))
const repoRoot = path.resolve(scriptDir, '..')
const frontendRoot = path.join(repoRoot, 'hoj-vue')
const sourceRoot = path.join(frontendRoot, 'src')
const localeRoot = path.join(frontendRoot, 'src', 'i18n')
const requireFromFrontend = createRequire(path.join(frontendRoot, 'package.json'))
const { baseCompile } = requireFromFrontend('@intlify/message-compiler')
const { parse: parseSfc } = requireFromFrontend('@vue/compiler-sfc')

function findFiles(directory, extensions) {
  const files = []
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const entryPath = path.join(directory, entry.name)
    if (entry.isDirectory()) {
      files.push(...findFiles(entryPath, extensions))
    } else if (entry.isFile() && extensions.has(path.extname(entry.name))) {
      files.push(entryPath)
    }
  }
  return files.sort()
}

function findLocaleFiles(directory) {
  const files = []
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const entryPath = path.join(directory, entry.name)
    if (entry.isDirectory()) {
      files.push(...findLocaleFiles(entryPath))
    } else if (entry.isFile() && entry.name.endsWith('.js') && entry.name !== 'index.js') {
      files.push(entryPath)
    }
  }
  return files.sort()
}

function readLocaleObject(filePath) {
  const source = fs.readFileSync(filePath, 'utf8')
  const objectStart = source.indexOf('{')
  const objectEnd = source.lastIndexOf('}')
  if (objectStart === -1 || objectEnd <= objectStart) {
    throw new Error('Locale object boundary is malformed: ' + filePath)
  }
  return vm.runInNewContext(
    '(' + source.slice(objectStart, objectEnd + 1) + ')',
    Object.create(null),
    { filename: filePath }
  )
}

function visitStrings(value, keyPath, visitor) {
  if (typeof value === 'string') {
    visitor(value, keyPath)
    return
  }
  if (!value || typeof value !== 'object') {
    return
  }
  for (const [key, child] of Object.entries(value)) {
    visitStrings(child, keyPath ? `${keyPath}.${key}` : key, visitor)
  }
}

const compileErrors = []
const localeMessages = new Map()
const literalBraceFindings = []
let localeMessageCount = 0
const localeFiles = findLocaleFiles(localeRoot)
for (const filePath of localeFiles) {
  const localeObject = readLocaleObject(filePath)
  visitStrings(localeObject, '', (message, key) => {
    localeMessageCount += 1
    const relativePath = path.relative(repoRoot, filePath).replaceAll('\\', '/')
    localeMessages.set(`${relativePath}:${key}`, message)
    if (message.includes("{'{'}...{'}'}")) {
      literalBraceFindings.push(`${relativePath}:${key}`)
    }
    baseCompile(message, {
      onError(error) {
        compileErrors.push({
          file: relativePath,
          key,
          code: error.code,
          message: error.message,
          value: message,
        })
      },
    })
  })
}

const summary = {
  localeFiles: localeFiles.length,
  localeMessageCount,
  localeCompileErrors: compileErrors.length,
  localeLiteralBraceFixes: literalBraceFindings.length,
  legacyNativeModifiers: 0,
  legacySyncBindings: 0,
  legacyInstanceEventApis: 0,
  removedInstanceProperties: 0,
  legacyGlobalVueApis: 0,
  legacyLifecycleHooks: 0,
  functionalComponents: 0,
  componentModelOptions: 0,
  explicitMode3Boundaries: 0,
  explicitWatchArrayVue3Boundaries: 0,
  malformedSfcFiles: 0,
}
const findings = Object.fromEntries(Object.keys(summary).map((key) => [key, []]))
findings.localeCompileErrors = compileErrors.map(
  ({ file, key, code }) => `${file}:${key}:code-${code}`
)
findings.localeLiteralBraceFixes = literalBraceFindings

function lineNumberAt(source, offset) {
  return source.slice(0, offset).split(/\r?\n/).length
}

const sourceChecks = [
  ['legacyNativeModifiers', /\.native(?==|\s)/g],
  ['legacySyncBindings', /\.sync(?==|\s)/g],
  ['legacyInstanceEventApis', /\.\$(?:on|off|once)\(/g],
  ['removedInstanceProperties', /\$(?:listeners|scopedSlots|children)\b/g],
  ['legacyGlobalVueApis', /\bVue\.(?:extend|prototype|component|directive|filter|mixin|use|observable|set|delete)\b/g],
  ['legacyLifecycleHooks', /\b(?:beforeDestroy|destroyed)\s*(?:\(|:)/g],
  ['functionalComponents', /\bfunctional\s*:\s*true/g],
  ['componentModelOptions', /\bmodel\s*:\s*\{/g],
  ['explicitMode3Boundaries', /\bMODE\s*:\s*3\b/g],
  ['explicitWatchArrayVue3Boundaries', /\bWATCH_ARRAY\s*:\s*false\b/g],
]

for (const filePath of findFiles(sourceRoot, new Set(['.js', '.mjs', '.vue']))) {
  const source = fs.readFileSync(filePath, 'utf8')
  const relativePath = path.relative(repoRoot, filePath)
  for (const [property, pattern] of sourceChecks) {
    for (const match of source.matchAll(pattern)) {
      summary[property] += 1
      findings[property].push(relativePath + ':' + lineNumberAt(source, match.index))
    }
  }
  if (filePath.endsWith('.vue')) {
    const parsed = parseSfc(source, { filename: filePath })
    if (parsed.errors.length > 0) {
      summary.malformedSfcFiles += 1
      findings.malformedSfcFiles.push(relativePath)
    }
  }
}

const packageJson = JSON.parse(fs.readFileSync(path.join(frontendRoot, 'package.json'), 'utf8'))
const mainSource = fs.readFileSync(path.join(sourceRoot, 'main.js'), 'utf8')
const viteSource = fs.readFileSync(path.join(frontendRoot, 'vite.config.mjs'), 'utf8')
const vue3RuntimeSource = fs.readFileSync(
  path.join(sourceRoot, 'common', 'vue3-compat-runtime.mjs'),
  'utf8'
)
const cropperSource = fs.readFileSync(
  path.join(sourceRoot, 'components', 'common', 'VueCropperAdapter.mjs'),
  'utf8'
)
const heatmapSource = fs.readFileSync(
  path.join(sourceRoot, 'components', 'common', 'CalendarHeatmap.vue'),
  'utf8'
)
const editorSource = fs.readFileSync(
  path.join(sourceRoot, 'components', 'admin', 'Editor.vue'),
  'utf8'
)
const introductionSource = fs.readFileSync(
  path.join(sourceRoot, 'views', 'oj', 'about', 'Introduction.vue'),
  'utf8'
)
const expectedLiteralFixes = [
  'hoj-vue/src/i18n/oj/en-US.js:Compile_Tips3',
  'hoj-vue/src/i18n/oj/ja-JP.js:Compile_Tips3',
  'hoj-vue/src/i18n/oj/ko-KR.js:Compile_Tips3',
  'hoj-vue/src/i18n/oj/zh-CN.js:Compile_Tips3',
  'hoj-vue/src/i18n/oj/zh-TW.js:Compile_Tips3',
]

const boundaries = {
  exactVueCompatVersions:
    packageJson.dependencies?.vue === '3.5.39' &&
    packageJson.dependencies?.['@vue/compat'] === '3.5.39' &&
    packageJson.dependencies?.['vue-i18n'] === '11.4.2',
  intentionalGlobalMode2:
    /configureCompat\(\{/.test(mainSource) &&
    /compatConfig:\s*\{\s*MODE:\s*2/s.test(viteSource) &&
    /vue:\s*'@vue\/compat'/.test(viteSource),
  reviewedWarningSuppressions:
    /COMPONENT_ASYNC:\s*false/.test(mainSource) &&
    /ATTR_ENUMERATED_COERCION:\s*'suppress-warning'/.test(mainSource) &&
    /ATTR_FALSE_VALUE:\s*'suppress-warning'/.test(mainSource) &&
    /INSTANCE_ATTRS_CLASS_STYLE:\s*'suppress-warning'/.test(mainSource),
  nativeDependencyMode3:
    /\^\(El\|Vxe\)/.test(mainSource) &&
    /component\.compatConfig\s*=/.test(mainSource) &&
    /MODE:\s*3/.test(vue3RuntimeSource) &&
    /MODE:\s*3/.test(cropperSource) &&
    /MODE:\s*3/.test(heatmapSource) &&
    /MODE:\s*3/.test(editorSource),
  exactLiteralBraceFixSet:
    compileErrors.length === 0 &&
    literalBraceFindings.length === expectedLiteralFixes.length &&
    expectedLiteralFixes.every((finding) => literalBraceFindings.includes(finding)) &&
    expectedLiteralFixes.every((finding) =>
      localeMessages.get(finding)?.includes("for(int i=0...){'{'}...{'}'}")
    ),
  explicitIntroductionWatchArrayMigration:
    /compatConfig:\s*\{\s*WATCH_ARRAY:\s*false/s.test(introductionSource) &&
    /languages:\s*\{\s*deep:\s*true,\s*handler\(newVal\)/s.test(introductionSource),
  noLegacyApplicationApis: [
    'legacyNativeModifiers',
    'legacySyncBindings',
    'legacyInstanceEventApis',
    'removedInstanceProperties',
    'legacyGlobalVueApis',
    'legacyLifecycleHooks',
    'functionalComponents',
    'componentModelOptions',
  ].every((property) => summary[property] === 0),
}

console.log(JSON.stringify({
  summary,
  boundaries,
  findings,
  localeCompileErrors: compileErrors,
}, null, 2))
