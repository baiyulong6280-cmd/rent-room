<!--
  Shop.vue — 小店商品页（分享链接落地页）
  路由：/shop/:id   id = chainCode
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
