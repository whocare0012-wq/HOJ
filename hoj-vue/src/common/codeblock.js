import myMessage from '@/common/message';
import { copyText } from '@/common/clipboard';

const PROCESSED_ATTR = 'data-hoj-code-btn'

export const addCodeBtn = _ => {
  //markdown代码存放在pre code 标签对中；已处理过的代码块跳过，避免重复添加行号和复制按钮
  document.querySelectorAll('pre > code').forEach((code) => {
    const pre = code.parentElement
    if (pre.hasAttribute(PROCESSED_ATTR)) return
    pre.setAttribute(PROCESSED_ATTR, '')

    //添加有序列表
    const numbering = document.createElement('ol')
    numbering.className = 'pre-numbering'
    const lines = code.textContent.split('\n').length
    for (let i = 0; i < lines; i++) {
      numbering.appendChild(document.createElement('li'))
    }

    //添加复制按钮；直接复制文本内容，不经过 HTML 解析
    const copy = document.createElement('i')
    copy.className = 'el-icon-document-copy code-copy'
    copy.title = 'copy'
    copy.textContent = ' COPY'
    copy.addEventListener('click', () => {
      copyText(code.textContent).then(
        () => myMessage.success('success'),
        () => myMessage.error('copy failed')
      )
    })

    pre.appendChild(numbering)
    pre.appendChild(copy)
  })
}
