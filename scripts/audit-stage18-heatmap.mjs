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

const legacyPackages = [
  'vue-calendar-heatmap',
  'vue-resize',
  'v-tooltip',
  'vue3-calendar-heatmap',
  'tippy.js',
]

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
const packageLock = JSON.parse(
  fs.readFileSync(path.join(frontendRoot, 'package-lock.json'), 'utf8')
)
const userHomePath = path.join(sourceRoot, 'views', 'oj', 'user', 'UserHome.vue')
const componentPath = path.join(sourceRoot, 'components', 'common', 'CalendarHeatmap.vue')
const apiPath = path.join(sourceRoot, 'common', 'api.js')
const heatmapModelPath = path.join(sourceRoot, 'common', 'calendar-heatmap.mjs')

for (const requiredFile of [userHomePath, componentPath, apiPath, heatmapModelPath]) {
  if (!fs.existsSync(requiredFile)) {
    throw new Error('Required stage-eighteen file is missing: ' + requiredFile)
  }
}

const summary = {
  legacySourceUsages: 0,
  legacyDependencyDeclarations: 0,
  legacyLockEntries: 0,
  heatmapInstances: 0,
  nativeComponentImports: 0,
  malformedSfcFiles: 0,
}
const findings = Object.fromEntries(Object.keys(summary).map((key) => [key, []]))
const legacyPattern = new RegExp(legacyPackages.map((name) => name.replace('.', '\\.')).join('|'), 'g')

for (const filePath of findFiles(sourceRoot, new Set(['.js', '.mjs', '.vue']))) {
  const source = fs.readFileSync(filePath, 'utf8')
  const relativePath = path.relative(repoRoot, filePath)

  for (const match of source.matchAll(legacyPattern)) {
    summary.legacySourceUsages += 1
    findings.legacySourceUsages.push(relativePath + ':' + lineNumberAt(source, match.index))
  }
  for (const match of source.matchAll(/from ['"]@\/components\/common\/CalendarHeatmap\.vue['"]/g)) {
    summary.nativeComponentImports += 1
    findings.nativeComponentImports.push(relativePath + ':' + lineNumberAt(source, match.index))
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
    if (node.tag.toLowerCase() === 'calendar-heatmap') {
      summary.heatmapInstances += 1
      findings.heatmapInstances.push(relativePath + ':' + node.loc.start.line)
    }
    for (const child of node.children) {
      walk(child)
    }
  }

  for (const child of ast.children) {
    walk(child)
  }
}

for (const packageName of legacyPackages) {
  for (const section of ['dependencies', 'devDependencies', 'optionalDependencies', 'peerDependencies']) {
    if (packageJson[section]?.[packageName]) {
      summary.legacyDependencyDeclarations += 1
      findings.legacyDependencyDeclarations.push(section + '.' + packageName)
    }
  }
  const packageKey = 'node_modules/' + packageName
  if (packageLock.packages?.[packageKey]) {
    summary.legacyLockEntries += 1
    findings.legacyLockEntries.push('hoj-vue\\package-lock.json:' + packageKey)
  }
}

const userHomeSource = fs.readFileSync(userHomePath, 'utf8')
const componentSource = fs.readFileSync(componentPath, 'utf8')
const apiSource = fs.readFileSync(apiPath, 'utf8')
const boundaries = {
  nativeComponentRegistration:
    /import CalendarHeatmap from '@\/components\/common\/CalendarHeatmap\.vue'/.test(userHomeSource) &&
    /components:\s*\{[\s\S]*?CalendarHeatmap[\s\S]*?\}/.test(userHomeSource),
  existingTemplateContract:
    /:values="calendarHeatmapValue"/.test(userHomeSource) &&
    /:end-date="calendarHeatmapEndDate"/.test(userHomeSource) &&
    /:tooltip-unit="\$t\('m\.Calendar_Tooltip_Uint'\)"/.test(userHomeSource) &&
    /:locale="calendarHeatLocale"/.test(userHomeSource) &&
    /:range-color=/.test(userHomeSource),
  existingResponseContract:
    /calendarHeatmapValue\s*=\s*res\.data\.data\.dataList/.test(userHomeSource) &&
    /calendarHeatmapEndDate\s*=\s*res\.data\.data\.endDate/.test(userHomeSource),
  reactiveRootState:
    /\n\s{6}calendarHeatLocale:null,/.test(userHomeSource) &&
    /\n\s{6}calendarHeatmapValue:\[\],/.test(userHomeSource) &&
    /\n\s{6}calendarHeatmapEndDate:'',/.test(userHomeSource) &&
    /\n\s{6}loadingCalendarHeatmap:false,/.test(userHomeSource),
  readOnlyApiContract:
    /getUserCalendarHeatmap\(uid, username\)/.test(apiSource) &&
    /ajax\("\/api\/get-user-calendar-heatmap", 'get'/.test(apiSource),
  nativeVue3Mode:
    /name:\s*'CalendarHeatmap'/.test(componentSource) &&
    /compatConfig:\s*\{\s*MODE:\s*3/s.test(componentSource),
  svgAndTooltipContract:
    /<svg[\s\S]*?role="img"/.test(componentSource) &&
    /class="vch__day__square"/.test(componentSource) &&
    /<title v-if="day\.visible">/.test(componentSource) &&
    /:data-date="day\.visible \? day\.key/.test(componentSource) &&
    /:data-count="day\.visible \? day\.count/.test(componentSource),
  responsiveBoundary:
    /overflow-x:\s*auto/.test(componentSource) &&
    /min-width:\s*620px/.test(componentSource) &&
    /width:\s*100%/.test(componentSource),
}

const heatmapModel = await import(pathToFileURL(heatmapModelPath).href)
const heatmap = heatmapModel.buildCalendarHeatmap({
  endDate: '2024-03-01',
  values: [
    { date: '2024-03-01', count: 10 },
    { date: '2024-02-29', count: '2' },
    { date: '2024-02-28', count: -5 },
    { date: 'invalid', count: 100 },
  ],
})
const flattenedDays = heatmap.calendar.flat()
const startKey = heatmapModel.calendarDateKey(heatmap.startDate)
const endKey = heatmapModel.calendarDateKey(heatmap.endDate)
const leapDay = flattenedDays.find((day) => day.key === '2024-02-29')
const endDay = flattenedDays.find((day) => day.key === '2024-03-01')
const negativeDay = flattenedDays.find((day) => day.key === '2024-02-28')
const monthLabels = heatmapModel.getCalendarMonthLabels(heatmap.calendar)
const smoke = {
  localDateOnlyParsing:
    heatmapModel.calendarDateKey(heatmapModel.parseCalendarDate('2024-02-29')) === '2024-02-29',
  stableYearRange: startKey === '2023-03-02' && endKey === '2024-03-01',
  completeWeekGrid: heatmap.calendar.length === 53 && flattenedDays.length === 371,
  inclusiveDayRange: flattenedDays.filter((day) => day.inRange).length === 366,
  legacyMaximumScale: heatmap.maximumCount === 8,
  countNormalization:
    leapDay?.count === 2 && leapDay.colorIndex === 1 &&
    endDay?.count === 10 && endDay.colorIndex === 4 &&
    negativeDay?.count === 0 && negativeDay.colorIndex === 0,
  invalidDatesIgnored: heatmap.activityByDate.size === 3,
  localizedMonthSlots: monthLabels.length === 12,
}

console.log(JSON.stringify({
  summary,
  forbiddenPackages: legacyPackages,
  boundaries,
  smoke,
  findings,
}, null, 2))
