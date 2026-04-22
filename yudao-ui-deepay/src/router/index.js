import { createRouter, createWebHistory } from 'vue-router'
import Design from '@/pages/Design.vue'
import Shop    from '@/pages/Shop.vue'
import Pay     from '@/pages/Pay.vue'

const routes = [
  { path: '/',         component: Design, meta: { title: 'AI 爆款设计' } },
  { path: '/shop/:id', component: Shop,   meta: { title: '商品页' } },
  { path: '/pay/:id',  component: Pay,    meta: { title: '支付' } },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 同步页面标题
router.afterEach(to => {
  document.title = `Deepay · ${to.meta.title || 'AI设计'}`
})

export default router
