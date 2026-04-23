<!--
  TemplatePreview.vue — 模拟店铺预览 + 一键开店
  路径：/template/:id
-->
<script setup>
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { templates } from '@/data/templates'

const route  = useRoute()
const router = useRouter()

const tpl = computed(() =>
  templates.find(t => t.id === route.params.id) || null
)

const opening = ref(false)

function createShop() {
  if (!tpl.value || opening.value) return
  opening.value = true

  const shopId = Date.now()
  localStorage.setItem(`shop_${shopId}`, JSON.stringify(tpl.value))

  setTimeout(() => {
    router.push(`/shop/${shopId}`)
  }, 600)
}

function share() {
  const url = window.location.href
  if (navigator.share) {
    navigator.share({ title: tpl.value?.name, url })
  } else {
    navigator.clipboard?.writeText(url)
      .then(() => alert('链接已复制'))
  }
}
</script>

<template>
  <div class="min-h-screen bg-bg text-white">

    <!-- 顶部导航 -->
    <header class="sticky top-0 z-10 bg-bg/90 backdrop-blur-md
                   border-b border-border px-4 py-3
                   flex items-center justify-between">
      <button class="text-muted active:text-white transition-colors"
              @click="router.back()">
        <svg class="h-5 w-5" fill="none" viewBox="0 0 24 24"
             stroke="currentColor" stroke-width="2.5">
          <path stroke-linecap="round" stroke-linejoin="round" d="M15 19l-7-7 7-7"/>
        </svg>
      </button>
      <span class="font-semibold text-sm">{{ tpl?.name || '模板预览' }}</span>
      <button class="text-muted active:text-white transition-colors" @click="share">
        <svg class="h-5 w-5" fill="none" viewBox="0 0 24 24"
             stroke="currentColor" stroke-width="2">
          <path stroke-linecap="round" stroke-linejoin="round"
                d="M8.684 13.342C8.886 12.938 9 12.482 9 12c0-.482-.114-.938-.316-1.342m0 2.684a3 3 0 1 1 0-2.684m0 2.684l6.632 3.316m-6.632-6l6.632-3.316m0 0a3 3 0 1 0 5.367-2.684 3 3 0 0 0-5.367 2.684zm0 9.316a3 3 0 1 0 5.368 2.684 3 3 0 0 0-5.368-2.684z"/>
        </svg>
      </button>
    </header>

    <!-- 模板不存在 -->
    <div v-if="!tpl"
         class="flex flex-col items-center justify-center min-h-[60vh] gap-4 text-center px-6">
      <p class="text-4xl">🤔</p>
      <p class="text-muted text-sm">模板不存在</p>
      <button class="btn-primary max-w-[200px]"
              @click="router.push('/template')">
        返回模板列表
      </button>
    </div>

    <!-- 模拟店铺预览（留出底部固定栏空间）-->
    <template v-else>
      <div class="pb-28">

        <!-- 店铺横幅 -->
        <div
          class="w-full h-56 flex flex-col justify-end p-5"
          :style="{ background: tpl.gradient }"
        >
          <span class="text-xs bg-black/40 backdrop-blur-sm self-start
                       px-2 py-0.5 rounded-full mb-2 text-white/80">
            {{ tpl.tag }}
          </span>
          <h1 class="text-2xl font-bold">{{ tpl.name }}</h1>
          <p class="text-white/60 text-sm mt-1">
            {{ tpl.products.length }} 款精选商品
          </p>
        </div>

        <!-- 商品列表（single）-->
        <div v-if="tpl.type === 'single'"
             class="max-w-[480px] mx-auto px-4 pt-5 space-y-4">
          <div
            v-for="(p, i) in tpl.products"
            :key="i"
            class="card overflow-hidden"
          >
            <div
              class="w-full aspect-square"
              :style="{ background: p.gradient }"
            />
            <div class="p-4">
              <p class="font-semibold">{{ p.name }}</p>
              <p class="text-accent text-lg font-bold mt-1">€{{ p.price }}</p>
              <button class="btn-primary mt-3 text-sm">立即购买</button>
            </div>
          </div>
        </div>

        <!-- 商品网格（grid）-->
        <div v-else-if="tpl.type === 'grid'"
             class="max-w-[480px] mx-auto px-4 pt-5">
          <div class="grid grid-cols-2 gap-3">
            <div
              v-for="(p, i) in tpl.products"
              :key="i"
              class="card overflow-hidden"
            >
              <div
                class="w-full aspect-square"
                :style="{ background: p.gradient }"
              />
              <div class="p-3">
                <p class="font-semibold text-sm">{{ p.name }}</p>
                <p class="text-accent font-bold mt-0.5">€{{ p.price }}</p>
              </div>
            </div>
          </div>
        </div>

      </div><!-- /pb-28 -->

      <!-- 底部固定操作栏 -->
      <div class="fixed bottom-0 left-0 right-0 z-20
                  bg-bg/95 backdrop-blur-md border-t border-border
                  px-4 pt-3 pb-[calc(.75rem+env(safe-area-inset-bottom))]">
        <p class="text-muted text-xs text-center mb-3">
          预览效果 · 开店后获得专属链接
        </p>
        <button
          :disabled="opening"
          class="btn-primary w-full text-sm font-bold"
          @click="createShop"
        >
          <svg v-if="opening" class="animate-spin h-4 w-4 shrink-0"
               viewBox="0 0 24 24" fill="none">
            <circle cx="12" cy="12" r="10" stroke="currentColor"
                    stroke-opacity=".3" stroke-width="3"/>
            <path d="M22 12a10 10 0 0 0-10-10" stroke="currentColor"
                  stroke-width="3" stroke-linecap="round"/>
          </svg>
          {{ opening ? '正在生成店铺…' : '🚀 一键开店' }}
        </button>
      </div>
    </template>

  </div>
</template>
