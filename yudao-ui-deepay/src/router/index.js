import { createRouter, createWebHistory } from 'vue-router'
import Home            from '@/pages/Home.vue'
import Generate        from '@/pages/Generate.vue'
import Template        from '@/pages/Template.vue'
import TemplatePreview from '@/pages/TemplatePreview.vue'
import Shop            from '@/pages/Shop.vue'
import Me              from '@/pages/Me.vue'
import Landing         from '@/pages/Landing.vue'
import PayResult       from '@/pages/PayResult.vue'
import Pay             from '@/pages/Pay.vue'

const routes = [
  // ── 主 App 路由 ──────────────────────────────
  { path: '/',             component: Home,            meta: { title: 'Deepay · 开店' } },
  { path: '/generate',     component: Generate,        meta: { title: 'AI生成' } },
  { path: '/template',     component: Template,        meta: { title: '模板' } },
  { path: '/template/:id', component: TemplatePreview, meta: { title: '模板预览' } },
  { path: '/shop/:id',     component: Shop,            meta: { title: '店铺' } },
  { path: '/me',           component: Me,              meta: { title: '我的' } },

  // ── 支付（静态路由必须在动态路由前）────────────
  { path: '/pay/result',   component: PayResult,       meta: { title: '支付完成' } },
  { path: '/pay/:id',      component: Pay,             meta: { title: '支付结果' } },

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

export default router
