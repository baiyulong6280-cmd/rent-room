<!--
  Me.vue — 我的页面
  路径：/me

  功能：
  ✔ 用户 ID 初始化（首次生成）
  ✔ 佣金收益卡片（总收益 + 明细）
  ✔ 我的店铺列表（localStorage）
  ✔ 分享链接带 ?ref=myUserId（裂变）
-->
<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { initUserId, buildShareLink, shareOrCopy } from '@/utils/user'
import { getEarnings } from '@/api/user'
import { applyWithdraw, getWithdrawList, getUserBalance } from '@/api/withdraw'

const router = useRouter()

// ── 用户身份 ────────────────────────────────────────────────────────
const userId = initUserId()

// ── 佣金收益 ────────────────────────────────────────────────────────
const totalEarn       = ref(null)   // null = loading
const commissionList  = ref([])
const earningsError   = ref(false)

async function loadEarnings() {
  try {
    const data = await getEarnings(userId)
    totalEarn.value      = data?.total      ?? 0
    commissionList.value = data?.list       ?? []
  } catch (_) {
    earningsError.value = true
    totalEarn.value     = 0
  }
}

// ── 余额 + 提现 ─────────────────────────────────────────────────────
const balance        = ref(null)
const frozen         = ref(0)
const withdrawAmount = ref('')
const withdrawAcct   = ref('')
const withdrawing    = ref(false)
const withdrawError  = ref('')
const withdrawOk     = ref(false)
const withdrawList   = ref([])

async function loadBalance() {
  try {
    const data    = await getUserBalance()
    balance.value = data?.balance ?? 0
    frozen.value  = data?.frozen  ?? 0
  } catch (_) {
    balance.value = 0
  }
}

async function loadWithdrawList() {
  try {
    withdrawList.value = await getWithdrawList() ?? []
  } catch (_) {}
}

async function submitWithdraw() {
  withdrawError.value = ''
  withdrawOk.value    = false
  const amt = Number(withdrawAmount.value)
  if (!amt || amt < 10) {
    withdrawError.value = '最低提现金额 €10'
    return
  }
  if (!withdrawAcct.value.trim()) {
    withdrawError.value = '请填写收款账号'
    return
  }
  if (balance.value !== null && amt > balance.value) {
    withdrawError.value = `余额不足（可提现 €${Number(balance.value).toFixed(2)}）`
    return
  }
  withdrawing.value = true
  try {
    await applyWithdraw(amt, withdrawAcct.value.trim())
    withdrawOk.value    = true
    withdrawAmount.value = ''
    withdrawAcct.value   = ''
    await loadBalance()
    await loadWithdrawList()
  } catch (e) {
    withdrawError.value = e?.response?.data?.msg || '提交失败，请稍后重试'
  } finally {
    withdrawing.value = false
  }
}

// ── 店铺列表（localStorage）─────────────────────────────────────────
function loadShops() {
  const result = []
  for (let i = 0; i < localStorage.length; i++) {
    const key = localStorage.key(i)
    if (key?.startsWith('shop_')) {
      try {
        const data = JSON.parse(localStorage.getItem(key))
        result.push({ shopId: key.replace('shop_', ''), ...data })
      } catch (_) {}
    }
  }
  return result.sort((a, b) => Number(b.shopId) - Number(a.shopId))
}

const shops = ref([])

function deleteShop(shopId) {
  localStorage.removeItem(`shop_${shopId}`)
  shops.value = loadShops()
}

// 分享带 ref 的裂变链接
function shareShop(shopId, shopName) {
  const link = buildShareLink(shopId, userId)
  shareOrCopy(link, shopName || '我的店铺')
}

function formatDate(shopId) {
  try {
    return new Date(Number(shopId)).toLocaleDateString('zh-CN', {
      month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit',
    })
  } catch (_) { return '' }
}

onMounted(() => {
  shops.value = loadShops()
  loadEarnings()
  loadBalance()
  loadWithdrawList()
})
</script>

<template>
  <div class="min-h-screen bg-bg text-white">

    <!-- 顶部导航 -->
    <header class="sticky top-0 z-10 bg-bg/90 backdrop-blur-md
                   border-b border-border px-4 py-3
                   flex items-center gap-3">
      <button class="text-muted active:text-white transition-colors"
              @click="router.back()">
        <svg class="h-5 w-5" fill="none" viewBox="0 0 24 24"
             stroke="currentColor" stroke-width="2.5">
          <path stroke-linecap="round" stroke-linejoin="round" d="M15 19l-7-7 7-7"/>
        </svg>
      </button>
      <span class="font-semibold text-sm">我的</span>
    </header>

    <div class="max-w-[480px] mx-auto px-4 pt-5 pb-24">

      <!-- ── 佣金收益卡片 ──────────────────────────────────────── -->
      <section class="mb-6">
        <div class="card p-5 rounded-2xl"
             style="background:linear-gradient(135deg,#0f2d1a,#111)">

          <!-- 总收益 -->
          <div class="flex items-start justify-between mb-4">
            <div>
              <p class="text-xs text-muted mb-1">我的佣金收益</p>
              <div v-if="totalEarn === null"
                   class="h-8 w-24 bg-surface2 rounded animate-pulse" />
              <p v-else class="text-3xl font-black text-accent">
                €{{ Number(totalEarn).toFixed(2) }}
              </p>
            </div>
            <span class="text-xs px-2 py-1 rounded-full font-semibold"
                  style="background:#00FF8822;color:#00FF88">
              10% 佣金
            </span>
          </div>

          <!-- 分成说明 -->
          <p class="text-xs text-muted mb-4">
            每带来一笔订单自动结算 10% 佣金 · 分享越多赚越多
          </p>

          <!-- 推广链接 -->
          <div class="bg-bg/60 rounded-xl p-3 mb-4">
            <p class="text-[10px] text-muted mb-1 uppercase tracking-widest">我的专属推广链接</p>
            <p class="text-xs text-white/80 font-mono truncate">
              {{ buildShareLink('YOUR_SHOP_ID', userId) }}
            </p>
          </div>

          <!-- 佣金明细（最近5条）-->
          <div v-if="commissionList.length" class="space-y-2">
            <p class="text-[10px] text-muted uppercase tracking-widest mb-2">最近佣金</p>
            <div
              v-for="item in commissionList.slice(0, 5)"
              :key="item.orderId"
              class="flex items-center justify-between text-sm"
            >
              <div class="flex items-center gap-2">
                <span class="w-1.5 h-1.5 rounded-full shrink-0"
                      :style="{ background: item.status === 'settled' ? '#00FF88' : '#F59E0B' }" />
                <span class="text-muted text-xs font-mono truncate max-w-[140px]">
                  {{ item.orderId }}
                </span>
              </div>
              <span class="font-semibold text-xs"
                    :style="{ color: item.status === 'settled' ? '#00FF88' : '#F59E0B' }">
                +€{{ Number(item.amount).toFixed(2) }}
              </span>
            </div>
          </div>

          <!-- 无佣金空态 -->
          <div v-else-if="!earningsError" class="text-center py-3">
            <p class="text-muted text-xs">还没有佣金记录</p>
            <p class="text-muted text-xs mt-1">分享你的店铺链接，带来订单即可赚钱 💰</p>
          </div>

        </div>
      </section>

      <!-- ── 提现系统 ──────────────────────────────────────────────── -->
      <section class="mb-6">
        <h2 class="font-semibold text-sm mb-3">提现</h2>
        <div class="card p-5 rounded-2xl space-y-4">

          <!-- 余额行 -->
          <div class="flex items-center justify-between">
            <div>
              <p class="text-xs text-muted mb-1">可提现余额</p>
              <div v-if="balance === null"
                   class="h-7 w-20 bg-surface2 rounded animate-pulse" />
              <p v-else class="text-2xl font-black text-white">
                €{{ Number(balance).toFixed(2) }}
              </p>
            </div>
            <div v-if="frozen > 0" class="text-right">
              <p class="text-xs text-muted mb-1">提现中</p>
              <p class="text-sm text-yellow-400 font-semibold">€{{ Number(frozen).toFixed(2) }}</p>
            </div>
          </div>

          <!-- 提现表单 -->
          <div class="space-y-3">
            <div>
              <label class="text-xs text-muted block mb-1">提现金额（最低 €10）</label>
              <input
                v-model="withdrawAmount"
                type="number"
                min="10"
                step="1"
                placeholder="0.00"
                class="w-full h-11 rounded-xl px-3 text-sm bg-surface2
                       border border-border text-white placeholder:text-muted
                       focus:outline-none focus:border-accent"
              />
            </div>
            <div>
              <label class="text-xs text-muted block mb-1">收款账号（PayPal / 银行）</label>
              <input
                v-model="withdrawAcct"
                type="text"
                placeholder="paypal:your@email.com"
                class="w-full h-11 rounded-xl px-3 text-sm bg-surface2
                       border border-border text-white placeholder:text-muted
                       focus:outline-none focus:border-accent"
              />
            </div>

            <!-- 错误 / 成功提示 -->
            <p v-if="withdrawError" class="text-xs text-danger">{{ withdrawError }}</p>
            <p v-if="withdrawOk"    class="text-xs text-accent">✔ 提现申请已提交，1-3 个工作日处理</p>

            <button
              :disabled="withdrawing"
              class="w-full h-11 rounded-full font-bold text-sm
                     active:scale-95 transition-transform duration-100
                     disabled:opacity-50 disabled:cursor-not-allowed"
              style="background:#00FF88;color:#000"
              @click="submitWithdraw"
            >
              {{ withdrawing ? '提交中…' : '申请提现' }}
            </button>
          </div>

          <!-- 提现记录 -->
          <div v-if="withdrawList.length" class="border-t border-border pt-4 space-y-2">
            <p class="text-[10px] text-muted uppercase tracking-widest mb-2">提现记录</p>
            <div
              v-for="w in withdrawList.slice(0, 5)"
              :key="w.id"
              class="flex items-center justify-between text-sm"
            >
              <div>
                <p class="text-xs font-mono text-muted truncate max-w-[160px]">
                  {{ w.account }}
                </p>
                <p class="text-[10px] text-muted/60">
                  {{ w.createdAt ? new Date(w.createdAt).toLocaleDateString('zh-CN') : '' }}
                </p>
              </div>
              <div class="text-right">
                <p class="font-semibold text-xs">€{{ Number(w.amount).toFixed(2) }}</p>
                <p class="text-[10px]"
                   :style="{
                     color: w.status === 'success' ? '#00FF88'
                          : w.status === 'reject'  ? '#FF6B6B'
                          : '#F59E0B'
                   }">
                  {{ w.status === 'success' ? '已到账'
                   : w.status === 'reject'  ? '已拒绝'
                   : '处理中' }}
                </p>
              </div>
            </div>
          </div>

        </div>
      </section>

      <!-- ── 我的店铺 ──────────────────────────────────────────── -->
      <section class="mb-8">
        <div class="flex items-center justify-between mb-3">
          <h2 class="font-semibold text-sm">我的店铺</h2>
          <span class="text-muted text-xs">{{ shops.length }} 家</span>
        </div>

        <!-- 空状态 -->
        <div v-if="!shops.length" class="card p-8 text-center">
          <p class="text-3xl mb-3">🏪</p>
          <p class="text-muted text-sm mb-4">还没有店铺</p>
          <div class="flex gap-2 justify-center">
            <button class="btn-primary max-w-[140px] text-sm"
                    @click="router.push('/template')">选模板开店</button>
            <button class="btn-ghost max-w-[140px] text-sm"
                    @click="router.push('/generate')">AI生成</button>
          </div>
        </div>

        <!-- 店铺列表 -->
        <div v-else class="space-y-3">
          <div
            v-for="shop in shops"
            :key="shop.shopId"
            class="card overflow-hidden"
          >
            <!-- 店铺头部 -->
            <div
              class="flex items-center gap-3 p-3 cursor-pointer active:opacity-80 transition-opacity"
              :style="{ background: shop.gradient || '#1A1A1A' }"
              @click="router.push(`/shop/${shop.shopId}`)"
            >
              <div class="flex-1 min-w-0">
                <p class="font-semibold text-sm truncate"
                   :style="{ color: shop.theme?.text || '#fff' }">
                  {{ shop.name || 'AI设计款' }}
                </p>
                <p class="text-xs mt-0.5 truncate"
                   :style="{ color: shop.theme?.subText || '#9CA3AF' }">
                  {{ formatDate(shop.shopId) }}
                </p>
              </div>
              <svg class="h-4 w-4 shrink-0 opacity-60" fill="none"
                   viewBox="0 0 24 24" stroke="currentColor" stroke-width="2"
                   :style="{ color: shop.theme?.text || '#fff' }">
                <path stroke-linecap="round" stroke-linejoin="round" d="M9 5l7 7-7 7"/>
              </svg>
            </div>

            <!-- 操作栏 -->
            <div class="flex items-center gap-2 px-3 py-2.5 border-t border-border">
              <span class="text-muted text-xs flex-1 truncate">/shop/{{ shop.shopId }}</span>
              <!-- 分享赚钱（带 ref）-->
              <button
                class="text-xs font-semibold px-3 py-1.5 rounded-full
                       active:scale-95 transition-transform duration-100"
                style="background:#00FF8820;color:#00FF88;border:1px solid #00FF8840"
                @click="shareShop(shop.shopId, shop.name)"
              >
                分享赚钱
              </button>
              <!-- 删除 -->
              <button
                class="text-xs font-semibold px-3 py-1.5 rounded-full
                       bg-danger/10 text-danger border border-danger/20
                       active:scale-95 transition-transform duration-100"
                @click="deleteShop(shop.shopId)"
              >
                删除
              </button>
            </div>
          </div>
        </div>
      </section>

      <!-- ── 新建入口 ──────────────────────────────────────────── -->
      <section class="mb-8">
        <h2 class="font-semibold text-sm mb-3">新建</h2>
        <div class="grid grid-cols-2 gap-3">
          <button
            class="card p-4 text-left active:scale-95 transition-transform duration-100"
            @click="router.push('/template')"
          >
            <span class="text-2xl mb-2 block">📋</span>
            <p class="font-semibold text-sm">模板开店</p>
            <p class="text-muted text-xs mt-0.5">选模板一键生成</p>
          </button>
          <button
            class="card p-4 text-left active:scale-95 transition-transform duration-100"
            @click="router.push('/generate')"
          >
            <span class="text-2xl mb-2 block">✨</span>
            <p class="font-semibold text-sm">AI生成</p>
            <p class="text-muted text-xs mt-0.5">设计图做成商品</p>
          </button>
        </div>
      </section>

      <!-- ── 用户 ID（底部小字，方便调试）──────────────────────── -->
      <p class="text-center text-muted text-[10px] font-mono break-all">
        ID: {{ userId }}
      </p>

    </div>

    <!-- 底部导航 -->
    <nav class="fixed bottom-0 left-0 right-0 z-20
                bg-bg/95 backdrop-blur-md border-t border-border
                flex items-center justify-around
                px-2 pt-2 pb-[calc(.5rem+env(safe-area-inset-bottom))]">
      <button class="flex flex-col items-center gap-0.5 px-4 py-1 text-muted
                     active:text-white transition-colors"
              @click="router.push('/')">
        <svg class="h-5 w-5" fill="none" viewBox="0 0 24 24"
             stroke="currentColor" stroke-width="2">
          <path stroke-linecap="round" stroke-linejoin="round"
                d="M3 12l9-9 9 9M5 10v9a1 1 0 001 1h4v-5h4v5h4a1 1 0 001-1v-9"/>
        </svg>
        <span class="text-[10px] font-semibold">首页</span>
      </button>
      <button class="flex flex-col items-center gap-0.5 px-4 py-1 text-muted
                     active:text-white transition-colors"
              @click="router.push('/generate')">
        <svg class="h-5 w-5" fill="none" viewBox="0 0 24 24"
             stroke="currentColor" stroke-width="2">
          <path stroke-linecap="round" stroke-linejoin="round" d="M12 4v16m8-8H4"/>
        </svg>
        <span class="text-[10px] font-semibold">AI生成</span>
      </button>
      <button class="flex flex-col items-center gap-0.5 px-4 py-1 text-muted
                     active:text-white transition-colors"
              @click="router.push('/template')">
        <svg class="h-5 w-5" fill="none" viewBox="0 0 24 24"
             stroke="currentColor" stroke-width="2">
          <path stroke-linecap="round" stroke-linejoin="round"
                d="M4 5a1 1 0 011-1h4a1 1 0 011 1v4a1 1 0 01-1 1H5a1 1 0 01-1-1V5zm10 0a1 1 0 011-1h4a1 1 0 011 1v4a1 1 0 01-1 1h-4a1 1 0 01-1-1V5zM4 15a1 1 0 011-1h4a1 1 0 011 1v4a1 1 0 01-1 1H5a1 1 0 01-1-1v-4zm10 0a1 1 0 011-1h4a1 1 0 011 1v4a1 1 0 01-1 1h-4a1 1 0 01-1-1v-4z"/>
        </svg>
        <span class="text-[10px] font-semibold">模板</span>
      </button>
      <button class="flex flex-col items-center gap-0.5 px-4 py-1 text-accent">
        <svg class="h-5 w-5" fill="none" viewBox="0 0 24 24"
             stroke="currentColor" stroke-width="2">
          <path stroke-linecap="round" stroke-linejoin="round"
                d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"/>
        </svg>
        <span class="text-[10px] font-semibold">我的</span>
      </button>
    </nav>

  </div>
</template>
