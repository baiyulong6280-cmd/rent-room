<!--
  Pay.vue — 支付结果页
  路由：/pay/:id   id = paymentId

  支付网关跳转回此页后，展示成功/失败状态，
  并返回最新配额（STEP 42：支付成功后显示剩余次数）。
-->
<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getQuotaInfo } from '@/api'
import axios from 'axios'

const route  = useRoute()
const router = useRouter()

const status   = ref('loading')  // loading | success | failed
const quota    = ref(null)
const message  = ref('')

const USER_ID = 'u1' // TODO: 从登录态读取

onMounted(async () => {
  // 支付网关通常通过 query 参数传状态，如 ?result=success&plan=PACK_M
  const result = route.query.result || 'success'
  const plan   = route.query.plan   || ''

  if (result === 'success') {
    status.value  = 'success'
    message.value = '支付成功！快去生成爆款吧 🎉'
    // 拉取最新配额
    try {
      quota.value = await getQuotaInfo(USER_ID)
    } catch (_) {}
  } else {
    status.value  = 'failed'
    message.value = '支付未完成，请重试'
  }
})
</script>

<template>
  <div class="page">
    <!-- Loading -->
    <div v-if="status === 'loading'" class="center">
      <div class="spinner" />
      <p>确认支付结果中…</p>
    </div>

    <!-- Success -->
    <div v-else-if="status === 'success'" class="center success">
      <div class="icon">🎉</div>
      <h2>{{ message }}</h2>

      <div v-if="quota" class="quota-info">
        <div class="quota-row">
          <span>免费剩余</span>
          <strong>{{ quota.remainFree }} 次</strong>
        </div>
        <div class="quota-row">
          <span>付费剩余</span>
          <strong>{{ quota.remainPaid }} 次</strong>
        </div>
        <div class="quota-row total">
          <span>合计可用</span>
          <strong>{{ quota.totalRemain }} 次</strong>
        </div>
      </div>

      <button class="primary-btn" @click="router.push('/')">
        立即生成爆款设计
      </button>
    </div>

    <!-- Failed -->
    <div v-else class="center failed">
      <div class="icon">😕</div>
      <h2>{{ message }}</h2>
      <button class="primary-btn" @click="router.push('/')">返回重试</button>
    </div>
  </div>
</template>

<style scoped>
.page  { max-width: 480px; margin: 0 auto; padding: 60px 16px; }

.center {
  display: flex; flex-direction: column;
  align-items: center; gap: 16px; text-align: center;
}

.icon { font-size: 64px; }
h2    { font-size: 22px; font-weight: 800; }

/* Quota card */
.quota-info {
  background: #f0fdf4; border-radius: 16px;
  padding: 20px; width: 100%; margin: 8px 0;
}
.quota-row {
  display: flex; justify-content: space-between;
  padding: 8px 0; font-size: 15px;
  border-bottom: 1px solid #d1fae5;
}
.quota-row:last-child { border-bottom: none; }
.quota-row.total strong { color: #4f46e5; font-size: 18px; }

.primary-btn {
  background: linear-gradient(135deg, #4f46e5, #7c3aed);
  color: #fff; font-size: 16px; font-weight: 700;
  padding: 16px 32px; border-radius: 14px; width: 100%;
}

/* Spinner */
.spinner {
  width: 40px; height: 40px; border-radius: 50%;
  border: 4px solid #e5e7eb;
  border-top-color: #4f46e5;
  animation: spin .8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }
</style>
