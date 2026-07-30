import { createI18n } from 'vue-i18n'
import elenUS from 'element-plus/es/locale/lang/en'
import elzhCN from 'element-plus/es/locale/lang/zh-cn'
import elzhTW from 'element-plus/es/locale/lang/zh-tw'
import eljaJP from 'element-plus/es/locale/lang/ja'
import elkoKR from 'element-plus/es/locale/lang/ko'
import vxeEnUS from 'vxe-table/lib/locale/lang/en-US'
import vxeZhCN from 'vxe-table/lib/locale/lang/zh-CN'
import vxeZhTW from 'vxe-table/lib/locale/lang/zh-TW'
import vxeJaJP from 'vxe-table/lib/locale/lang/ja-JP'
import vxeKoKR from 'vxe-table/lib/locale/lang/en-US'
import storage from '@/common/storage'
import { m as ojEnUS } from './oj/en-US'
import { m as ojZhCN } from './oj/zh-CN'
import { m as ojZhTW } from './oj/zh-TW'
import { m as ojJaJP } from './oj/ja-JP'
import { m as ojKoKR } from './oj/ko-KR'
import { m as adminEnUS } from './admin/en-US'
import { m as adminZhCN } from './admin/zh-CN'
import { m as adminZhTW } from './admin/zh-TW'
import { m as adminJaJP } from './admin/ja-JP'
import { m as adminKoKR } from './admin/ko-KR'
const languages = [
  {value: 'en-US', label: 'English', el: elenUS.el, elementLocale: elenUS, vxe: {...vxeEnUS}},
  {value: 'zh-CN', label: '简体中文', el: elzhCN.el, elementLocale: elzhCN, vxe: {...vxeZhCN}},
  {value: 'zh-TW', label: '正體中文', el: elzhTW.el, elementLocale: elzhTW, vxe: {...vxeZhTW}},
  {value: 'ja-JP', label: '日本語', el: eljaJP.el, elementLocale: eljaJP, vxe: {...vxeJaJP}},
  {value: 'ko-KR', label: '한국어', el: elkoKR.el, elementLocale: elkoKR, vxe: {...vxeKoKR}},
]
const messages = {}
const applicationMessages = {
  'en-US': {oj: ojEnUS, admin: adminEnUS},
  'zh-CN': {oj: ojZhCN, admin: adminZhCN},
  'zh-TW': {oj: ojZhTW, admin: adminZhTW},
  'ja-JP': {oj: ojJaJP, admin: adminJaJP},
  'ko-KR': {oj: ojKoKR, admin: adminKoKR},
}

// combine admin and oj
for (let lang of languages) {
  let locale = lang.value
  let m = Object.assign({}, applicationMessages[locale].oj, applicationMessages[locale].admin)
  let ui = Object.assign(lang.vxe, lang.el)
  messages[locale] = Object.assign({m: m}, ui);
}


// load language packages
const i18nPlugin = createI18n({
  legacy: false,
  globalInjection: true,
  locale: storage.get('Web_Language') || 'zh-CN',
  fallbackLocale: 'zh-CN',
  messages
})

const i18n = i18nPlugin.global

export default i18n

function getLangLabelByValue(value){
  for (let lang of languages) {
    if(lang.value == value){
      return lang.label;
    }
  }
  return "unknown"
}

export {languages, getLangLabelByValue, i18nPlugin}
