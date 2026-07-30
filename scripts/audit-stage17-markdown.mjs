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

const requiredVersions = {
  cropperjs: '1.6.2',
  'md-editor-v3': '6.5.3',
  'markdown-it': '14.3.0',
  'markdown-it-abbr': '2.0.0',
  'markdown-it-container': '4.0.0',
  'markdown-it-deflist': '3.0.1',
  'markdown-it-emoji': '3.0.0',
  'markdown-it-footnote': '4.0.0',
  'markdown-it-ins': '4.0.0',
  'markdown-it-mark': '4.0.0',
  'markdown-it-sub': '2.0.0',
  'markdown-it-sup': '2.0.0',
  'markdown-it-task-lists': '2.1.1'
}

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

const packageJson = JSON.parse(
  fs.readFileSync(path.join(frontendRoot, 'package.json'), 'utf8')
)
const packageLockSource = fs.readFileSync(path.join(frontendRoot, 'package-lock.json'), 'utf8')
const dependencyVersions = Object.fromEntries(
  Object.keys(requiredVersions).map((name) => [name, packageJson.dependencies[name] ?? null])
)
const dependencyMismatches = Object.entries(requiredVersions)
  .filter(([name, version]) => dependencyVersions[name] !== version)
  .map(([name, version]) => name + ': expected ' + version + ', found ' + dependencyVersions[name])

const summary = {
  legacyMavonUsages: 0,
  legacyMavonLockEntries: 0,
  vulnerableTocEntries: 0,
  editorInstances: 0,
  invalidEditorModels: 0,
  markdownRendererConsumers: 0,
  externalEditorRuntimeUrls: 0
}
const findings = Object.fromEntries(Object.keys(summary).map((key) => [key, []]))

for (const filePath of findFiles(sourceRoot, new Set(['.js', '.mjs', '.vue']))) {
  const source = fs.readFileSync(filePath, 'utf8')
  const relativePath = path.relative(repoRoot, filePath)

  for (const match of source.matchAll(/mavon-editor|mavonEditor|<mavon-editor\b/gi)) {
    summary.legacyMavonUsages += 1
    findings.legacyMavonUsages.push(relativePath + ':' + lineNumberAt(source, match.index))
  }
  for (const match of source.matchAll(/\$markDown\b/g)) {
    summary.markdownRendererConsumers += 1
    findings.markdownRendererConsumers.push(relativePath + ':' + lineNumberAt(source, match.index))
  }
  for (const match of source.matchAll(/https?:\/\/(?:cdnjs|cdn\.jsdelivr|unpkg)\./g)) {
    summary.externalEditorRuntimeUrls += 1
    findings.externalEditorRuntimeUrls.push(relativePath + ':' + lineNumberAt(source, match.index))
  }

  if (!filePath.endsWith('.vue')) {
    continue
  }
  const parsedSfc = parseSfc(source, { filename: filePath })
  if (parsedSfc.errors.length > 0) {
    throw new Error('Unable to parse SFC ' + filePath + ': ' + parsedSfc.errors.join(', '))
  }
  if (!parsedSfc.descriptor.template) {
    continue
  }
  const ast = parseTemplate(parsedSfc.descriptor.template.content)

  function walk(node) {
    if (node.type !== 1) {
      return
    }
    if (node.tag.toLowerCase() === 'editor') {
      summary.editorInstances += 1
      const valueModel = node.props.find((property) =>
        property.type === 7 &&
        property.name === 'model' &&
        property.arg?.type === 4 &&
        property.arg.content === 'value'
      )
      if (!valueModel) {
        summary.invalidEditorModels += 1
        findings.invalidEditorModels.push(relativePath + ':' + node.loc.start.line)
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

for (const match of packageLockSource.matchAll(/(?:mavon-editor|node_modules\/mavon-editor)/g)) {
  summary.legacyMavonLockEntries += 1
  findings.legacyMavonLockEntries.push('hoj-vue\\package-lock.json:' + lineNumberAt(packageLockSource, match.index))
}
for (const match of packageLockSource.matchAll(/(?:markdown-it-toc|node_modules\/markdown-it-toc)/g)) {
  summary.vulnerableTocEntries += 1
  findings.vulnerableTocEntries.push('hoj-vue\\package-lock.json:' + lineNumberAt(packageLockSource, match.index))
}

const editorSource = fs.readFileSync(
  path.join(sourceRoot, 'components', 'admin', 'Editor.vue'),
  'utf8'
)
const mainSource = fs.readFileSync(path.join(sourceRoot, 'main.js'), 'utf8')
const rendererSource = fs.readFileSync(path.join(sourceRoot, 'common', 'markdown.mjs'), 'utf8')
const vue3CompatRuntimeSource = fs.readFileSync(
  path.join(sourceRoot, 'common', 'vue3-compat-runtime.mjs'),
  'utf8'
)
const viteConfigSource = fs.readFileSync(path.join(frontendRoot, 'vite.config.mjs'), 'utf8')
const boundaries = {
  nativeEditorComponent: /from 'md-editor-v3'/.test(editorSource) && /<MdEditor\b/.test(editorSource),
  explicitVue3ModelBinding: /:model-value="currentValue"/.test(editorSource) &&
    /@update:model-value="currentValue = \$event"/.test(editorSource),
  nativeVue3CompatMode: /MdEditor\.compatConfig\s*=/.test(editorSource) &&
    /MODE:\s*3/.test(editorSource),
  controlledValueFallback: /:onChange="handleEditorChange"/.test(editorSource) &&
    /handleEditorChange\(value\)/.test(editorSource),
  nativeDependencyCompatRuntime:
    /VueCompat\.defineComponent/.test(vue3CompatRuntimeSource) &&
    /MODE:\s*3/.test(vue3CompatRuntimeSource) &&
    /nativeVue3DependencyCompatPlugin/.test(viteConfigSource) &&
    /transform\(source, id\)/.test(viteConfigSource) &&
    /virtual:hoj-vue3-compat-runtime/.test(viteConfigSource) &&
    /md-editor-v3/.test(viteConfigSource),
  imageUploadContract: /\/api\/file\/upload-md-img/.test(editorSource) && /@onUploadImg=/.test(editorSource),
  imageDeleteContract: /\/api\/file\/delete-md-img/.test(editorSource),
  fileInsertContract: /\/api\/file\/upload-md-file/.test(editorSource) && /\.insert\(/.test(editorSource),
  localRuntimeBoundary: /:no-mermaid="true"/.test(editorSource) &&
    /:no-katex="true"/.test(editorSource) &&
    /:no-highlight="true"/.test(editorSource) &&
    /:no-prettier="true"/.test(editorSource) &&
    /:no-echarts="true"/.test(editorSource) &&
    /from 'cropperjs'/.test(editorSource) &&
    !/^\s*'fullscreen',\s*$/m.test(editorSource),
  globalRenderer: /\$markDown\s*=\s*markdownRenderer/.test(mainSource),
  explicitLegacyExtensions: [
    'markdown-it-abbr',
    'markdown-it-container',
    'markdown-it-deflist',
    'markdown-it-emoji',
    'markdown-it-footnote',
    'markdown-it-ins',
    'markdown-it-mark',
    'markdown-it-sub',
    'markdown-it-sup',
    'markdown-it-task-lists'
  ].every((name) => rendererSource.includes("from '" + name + "'"))
}

const { default: markdownRenderer } = await import(
  pathToFileURL(path.join(sourceRoot, 'common', 'markdown.mjs')).href
)
const smokeHtml = markdownRenderer.render(`
## Compatibility

++insert++ ==mark== H~2~ X^2^ :smile:

- [x] task

::: hljs-center
centered
:::

$a^2$

[external](https://example.com)
`)
const smoke = {
  insert: smokeHtml.includes('<ins>insert</ins>'),
  mark: smokeHtml.includes('<mark>mark</mark>'),
  subscript: smokeHtml.includes('<sub>2</sub>'),
  superscript: smokeHtml.includes('<sup>2</sup>'),
  emoji: smokeHtml.includes('😄'),
  taskList: smokeHtml.includes('contains-task-list'),
  alignment: smokeHtml.includes('class="hljs-center"'),
  katex: smokeHtml.includes('class="katex"'),
  safeExternalLink: smokeHtml.includes('target="_blank"') &&
    smokeHtml.includes('rel="noopener noreferrer"')
}

console.log(JSON.stringify({
  summary,
  dependencies: {
    required: requiredVersions,
    installed: dependencyVersions,
    mismatches: dependencyMismatches,
    forbiddenMavonDependency: packageJson.dependencies['mavon-editor'] ?? null,
    forbiddenTocDependency: packageJson.dependencies['markdown-it-toc'] ?? null
  },
  boundaries,
  smoke,
  findings
}, null, 2))
