import { reactive } from 'vue'

export const JUDGE_STATUS = {
  '-10': {
    name: 'Not Submitted',
    short: 'NS',
    color: 'gray',
    type: 'info',
    rgb:'#909399'
  },
  '-5': {
    name: 'Submitted Unknown Result',
    short: 'SNR',
    color: 'gray',
    type: 'info',
    rgb:'#909399'
  },
  '-4': {
    name: 'Cancelled',
    short: 'CA',
    color: 'purple',
    type: 'info',
    rgb:'#676fc1'
  },
  '-3': {
    name: 'Presentation Error',
    short: 'PE',
    color: 'yellow',
    type: 'warning',
    rgb:'#f90'
  },
  '-2': {
    name: 'Compile Error',
    short: 'CE',
    color: 'yellow',
    type: 'warning',
    rgb:'#f90'
  },
  '-1': {
    name: 'Wrong Answer',
    short: 'WA',
    color: 'red',
    type: 'error',
    rgb:'#ed3f14'
  },
  '0': {
    name: 'Accepted',
    short: 'AC',
    color: 'green',
    type: 'success',
    rgb:'#19be6b'
  },
  '1': {
    name: 'Time Limit Exceeded',
    short: 'TLE',
    color: 'red',
    type: 'error',
    rgb:'#ed3f14'
  },
  '2': {
    name: 'Memory Limit Exceeded',
    short: 'MLE',
    color: 'red',
    type: 'error',
    rgb:'#ed3f14'
  },
  '3': {
    name: 'Runtime Error',
    short: 'RE',
    color: 'red',
    type: 'error',
    rgb:'#ed3f14'
  },
  '4': {
    name: 'System Error',
    short: 'SE',
    color: 'gray',
    type: 'info',
    rgb:'#909399'
  },
  '5': {
    name: 'Pending',
    color: 'yellow',
    type: 'warning',
    rgb:'#f90'
  },
  '6':{
    name: 'Compiling',
    short: 'CP',
    color: 'green',
    type: 'info',
    rgb:'#25bb9b'
  },
  '7': {
    name: 'Judging',
    color: 'blue',
    type: '',
    rgb:'#2d8cf0'
  },
  '8': {
    name: 'Partial Accepted',
    short: 'PAC',
    color: 'blue',
    type: '',
    rgb:'#2d8cf0'
  },
  '9': {
    name: 'Submitting',
    color: 'yellow',
    type: 'warning',
    rgb:'#f90'
  },
  '10':{
    name:"Submitted Failed",
    color:'gray',
    short:'SF',
    type: 'info',
    rgb:'#909399',
  }
}

export const JUDGE_STATUS_RESERVE={
  'ns':-10,
  'snr':-5,
  'ca':-4,
  'pe':-3,
  'ce':-2,
  'wa':-1,
  'ac':0,
  'tle':1,
  'mle':2,
  're':3,
  'se':4,
  'Pending':5,
  'Compiling':6,
  'Judging':7,
  'pa':8,
  'Submitting':9,
  'sf':10,
}

const DEFAULT_PROBLEM_LEVEL = {
  '0': {
    name: {
      'zh-CN': '简单',
      'en-US': 'Easy',
      'zh-TW': '簡單',
      'ja-JP': '簡単',
      'ko-KR': '쉬움',
    },
    color: '#19BE6B',
    borderColor: '#19BE6B',
  },
  '1': {
    name: {
      'zh-CN': '中等',
      'en-US': 'Medium',
      'zh-TW': '中等',
      'ja-JP': '中等',
      'ko-KR': '보통',
    },
    color: '#2D8CF0',
    borderColor: '#2D8CF0',
  },
  '2': {
    name: {
      'zh-CN': '困难',
      'en-US': 'Hard',
      'zh-TW': '困難',
      'ja-JP': '困難',
      'ko-KR': '어려움',
    },
    color: '#ED3F14',
    borderColor: '#ED3F14',
  },
}

export const PROBLEM_LEVEL = reactive(DEFAULT_PROBLEM_LEVEL)

export const PROBLEM_LEVEL_OPTIONS = reactive(
  Object.entries(DEFAULT_PROBLEM_LEVEL).map(([value, level]) => ({
    value: Number(value),
    ...level,
  }))
)

export function applyProblemLevelConfig(difficulties) {
  if (!Array.isArray(difficulties) || difficulties.length === 0) {
    return
  }
  const nextLevels = {}
  const nextOptions = []
  difficulties.forEach((difficulty) => {
    const key = String(difficulty.difficultyValue)
    const displayText = difficulty.displayText
    const borderColor = difficulty.borderColor
    if (
      !Number.isInteger(Number(difficulty.difficultyValue)) ||
      Number(difficulty.difficultyValue) < 0 ||
      !displayText ||
      !borderColor
    ) {
      return
    }
    const level = {
      name: {
        'zh-CN': displayText,
        'en-US': displayText,
        'zh-TW': displayText,
        'ja-JP': displayText,
        'ko-KR': displayText,
      },
      basePoints: difficulty.basePoints,
      color: borderColor,
      borderColor,
    }
    nextLevels[key] = level
    nextOptions.push({
      value: Number(difficulty.difficultyValue),
      ...level,
    })
  })
  if (Object.keys(nextLevels).length !== difficulties.length) {
    return
  }
  Object.keys(PROBLEM_LEVEL).forEach((key) => delete PROBLEM_LEVEL[key])
  Object.assign(PROBLEM_LEVEL, nextLevels)
  PROBLEM_LEVEL_OPTIONS.splice(
    0,
    PROBLEM_LEVEL_OPTIONS.length,
    ...nextOptions
  )
}


export const REMOTE_OJ = [
  {
    name:'HDU',
    key:"HDU"
  },
  {
    name:"Codeforces",
    key:"CF"
  },
  {
    name:"POJ",
    key:"POJ"
  },
  {
    name:"GYM",
    key:"GYM"
  },
  {
    name:"AtCoder",
    key:"AC"
  },
  {
    name:"SPOJ",
    key:"SPOJ"
  },{
    name:"LIBREOJ",
    key: "LIBRE"
  }
]

export const CONTEST_STATUS = {
  'SCHEDULED': -1,
  'RUNNING': 0,
  'ENDED': 1
}

export const CONTEST_STATUS_REVERSE = {
  '-1': {
    name: 'Scheduled',
    color: '#f90'
  },
  '0': {
    name: 'Running',
    color: '#19be6b'
  },
  '1': {
    name: 'Ended',
    color: '#ed3f14'
  }
}

export const TRAINING_TYPE = {
  'Public':{
    color:'success',
    name:'Public'
  },
  'Private':{
    color:'danger',
    name:'Private'
  }
}

export const GROUP_TYPE = {
  PUBLIC: 1,
  PROTECTED: 2,
  PRIVATE: 3
}

export const GROUP_TYPE_REVERSE = {
  '1':{
    name: 'Public',
    color: 'success',
    tips: 'Group_Public_Tips',
  },
  '2':{
    name: 'Protected',
    color: 'warning',
    tips: 'Group_Protected_Tips',
  },
  '3':{
    name: 'Private',
    color: 'danger',
    tips: 'Group_Private_Tips',
  }
}

export const RULE_TYPE = {
  ACM: 0,
  OI: 1
}

export const CONTEST_TYPE_REVERSE = {
  '0': {
    name:'Public',
    color:'success',
    tips:'Public_Tips',
    submit:true,              // 公开赛可看可提交
    look:true,
  },
  '1':{
    name:'Private',
    color:'danger',
    tips:'Private_Tips',
    submit:false,         // 私有赛 必须要密码才能看和提交
    look:false,
  },
  '2':{
    name:'Protected',
    color:'warning',
    tips:'Protected_Tips',
    submit:false,       //保护赛，可以看但是不能提交，提交需要附带比赛密码
    look:true,
  }
}

export const CONTEST_TYPE = {
  PUBLIC: 0,
  PRIVATE: 1,
  PROTECTED: 2
}

export const USER_TYPE = {
  REGULAR_USER: 'user',
  ADMIN: 'admin',
  PROBLEM_ADMIN:'problem_admin',
  SUPER_ADMIN: 'root'
}

export const JUDGE_CASE_MODE = {
  DEFAULT: 'default',
  SUBTASK_LOWEST: 'subtask_lowest',
  SUBTASK_AVERAGE: 'subtask_average',
  ERGODIC_WITHOUT_ERROR: 'ergodic_without_error'
}

export const FOCUS_MODE_ROUTE_NAME = {
  'TrainingFullProblemDetails': 'TrainingProblemDetails',
  'ContestFullProblemDetails': 'ContestProblemDetails',
  'GroupFullProblemDetails':'GroupProblemDetails',
  'GroupTrainingFullProblemDetails': 'GroupTrainingProblemDetails'
}


export const STORAGE_KEY = {
  AUTHED: 'authed',
  PROBLEM_CODE_AND_SETTING: 'hojProblemCodeAndSetting',
  languages: 'languages',
  CONTEST_ANNOUNCE:'hojContestAnnounce',
  individualLanguageAndSetting:'hojIndividualLanguageAndSetting',
  CONTEST_RANK_CONCERNED:'hojContestRankConcerned'
}

export function buildIndividualLanguageAndSettingKey () {
  return `${STORAGE_KEY.individualLanguageAndSetting}`
}

export function buildProblemCodeAndSettingKey (problemID, contestID = null) {
  if (contestID) {
    return `${STORAGE_KEY.PROBLEM_CODE_AND_SETTING}_${contestID}_${problemID}`
  }
  return `${STORAGE_KEY.PROBLEM_CODE_AND_SETTING}_NoContest_${problemID}`
}

export function buildContestAnnounceKey (uid, contestID) {
  return `${STORAGE_KEY.CONTEST_ANNOUNCE}_${uid}_${contestID}`
}

export function buildContestRankConcernedKey(contestID) {
  return `${STORAGE_KEY.CONTEST_RANK_CONCERNED}_${contestID}`
}

