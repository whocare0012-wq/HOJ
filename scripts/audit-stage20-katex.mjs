import fs from 'node:fs'
import path from 'node:path'
import { createRequire } from 'node:module'
import { fileURLToPath, pathToFileURL } from 'node:url'

const scriptDir = path.dirname(fileURLToPath(import.meta.url))
const repoRoot = path.resolve(scriptDir, '..')
const frontendRoot = path.join(repoRoot, 'hoj-vue')
const sourceRoot = path.join(frontendRoot, 'src')
const requireFromFrontend = createRequire(path.join(frontendRoot, 'package.json'))
const { parse: parseSfc } = requireFromFrontend('@vue/compiler-sfc')
const { parse: parseTemplate } = requireFromFrontend('@vue/compiler-dom')
const katex = requireFromFrontend('katex')

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

function lineNumberAt(source, offset) {
  return source.slice(0, offset).split(/\r?\n/).length
}

function hasAll(source, patterns) {
  return patterns.every((pattern) => pattern.test(source))
}

const packageJsonPath = path.join(frontendRoot, 'package.json')
const packageLockPath = path.join(frontendRoot, 'package-lock.json')
const katexPackagePath = path.join(frontendRoot, 'node_modules', 'katex', 'package.json')
const directivePath = path.join(sourceRoot, 'common', 'katex.js')
const optionsPath = path.join(sourceRoot, 'common', 'katex-options.mjs')
const mainPath = path.join(sourceRoot, 'main.js')

for (const requiredFile of [
  packageJsonPath,
  packageLockPath,
  katexPackagePath,
  directivePath,
  optionsPath,
  mainPath,
]) {
  if (!fs.existsSync(requiredFile)) {
    throw new Error('Required stage-twenty file is missing: ' + requiredFile)
  }
}

const packageJson = JSON.parse(fs.readFileSync(packageJsonPath, 'utf8'))
const packageLock = JSON.parse(fs.readFileSync(packageLockPath, 'utf8'))
const katexPackage = JSON.parse(fs.readFileSync(katexPackagePath, 'utf8'))
const directiveSource = fs.readFileSync(directivePath, 'utf8')
const optionsSource = fs.readFileSync(optionsPath, 'utf8')
const mainSource = fs.readFileSync(mainPath, 'utf8')
const {
  DEFAULT_KATEX_DELIMITERS,
  DEFAULT_KATEX_IGNORED_TAGS,
  DEFAULT_KATEX_IGNORED_CLASSES,
  createKatexOptions,
} = await import(pathToFileURL(optionsPath).href)

const summary = {
  katexDirectiveInstances: 0,
  legacyPackageSourceUsages: 0,
  autoRenderImports: 0,
  katexCssImports: 0,
  katexPluginImports: 0,
  katexPluginUses: 0,
  legacyDirectiveHooks: 0,
  malformedSfcFiles: 0,
}
const findings = Object.fromEntries(Object.keys(summary).map((key) => [key, []]))
const directiveConsumers = new Set()

for (const filePath of findFiles(sourceRoot, new Set(['.js', '.mjs', '.vue']))) {
  const source = fs.readFileSync(filePath, 'utf8')
  const relativePath = path.relative(repoRoot, filePath)
  const sourceChecks = [
    ['legacyPackageSourceUsages', /vue-katex-auto-render/g],
    ['autoRenderImports', /from ['"]katex\/contrib\/auto-render['"]/g],
    ['katexCssImports', /['"]katex\/dist\/katex\.min\.css['"]/g],
    ['katexPluginImports', /import Katex from ['"]@\/common\/katex['"]/g],
    ['katexPluginUses', /app\.use\(Katex\)/g],
  ]

  for (const [property, pattern] of sourceChecks) {
    for (const match of source.matchAll(pattern)) {
      summary[property] += 1
      findings[property].push(relativePath + ':' + lineNumberAt(source, match.index))
    }
  }

  if (filePath === directivePath) {
    for (const match of source.matchAll(/\b(?:bind|inserted|componentUpdated|unbind|beforeMount)\s*:/g)) {
      summary.legacyDirectiveHooks += 1
      findings.legacyDirectiveHooks.push(relativePath + ':' + lineNumberAt(source, match.index))
    }
  }

  if (!filePath.endsWith('.vue')) {
    continue
  }
  const parsedSfc = parseSfc(source, { filename: filePath })
  if (parsedSfc.errors.length > 0) {
    summary.malformedSfcFiles += 1
    findings.malformedSfcFiles.push(relativePath)
    continue
  }
  if (!parsedSfc.descriptor.template) {
    continue
  }

  let ast
  try {
    ast = parseTemplate(parsedSfc.descriptor.template.content)
  } catch {
    summary.malformedSfcFiles += 1
    findings.malformedSfcFiles.push(relativePath)
    continue
  }

  function walk(node) {
    if (node.type !== 1) {
      return
    }
    for (const property of node.props) {
      if (property.type === 7 && property.name === 'katex') {
        summary.katexDirectiveInstances += 1
        findings.katexDirectiveInstances.push(relativePath + ':' + property.loc.start.line)
        directiveConsumers.add(relativePath.replaceAll('\\', '/'))
      }
    }
    for (const child of node.children) {
      walk(child)
    }
  }

  for (const child of ast.children) {
    walk(child)
  }
}

const dependencySections = ['dependencies', 'devDependencies', 'optionalDependencies', 'peerDependencies']
const legacyDeclarations = dependencySections.filter(
  (section) => packageJson[section]?.['vue-katex-auto-render']
)
const packageLockRoot = packageLock.packages?.[''] ?? {}
const lockedKatex = packageLock.packages?.['node_modules/katex'] ?? null
const lockedLegacyPackage = packageLock.packages?.['node_modules/vue-katex-auto-render'] ?? null
const lockedMarkdownKatex = packageLock.packages?.['node_modules/@iktakahiro/markdown-it-katex'] ?? null

const dependencies = {
  declaredKatex: packageJson.dependencies?.katex ?? null,
  katexOverride: packageJson.overrides?.katex ?? null,
  lockedRootKatex: packageLockRoot.dependencies?.katex ?? null,
  lockedKatexVersion: lockedKatex?.version ?? null,
  installedKatexVersion: katexPackage.version ?? null,
  markdownKatexVersion: packageJson.dependencies?.['@iktakahiro/markdown-it-katex'] ?? null,
  lockedMarkdownKatexVersion: lockedMarkdownKatex?.version ?? null,
  legacyDeclarations,
  legacyLockEntry: lockedLegacyPackage ? 'node_modules/vue-katex-auto-render' : null,
}

const expectedConsumers = [
  'hoj-vue/src/App.vue',
  'hoj-vue/src/components/oj/common/Announcements.vue',
  'hoj-vue/src/components/oj/common/Markdown.vue',
  'hoj-vue/src/components/oj/group/Announcement.vue',
  'hoj-vue/src/views/oj/rank/ACMRank.vue',
  'hoj-vue/src/views/oj/rank/OIRank.vue',
]
const defaultOptions = createKatexOptions()
const directCustomSource = {
  delimiters: [{ left: '%%', right: '%%', display: false }],
  ignoredTags: ['kbd'],
  ignoredClasses: ['custom-ignore'],
  throwOnError: true,
  trust: true,
}
const directCustomSnapshot = JSON.stringify(directCustomSource)
const directOptions = createKatexOptions(directCustomSource)
const nestedOptions = createKatexOptions({
  options: {
    delimiters: [{ left: '@@', right: '@@', display: true }],
    ignoredClasses: ['nested-ignore'],
  },
})
directOptions.delimiters[0].left = 'mutated-copy'
directOptions.ignoredTags.push('mutated-copy')
directOptions.ignoredClasses.push('mutated-copy')

const renderedMath = katex.renderToString('\\frac{a}{b}+\\sqrt{x}', {
  throwOnError: false,
  trust: false,
})
const renderedUntrusted = katex.renderToString('\\htmlClass{unsafe}{x}', {
  throwOnError: false,
  strict: 'ignore',
  trust: false,
})

const boundaries = {
  exactKatexDependency:
    packageJson.dependencies?.katex === '0.17.0' &&
    packageLockRoot.dependencies?.katex === '0.17.0' &&
    lockedKatex?.version === '0.17.0' &&
    katexPackage.version === '0.17.0',
  exactKatexOverride: packageJson.overrides?.katex === '0.17.0',
  preservedMarkdownKatex:
    packageJson.dependencies?.['@iktakahiro/markdown-it-katex'] === '^4.0.1' &&
    packageLockRoot.dependencies?.['@iktakahiro/markdown-it-katex'] === '^4.0.1' &&
    lockedMarkdownKatex?.version === '4.0.1',
  noLegacyPackage:
    legacyDeclarations.length === 0 &&
    lockedLegacyPackage === null,
  nativeDirectiveBoundary: hasAll(directiveSource, [
    /import renderMathInElement from 'katex\/contrib\/auto-render'/,
    /import 'katex\/dist\/katex\.min\.css'/,
    /import \{ createKatexOptions \} from '@\/common\/katex-options\.mjs'/,
    /export function renderKatex \(el, binding = \{\}\)/,
    /renderMathInElement\(el, createKatexOptions\(binding\.value\)\)/,
    /mounted:\s*renderKatex/,
    /updated:\s*renderKatex/,
    /app\.directive\('katex', katexDirective\)/,
  ]),
  centralizedPluginRegistration:
    /import Katex from '@\/common\/katex'/.test(mainSource) &&
    /app\.use\(Katex\)/.test(mainSource),
  exactConsumerSet:
    directiveConsumers.size === expectedConsumers.length &&
    expectedConsumers.every((consumer) => directiveConsumers.has(consumer)),
  delimiterPriority:
    JSON.stringify(DEFAULT_KATEX_DELIMITERS.map(({ left }) => left)) ===
      JSON.stringify(['$$', '$', '\\(', '\\[']) &&
    DEFAULT_KATEX_DELIMITERS[0].display === true &&
    DEFAULT_KATEX_DELIMITERS[1].display === false,
  ignoredTextBoundaries:
    ['pre', 'code'].every((tag) => DEFAULT_KATEX_IGNORED_TAGS.includes(tag)) &&
    ['katex', 'katex-display'].every((className) =>
      DEFAULT_KATEX_IGNORED_CLASSES.includes(className)
    ),
  immutableDefaults:
    Object.isFrozen(DEFAULT_KATEX_DELIMITERS) &&
    DEFAULT_KATEX_DELIMITERS.every(Object.isFrozen) &&
    Object.isFrozen(DEFAULT_KATEX_IGNORED_TAGS) &&
    Object.isFrozen(DEFAULT_KATEX_IGNORED_CLASSES),
  lockedTrustBoundary:
    defaultOptions.trust === false &&
    directOptions.trust === false &&
    !renderedUntrusted.includes('class="unsafe"'),
}

const smoke = {
  defaultOptionsAreCopied:
    defaultOptions.delimiters !== DEFAULT_KATEX_DELIMITERS &&
    defaultOptions.delimiters[0] !== DEFAULT_KATEX_DELIMITERS[0] &&
    defaultOptions.ignoredTags !== DEFAULT_KATEX_IGNORED_TAGS &&
    defaultOptions.ignoredClasses !== DEFAULT_KATEX_IGNORED_CLASSES,
  directOptionsSupported:
    directOptions.delimiters[0].left === 'mutated-copy' &&
    directOptions.throwOnError === true &&
    directOptions.ignoredClasses.includes('custom-ignore'),
  nestedOptionsSupported:
    nestedOptions.delimiters[0].left === '@@' &&
    nestedOptions.ignoredClasses.includes('nested-ignore') &&
    nestedOptions.trust === false,
  callerOptionsRemainUnchanged: JSON.stringify(directCustomSource) === directCustomSnapshot,
  defaultErrorPolicy:
    defaultOptions.throwOnError === false &&
    typeof defaultOptions.errorCallback === 'function',
  installedKatexVersion: katex.version === '0.17.0',
  renderToString:
    renderedMath.includes('class="katex"') &&
    renderedMath.includes('<math') &&
    renderedMath.includes('katex-html'),
}

console.log(JSON.stringify({
  summary,
  dependencies,
  boundaries,
  smoke,
  findings,
}, null, 2))
