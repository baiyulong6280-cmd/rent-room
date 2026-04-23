<!--
  Inspiration.vue — 时装灵感库
  路径：/inspiration

  功能：
  ✔ 分类过滤（时装周 / 品牌新品 / 大片精选）
  ✔ 风格过滤（极简 / 高端 / 潮流 / 街头 / 优雅）
  ✔ 瀑布流展示（CSS columns）
  ✔ 多选参考图（最多 5 张）
  ✔ 来源徽章、品牌名、风格标签
  ✔ 点击"加入设计" → 跳转 /redesign?refs=url1,url2,...
  ✔ 单张"快速改款"直接跳转

  灵感融合方式（标注在 UI 上）：
  秀场图 → 提取结构/剪裁
  品牌图 → 提取可穿性/配色
  AI 混合两种图 → 生成新款
-->
<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  inspirationItems,
  INSPIRATION_CATEGORIES,
  INSPIRATION_STYLES,
  SOURCE_LABELS,
} from '@/data/inspiration'

const router = useRouter()

// ── 筛选状态 ──────────────────────────────────────────────────────────
const activeCategory = ref('all')
const activeStyle    = ref('')
const searchQuery    = ref('')

// ── 多选状态 ──────────────────────────────────────────────────────────
const selectedIds = ref(new Set())

// ── 数据（先用本地种子，可后续接口替换）──────────────────────────────
const items = ref([...inspirationItems])

// ── 过滤后列表 ────────────────────────────────────────────────────────
const filtered = computed(() => {
  let list = items.value
  if (activeCategory.value !== 'all') {
    list = list.filter(i => i.source === activeCategory.value)
  }
  if (activeStyle.value) {
    list = list.filter(i => i.style === activeStyle.value)
  }
  if (searchQuery.value.trim()) {
    const q = searchQuery.value.trim().toLowerCase()
    list = list.filter(i =>
      i.desc?.toLowerCase().includes(q) ||
      i.brand?.toLowerCase().includes(q) ||
      i.type?.toLowerCase().includes(q) ||
      i.style?.toLowerCase().includes(q)
    )
  }
  return list
})

// ── 选中的 URL 列表 ───────────────────────────────────────────────────
const selectedUrls = computed(() =>
  items.value
    .filter(i => selectedIds.value.has(i.id))
    .map(i => i.image)
)

function toggleSelect(item) {
  if (selectedIds.value.has(item.id)) {
    selectedIds.value.delete(item.id)
  } else {
    if (selectedIds.value.size >= 5) return // max 5
    selectedIds.value.add(item.id)
  }
}

function isSelected(item) {
  return selectedIds.value.has(item.id)
}

// ── 跳转到改款 ────────────────────────────────────────────────────────
function goRedesign() {
  if (selectedUrls.value.length === 0) return
  const refs = selectedUrls.value.map(u => encodeURIComponent(u)).join(',')
  router.push(`/redesign?refs=${refs}`)
}

function quickRedesign(item) {
  const refs = encodeURIComponent(item.image)
  router.push(`/redesign?refs=${refs}`)
}

// ── 来源标签颜色 ──────────────────────────────────────────────────────
function sourceColor(source) {
  if (source === 'fashion_week')   return '#A855F7'   // purple
  if (source === 'brand_lookbook') return '#00FF88'   // green
  return '#F59E0B'                                    // amber (editorial)
}

function sourceLabel(source) {
  return SOURCE_LABELS[source] ?? source
}

// ── 风格标签颜色 ──────────────────────────────────────────────────────
function styleTagBg(style) {
  const map = {
    minimal:    '#1F2937',
    luxury:     '#1C1917',
    trendy:     '#1E1B4B',
    streetwear: '#0F172A',
    elegant:    '#1A1429',
    'avant-garde': '#111827',
  }
  return map[style] ?? '#1A1A1A'
}
</script>

<template>
  <div class="min-h-screen bg-bg text-white">

    <!-- ── 顶部导航 ──────────────────────────────────── -->
    <header class="sticky top-0 z-10 bg-bg/90 backdrop-blur-md
                   border-b border-border px-4 py-3">
      <div class="flex items-center gap-3 mb-3">
        <button class="text-muted active:text-white transition-colors"
                @click="router.back()">
          <svg class="h-5 w-5" fill="none" viewBox="0 0 24 24"
               stroke="currentColor" stroke-width="2.5">
            <path stroke-linecap="round" stroke-linejoin="round" d="M15 19l-7-7 7-7"/>
          </svg>
        </button>
        <div class="flex flex-col flex-1">
          <span class="font-semibold text-sm">🎭 时装灵感库</span>
          <span class="text-[10px]" style="color:#A855F7">
            秀场图 × 品牌图 → AI 融合改款
          </span>
        </div>

        <!-- 已选计数 + 快速跳转 -->
        <button
          v-if="selectedIds.size > 0"
          class="flex items-center gap-1.5 px-3 py-1.5 rounded-full
                 font-bold text-xs active:scale-95 transition-transform"
          style="background:#00FF88;color:#000"
          @click="goRedesign"
        >
          ✨ 改款 ({{ selectedIds.size }})
        </button>
      </div>

      <!-- 搜索框 -->
      <div class="relative">
        <svg class="absolute left-3 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted"
             fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
          <path stroke-linecap="round" stroke-linejoin="round"
                d="M21 21l-4.35-4.35M17 11A6 6 0 115 11a6 6 0 0112 0z"/>
        </svg>
        <input
          v-model="searchQuery"
          type="search"
          placeholder="搜索品牌、风格、款式…"
          class="w-full h-9 pl-8 pr-3 rounded-xl text-xs bg-surface2
                 border border-border text-white placeholder:text-muted
                 focus:outline-none focus:border-accent"
        />
      </div>
    </header>

    <div class="max-w-[480px] mx-auto px-3 pt-4 pb-32">

      <!-- ── 分类 tabs ─────────────────────────────── -->
      <div class="flex gap-2 overflow-x-auto pb-2 scrollbar-hide mb-2">
        <button
          v-for="cat in INSPIRATION_CATEGORIES"
          :key="cat.key"
          :class="['shrink-0 px-3 py-1.5 rounded-full text-xs font-semibold transition-all',
                   activeCategory === cat.key
                     ? 'text-black'
                     : 'border border-border text-muted']"
          :style="activeCategory === cat.key
            ? 'background:#A855F7'
            : ''"
          @click="activeCategory = cat.key"
        >
          {{ cat.label }}
        </button>
      </div>

      <!-- ── 风格 chips ─────────────────────────────── -->
      <div class="flex gap-2 overflow-x-auto pb-2 scrollbar-hide mb-4">
        <button
          v-for="s in INSPIRATION_STYLES"
          :key="s.key"
          :class="['shrink-0 chip text-xs transition-all',
                   activeStyle === s.key ? 'chip-active' : '']"
          @click="activeStyle = s.key"
        >
          {{ s.label }}
        </button>
      </div>

      <!-- 结果数 & 融合提示 -->
      <div class="flex items-center justify-between mb-3">
        <p class="text-xs text-muted">共 {{ filtered.length }} 件</p>
        <p v-if="selectedIds.size > 0" class="text-xs font-semibold"
           style="color:#00FF88">
          已选 {{ selectedIds.size }}/5 张参考
        </p>
        <p v-else class="text-[11px] text-muted">点击图片多选 → AI 混合改款</p>
      </div>

      <!-- ── 瀑布流 ─────────────────────────────────── -->
      <div v-if="filtered.length"
           class="columns-2 gap-3">
        <div
          v-for="item in filtered"
          :key="item.id"
          class="break-inside-avoid mb-3 rounded-2xl overflow-hidden
                 relative cursor-pointer group"
          :class="isSelected(item)
            ? 'ring-2 ring-offset-1 ring-offset-bg'
            : ''"
          :style="isSelected(item) ? 'ring-color:#00FF88' : ''"
          style="border: 2px solid transparent"
          :data-selected="isSelected(item)"
          @click="toggleSelect(item)"
        >
          <!-- 图片 -->
          <img
            :src="item.image"
            :alt="item.desc"
            loading="lazy"
            class="w-full object-cover transition-transform duration-300
                   group-active:scale-[.98]"
            :style="{ minHeight: '120px' }"
          />

          <!-- 渐变遮罩 -->
          <div class="absolute inset-0 bg-gradient-to-t
                      from-black/70 via-transparent to-transparent
                      pointer-events-none" />

          <!-- 底部信息 -->
          <div class="absolute bottom-0 left-0 right-0 p-2.5 space-y-1">
            <!-- 来源徽章 -->
            <span class="text-[10px] font-bold px-2 py-0.5 rounded-full"
                  :style="{ background: sourceColor(item.source) + '33',
                            color: sourceColor(item.source),
                            border: `1px solid ${sourceColor(item.source)}44` }">
              {{ sourceLabel(item.source) }}
            </span>
            <p class="text-white text-[11px] font-semibold leading-snug line-clamp-2">
              {{ item.desc }}
            </p>
            <div class="flex items-center gap-1 flex-wrap">
              <span class="text-[10px] text-white/60">{{ item.brand }}</span>
              <span class="text-[10px] text-white/40">·</span>
              <span class="text-[10px] px-1.5 py-0.5 rounded"
                    :style="{ background: styleTagBg(item.style), color: '#9CA3AF' }">
                {{ item.style }}
              </span>
              <span v-if="item.season"
                    class="text-[10px] text-white/40">{{ item.season }}</span>
            </div>
          </div>

          <!-- 选中勾 -->
          <div v-if="isSelected(item)"
               class="absolute top-2 right-2 w-6 h-6 rounded-full
                      flex items-center justify-center font-bold text-xs"
               style="background:#00FF88;color:#000">✓</div>

          <!-- 快速改款按钮（hover / 未选中时显示）-->
          <button
            v-if="!isSelected(item)"
            class="absolute top-2 right-2 opacity-0 group-hover:opacity-100
                   group-active:opacity-100 transition-opacity
                   bg-black/60 backdrop-blur-sm border border-white/20
                   text-white text-[10px] font-semibold
                   px-2 py-1 rounded-full"
            @click.stop="quickRedesign(item)"
          >
            改款 →
          </button>
        </div>
      </div>

      <!-- 空状态 -->
      <div v-else class="text-center py-20">
        <p class="text-3xl mb-4">🔍</p>
        <p class="text-white font-semibold">没有符合条件的灵感</p>
        <p class="text-muted text-sm mt-1">换个分类或清空搜索</p>
      </div>

    </div><!-- /max-w -->

    <!-- ── 底部选中操作栏 ────────────────────────────── -->
    <transition
      enter-active-class="transition-all duration-300 ease-out"
      leave-active-class="transition-all duration-200 ease-in"
      enter-from-class="translate-y-full opacity-0"
      leave-to-class="translate-y-full opacity-0"
    >
      <div v-if="selectedIds.size > 0"
           class="fixed bottom-0 left-0 right-0 z-30
                  bg-bg/95 backdrop-blur-md border-t border-border
                  px-4 pt-3 pb-[calc(.75rem+env(safe-area-inset-bottom))]">

        <!-- 已选缩略图 -->
        <div class="flex gap-2 overflow-x-auto pb-2 scrollbar-hide mb-3">
          <div
            v-for="url in selectedUrls"
            :key="url"
            class="shrink-0 w-12 h-12 rounded-xl overflow-hidden border border-accent"
          >
            <img :src="url" class="w-full h-full object-cover" />
          </div>
          <div v-if="selectedIds.size < 5"
               class="shrink-0 w-12 h-12 rounded-xl border border-dashed border-border
                      flex items-center justify-center text-muted text-xs">
            +{{ 5 - selectedIds.size }}
          </div>
        </div>

        <div class="flex gap-3">
          <button
            class="flex-1 h-12 rounded-full border border-border text-muted
                   text-sm font-medium active:opacity-70 transition-opacity"
            @click="selectedIds.clear()"
          >
            取消选择
          </button>
          <button
            class="flex-[2] h-12 rounded-full font-bold text-sm
                   active:scale-95 transition-transform duration-100
                   flex items-center justify-center gap-2"
            style="background:#00FF88;color:#000"
            @click="goRedesign"
          >
            ✨ 用这 {{ selectedIds.size }} 张改款
          </button>
        </div>

        <!-- 融合提示 -->
        <p v-if="selectedIds.size >= 2" class="text-center text-[10px] text-muted mt-2">
          💡 选一张秀场图 + 一张品牌图 → AI 融合结构 × 可穿性
        </p>
      </div>
    </transition>

    <!-- ── 底部导航（无选中状态时显示）──────────────── -->
    <nav v-if="selectedIds.size === 0"
         class="fixed bottom-0 left-0 right-0 z-20
                bg-bg/95 backdrop-blur-md border-t border-border
                flex items-center justify-around
                px-2 pt-2 pb-[calc(.5rem+env(safe-area-inset-bottom))]">
      <button class="flex flex-col items-center gap-0.5 px-3 py-1 text-muted
                     active:text-white transition-colors"
              @click="router.push('/')">
        <svg class="h-5 w-5" fill="none" viewBox="0 0 24 24"
             stroke="currentColor" stroke-width="2">
          <path stroke-linecap="round" stroke-linejoin="round"
                d="M3 12l9-9 9 9M5 10v9a1 1 0 001 1h4v-5h4v5h4a1 1 0 001-1v-9"/>
        </svg>
        <span class="text-[10px] font-semibold">首页</span>
      </button>
      <button class="flex flex-col items-center gap-0.5 px-3 py-1 text-muted
                     active:text-white transition-colors"
              @click="router.push('/generate')">
        <svg class="h-5 w-5" fill="none" viewBox="0 0 24 24"
             stroke="currentColor" stroke-width="2">
          <path stroke-linecap="round" stroke-linejoin="round" d="M12 4v16m8-8H4"/>
        </svg>
        <span class="text-[10px] font-semibold">AI生成</span>
      </button>
      <button class="flex flex-col items-center gap-0.5 px-3 py-1 text-accent">
        <svg class="h-5 w-5" fill="none" viewBox="0 0 24 24"
             stroke="currentColor" stroke-width="2">
          <path stroke-linecap="round" stroke-linejoin="round"
                d="M5 3l14 9-14 9V3z"/>
        </svg>
        <span class="text-[10px] font-semibold">灵感</span>
      </button>
      <button class="flex flex-col items-center gap-0.5 px-3 py-1 text-muted
                     active:text-white transition-colors"
              @click="router.push('/redesign')">
        <svg class="h-5 w-5" fill="none" viewBox="0 0 24 24"
             stroke="currentColor" stroke-width="2">
          <path stroke-linecap="round" stroke-linejoin="round"
                d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z"/>
        </svg>
        <span class="text-[10px] font-semibold">改款</span>
      </button>
      <button class="flex flex-col items-center gap-0.5 px-3 py-1 text-muted
                     active:text-white transition-colors"
              @click="router.push('/me')">
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

<style scoped>
/* CSS column-based waterfall layout — no extra JS library needed */
.columns-2 {
  column-count: 2;
  column-gap: 0.75rem;
}
/* Fix selected border highlight using attribute */
[data-selected="true"] {
  border-color: #00FF88 !important;
}
</style>
