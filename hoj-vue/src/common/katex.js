import renderMathInElement from 'katex/contrib/auto-render'
import 'katex/dist/katex.min.css'
import { createKatexOptions } from '@/common/katex-options.mjs'

export function renderKatex (el, binding = {}) {
  renderMathInElement(el, createKatexOptions(binding.value))
}

export const katexDirective = {
  mounted: renderKatex,
  updated: renderKatex
}

export default {
  install (app) {
    app.directive('katex', katexDirective)
  }
}
