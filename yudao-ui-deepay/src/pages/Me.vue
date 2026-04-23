<!--
  Me.vue — 我的页面
  路径：/me

  功能：
  ✔ 我的店铺列表（从 localStorage 读取）
  ✔ 我的设计（从 localStorage 读取 AI 设计）
  ✔ 清空单个店铺
-->
<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

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
  // newest first
  return result.sort((a, b) => Number(b.shopId) - Number(a.shopId))
}

const shops = ref(loadShops())

function deleteShop(shopId) {
  localStorage.removeItem(`shop_${shopId}`)
  shops.value = loadShops()
}

function shareShop(shopId) {
  const url = `${window.location.origin}/shop/${shopId}`
  if (navigator.share) {
    navigator.share({ title: '我的店铺', url })
  } else {
    navigator.clipboard?.writeText(url)
      .then(() => alert('店铺链接已复制！'))
  }
}

function formatDate(shopId) {
  try {
    return new Date(Number(shopId)).toLocaleDateString('zh-CN', {
      month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit',
    })
  } catch (_) {
    return ''
  }
}
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

      <!-- 我的店铺 -->
      <section class="mb-8">
        <div class="flex items-center justify-between mb-3">
          <h2 class="font-semibold text-sm">我的店铺</h2>
          <span class="text-muted text-xs">{{ shops.length }} 家</span>
        </div>

        <!-- 空状态 -->
        <div v-if="!shops.length"
             class="card p-8 text-center">
          <p class="text-3xl mb-3">🏪</p>
          <p class="text-muted text-sm mb-4">还没有店铺</p>
          <div class="flex gap-2 justify-center">
            <button class="btn-primary max-w-[140px] text-sm"
                    @click="router.push('/template')">
              选模板开店
            </button>
            <button class="btn-ghost max-w-[140px] text-sm"
                    @click="router.push('/generate')">
              AI生成
            </button>
          </div>
        </div>

        <!-- 店铺列表 -->
        <div v-else class="space-y-3">
          <div
            v-for="shop in shops"
            :key="shop.shopId"
            class="card overflow-hidden"
          >
            <!-- 店铺头部（主题色）-->
            <div
              class="flex items-center gap-3 p-3 cursor-pointer
                     active:opacity-80 transition-opacity"
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
              <!-- 店铺链接 -->
              <span class="text-muted text-xs flex-1 truncate">
                /shop/{{ shop.shopId }}
              </span>
              <!-- 分享 -->
              <button
                class="text-xs font-semibold px-3 py-1.5 rounded-full
                       bg-surface2 text-white border border-border
                       active:scale-95 transition-transform duration-100"
                @click="shareShop(shop.shopId)"
              >
                分享
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

      <!-- 新建入口 -->
      <section>
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

    </div>

    <!-- 底部导航 -->
    <nav class="fixed bottom-0 left-0 right-0 z-20
                bg-bg/95 backdrop-blur-md border-t border-border
                flex items-center justify-around
                px-2 pt-2 pb-[calc(.5rem+env(safe-area-inset-bottom))]">
      <button
        class="flex flex-col items-center gap-0.5 px-4 py-1 text-muted
               active:text-white transition-colors"
        @click="router.push('/')"
      >
        <svg class="h-5 w-5" fill="none" viewBox="0 0 24 24"
             stroke="currentColor" stroke-width="2">
          <path stroke-linecap="round" stroke-linejoin="round"
                d="M3 12l9-9 9 9M5 10v9a1 1 0 001 1h4v-5h4v5h4a1 1 0 001-1v-9"/>
        </svg>
        <span class="text-[10px] font-semibold">首页</span>
      </button>
      <button
        class="flex flex-col items-center gap-0.5 px-4 py-1 text-muted
               active:text-white transition-colors"
        @click="router.push('/generate')"
      >
        <svg class="h-5 w-5" fill="none" viewBox="0 0 24 24"
             stroke="currentColor" stroke-width="2">
          <path stroke-linecap="round" stroke-linejoin="round" d="M12 4v16m8-8H4"/>
        </svg>
        <span class="text-[10px] font-semibold">AI生成</span>
      </button>
      <button
        class="flex flex-col items-center gap-0.5 px-4 py-1 text-muted
               active:text-white transition-colors"
        @click="router.push('/template')"
      >
        <svg class="h-5 w-5" fill="none" viewBox="0 0 24 24"
             stroke="currentColor" stroke-width="2">
          <path stroke-linecap="round" stroke-linejoin="round"
                d="M4 5a1 1 0 011-1h4a1 1 0 011 1v4a1 1 0 01-1 1H5a1 1 0 01-1-1V5zm10 0a1 1 0 011-1h4a1 1 0 011 1v4a1 1 0 01-1 1h-4a1 1 0 01-1-1V5zM4 15a1 1 0 011-1h4a1 1 0 011 1v4a1 1 0 01-1 1H5a1 1 0 01-1-1v-4zm10 0a1 1 0 011-1h4a1 1 0 011 1v4a1 1 0 01-1 1h-4a1 1 0 01-1-1v-4z"/>
        </svg>
        <span class="text-[10px] font-semibold">模板</span>
      </button>
      <button
        class="flex flex-col items-center gap-0.5 px-4 py-1 text-accent"
      >
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
