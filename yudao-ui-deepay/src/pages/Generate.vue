<!--
  Generate.vue — AI生成页（高级版）

  流程：筛选 → 生成按钮 → 骨架屏 → 图片瀑布（逐张渐显）→ 选图 → 超限弹窗
-->
<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { createGenerateTask, getTaskResult, selectImage, getQuotaInfo } from '@/api/design'
import PaywallModal from './PaywallModal.vue'

// ── 用户标识（TODO: 从登录态读取）─────────────────────────
const USER_ID = localStorage.getItem('deepay_uid') || 'u1'

// ── 筛选项 ─────────────────────────────────────────────────
const CATEGORIES = ['外套', '连衣裙', '裤子', '上衣', '运动', '内衣']
const STYLES     = ['欧美', '工装', '韩系', '极简', '性感', '休闲']

const category = ref('外套')
const style    = ref('欧美')

// ── 状态 ────────────────────────────────────────────────────
const images     = ref([])
const selected   = ref(null)
const loading    = ref(false)
const loadingMsg = ref('')
const error      = ref('')

// ── 配额 ────────────────────────────────────────────────────
const remainFree   = ref(3)
const remainPaid   = ref(0)
const paywallShow  = ref(false)
const paywallMsg   = ref('')
const paywallPlans = ref([])
const clickCount   = ref(0)

const totalRemain = computed(() => remainFree.value + remainPaid.value)

// ── 轮询 ────────────────────────────────────────────────────
let pollTimer = null

onMounted(async () => {
  try {
    const info = await getQuotaInfo(USER_ID)
    remainFree.value = info?.remainFree ?? 3
    remainPaid.value = info?.remainPaid ?? 0
  } catch (_) {}
})
onUnmounted(() => clearInterval(pollTimer))

// ── 生成 ────────────────────────────────────────────────────
async function generate() {
  clickCount.value++

  // 连续2次点击 且 剩余≤1 → 损失感提前弹窗
  if (clickCount.value >= 2 && totalRemain.value <= 1) {
    openPaywall('再一次就能找到爆款 — 解锁更多次数！')
    return
  }

  if (loading.value) return
  loading.value  = true
  error.value    = ''
  images.value   = []
  selected.value = null
  loadingMsg.value = 'AI正在分析1688 / TikTok趋势…'

  try {
    const res = await createGenerateTask(USER_ID, category.value, style.value, '欧美', null)

    if (res?.exceeded || res?.code === 'NO_QUOTA' || res?.code === 402) {
      loading.value = false
      openPaywall(res?.message || '今日免费次数已用完', res?.plans)
      return
    }

    if (res?.quota) {
      remainFree.value = res.quota.remainFree ?? remainFree.value
      remainPaid.value = res.quota.remainPaid ?? remainPaid.value
    }

    loadingMsg.value = '正在生成10款热卖款式…'
    startPoll(res.taskId)
  } catch {
    loading.value = false
    error.value   = '生成失败，请稍后重试'
  }
}

function startPoll(taskId) {
  let attempts = 0
  pollTimer = setInterval(async () => {
    if (++attempts > 60) {
      clearInterval(pollTimer)
      loading.value = false
      error.value   = '生成超时，请重试'
      return
    }
    try {
      const res = await getTaskResult(taskId)
      if (res?.status === 'success') {
        clearInterval(pollTimer)
        images.value  = res.images || []
        loading.value = false
        clickCount.value = 0
        // 次数减1（前端乐观更新，后端已原子扣减）
        if (remainFree.value > 0) remainFree.value--
        else if (remainPaid.value > 0) remainPaid.value--
      } else if (res?.status === 'failed') {
        clearInterval(pollTimer)
        loading.value = false
        error.value   = res.error || '生成失败，请重试'
      }
    } catch (_) {}
  }, 1000)
}

// ── 选图 ────────────────────────────────────────────────────
async function onSelect(img) {
  selected.value = img
  try {
    await selectImage(USER_ID, img, images.value, null, category.value, style.value)
  } catch (_) {}
}

// ── 弹窗 ────────────────────────────────────────────────────
function openPaywall(msg, plans) {
  paywallMsg.value   = msg   || '今日免费次数已用完'
  paywallPlans.value = plans || []
  paywallShow.value  = true
}
</script>

<template>
  <div class="min-h-screen bg-bg text-white">

    <!-- ── 顶部导航 ─────────────────────────────────────── -->
    <header class="sticky top-0 z-10 bg-bg/90 backdrop-blur-md
                   border-b border-border px-4 py-3
                   flex items-center justify-between">
      <!-- 剩余次数 -->
      <span
        :class="[
          'text-xs font-semibold px-3 py-1.5 rounded-full cursor-pointer',
          totalRemain <= 0  ? 'badge-empty' :
          totalRemain <= 1  ? 'badge-low'   : 'badge-ok'
        ]"
        @click="totalRemain <= 0 && openPaywall()"
      >
        {{ totalRemain <= 0
            ? '次数已用完 👇'
            : remainFree > 0
              ? `今日剩余 ${remainFree} 次`
              : `付费剩余 ${remainPaid} 次` }}
      </span>

      <span class="text-accent text-xs font-semibold tracking-wide">AI Trend</span>
    </header>

    <!-- ── 主内容 ──────────────────────────────────────── -->
    <div class="max-w-[480px] mx-auto px-4 pt-5 pb-10">

      <!-- 页面标题 -->
      <h2 class="text-xl font-semibold mb-5">生成热门设计</h2>

      <!-- 品类筛选 -->
      <div class="flex gap-2 overflow-x-auto pb-1 scrollbar-hide mb-3">
        <button
          v-for="c in CATEGORIES" :key="c"
          :class="['chip shrink-0', category === c ? 'chip-active' : '']"
          @click="category = c"
        >{{ c }}</button>
      </div>

      <!-- 风格筛选 -->
      <div class="flex gap-2 overflow-x-auto pb-1 scrollbar-hide mb-5">
        <button
          v-for="s in STYLES" :key="s"
          :class="['chip shrink-0', style === s ? 'chip-active' : '']"
          @click="style = s"
        >{{ s }}</button>
      </div>

      <!-- 生成按钮 -->
      <button
        :disabled="loading"
        class="bg-accent text-black font-bold h-12 w-full rounded-full mb-6
               active:scale-95 transition-transform duration-100
               disabled:opacity-50 disabled:cursor-not-allowed
               flex items-center justify-center gap-2 text-sm tracking-wide"
        @click="generate"
      >
        <svg v-if="loading" class="animate-spin h-4 w-4 shrink-0"
             viewBox="0 0 24 24" fill="none">
          <circle cx="12" cy="12" r="10" stroke="currentColor"
                  stroke-opacity=".3" stroke-width="3"/>
          <path d="M22 12a10 10 0 0 0-10-10" stroke="currentColor"
                stroke-width="3" stroke-linecap="round"/>
        </svg>
        <span>{{ loading ? loadingMsg : '生成10款爆款设计' }}</span>
      </button>

      <!-- 错误 -->
      <p v-if="error" class="text-danger text-sm text-center mb-5">{{ error }}</p>

      <!-- 空状态 -->
      <div v-if="!loading && !images.length && !error"
           class="text-center py-16 opacity-0 animate-[fade-up_.6s_ease_.1s_forwards]">
        <p class="text-3xl mb-4">✨</p>
        <p class="text-white font-semibold mb-2">AI已分析爆款趋势</p>
        <p class="text-muted text-sm leading-loose">
          基于 1688 · TikTok · Shein 实时数据<br>
          点击按钮，立即生成专属款式
        </p>
      </div>

      <!-- 骨架屏 -->
      <div v-if="loading" class="grid grid-cols-2 gap-3 animate-pulse">
        <div v-for="i in 10" :key="i"
             class="aspect-[3/4] rounded-2xl bg-surface2" />
      </div>

      <!-- 图片瀑布（逐张渐显，card depth）-->
      <div v-if="images.length" class="grid grid-cols-2 gap-3">
        <div
          v-for="(img, idx) in images" :key="img"
          :class="['img-card img-in', selected === img ? 'img-card-selected' : '']"
          :style="{ animationDelay: `${idx * 0.06}s` }"
          @click="onSelect(img)"
        >
          <img :src="img" :alt="`${category} ${style}`"
               loading="lazy" class="w-full h-full object-cover" />
          <div v-if="selected === img"
               class="absolute bottom-2.5 left-1/2 -translate-x-1/2
                      bg-accent text-black text-xs font-bold
                      px-3 py-1 rounded-full whitespace-nowrap">
            ✓ 已选
          </div>
        </div>
      </div>

    </div><!-- /max-w -->

    <!-- 付费弹窗 -->
    <PaywallModal
      :show="paywallShow"
      :userId="USER_ID"
      :message="paywallMsg"
      :plans="paywallPlans"
      @close="paywallShow = false"
    />

  </div>
</template>

<style scoped>
/* empty-state fade (global .fade-up used on other elements) */
@keyframes fade-up {
  from { opacity: 0; transform: translateY(10px); }
  to   { opacity: 1; transform: translateY(0); }
}
</style>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { createGenerateTask, getTaskResult, selectImage, getQuotaInfo } from '@/api/design'
import Loading from '@/components/Loading.vue'
import PaywallModal from './PaywallModal.vue'

// ── 用户标识（TODO: 从登录态 / localStorage 读取）──────────
const USER_ID = localStorage.getItem('deepay_uid') || 'u1'

// ── 筛选项 ─────────────────────────────────────────────────
const CATEGORIES = ['外套', '连衣裙', '裤子', '上衣', '运动', '内衣']
const STYLES     = ['欧美', '工装', '韩系', '极简', '性感', '休闲']
const MARKETS    = ['欧美', '东南亚', '国内', '中东']

const category = ref('外套')
const style    = ref('欧美')
const market   = ref('欧美')

// ── 生成状态 ────────────────────────────────────────────────
const images     = ref([])
const selected   = ref(null)
const loading    = ref(false)
const loadingMsg = ref('')
const error      = ref('')

// ── 配额 ────────────────────────────────────────────────────
const remainFree   = ref(3)
const remainPaid   = ref(0)
const paywallShow  = ref(false)
const paywallMsg   = ref('')
const paywallPlans = ref([])
const clickCount   = ref(0)  // 连续点击计数

const totalRemain = computed(() => remainFree.value + remainPaid.value)
const quotaLabel  = computed(() => {
  if (totalRemain.value <= 0) return '次数已用完 👇'
  if (remainFree.value > 0)  return `免费剩余 ${remainFree.value} 次`
  return `付费剩余 ${remainPaid.value} 次`
})
const quotaBadgeClass = computed(() =>
  totalRemain.value <= 0 ? 'badge-empty'
    : totalRemain.value <= 1 ? 'badge-low'
    : 'badge-ok'
)

// ── 轮询控制 ────────────────────────────────────────────────
let pollTimer = null
const MAX_POLL = 60

// ── 初始化：拉取配额 ────────────────────────────────────────
onMounted(async () => {
  try {
    const info = await getQuotaInfo(USER_ID)
    remainFree.value = info?.remainFree ?? 3
    remainPaid.value = info?.remainPaid ?? 0
  } catch (_) {}
})
onUnmounted(() => clearInterval(pollTimer))

// ── 生成 ────────────────────────────────────────────────────
async function generate() {
  clickCount.value++

  // 连续2次点击 且 剩余≤1 → 提前触发弹窗（损失厌恶）
  if (clickCount.value >= 2 && totalRemain.value <= 1) {
    openPaywall('再一次就能找到爆款 — 解锁更多次数！')
    return
  }

  if (loading.value) return
  loading.value  = true
  error.value    = ''
  images.value   = []
  selected.value = null
  loadingMsg.value = 'AI正在分析1688 / TikTok趋势…'

  try {
    const res = await createGenerateTask(USER_ID, category.value, style.value, market.value, null)

    // 配额超限
    if (res?.exceeded || res?.code === 'NO_QUOTA' || res?.code === 402) {
      loading.value = false
      openPaywall(res?.message || '今日免费次数已用完', res?.plans)
      return
    }

    // 同步最新配额
    if (res?.quota) {
      remainFree.value = res.quota.remainFree ?? remainFree.value
      remainPaid.value = res.quota.remainPaid ?? remainPaid.value
    }

    loadingMsg.value = '正在生成10款热卖款式…'
    startPoll(res.taskId)
  } catch {
    loading.value    = false
    error.value      = '生成失败，请稍后重试'
  }
}

function startPoll(taskId) {
  let attempts = 0
  pollTimer = setInterval(async () => {
    if (++attempts > MAX_POLL) {
      clearInterval(pollTimer)
      loading.value = false
      error.value   = '生成超时，请重试'
      return
    }
    try {
      const res = await getTaskResult(taskId)
      if (res?.status === 'success') {
        clearInterval(pollTimer)
        images.value   = res.images || []
        loading.value  = false
        clickCount.value = 0
      } else if (res?.status === 'failed') {
        clearInterval(pollTimer)
        loading.value = false
        error.value   = res.error || '生成失败，请重试'
      }
    } catch (_) {}
  }, 1000)
}

// ── 选图 ────────────────────────────────────────────────────
async function onSelect(img) {
  selected.value = img
  try {
    await selectImage(USER_ID, img, images.value, null, category.value, style.value)
  } catch (_) {}
}

// ── 弹窗 ────────────────────────────────────────────────────
function openPaywall(msg, plans) {
  paywallMsg.value   = msg   || '今日免费次数已用完'
  paywallPlans.value = plans || []
  paywallShow.value  = true
}
</script>

<template>
  <div class="min-h-screen bg-bg">

    <!-- ── 顶部导航栏（sticky）────────────────────────────── -->
    <header class="sticky top-0 z-10 bg-bg/90 backdrop-blur-md
                   border-b border-border px-4 py-3
                   flex items-center justify-between">
      <span class="font-semibold text-sm tracking-wide">Deepay</span>

      <!-- 配额徽章 -->
      <button
        :class="['text-xs font-semibold px-3 py-1.5 rounded-full', quotaBadgeClass]"
        @click="totalRemain <= 0 && openPaywall()"
      >
        {{ quotaLabel }}
      </button>
    </header>

    <!-- ── 主内容区 ──────────────────────────────────────── -->
    <div class="max-w-[480px] mx-auto px-4 pb-10">

      <!-- 品类筛选 -->
      <section class="mt-5 mb-3">
        <p class="text-[11px] text-muted font-semibold uppercase tracking-widest mb-2.5">
          品类
        </p>
        <div class="flex gap-2 overflow-x-auto pb-1 scrollbar-hide">
          <button
            v-for="c in CATEGORIES" :key="c"
            :class="['chip', category === c ? 'chip-active' : '']"
            @click="category = c"
          >{{ c }}</button>
        </div>
      </section>

      <!-- 风格筛选 -->
      <section class="mb-5">
        <p class="text-[11px] text-muted font-semibold uppercase tracking-widest mb-2.5">
          风格
        </p>
        <div class="flex gap-2 overflow-x-auto pb-1 scrollbar-hide">
          <button
            v-for="s in STYLES" :key="s"
            :class="['chip', style === s ? 'chip-active' : '']"
            @click="style = s"
          >{{ s }}</button>
        </div>
      </section>

      <!-- 生成按钮 -->
      <button
        :disabled="loading"
        class="bg-accent text-black font-bold h-14 w-full rounded-2xl mb-5
               text-sm tracking-wide active:scale-95 transition-transform duration-100
               disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2"
        @click="generate"
      >
        <svg v-if="loading" class="animate-spin h-4 w-4 shrink-0" viewBox="0 0 24 24" fill="none">
          <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-opacity=".3" stroke-width="3"/>
          <path d="M22 12a10 10 0 0 0-10-10" stroke="currentColor" stroke-width="3" stroke-linecap="round"/>
        </svg>
        <span v-if="loading">{{ loadingMsg }}</span>
        <span v-else>生成10款「{{ category }} · {{ style }}」热卖爆款</span>
      </button>

      <!-- 错误提示 -->
      <p v-if="error" class="text-danger text-sm text-center mb-5">{{ error }}</p>

      <!-- 空状态 -->
      <div v-if="!loading && !images.length && !error"
           class="text-center py-16 fade-in">
        <p class="text-4xl mb-4">✨</p>
        <p class="text-white font-semibold text-lg mb-2">AI已分析爆款趋势</p>
        <p class="text-muted text-sm leading-loose">
          基于 1688 · TikTok · Shein 实时数据<br>
          点击上方按钮，立即生成专属款式
        </p>
      </div>

      <!-- Loading 骨架 -->
      <Loading v-if="loading" type="grid" :count="10" />

      <!-- 图片网格 -->
      <div v-if="images.length" class="grid grid-cols-2 gap-3">
        <div
          v-for="img in images" :key="img"
          :class="['img-card', selected === img ? 'img-card-selected' : '']"
          @click="onSelect(img)"
        >
          <img :src="img" :alt="`${category} ${style}`"
               loading="lazy" class="w-full h-full object-cover" />

          <!-- 已选标签 -->
          <div v-if="selected === img"
               class="absolute bottom-2.5 left-1/2 -translate-x-1/2
                      bg-accent text-black text-xs font-bold px-3 py-1 rounded-full">
            ✓ 已选
          </div>
        </div>
      </div>

    </div><!-- /max-w -->

    <!-- ── 付费弹窗 ──────────────────────────────────────── -->
    <PaywallModal
      :show="paywallShow"
      :userId="USER_ID"
      :message="paywallMsg"
      :plans="paywallPlans"
      @close="paywallShow = false"
    />

  </div>
</template>

<style scoped>
.fade-in {
  animation: fade-up .6s ease forwards;
}
@keyframes fade-up {
  from { opacity: 0; transform: translateY(10px); }
  to   { opacity: 1; transform: translateY(0); }
}
</style>
