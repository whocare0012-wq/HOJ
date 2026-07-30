<template>
  <div class="vue-codemirror-wrap">
    <textarea></textarea>
  </div>
</template>

<script>
import { markRaw } from 'vue'
import CodeMirror from 'codemirror/lib/codemirror.js'
import 'codemirror/lib/codemirror.css'

export default {
  name: 'CodeMirrorAdapter',
  emits: ['change', 'input', 'ready'],
  props: {
    value: { type: String, default: '' },
    options: {
      type: Object,
      default: () => ({ mode: 'text/javascript', lineNumbers: true, lineWrapping: true })
    }
  },
  data() {
    return { editor: null, skipNextChangeEvent: false }
  },
  mounted() {
    const textarea = this.$el.querySelector('textarea')
    textarea.value = typeof this.value === 'string' ? this.value : ''
    this.editor = markRaw(CodeMirror.fromTextArea(textarea, this.options))
    this.editor.on('change', (editor) => {
      if (this.skipNextChangeEvent) {
        this.skipNextChangeEvent = false
        return
      }
      const value = editor.getValue()
      this.$emit('change', value)
      this.$emit('input', value)
    })
    this.$emit('ready', this.editor)
  },
  watch: {
    value(newValue) {
      if (!this.editor || newValue === this.editor.getValue()) return
      this.skipNextChangeEvent = true
      const scrollInfo = this.editor.getScrollInfo()
      this.editor.setValue(newValue)
      this.editor.scrollTo(scrollInfo.left, scrollInfo.top)
    },
    options: {
      deep: true,
      handler(newOptions) {
        if (!this.editor) return
        Object.keys(newOptions).forEach((name) => this.editor.setOption(name, newOptions[name]))
      }
    }
  },
  beforeUnmount() {
    if (this.editor) {
      this.editor.toTextArea()
      this.editor = null
    }
  }
}
</script>

<style>
.CodeMirror-code {
  font-family: Menlo, Monaco, Consolas, "Courier New", monospace;
}
</style>
