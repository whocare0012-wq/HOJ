import moment from 'moment'
import utils from './utils'
import time from './time'
import {PROBLEM_LEVEL} from './constants'
import i18n from '@/i18n'
import storage from './storage'

const MOMENT_LOCALE_MAP = {
  'en-US': 'en',
  'zh-CN': 'zh-cn',
  'zh-TW': 'zh-tw',
  'ja-JP': 'ja',
  'ko-KR': 'ko',
}

function fromNowZhCN(time) {
  const value = moment(time)
  const now = moment()
  const isFuture = value.isAfter(now)
  const seconds = Math.abs(now.diff(value, 'seconds'))
  const suffix = isFuture ? '后' : '前'
  const format = (amount, unit) => `${amount} ${unit}${suffix}`

  if (seconds < 45) return `几秒${suffix}`
  if (seconds < 90) return format(1, '分钟')
  if (seconds < 45 * 60) return format(Math.round(seconds / 60), '分钟')
  if (seconds < 90 * 60) return format(1, '小时')
  if (seconds < 22 * 60 * 60) return format(Math.round(seconds / 3600), '小时')
  if (seconds < 36 * 60 * 60) return format(1, '天')
  if (seconds < 25 * 24 * 60 * 60) return format(Math.round(seconds / 86400), '天')
  if (seconds < 45 * 24 * 60 * 60) return format(1, '个月')
  if (seconds < 320 * 24 * 60 * 60) return format(Math.round(seconds / 2592000), '个月')
  if (seconds < 548 * 24 * 60 * 60) return format(1, '年')
  return format(Math.round(seconds / 31536000), '年')
}

// 友好显示时间
function fromNow (time) {
  const language = storage.get('Web_Language') || i18n.locale.value
  if (language === 'zh-CN') {
    return fromNowZhCN(time)
  }
  const locale = MOMENT_LOCALE_MAP[language] || 'zh-cn'
  return moment(time).locale(locale).fromNow()
}

function parseRole(num){
  if(num==1000){
    return '超级管理员'
  }else if(num==1001){
    return '普通管理员'
  }else if(num==1002){
    return '用户(默认)'
  }else if(num==1003){
    return '用户(禁止提交)'
  }else if(num==1004){
    return '用户(禁止发讨论)'
  }else if(num==1005){
    return '用户(禁言)'
  }else if(num==1006){
    return '用户(禁止提交&禁止发讨论)'
  }else if(num==1007){
    return '用户(禁止提交&禁言)'
  }else if(num==1008){
    return '题目管理员'
  }
}
function parseContestType(num){
  if(num==0){
    return 'ACM'
  }else if(num==1){
    return 'OI'
  }
}

function parseProblemLevel(num){
  return PROBLEM_LEVEL[num].name;
}

function ellipsis(value) {
  if (!value) return '';
  let result = '';
  let byteLength = 0;
  for (const character of value) {
    byteLength += character.charCodeAt(0) > 0xff ? 2 : 1;
    if (byteLength > 26) {
      return result + '...';
    }
    result += character;
  }
  return result;
}

export default {
  submissionMemory: utils.submissionMemoryFormat,
  submissionTime: utils.submissionTimeFormat,
  localtime: time.utcToLocal,
  fromNow: fromNow,
  parseContestType:parseContestType,
  parseRole:parseRole,
  parseProblemLevel:parseProblemLevel,
  ellipsis,
}
