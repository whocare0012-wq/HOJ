import { createApp } from 'vue'
import App from './App.vue'
import store from './store'
import Element from 'element-plus'
import i18n, { i18nPlugin } from '@/i18n'

import 'element-plus/dist/index.css'
import 'font-awesome/css/font-awesome.min.css'
import '@/styles/legacy-element-icons.css'
import axios from 'axios'

import markdownRenderer from '@/common/markdown'

import VXETable from 'vxe-table'
import 'vxe-table/lib/style.css'
import VxeUI from 'vxe-pc-ui'
import 'vxe-pc-ui/lib/style.css'

import Katex from '@/common/katex'

import { clipboardDirective, copyText } from '@/common/clipboard'

import highlight from '@/common/highlight'

import filters from '@/common/filters.js'
import legacyElementIcons from '@/common/element-icons'
import VueECharts from 'vue-echarts'
import { use } from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import {
  AriaComponent,
  DataZoomComponent,
  GridComponent,
  LegendComponent,
  MarkPointComponent,
  TitleComponent,
  ToolboxComponent,
  TooltipComponent
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

use([
  BarChart,
  LineChart,
  PieChart,
  AriaComponent,
  DataZoomComponent,
  GridComponent,
  LegendComponent,
  MarkPointComponent,
  TitleComponent,
  ToolboxComponent,
  TooltipComponent,
  CanvasRenderer
])


import VueParticles from '@/components/common/VueParticles.vue'
import SlideVerify from '@/components/common/SlideVerify.vue'

import VueDOMPurifyHTML from 'vue-dompurify-html'

import router from './router'

const app = createApp(App)

app.use(store)
app.use(router)
app.use(i18nPlugin)
app.config.globalProperties.legacyElementIcons = legacyElementIcons
app.use(VueDOMPurifyHTML)
app.component('ECharts', VueECharts)
app.config.globalProperties.$filters = filters
app.component('vue-particles', VueParticles) // 粒子特效背景
app.use(Katex)  // 数学公式渲染
VXETable.setup({
  // 对组件内置的提示语进行国际化翻译
  i18n: (key, value) => i18n.t(key, value)
})
app.use(VxeUI)
app.use(VXETable) // 表格组件
app.directive('clipboard', clipboardDirective)
app.config.globalProperties.$copyText = copyText
app.use(highlight)
app.use(Element)

app.component('slide-verify', SlideVerify)

app.config.globalProperties.$axios = axios
// Keep compatibility with legacy asynchronously loaded components that still
// call this.$http. This also protects users with an older component chunk in
// their browser cache after the main application bundle has been updated.
app.config.globalProperties.$http = axios
app.config.globalProperties.$markDown = markdownRenderer

app.mount('#app')
