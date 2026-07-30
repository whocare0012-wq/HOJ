import hljs from 'highlight.js'
import 'highlight.js/styles/atom-one-light.css'

export default {
  install (app) {
    const highlight = function (el, binding) {
      Array.from(el.querySelectorAll('pre code')).forEach((target) => {
        if (binding.value) {
          target.textContent = binding.value
        }
        target.removeAttribute('data-highlighted')
        hljs.highlightElement(target)
      })
    }
    app.directive('highlight', {
      deep: true,
      beforeMount: highlight,
      updated: highlight
    })
  }
}
