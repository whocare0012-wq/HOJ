import MarkdownIt from 'markdown-it'
import { escapeHtml } from 'markdown-it/lib/common/utils.mjs'
import markdownItAbbr from 'markdown-it-abbr'
import markdownItContainer from 'markdown-it-container'
import markdownItDeflist from 'markdown-it-deflist'
import { full as markdownItEmoji } from 'markdown-it-emoji'
import markdownItFootnote from 'markdown-it-footnote'
import markdownItIns from 'markdown-it-ins'
import markdownItMark from 'markdown-it-mark'
import markdownItSub from 'markdown-it-sub'
import markdownItSup from 'markdown-it-sup'
import markdownItTaskLists from 'markdown-it-task-lists'
import markdownItKatex from '@iktakahiro/markdown-it-katex'
import hljs from 'highlight.js'

const markdownOptions = {
  html: true,
  xhtmlOut: true,
  breaks: true,
  langPrefix: 'lang-',
  linkify: false,
  typographer: true,
  quotes: '“”‘’'
}

function addExternalLinkTargets(markdown) {
  const defaultLinkOpen = markdown.renderer.rules.link_open ||
    ((tokens, index, options, environment, renderer) =>
      renderer.renderToken(tokens, index, options))

  markdown.renderer.rules.link_open = (tokens, index, options, environment, renderer) => {
    const href = tokens[index].attrGet('href') || ''
    if (!href.startsWith('#')) {
      tokens[index].attrSet('target', '_blank')
      tokens[index].attrSet('rel', 'noopener noreferrer')
    }
    return defaultLinkOpen(tokens, index, options, environment, renderer)
  }
}

function addSharedLegacyExtensions(markdown) {
  markdown
    .use(markdownItEmoji)
    .use(markdownItDeflist)
    .use(markdownItAbbr)
    .use(markdownItFootnote)
    .use(markdownItIns)
    .use(markdownItMark)
    .use(markdownItContainer, 'hljs-left')
    .use(markdownItContainer, 'hljs-center')
    .use(markdownItContainer, 'hljs-right')

  addExternalLinkTargets(markdown)
  return markdown
}

function highlightCode(source, language) {
  if (!language || !hljs.getLanguage(language)) {
    return ''
  }
  const highlighted = hljs.highlight(source, {
    language,
    ignoreIllegals: true
  }).value
  return '<pre><code class="hljs language-' + escapeHtml(language) + '">' +
    highlighted + '</code></pre>'
}

export function configureMarkdownEditor(markdown) {
  addSharedLegacyExtensions(markdown)
  markdown.use(markdownItKatex)
}

export function createMarkdownRenderer() {
  const markdown = new MarkdownIt({
    ...markdownOptions,
    highlight: highlightCode
  })

  addSharedLegacyExtensions(markdown)
  markdown
    .use(markdownItSup)
    .use(markdownItSub)
    .use(markdownItKatex)
    .use(markdownItTaskLists)

  return markdown
}

const markdownRenderer = createMarkdownRenderer()

export default markdownRenderer
