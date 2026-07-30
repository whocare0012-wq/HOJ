export const DEFAULT_KATEX_DELIMITERS = Object.freeze([
  Object.freeze({ left: '$$', right: '$$', display: true }),
  Object.freeze({ left: '$', right: '$', display: false }),
  Object.freeze({ left: '\\(', right: '\\)', display: false }),
  Object.freeze({ left: '\\[', right: '\\]', display: true })
])

export const DEFAULT_KATEX_IGNORED_TAGS = Object.freeze([
  'script',
  'noscript',
  'style',
  'textarea',
  'pre',
  'code',
  'option'
])

export const DEFAULT_KATEX_IGNORED_CLASSES = Object.freeze([
  'katex',
  'katex-display'
])

function ignoreRenderError() {}

function copyDelimiters(delimiters) {
  return delimiters.map((delimiter) => ({ ...delimiter }))
}

function directiveOptions(bindingValue) {
  if (!bindingValue || typeof bindingValue !== 'object' || Array.isArray(bindingValue)) {
    return {}
  }
  const candidate = bindingValue.options ?? bindingValue
  return candidate && typeof candidate === 'object' && !Array.isArray(candidate)
    ? candidate
    : {}
}

export function createKatexOptions(bindingValue) {
  const customOptions = directiveOptions(bindingValue)
  const customIgnoredClasses = Array.isArray(customOptions.ignoredClasses)
    ? customOptions.ignoredClasses
    : []

  return {
    ...customOptions,
    delimiters: copyDelimiters(
      Array.isArray(customOptions.delimiters)
        ? customOptions.delimiters
        : DEFAULT_KATEX_DELIMITERS
    ),
    ignoredTags: Array.isArray(customOptions.ignoredTags)
      ? [...customOptions.ignoredTags]
      : [...DEFAULT_KATEX_IGNORED_TAGS],
    ignoredClasses: [...new Set([
      ...DEFAULT_KATEX_IGNORED_CLASSES,
      ...customIgnoredClasses
    ])],
    errorCallback: typeof customOptions.errorCallback === 'function'
      ? customOptions.errorCallback
      : ignoreRenderError,
    throwOnError: customOptions.throwOnError ?? false,
    // All current call sites render user- or administrator-controlled text.
    // KaTeX trust mode can enable commands that affect HTML or load resources.
    trust: false
  }
}
