import { createRouter, createWebHistory } from 'vue-router'
import Landing   from '@/pages/Landing.vue'
import Generate  from '@/pages/Generate.vue'
import Detail    from '@/pages/Detail.vue'
import Shop      from '@/pages/Shop.vue'
import PayResult from '@/pages/PayResult.vue'
import Pay       from '@/pages/Pay.vue'

const routes = [
  { path: '/',            component: Landing,    meta: { title: 'Deepay · AI爆款' } },
  { path: '/generate',    component: Generate,   meta: { title: 'AI生成' } },
  { path: '/detail',      component: Detail,     meta: { title: '款式详情' } },
  { path: '/shop/:id',    component: Shop,       meta: { title: '商品' } },
  // NOTE: static /pay/result must be declared BEFORE dynamic /pay/:id
  { path: '/pay/result',  component: PayResult,  meta: { title: '支付完成' } },
  { path: '/pay/:id',     component: Pay,        meta: { title: '支付结果' } },
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
