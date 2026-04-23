<!--
  Shop.vue — 商品落地页（品牌商品页 + 分享）
  路由：/shop/:id   id = chainCode

  结构：全幅图 → 信息区 → 固定底部操作栏
-->
<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getShopPage, createOrder } from '@/api'

const route  = useRoute()
const router = useRouter()

const product  = ref(null)
const loading  = ref(true)
const ordering = ref(false)
const error    = ref('')
const imgLoaded = ref(false)

const USER_ID = localStorage.getItem('deepay_uid') || 'u1'

onMounted(async () => {
  try {
    product.value = await getShopPage(route.params.id, 'EUR')
  } catch {
    error.value = '商品加载失败'
  } finally {
    loading.value = false
  }
})

async function buy() {
  if (ordering.value || !product.value) return
  ordering.value = true
  try {
    const order = await createOrder(USER_ID, route.params.id, product.value.price)
    if (order?.payUrl) window.location.href = order.payUrl
  } catch {
    error.value = '下单失败，请重试'
  } finally {
    ordering.value = false
  }
}

function share() {
  const url   = product.value?.shareUrl || window.location.href
  const title = product.value?.title    || 'Deepay 商品'
  if (navigator.share) {
    navigator.share({ title, url })
  } else {
    navigator.clipboard?.writeText(url)
      .then(() => alert('链接已复制，发给客户吧！'))
  }
}
</script>

<template>
  <div class="min-h-screen bg-bg text-white">

    <!-- 顶部返回 -->
    <header class="sticky top-0 z-10 bg-bg/90 backdrop-blur-md
                   border-b border-border px-4 py-3 flex items-center gap-3">
      <button class="text-muted active:text-white transition-colors"
              @click="router.back()">
        <svg class="h-5 w-5" fill="none" viewBox="0 0 24 24"
             stroke="currentColor" stroke-width="2.5">
          <path stroke-linecap="round" stroke-linejoin="round" d="M15 19l-7-7 7-7"/>
        </svg>
      </button>
      <span class="font-semibold text-sm">商品详情</span>
    </header>

    <!-- Loading -->
    <div v-if="loading" class="px-4 pt-4 animate-pulse space-y-4">
      <div class="w-full aspect-square rounded-2xl bg-surface2" />
      <div class="h-6 bg-surface2 rounded-xl w-3/4" />
      <div class="h-8 bg-surface2 rounded-xl w-1/3" />
      <div class="h-4 bg-surface2 rounded-xl" />
      <div class="h-4 bg-surface2 rounded-xl w-5/6" />
    </div>

    <!-- 错误 / 找不到 -->
    <div v-else-if="error || !product || product.status === 'NOT_FOUND'"
         class="flex flex-col items-center justify-center min-h-[60vh] gap-4 px-6 text-center">
      <p class="text-4xl">😕</p>
      <p class="text-muted text-sm">{{ error || '商品不存在' }}</p>
      <button class="btn-primary max-w-[200px]" @click="router.push('/')">返回首页</button>
    </div>

    <!-- 商品内容（底部留出固定栏高度）-->
    <template v-else>
      <div class="pb-28">

        <!-- 主图 -->
        <div class="relative w-full aspect-square overflow-hidden">
          <div v-if="!imgLoaded" class="absolute inset-0 bg-surface2 animate-pulse" />
          <img
            :src="product.image"
            :alt="product.title"
            class="w-full h-full object-cover transition-opacity duration-500"
            :class="imgLoaded ? 'opacity-100' : 'opacity-0'"
            @load="imgLoaded = true"
          />
        </div>

        <!-- 信息区 -->
        <div class="px-5 pt-5 space-y-3">
          <h1 class="text-xl font-bold leading-snug">{{ product.title }}</h1>

          <p class="text-accent text-2xl font-bold">€{{ product.price }}</p>

          <p class="text-muted text-sm leading-relaxed">{{ product.description }}</p>

          <p v-if="error" class="text-danger text-sm">{{ error }}</p>
        </div>

      </div><!-- /pb-28 -->

      <!-- 固定底部操作栏 -->
      <div class="fixed bottom-0 left-0 right-0 z-20
                  bg-bg/95 backdrop-blur-md border-t border-border
                  px-4 pt-3 pb-[calc(.75rem+env(safe-area-inset-bottom))]
                  flex gap-3">

        <!-- 分享 -->
        <button
          class="bg-surface2 text-white border border-border rounded-2xl
                 h-12 px-4 flex items-center justify-center gap-1.5
                 text-sm font-semibold active:scale-95 transition-transform duration-100
                 shrink-0"
          @click="share"
        >
          <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24"
               stroke="currentColor" stroke-width="2">
            <path stroke-linecap="round" stroke-linejoin="round"
                  d="M8.684 13.342C8.886 12.938 9 12.482 9 12c0-.482-.114-.938-.316-1.342m0 2.684a3 3 0 1 1 0-2.684m0 2.684l6.632 3.316m-6.632-6l6.632-3.316m0 0a3 3 0 1 0 5.367-2.684 3 3 0 0 0-5.367 2.684zm0 9.316a3 3 0 1 0 5.368 2.684 3 3 0 0 0-5.368-2.684z"/>
          </svg>
          分享
        </button>

        <!-- 购买 -->
        <button
          :disabled="ordering"
          class="bg-accent text-black font-bold h-12 flex-1 rounded-2xl
                 active:scale-95 transition-transform duration-100
                 disabled:opacity-50 disabled:cursor-not-allowed
                 flex items-center justify-center gap-2 text-sm"
          @click="buy"
        >
          <svg v-if="ordering" class="animate-spin h-4 w-4 shrink-0"
               viewBox="0 0 24 24" fill="none">
            <circle cx="12" cy="12" r="10" stroke="currentColor"
                    stroke-opacity=".3" stroke-width="3"/>
            <path d="M22 12a10 10 0 0 0-10-10" stroke="currentColor"
                  stroke-width="3" stroke-linecap="round"/>
          </svg>
          {{ ordering ? '处理中…' : '立即购买' }}
        </button>

      </div>
    </template>

  </div>
</template>
<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getShopPage, createOrder } from '@/api'

const route  = useRoute()
const router = useRouter()

const product  = ref(null)
const loading  = ref(true)
const ordering = ref(false)
const error    = ref('')

const USER_ID = 'u1' // TODO: 从登录态读取

onMounted(async () => {
  try {
    product.value = await getShopPage(route.params.id, 'EUR')
  } catch (_) {
    error.value = '商品加载失败'
  } finally {
    loading.value = false
  }
})

async function buy() {
  if (ordering.value || !product.value) return
  ordering.value = true
  try {
    const order = await createOrder(USER_ID, route.params.id, product.value.price)
    if (order?.payUrl) {
      window.location.href = order.payUrl
    }
  } catch (e) {
    error.value = '下单失败，请重试'
  } finally {
    ordering.value = false
  }
}

function share() {
  const url = product.value?.shareUrl || window.location.href
  if (navigator.share) {
    navigator.share({ title: product.value?.title, url })
  } else {
    navigator.clipboard?.writeText(url)
    alert('链接已复制，发给客户吧！')
  }
}
</script>

<template>
  <div class="page">
    <!-- Loading -->
    <div v-if="loading" class="loading">加载中…</div>

    <!-- Error -->
    <div v-else-if="error || !product || product.status === 'NOT_FOUND'" class="error-page">
      <p>{{ error || '商品不存在' }}</p>
      <button @click="router.push('/')">返回首页</button>
    </div>

    <!-- Product -->
    <template v-else>
      <div class="img-hero">
        <img :src="product.image" :alt="product.title" />
      </div>

      <div class="info">
        <h1 class="title">{{ product.title }}</h1>
        <p  class="price">€{{ product.price }}</p>
        <p  class="desc">{{ product.description }}</p>

        <button class="buy-btn" :disabled="ordering" @click="buy">
          {{ ordering ? '处理中…' : '立即购买' }}
        </button>

        <button class="share-btn" @click="share">
          📤 分享给客户
        </button>

        <p v-if="error" class="err-msg">{{ error }}</p>
      </div>
    </template>
  </div>
</template>

<style scoped>
.page { max-width: 480px; margin: 0 auto; padding-bottom: 60px; }

.loading, .error-page {
  text-align: center; padding: 80px 20px; color: #9ca3af;
}
.error-page button { margin-top: 16px; background: #4f46e5; color: #fff; }

.img-hero img {
  width: 100%; aspect-ratio: 1/1;
  object-fit: cover; display: block;
}

.info { padding: 20px 16px; }
.title { font-size: 20px; font-weight: 800; margin-bottom: 8px; }
.price { font-size: 26px; font-weight: 700; color: #4f46e5; margin-bottom: 12px; }
.desc  { font-size: 14px; color: #666; line-height: 1.7; margin-bottom: 24px; }

.buy-btn {
  width: 100%;
  background: linear-gradient(135deg, #4f46e5, #7c3aed);
  color: #fff;
  font-size: 17px; font-weight: 700;
  padding: 18px; border-radius: 14px;
  margin-bottom: 12px;
}

.share-btn {
  width: 100%;
  background: #f3f4f6; color: #374151;
  font-size: 15px; font-weight: 600;
  padding: 14px; border-radius: 14px;
}

.err-msg { color: #dc2626; font-size: 14px; margin-top: 12px; text-align: center; }
</style>
