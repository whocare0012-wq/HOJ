<template>
  <div
    class="markdown-editor"
    :class="{ 'markdown-editor--auto-grow': autoGrow }"
  >
    <MdEditor
      ref="md"
      :model-value="currentValue"
      :style="editorStyle"
      :language="editorLanguage"
      :toolbars="editorToolbars"
      :preview="openHtml"
      input-box-width="50%"
      :no-upload-img="!canUploadImages"
      :no-mermaid="true"
      :no-katex="true"
      :no-prettier="true"
      :no-echarts="true"
      :code-foldable="false"
      :auto-focus="false"
      :onChange="handleEditorChange"
      preview-theme="default"
      code-theme="arduino"
      @onHtmlChanged="handlePreviewChange"
      @update:model-value="currentValue = $event"
      @onUploadImg="uploadImages"
    >
      <template #defToolbars>
        <NormalToolbar
          v-if="isAdminRole"
          :title="$t('m.Upload_file')"
          @onClick="uploadFile"
        >
          <i
            :class="[
              'fa',
              loadingFileUpload ? 'fa-spinner fa-spin' : 'fa-upload',
              'md-editor-icon'
            ]"
            aria-hidden="true"
          ></i>
        </NormalToolbar>
      </template>
    </MdEditor>
    <input
      ref="uploadInput"
      style="display: none"
      type="file"
      @change="uploadFileChange"
    />
  </div>
</template>

<script>
import { mapGetters } from 'vuex'
import Cropper from 'cropperjs'
import hljs from 'highlight.js'
import {
  MdEditor,
  NormalToolbar,
  config as configureMdEditor
} from 'md-editor-v3'
import 'cropperjs/dist/cropper.css'
import 'md-editor-v3/lib/style.css'
import { configureMarkdownEditor } from '@/common/markdown'

configureMdEditor({
  editorConfig: {
    renderDelay: 0
  },
  editorExtensions: {
    highlight: {
      instance: hljs
    },
    cropper: {
      instance: Cropper
    }
  },
  markdownItConfig(markdown) {
    configureMarkdownEditor(markdown)
  }
})

export default {
  name: 'Editor',
  components: {
    MdEditor,
    NormalToolbar
  },
  props: {
    value: {
      type: String,
      default: ''
    },
    openHtml: {
      type: Boolean,
      default: true
    },
    autoGrow: {
      type: Boolean,
      default: false
    }
  },
  data() {
    return {
      currentValue: this.normalizeValue(this.value),
      editorHeight: 300,
      autoHeightFrame: null,
      autoHeightMutationObserver: null,
      autoHeightResizeObserver: null,
      editorWasVisible: false,
      uploadedImages: {},
      loadingFileUpload: false,
      toolbars: [
        'bold',
        'underline',
        'italic',
        '-',
        'title',
        'strikeThrough',
        'sub',
        'sup',
        'quote',
        'unorderedList',
        'orderedList',
        'task',
        '-',
        'codeRow',
        'code',
        'link',
        'image',
        'table',
        'katex',
        '-',
        'revoke',
        'next',
        'save',
        '=',
        'pageFullscreen',
        'preview',
        'previewOnly',
        'htmlPreview',
        'catalog'
      ]
    }
  },
  computed: {
    ...mapGetters(['isAdminRole', 'isGroupAdmin', 'webLanguage']),
    editorToolbars() {
      if (!this.isAdminRole) {
        return this.toolbars
      }
      const toolbars = [...this.toolbars]
      toolbars.splice(toolbars.indexOf('='), 0, 0)
      return toolbars
    },
    canUploadImages() {
      return this.isAdminRole || this.isGroupAdmin
    },
    editorLanguage() {
      return this.webLanguage === 'en-US' ? 'en-US' : 'zh-CN'
    },
    editorStyle() {
      return {
        height: `${this.autoGrow ? this.editorHeight : 300}px`
      }
    }
  },
  watch: {
    value(value) {
      const normalizedValue = this.normalizeValue(value)
      if (this.currentValue !== normalizedValue) {
        this.currentValue = normalizedValue
      }
      this.$nextTick(this.rerenderPreview)
    },
    currentValue(value, previousValue) {
      if (value === previousValue) {
        return
      }
      this.$emit('update:value', value)
      this.deleteRemovedImages(value, previousValue)
      this.scheduleAutoHeight()
    }
  },
  mounted() {
    this.setupAutoHeightObservers()
    this.scheduleAutoHeight()
    window.addEventListener('resize', this.scheduleAutoHeight)
  },
  beforeUnmount() {
    window.removeEventListener('resize', this.scheduleAutoHeight)
    this.autoHeightMutationObserver?.disconnect()
    this.autoHeightResizeObserver?.disconnect()
    if (this.autoHeightFrame !== null) {
      window.cancelAnimationFrame(this.autoHeightFrame)
    }
  },
  methods: {
    normalizeValue(value) {
      if (value === null || value === undefined) {
        return ''
      }
      return typeof value === 'string' ? value : String(value)
    },
    handleEditorChange(value) {
      this.currentValue = this.normalizeValue(value)
    },
    handlePreviewChange() {
      this.scheduleAutoHeight()
    },
    setupAutoHeightObservers() {
      if (!this.autoGrow || this.autoHeightMutationObserver) {
        return
      }
      const editor = this.$refs.md?.$el
      if (!editor) {
        this.$nextTick(this.setupAutoHeightObservers)
        return
      }
      this.autoHeightMutationObserver = new window.MutationObserver(
        this.scheduleAutoHeight
      )
      this.autoHeightMutationObserver.observe(editor, {
        childList: true,
        subtree: true,
        characterData: true
      })
      if (window.ResizeObserver) {
        this.autoHeightResizeObserver = new window.ResizeObserver(
          this.handleAutoHeightResize
        )
        this.autoHeightResizeObserver.observe(editor)
        const inputContent = editor.querySelector('.cm-content')
        const previewContent = editor.querySelector('.md-editor-preview')
        if (inputContent) {
          this.autoHeightResizeObserver.observe(inputContent)
        }
        if (previewContent) {
          this.autoHeightResizeObserver.observe(previewContent)
        }
      }
      this.handleAutoHeightResize()
    },
    handleAutoHeightResize() {
      this.scheduleAutoHeight()
      const editor = this.$refs.md?.$el
      const isVisible = Boolean(
        editor &&
        editor.getBoundingClientRect().width > 0 &&
        editor.getBoundingClientRect().height > 0
      )
      if (isVisible && !this.editorWasVisible) {
        this.editorWasVisible = true
        this.$nextTick(this.rerenderPreview)
      } else if (!isVisible) {
        this.editorWasVisible = false
      }
    },
    rerenderPreview() {
      const editor = this.$refs.md?.$el
      if (!editor || editor.getBoundingClientRect().width <= 0) {
        return
      }
      this.$refs.md?.rerender?.()
      this.scheduleAutoHeight()
    },
    scheduleAutoHeight() {
      if (!this.autoGrow) {
        return
      }
      this.$nextTick(() => {
        if (this.autoHeightFrame !== null) {
          window.cancelAnimationFrame(this.autoHeightFrame)
        }
        this.autoHeightFrame = window.requestAnimationFrame(() => {
          this.autoHeightFrame = null
          this.updateAutoHeight()
        })
      })
    },
    updateAutoHeight() {
      const editor = this.$refs.md?.$el
      const content = editor?.querySelector('.md-editor-content')
      if (!editor || !content) {
        return
      }
      const input = editor.querySelector('.cm-scroller')
      const preview = editor.querySelector('.md-editor-preview-wrapper')
      const contentHeight = Math.max(
        content.clientHeight,
        input?.scrollHeight || 0,
        preview?.scrollHeight || 0
      )
      const chromeHeight = editor.offsetHeight - content.clientHeight
      const nextHeight = Math.max(
        300,
        Math.ceil(chromeHeight + contentHeight)
      )
      if (nextHeight > this.editorHeight) {
        this.editorHeight = nextHeight
        this.$nextTick(() => {
          this.autoHeightFrame = window.requestAnimationFrame(() => {
            this.autoHeightFrame = null
            this.updateAutoHeight()
          })
        })
      }
    },
    getGroupId() {
      return this.$route.params.groupID
    },
    createUploadForm(field, file) {
      const formData = new FormData()
      formData.append(field, file)
      const groupId = this.getGroupId()
      if (groupId !== null && groupId !== undefined) {
        formData.append('gid', groupId)
      }
      return formData
    },
    async uploadImage(file) {
      const response = await this.$axios({
        url: '/api/file/upload-md-img',
        method: 'post',
        data: this.createUploadForm('image', file),
        headers: { 'Content-Type': 'multipart/form-data' }
      })
      const uploaded = response.data.data
      this.uploadedImages[uploaded.link] = uploaded.fileId
      return {
        url: uploaded.link,
        alt: file.name,
        title: file.name
      }
    },
    async uploadImages(files, callback) {
      if (!this.canUploadImages) {
        callback([])
        return
      }
      try {
        callback(await Promise.all(files.map((file) => this.uploadImage(file))))
      } catch (_) {
        callback([])
      }
    },
    deleteRemovedImages(value, previousValue) {
      if (!previousValue) {
        return
      }
      Object.entries(this.uploadedImages).forEach(([url, fileId]) => {
        if (previousValue.includes(url) && !value.includes(url)) {
          delete this.uploadedImages[url]
          this.$axios({
            url: '/api/file/delete-md-img',
            method: 'get',
            params: { fileId }
          }).catch(() => {})
        }
      })
    },
    uploadFile() {
      this.$refs.uploadInput.click()
    },
    async uploadFileChange(event) {
      const file = event.target.files?.[0]
      if (!file) {
        return
      }
      this.loadingFileUpload = true
      try {
        const response = await this.$axios({
          url: '/api/file/upload-md-file',
          method: 'post',
          data: this.createUploadForm('file', file),
          headers: { 'Content-Type': 'multipart/form-data' }
        })
        const label = file.name.replaceAll(']', '\\]')
        const targetValue = '[' + label + '](' + response.data.data.link + ')'
        this.$refs.md.insert(() => ({
          targetValue,
          select: false
        }))
      } finally {
        this.loadingFileUpload = false
        event.target.value = ''
      }
    }
  }
}
</script>

<style>
.markdown-editor {
  width: 100%;
}
.markdown-editor .md-editor {
  min-height: 300px;
  border: 1px solid #dcdfe6;
}
/* Arduino Light theme, scoped so the editor does not override article code. */
.markdown-editor .hljs,
.markdown-editor .hljs-subst {
  color: #434f54;
}
.markdown-editor .hljs-keyword,
.markdown-editor .hljs-attribute,
.markdown-editor .hljs-selector-tag,
.markdown-editor .hljs-doctag,
.markdown-editor .hljs-name {
  color: #00979d;
}
.markdown-editor .hljs-built_in,
.markdown-editor .hljs-literal,
.markdown-editor .hljs-bullet,
.markdown-editor .hljs-code,
.markdown-editor .hljs-addition {
  color: #d35400;
}
.markdown-editor .hljs-regexp,
.markdown-editor .hljs-symbol,
.markdown-editor .hljs-variable,
.markdown-editor .hljs-template-variable,
.markdown-editor .hljs-link,
.markdown-editor .hljs-selector-attr,
.markdown-editor .hljs-selector-pseudo {
  color: #00979d;
}
.markdown-editor .hljs-type,
.markdown-editor .hljs-string,
.markdown-editor .hljs-selector-id,
.markdown-editor .hljs-selector-class,
.markdown-editor .hljs-quote,
.markdown-editor .hljs-template-tag,
.markdown-editor .hljs-deletion {
  color: #005c5f;
}
.markdown-editor .hljs-title,
.markdown-editor .hljs-section {
  color: #880000;
  font-weight: 700;
}
.markdown-editor .hljs-comment {
  color: rgba(149, 165, 166, 0.8);
}
.markdown-editor .hljs-meta-keyword {
  color: #728e00;
}
.markdown-editor .hljs-meta {
  color: #434f54;
}
.markdown-editor .hljs-emphasis {
  font-style: italic;
}
.markdown-editor .hljs-strong {
  font-weight: 700;
}
.markdown-editor .hljs-function {
  color: #728e00;
}
.markdown-editor .hljs-number {
  color: #8a7b52;
}
.markdown-editor .md-editor-preview .md-editor-code {
  margin: 10px 0;
}
.markdown-editor
  .md-editor-preview
  .md-editor-code
  .md-editor-code-head {
  display: block;
  position: static;
  height: 0;
  overflow: hidden;
  opacity: 0;
  pointer-events: none;
}
.markdown-editor .md-editor-preview .md-editor-code pre {
  overflow: hidden;
  margin: 0;
  border: 1px solid #bfc7d2;
  border-radius: 3px;
  background: #fff;
}
.markdown-editor .md-editor-preview .md-editor-code pre code {
  box-sizing: border-box;
  min-height: 52px;
  padding-block: 13px;
  padding-inline-start: 50px !important;
  padding-inline-end: 12px;
  border-radius: 0;
  color: #434f54;
  background: #fff;
  line-height: 26px;
}
.markdown-editor
  .md-editor-preview.md-editor-scrn
  .md-editor-code
  span[rn-wrapper] {
  inset-block-start: 0;
  box-sizing: border-box;
  width: 40px;
  height: 100%;
  padding-block: 13px;
  border-inline-end: 1px solid #e1e4e8;
  background: #f5f6f7;
  line-height: 26px;
}
.markdown-editor
  .md-editor-preview.md-editor-scrn
  .md-editor-code
  span[rn-wrapper]
  > span::before {
  padding-inline-end: 0;
  color: #6f7782;
  text-align: center;
}
.discussion-editor-dialog .el-dialog {
  --el-dialog-padding-primary: 0px;
  padding: 0;
}
.discussion-editor-dialog .el-dialog__header {
  box-sizing: border-box;
  padding: 20px 20px 10px;
}
.discussion-editor-dialog .el-dialog__header.show-close {
  padding-right: 20px;
}
.discussion-editor-dialog .el-dialog__headerbtn {
  top: 7px;
  right: 7px;
  width: 46px;
  height: 46px;
}
.discussion-editor-dialog .el-dialog__body {
  padding: 30px 20px;
}
.discussion-editor-dialog .el-dialog__footer {
  padding: 10px 20px 20px;
}
.discussion-editor-dialog .el-dialog__footer .el-button {
  min-height: 40px;
  padding: 12px 20px;
}
.discussion-editor-dialog .el-form-item {
  margin-bottom: 22px;
}
.discussion-editor-dialog
  .el-form--label-top
  .el-form-item
  .el-form-item__label {
  height: 50px;
  margin: 0;
  padding: 0 0 10px;
  line-height: 40px;
}
.discussion-editor-dialog .el-form-item__content {
  min-height: 40px;
  line-height: 40px;
}
.discussion-editor-dialog .el-input__wrapper {
  min-height: 40px;
  padding: 1px 15px;
}
.discussion-editor-dialog .el-input__inner {
  height: 38px;
  line-height: 38px;
}
.discussion-editor-dialog .el-select__wrapper {
  min-height: 40px;
  padding: 4px 15px;
}
.discussion-editor-dialog .discussion-category-select {
  width: 217px;
}
.discussion-editor-dialog .el-switch {
  height: 20px;
}
</style>
