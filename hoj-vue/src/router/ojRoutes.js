import Home from '@/views/oj/Home.vue'

const SetNewPassword = () => import('@/views/oj/user/SetNewPassword.vue')
const UserHome = () => import('@/views/oj/user/UserHome.vue')
const Setting = () => import('@/views/oj/user/Setting.vue')
const ProblemLIst = () => import('@/views/oj/problem/ProblemList.vue')
const Logout = () => import('@/views/oj/user/Logout.vue')
const SubmissionList = () => import('@/views/oj/status/SubmissionList.vue')
const SubmissionDetails = () => import('@/views/oj/status/SubmissionDetails.vue')
const ContestList = () => import('@/views/oj/contest/ContestList.vue')
const Problem = () => import('@/views/oj/problem/Problem.vue')
const BlocklyStandalone = () => import('@/views/oj/problem/BlocklyStandalone.vue')
const OIRank = () => import('@/views/oj/rank/OIRank.vue')
const ContestDetails = () => import('@/views/oj/contest/ContestDetails.vue')
const ACMScoreBoard = () => import('@/views/oj/contest/outside/ACMScoreBoard.vue')
const OIScoreBoard = () => import('@/views/oj/contest/outside/OIScoreBoard.vue')
const ContestProblemList = () => import('@/views/oj/contest/children/ContestProblemList.vue')
const ContestRank = () => import('@/views/oj/contest/children/ContestRank.vue')
const ACMInfoAdmin = () => import('@/views/oj/contest/children/ACMInfoAdmin.vue')
const Announcements = () => import('@/components/oj/common/Announcements.vue')
const ContestComment = () => import('@/views/oj/contest/children/ContestComment.vue')
const ContestPrint = () => import('@/views/oj/contest/children/ContestPrint.vue')
const ContestAdminPrint = () => import('@/views/oj/contest/children/ContestAdminPrint.vue')
const ScrollBoard = () => import('@/views/oj/contest/children/ScrollBoard.vue')
const ContestRejudgeAdmin = () => import('@/views/oj/contest/children/ContestRejudgeAdmin.vue')
const DiscussionList = () => import('@/views/oj/discussion/discussionList.vue')
const Discussion = () => import('@/views/oj/discussion/discussion.vue')
const Introduction = () => import('@/views/oj/about/Introduction.vue')
const Developer = () => import('@/views/oj/about/Developer.vue')
const Message = () => import('@/views/oj/message/message.vue')
const UserMsg = () => import('@/views/oj/message/UserMsg.vue')
const SysMsg = () => import('@/views/oj/message/SysMsg.vue')
const TrainingList = () => import('@/views/oj/training/TrainingList.vue')
const TrainingDetails = () => import('@/views/oj/training/TrainingDetails.vue')
const TrainingProblemList = () => import('@/views/oj/training/TrainingProblemList.vue')
const TrainingRank = () => import('@/views/oj/training/TrainingRank.vue')
const GroupList = () => import('@/views/oj/group/GroupList.vue')
const GroupDetails = () => import('@/views/oj/group/GroupDetails.vue')
const GroupAnnouncementList = () => import('@/views/oj/group/children/GroupAnnouncementList.vue')
const GroupProblemList = () => import('@/views/oj/group/children/GroupProblemList.vue')
const GroupTrainingList = () => import('@/views/oj/group/children/GroupTrainingList.vue')
const GroupContestList = () => import('@/views/oj/group/children/GroupContestList.vue')
const GroupDiscussionList = () => import('@/views/oj/group/children/GroupDiscussionList.vue')
const GroupMemberList = () => import('@/views/oj/group/children/GroupMemberList.vue')
const GroupSetting = () => import('@/views/oj/group/children/GroupSetting.vue')
const GroupRank = () => import('@/views/oj/group/children/GroupRank.vue')
const LearningResource = () => import('@/views/oj/resource/LearningResource.vue')
const NotFound = () => import('@/views/404.vue')

const ojRoutes = [
  {
    path: '/',
    redirect: '/home',
    component: Home,
    meta: { title: 'Home' }
  },
  {
    path: '/home',
    name: 'Home',
    component: Home,
    meta: { title: 'Home' }
  },
  {
    path: '/problem',
    name: 'ProblemList',
    component: ProblemLIst,
    meta: { title: 'Problem' }
  },
  {
    path: '/problem/:problemID',
    name: 'ProblemDetails',
    component: Problem,
    meta: { title: 'Problem Details' }
  },
  {
    path: '/blockly/editor',
    name: 'BlocklyStandalone',
    component: BlocklyStandalone,
    meta: { title: 'Blockly Editor' }
  },
  {
    name: 'TrainingFullProblemDetails',
    path: '/training/:trainingID/problem/:problemID/full-screen',
    component: Problem,
    meta: { title: 'Training Problem Details', fullScreenSource: 'training'}
  },
  {
    name: 'ContestFullProblemDetails',
    path: '/contest/:contestID/problem/:problemID/full-screen',
    component: Problem,
    meta: { title: 'Contest Problem Details', fullScreenSource: 'contest'}
  },
  {
    name: 'GroupFullProblemDetails',
    path: '/group/:groupID/problem/:problemID/full-screen',
    component: Problem,
    meta: { title: 'Group Problem Details', fullScreenSource: 'group' }
  },
  {
    name: 'GroupTrainingFullProblemDetails',
    path: '/group/:groupID/training/:trainingID/problem/:problemID/full-screen',
    component: Problem,
    meta: { title: 'Group Training Problem Details', fullScreenSource: 'training'}
  },
  {
    path: '/training',
    name: 'TrainingList',
    component: TrainingList,
    meta: { title: 'Training' }
  },
  {
    name: 'TrainingDetails',
    path: '/training/:trainingID/',
    component:TrainingDetails,
    meta: {title: 'Training Details'},
    children: [
      {
        name: 'TrainingProblemList',
        path: 'problems',
        component: TrainingProblemList,
        meta: { title: 'Training Problem' }
      },
      {
        name: 'TrainingProblemDetails',
        path: 'problem/:problemID',
        component: Problem,
        meta: { title: 'Training Problem Details' }
      },
      {
        name: 'TrainingRank',
        path: 'rank',
        component: TrainingRank,
        meta: { title: 'Training Rank' }
      }
    ]
  },
  {
    path: '/contest',
    name: 'ContestList',
    component: ContestList,
    meta: { title: 'Contest' }
  },
  {
    path: '/contest/acm-scoreboard/:contestID',
    name: 'ACMScoreBoard',
    component: ACMScoreBoard,
    meta: { title: 'ACM Contest ScoreBoard' }
  },
  {
    path: '/contest/oi-scoreboard/:contestID',
    name: 'OIScoreBoard',
    component: OIScoreBoard,
    meta: { title: 'OI Contest ScoreBoard' }
  },
  {
    name: 'ContestDetails',
    path: '/contest/:contestID/',
    component:ContestDetails,
    meta: {title: 'Contest Details',requireAuth:true},
    children: [
      {
        name: 'ContestSubmissionList',
        path: 'submissions',
        component: SubmissionList,
        meta: { title: 'Contest Submission' }
      },
      {
        name: 'ContestSubmissionDetails',
        path: 'problem/:problemID/submission-detail/:submitID',
        component: SubmissionDetails,
        meta: { title: 'Contest Submission Details' }
      },
      {
        name: 'ContestProblemList',
        path: 'problems',
        component: ContestProblemList,
        meta: { title: 'Contest Problem' }
      },
      {
        name: 'ContestProblemDetails',
        path: 'problem/:problemID/',
        component: Problem,
        meta: { title: 'Contest Problem Details' }
      },
      {
        name: 'ContestAnnouncementList',
        path: 'announcements',
        component: Announcements,
        meta: { title: 'Contest Announcement' }
      },
      {
        name: 'ContestRank',
        path: 'rank',
        component: ContestRank,
        meta: { title: 'Contest Rank' }
      },
      {
        name: 'ContestACInfo',
        path: 'ac-info',
        component: ACMInfoAdmin,
        meta: { title: 'Contest AC Info'}
      },
      {
        name:'ContestRejudgeAdmin',
        path:'rejudge',
        component:ContestRejudgeAdmin,
        meta: { title: 'Contest Rejudge',requireSuperAdmin:true }
      },
      {
        name: 'ContestComment',
        path:'comment',
        component: ContestComment,
        meta: { title: 'Contest Comment', access:'contestComment'}
      },
      {
        name: 'ContestPrint',
        path:'print',
        component: ContestPrint,
        meta: { title: 'Contest Print'}
      },
      {
        name: 'ContestAdminPrint',
        path:'admin-print',
        component: ContestAdminPrint,
        meta: { title: 'Contest Admin Print'}
      },
      {
        name: 'ScrollBoard',
        path:'scroll-board',
        component: ScrollBoard,
        meta: { title: 'Contest Scroll Board'}
      }
    ]
  },
  {
    path: '/status',
    name: 'SubmissionList',
    component: SubmissionList,
    meta: { title: 'Status' }
  },
  {
    path: '/submission-detail/:submitID',
    name: 'SubmissionDetails',
    component: SubmissionDetails,
    meta: {title: 'Submission Details' }
  },
  {
    path: '/acm-rank',
    name: 'ACM Rank',
    redirect: to => ({ path: '/oi-rank', query: to.query, hash: to.hash })
  },
  {
    path: '/oi-rank',
    name: 'OI Rank',
    component: OIRank,
    meta: { title: 'OJ 积分排名' }
  },
  {
    path: '/reset-password',
    name: 'SetNewPassword',
    component: SetNewPassword,
    meta: { title: 'Reset Password' }
  },
  {
    name: 'UserHome',
    path: '/user-home',
    component: UserHome,
    meta: { title: 'User Home' }
  },
  {
    name: 'Setting',
    path: '/setting',
    component: Setting,
    meta: { requireAuth: true, title: 'Setting' }
  },
  {
    name: 'Logout',
    path: '/logout',
    component: Logout,
    meta: { requireAuth: true, title: 'Logout' }
  },
  {
    path: '/discussion',
    name: 'AllDiscussion',
    meta: {title: 'Discussion', access:'discussion'},
    component:DiscussionList
  },
  {
    path: '/discussion/:problemID',
    name: 'ProblemDiscussion',
    meta: {title: 'Discussion', access:'discussion'},
    component:DiscussionList
  },
  {
    path: '/discussion-detail/:discussionID',
    name:'DiscussionDetails',
    meta: {title: 'Discussion Details', access:'discussion'},
    component: Discussion
  },
  {
    path: '/group',
    name: 'GroupList',
    component: GroupList,
    meta: {title: 'Group'}
  },
  {
    path: '/resource',
    name: 'LearningResource',
    component: LearningResource,
    meta: {title: 'Learning Resource', requireAuth: true}
  },
  {
    path: '/group/:groupID',
    name: 'GroupDetails',
    component: GroupDetails,
    meta: {title: 'Group Details', requireAuth: true},
    children: [
      {
        path: 'announcement',
        name: 'GroupAnnouncementList',
        component: GroupAnnouncementList,
        meta: { title: 'Group Announcement' },
      },
      {
        path: 'problem',
        name: 'GroupProblemList',
        component: GroupProblemList,
        meta: { title: 'Group Problem' },
      },
      {
        name: 'GroupProblemDetails',
        path: 'problem/:problemID/',
        component: Problem,
        meta: { title: 'Group Problem Details' }
      },
      {
        path: 'training',
        name: 'GroupTrainingList',
        component: GroupTrainingList,
        meta: { title: 'Group Training' }
      },
      {
        name: 'GroupTrainingDetails',
        path: 'training/:trainingID/',
        component:TrainingDetails,
        meta: {title: 'Group Training Details'},
        children: [
          {
            name: 'GroupTrainingProblemList',
            path: 'problems',
            component: TrainingProblemList,
            meta: { title: 'Group Training Problem' }
          },
          {
            name: 'GroupTrainingProblemDetails',
            path: 'problem/:problemID/',
            component: Problem,
            meta: { title: 'Group Training Problem Details' }
          },
          {
            name: 'GroupTrainingRank',
            path: 'rank',
            component: TrainingRank,
            meta: { title: 'Group Training Rank' }
          }
        ]
      },
      {
        path: 'contest',
        name: 'GroupContestList',
        component: GroupContestList,
        meta: { title: 'Group Contest' }
      },
      {
        path: 'status',
        name: 'GroupSubmissionList',
        component: SubmissionList,
        meta: { title: 'Group Status' }
      },
      {
        path: 'submission-detail/:submitID',
        name: 'GroupSubmissionDetails',
        component: SubmissionDetails,
        meta: {title: 'Group Submission Details' }
      },
      {
        path: 'discussion',
        name: 'GroupDiscussionList',
        component: GroupDiscussionList,
        meta: { title: 'Group Discussion', access:'groupDiscussion' }
      },
      {
        path: 'discussion/:problemID',
        name: 'GroupProblemDiscussion',
        meta: {title: 'Group Discussion', access:'groupDiscussion'},
        component:GroupDiscussionList
      },
      {
        path: 'discussion-detail/:discussionID',
        name:'GroupDiscussionDetails',
        meta: {title: 'Group Discussion Details', access:'groupDiscussion'},
        component: Discussion
      },
      {
        path: 'member',
        name: 'GroupMemberList',
        component: GroupMemberList,
        meta: { title: 'Group Member' }
      },
      {
        path: 'setting',
        name: 'GroupSetting',
        component: GroupSetting,
        meta: { title: 'Group Setting' }
      },
      {
        path: 'rank',
        name: 'GroupRank',
        component: GroupRank,
        meta: { title: 'Group Rank' }
      },
    ]
  },
  {
    path: '/introduction',
    meta: {title: 'Introduction'},
    component:Introduction,
  },
  {
    path: '/developer',
    meta: {title: 'Developer'},
    component:Developer,
  },
  {
    name:'Message',
    path:'/message/',
    component:Message,
    meta: { requireAuth: true, title: 'Message' },
    children: [
      {
        name: 'DiscussMsg',
        path: 'discuss',
        component: UserMsg,
        meta: { requireAuth: true,title: 'Discuss Message' }
      },
      {
        name: 'ReplyMsg',
        path: 'reply',
        component: UserMsg,
        meta: { requireAuth: true,title: 'Reply Message' }
      },
      {
        name: 'LikeMsg',
        path: 'like',
        component: UserMsg,
        meta: { requireAuth: true,title: 'Like Message' }
      },
      {
        name: 'SysMsg',
        path: 'sys',
        component: SysMsg,
        meta: { requireAuth: true,title: 'System Message' }
      },
      {
        name: 'MineMsg',
        path: 'mine',
        component: SysMsg,
        meta: { requireAuth: true,title: 'Mine Message' }
      },
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    meta: {title: '404'},
    component:NotFound,
    meta: { title: '404' }
  }
]
export default ojRoutes
