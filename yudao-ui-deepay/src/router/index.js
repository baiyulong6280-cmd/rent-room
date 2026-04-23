import { createRouter, createWebHistory } from 'vue-router'
import Home            from '@/pages/Home.vue'
import Generate        from '@/pages/Generate.vue'
import Template        from '@/pages/Template.vue'
import TemplatePreview from '@/pages/TemplatePreview.vue'
import Shop            from '@/pages/Shop.vue'
import Me              from '@/pages/Me.vue'
import Leaderboard     from '@/pages/Leaderboard.vue'
import Landing         from '@/pages/Landing.vue'
import PayResult       from '@/pages/PayResult.vue'
import PayOrderResult  from '@/pages/PayOrderResult.vue'
import Pay             from '@/pages/Pay.vue'
import { isLoggedIn } from '@/utils/auth'

const routes = [
  // ── 公开路由 ──────────────────────────────────
  { path: '/',             component: Home,            meta: { title: 'Deepay · 开店' } },
  { path: '/generate',     component: Generate,        meta: { title: 'AI生成' } },
  { path: '/template',     component: Template,        meta: { title: '模板' } },
  { path: '/template/:id', component: TemplatePreview, meta: { title: '模板预览' } },
  { path: '/shop/:id',     component: Shop,            meta: { title: '店铺' } },
  { path: '/login',        component: () => import('@/pages/Login.vue'), meta: { title: '登录' } },

  // ── 需要登录的路由 ─────────────────────────────
  { path: '/me',           component: Me,              meta: { title: '我的',      requiresAuth: true } },
  { path: '/leaderboard',  component: Leaderboard,     meta: { title: '收益排行榜', requiresAuth: true } },
  { path: '/invite',       component: () => import('@/pages/Invite.vue'), meta: { title: '邀请好友', requiresAuth: true } },

  // ── 支付（静态路由必须在动态路由前，避免参数捕获）──────────
  { path: '/pay/result',        component: PayResult,       meta: { title: '支付完成' } },
  { path: '/pay/order/result',  component: PayOrderResult,  meta: { title: '购买成功' } },
  { path: '/pay/:id',           component: Pay,             meta: { title: '支付结果' } },

  // ── 备用宣传页 ────────────────────────────────
  { path: '/landing',      component: Landing,         meta: { title: 'Deepay · AI爆款' } },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

router.afterEach(to => {
  document.title = to.meta.title || 'Deepay'
})

// ── 路由守卫：未登录跳转到登录页 ────────────────────────────────────
router.beforeEach((to, _from, next) => {
  if (to.meta.requiresAuth && !isLoggedIn()) {
    // 记录目标路径，登录后可跳回
    next({ path: '/login', query: { redirect: to.fullPath } })
  } else {
    next()
  }
})

export default router
