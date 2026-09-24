import { createRouter, createWebHistory } from 'vue-router'
import adminRoutes from '@/router/adminRoutes'
import ojRoutes from '@/router/ojRoutes'
import mMessage from '@/common/message'
import store from '@/store'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import i18n from '@/i18n'

NProgress.configure({ ease: 'ease', speed: 1000, showSpinner: false })

const routes = [...ojRoutes, ...adminRoutes]
const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) {
      return savedPosition
    }
    if (to.path === from.path) {
      return false
    }
    return { left: 0, top: 0 }
  },
})

const publicPaths = new Set(['/', '/home', '/admin/login'])

function redirectToLogin(to, next, messageKey = 'm.Please_login_first') {
  store.commit('clearUserInfoAndToken')
  if (to.path.startsWith('/admin')) {
    next({ path: '/admin/login' })
  } else {
    store.commit('changeModalStatus', { mode: 'Login', visible: true })
    next({ path: '/home' })
  }
  mMessage.error(i18n.t(messageKey))
}

// The homepage is public. Every other OJ route requires an authenticated user.
// The admin login route remains public so administrators can establish a session.
router.beforeEach((to, from, next) => {
  NProgress.start()

  const token = localStorage.getItem('token') || ''
  const isPublicPage = publicPaths.has(to.path)
  const isAdminRoute = to.path.startsWith('/admin')

  if (!token && !isPublicPage) {
    redirectToLogin(
      to,
      next,
      isAdminRoute
        ? 'm.Please_login_first_by_admin_account'
        : 'm.Please_login_first'
    )
    return
  }

  if (to.matched.some(record => record.meta.requireAuth)) {
    const isSuperAdmin = store.getters.isSuperAdmin
    const isAdmin = store.getters.isAdminRole

    if (to.matched.some(record => record.meta.requireSuperAdmin) && !isSuperAdmin) {
      redirectToLogin(
        to,
        next,
        isAdminRoute
          ? 'm.Please_login_first_by_admin_account'
          : 'm.Please_login_first'
      )
      return
    }

    if (to.matched.some(record => record.meta.requireAdmin) && !isAdmin) {
      redirectToLogin(
        to,
        next,
        isAdminRoute
          ? 'm.Please_login_first_by_admin_account'
          : 'm.Please_login_first'
      )
      return
    }
  }

  if (to.meta.access) {
    const webConfig = store.getters.websiteConfig
    const accessRules = {
      discussion: {
        allowed: webConfig.openPublicDiscussion,
        message: 'm.No_Access_There_is_no_open_discussion_area_on_the_website',
      },
      groupDiscussion: {
        allowed: webConfig.openGroupDiscussion,
        message: 'm.No_Access_There_is_no_open_group_discussion_area_on_the_website',
      },
      contestComment: {
        allowed: webConfig.openContestComment,
        message: 'm.No_Access_There_is_no_open_contest_comment_area_on_the_website',
      },
    }
    const rule = accessRules[to.meta.access]
    if (rule && !rule.allowed) {
      next({ path: '/home' })
      mMessage.error(i18n.t(rule.message))
      return
    }
  }

  next()
})

router.afterEach((to) => {
  store.commit('updateRoute', {
    name: to.name,
    path: to.path,
    hash: to.hash,
    query: { ...to.query },
    params: { ...to.params },
    fullPath: to.fullPath,
    meta: { ...to.meta },
  })
  NProgress.done()
})

export default router
