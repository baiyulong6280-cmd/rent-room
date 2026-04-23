<!--
  Shop.vue — 店铺总入口（路由层）
  路径：/shop/:id

  逻辑：
  1. 先检查 localStorage shop_{id}（模板店 / AI店）
  2. 否则调 API 获取商品数据
  3. 根据 shop.id / shop.type 切换模板组件
  4. buy 事件 → 跳转支付（MVP先占位）
-->
<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getShop } from '@/api/shop'
import { createOrder } from '@/api'

import TemplateMinimal from '@/components/templates/TemplateMinimal.vue'
import TemplateStreet  from '@/components/templates/TemplateStreet.vue'
import TemplateLuxury  from '@/components/templates/TemplateLuxury.vue'
import TemplateGrid    from '@/components/templates/TemplateGrid.vue'
import TemplateSingle  from '@/components/templates/TemplateSingle.vue'
import TemplateAI      from '@/components/templates/TemplateAI.vue'

// template-id → component（模板名 + 布局类型 双映射）
const TEMPLATE_MAP = {
  // 按模板 id
  minimal:    TemplateMinimal,
  street:     TemplateStreet,
  luxury:     TemplateLuxury,
  ecommerce:  TemplateGrid,
  spotlight:  TemplateSingle,
  // 按布局 type（AI商品 / 旧数据 fallback）
  single:     TemplateSingle,
  grid:       TemplateGrid,
  ai:         TemplateAI,
}

const route  = useRoute()
const router = useRouter()

const shop     = ref(null)
const loading  = ref(true)
const error    = ref('')
const ordering = ref(false)

const USER_ID = localStorage.getItem('deepay_uid') || 'u1'

// 选出模板组件（id 优先，type 兜底）
const CurrentTemplate = computed(() =>
  shop.value
    ? (TEMPLATE_MAP[shop.value.id] || TEMPLATE_MAP[shop.value.type] || TemplateMinimal)
    : null
)

onMounted(async () => {
  const shopId = route.params.id
  try {
    const data = await getShop(shopId)
    // Normalize API response into unified shop structure when it doesn't
    // already contain a theme (i.e. it came from the backend, not localStorage)
    if (data && !data.theme) {
      shop.value = {
        id:       data.type || 'minimal',
        type:     data.type || 'minimal',
        name:     data.title,
        theme: {
          bg:      '#0B0B0B',
          card:    '#111111',
          border:  '#1A1A1A',
          text:    '#FFFFFF',
          subText: '#9CA3AF',
          primary: '#00FF88',
        },
        gradient: '#111',
        products: [{
          img:   data.image,
          title: data.title,
          price: data.price,
        }],
        _raw: data,
      }
    } else {
      shop.value = data
    }
  } catch {
    error.value = '商品加载失败'
  } finally {
    loading.value = false
  }
})

// ── 购买（MVP：模板店提示，API店跳支付）──────────────────────────────
async function onBuy() {
  if (ordering.value || !shop.value) return

  // 模板店 / AI店暂未接支付——占位提示
  if (!shop.value._raw) {
    const url = window.location.href
    if (navigator.share) {
      navigator.share({ title: shop.value.name || 'Deepay', url })
    } else {
      alert('店铺已生成！复制链接发给客户：\n' + url)
    }
    return
  }

  // API 商品 → 走支付
  ordering.value = true
  try {
    const order = await createOrder(USER_ID, route.params.id, shop.value._raw.price)
    if (order?.payUrl) window.location.href = order.payUrl
  } catch {
    error.value = '下单失败，请重试'
  } finally {
    ordering.value = false
  }
}
</script>

<template>
  <div>

    <!-- Loading -->
    <div v-if="loading"
         class="min-h-screen bg-bg flex items-center justify-center">
      <div class="text-center">
        <div class="w-8 h-8 border-2 border-accent border-t-transparent
                    rounded-full animate-spin mx-auto mb-3" />
        <p class="text-muted text-sm">加载中…</p>
      </div>
    </div>

    <!-- 错误 -->
    <div v-else-if="error || !shop"
         class="min-h-screen bg-bg text-white
                flex flex-col items-center justify-center gap-4 px-6 text-center">
      <p class="text-4xl">😕</p>
      <p class="text-muted text-sm">{{ error || '店铺不存在' }}</p>
      <button class="btn-primary max-w-[200px]"
              @click="router.push('/')">返回首页</button>
    </div>

    <!-- 模板组件（动态挂载）-->
    <component
      v-else
      :is="CurrentTemplate"
      :shop="shop"
      @buy="onBuy"
      @share="() => {
        const url = window.location.href
        if (navigator.share) navigator.share({ title: shop.name, url })
        else navigator.clipboard?.writeText(url).then(() => alert('链接已复制'))
      }"
    />

  </div>
</template>
