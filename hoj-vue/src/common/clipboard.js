export function copyText(value) {
  const text = value == null ? '' : String(value)
  if (navigator.clipboard && window.isSecureContext) {
    return navigator.clipboard.writeText(text)
  }

  return new Promise((resolve, reject) => {
    const textarea = document.createElement('textarea')
    textarea.value = text
    textarea.setAttribute('readonly', '')
    textarea.style.position = 'fixed'
    textarea.style.opacity = '0'
    document.body.appendChild(textarea)
    textarea.select()
    try {
      if (!document.execCommand('copy')) {
        throw new Error('The browser rejected the copy command.')
      }
      resolve()
    } catch (error) {
      reject(error)
    } finally {
      textarea.remove()
    }
  })
}

const stateKey = Symbol('hojClipboard')

function updateBinding(el, binding) {
  const state = el[stateKey]
  if (state && ['copy', 'success', 'error'].includes(binding.arg)) {
    state[binding.arg] = binding.value
  }
}

export const clipboardDirective = {
  beforeMount(el, binding) {
    if (!el[stateKey]) {
      const state = {
        copy: '',
        error: null,
        success: null,
        handler: null
      }
      state.handler = () => {
        copyText(state.copy).then(
          () => typeof state.success === 'function' && state.success(),
          error => typeof state.error === 'function' && state.error(error)
        )
      }
      el[stateKey] = state
      el.addEventListener('click', state.handler)
    }
    updateBinding(el, binding)
  },
  updated(el, binding) {
    updateBinding(el, binding)
  },
  unmounted(el) {
    const state = el[stateKey]
    if (state) {
      el.removeEventListener('click', state.handler)
      delete el[stateKey]
    }
  }
}

