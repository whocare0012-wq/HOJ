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

function countMatches(source, pattern) {
  return [...source.matchAll(pattern)].length
}

function lineNumberAt(source, offset) {
  return source.slice(0, offset).split(/\r?\n/).length
}

const packageJsonPath = path.join(frontendRoot, 'package.json')
const packageLockPath = path.join(frontendRoot, 'package-lock.json')
const adapterPath = path.join(sourceRoot, 'components', 'common', 'VueCropperAdapter.mjs')
const userInfoPath = path.join(sourceRoot, 'components', 'oj', 'setting', 'UserInfo.vue')
const groupSettingPath = path.join(sourceRoot, 'views', 'oj', 'group', 'children', 'GroupSetting.vue')
const editorPath = path.join(sourceRoot, 'components', 'admin', 'Editor.vue')

for (const requiredFile of [
  packageJsonPath,
  packageLockPath,
  adapterPath,
  userInfoPath,
  groupSettingPath,
  editorPath,
]) {
  if (!fs.existsSync(requiredFile)) {
    throw new Error('Required stage-nineteen file is missing: ' + requiredFile)
  }
}

const packageJson = JSON.parse(fs.readFileSync(packageJsonPath, 'utf8'))
const packageLock = JSON.parse(fs.readFileSync(packageLockPath, 'utf8'))
const adapterSource = fs.readFileSync(adapterPath, 'utf8')
const userInfoSource = fs.readFileSync(userInfoPath, 'utf8')
const groupSettingSource = fs.readFileSync(groupSettingPath, 'utf8')
const editorSource = fs.readFileSync(editorPath, 'utf8')
const cropperConsumers = [userInfoSource, groupSettingSource]

const summary = {
  cropperInstances: 0,
  validCropperTemplates: 0,
  adapterImports: 0,
  directPackageImports: 0,
  cropperCssImports: 0,
  cropDataCalls: 0,
  cropBlobCalls: 0,
  rotateLeftCalls: 0,
  rotateRightCalls: 0,
  userUploadEndpoints: 0,
  groupUploadEndpoints: 0,
  cropperJsImports: 0,
  forbiddenNextSourceUsages: 0,
  malformedSfcFiles: 0,
}
const findings = Object.fromEntries(Object.keys(summary).map((key) => [key, []]))
const templateTokens = [
  '<VueCropper',
  'ref="cropper"',
  'auto-crop',
  'fixed',
  ':auto-crop-width="200"',
  ':auto-crop-height="200"',
  ':img="avatarOption.imgSrc"',
  ':output-size="avatarOption.size"',
  ':output-type="avatarOption.outputType"',
  ':info="true"',
  '@real-time="realTime"',
]

for (const filePath of findFiles(sourceRoot, new Set(['.js', '.mjs', '.vue']))) {
  const source = fs.readFileSync(filePath, 'utf8')
  const relativePath = path.relative(repoRoot, filePath)
  const sourceChecks = [
    ['adapterImports', /from ['"]@\/components\/common\/VueCropperAdapter\.mjs['"]/g],
    ['directPackageImports', /from ['"]vue-cropper['"]/g],
    ['cropperCssImports', /['"]vue-cropper\/dist\/index\.css['"]/g],
    ['cropDataCalls', /\$refs\.cropper\.getCropData\(/g],
    ['cropBlobCalls', /\$refs\.cropper\.getCropBlob\(/g],
    ['rotateLeftCalls', /\$refs\.cropper\.rotateLeft\(/g],
    ['rotateRightCalls', /\$refs\.cropper\.rotateRight\(/g],
    ['userUploadEndpoints', /['"]\/api\/file\/upload-avatar['"]/g],
    ['groupUploadEndpoints', /['"]\/api\/file\/upload-group-avatar['"]/g],
    ['cropperJsImports', /from ['"]cropperjs['"]/g],
    ['forbiddenNextSourceUsages', /cropper-next-vue/g],
  ]

  for (const [property, pattern] of sourceChecks) {
    for (const match of source.matchAll(pattern)) {
      summary[property] += 1
      findings[property].push(relativePath + ':' + lineNumberAt(source, match.index))
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
  const ast = parseTemplate(parsedSfc.descriptor.template.content)

  function walk(node) {
    if (node.type !== 1) {
      return
    }
    if (node.tag.toLowerCase() === 'vuecropper') {
      summary.cropperInstances += 1
      findings.cropperInstances.push(relativePath + ':' + node.loc.start.line)
      if (templateTokens.every((token) => node.loc.source.includes(token))) {
        summary.validCropperTemplates += 1
        findings.validCropperTemplates.push(relativePath + ':' + node.loc.start.line)
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
const dependencyValues = Object.fromEntries(
  dependencySections.map((section) => [section, {
    vueCropper: packageJson[section]?.['vue-cropper'] ?? null,
    cropperJs: packageJson[section]?.cropperjs ?? null,
    cropperNextVue: packageJson[section]?.['cropper-next-vue'] ?? null,
  }])
)
const packageLockRoot = packageLock.packages?.[''] ?? {}
const lockedVueCropper = packageLock.packages?.['node_modules/vue-cropper'] ?? null
const lockedCropperJs = packageLock.packages?.['node_modules/cropperjs'] ?? null
const lockedCropperNextVue = packageLock.packages?.['node_modules/cropper-next-vue'] ?? null

function hasAll(source, patterns) {
  return patterns.every((pattern) => pattern.test(source))
}

const commonConsumerContract = (source) => hasAll(source, [
  /import VueCropper from '@\/components\/common\/VueCropperAdapter\.mjs'/,
  /components:\s*\{[\s\S]*?VueCropper[\s\S]*?\}/,
  /avatarOption:\s*\{\s*imgSrc:\s*'',\s*size:\s*0\.8,\s*outputType:\s*'png'/s,
  /this\.preview = data/,
  /this\.\$refs\.cropper\.rotateLeft\(\)/,
  /this\.\$refs\.cropper\.rotateRight\(\)/,
  /this\.\$refs\.cropper\.getCropData\(\(data\) =>/,
  /this\.uploadImgSrc = data/,
  /this\.\$refs\.cropper\.getCropBlob\(\(blob\) =>/,
  /new window\.FormData\(\)/,
  /new window\.File\(\s*\[blob\],\s*'avatar\.' \+ this\.avatarOption\.outputType/s,
  /form\.append\('image', file\)/,
  /headers:\s*\{ 'content-type': 'multipart\/form-data' \}/,
  /file\.size > 2 \* 1024 \* 1024/,
  /reader\.readAsDataURL\(file\)/,
  /width:\s*min\(400px, 100%\)/,
])

const dependencies = {
  declaredVueCropper: packageJson.dependencies?.['vue-cropper'] ?? null,
  declaredCropperJs: packageJson.dependencies?.cropperjs ?? null,
  lockedVueCropperVersion: lockedVueCropper?.version ?? null,
  lockedCropperJsVersion: lockedCropperJs?.version ?? null,
  forbiddenNextDeclarations: dependencySections.filter(
    (section) => packageJson[section]?.['cropper-next-vue']
  ),
  forbiddenNextLockEntry: lockedCropperNextVue ? 'node_modules/cropper-next-vue' : null,
  dependencyValues,
}

const boundaries = {
  exactVueCropperDependency:
    packageJson.dependencies?.['vue-cropper'] === '1.1.4' &&
    packageLockRoot.dependencies?.['vue-cropper'] === '1.1.4' &&
    lockedVueCropper?.version === '1.1.4',
  preservedEditorCropperDependency:
    packageJson.dependencies?.cropperjs === '1.6.2' &&
    packageLockRoot.dependencies?.cropperjs === '1.6.2' &&
    lockedCropperJs?.version === '1.6.2',
  adapterBoundary: hasAll(adapterSource, [
    /import \{ VueCropper \} from 'vue-cropper'/,
    /import 'vue-cropper\/dist\/index\.css'/,
    /VueCropper\.compatConfig = \{/,
    /\.\.\.\(VueCropper\.compatConfig \|\| \{\}\)/,
    /MODE:\s*3/,
    /export default VueCropper/,
  ]),
  userCropperContract: commonConsumerContract(userInfoSource),
  groupCropperContract:
    commonConsumerContract(groupSettingSource) &&
    /form\.append\('gid', this\.\$route\.params\.groupID\)/.test(groupSettingSource),
  preservedUploadEndpoints:
    /method:\s*'post',[\s\S]*?url:\s*'\/api\/file\/upload-avatar'/.test(userInfoSource) &&
    /method:\s*'post',[\s\S]*?url:\s*'\/api\/file\/upload-group-avatar'/.test(groupSettingSource),
  preservedPreviewContract: cropperConsumers.every((source) =>
    /:style="preview\.div"/.test(source) && /:style="preview\.img"/.test(source)
  ),
  preservedEditorBoundary:
    /import Cropper from 'cropperjs'/.test(editorSource) &&
    /import 'cropperjs\/dist\/cropper\.css'/.test(editorSource) &&
    /cropper:\s*\{\s*instance:\s*Cropper\s*\}/s.test(editorSource),
  noForbiddenNextPackage:
    dependencies.forbiddenNextDeclarations.length === 0 &&
    dependencies.forbiddenNextLockEntry === null,
}

console.log(JSON.stringify({
  summary,
  dependencies,
  boundaries,
  findings,
}, null, 2))
