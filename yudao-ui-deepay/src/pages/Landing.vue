<!--
  Landing.vue — 官网入口（高级品牌版）
  原则：少 · 大 · 稳 | 冷静 / 高级 / AI工具感
  结构：① 首屏冲击  ② 视觉展示  ③ CTA转化（≤3屏）
-->
<script setup>
import { useRouter } from 'vue-router'
import Button from '@/components/Button.vue'

const router = useRouter()

const previews = [
  { id: 1, gradient: 'linear-gradient(145deg,#0e1117,#1c2333)', label: '欧美外套', tag: '🔥 热卖' },
  { id: 2, gradient: 'linear-gradient(145deg,#0f1923,#1a3a5c)', label: '韩系连衣裙', tag: '✨ 新款' },
  { id: 3, gradient: 'linear-gradient(145deg,#141414,#1f1f1f)', label: '极简上衣', tag: '💎 精选' },
  { id: 4, gradient: 'linear-gradient(145deg,#0d1520,#142030)', label: '工装裤', tag: '📈 爆款' },
]
</script>

<template>
  <div class="bg-bg text-white overflow-x-hidden font-sans">

    <!-- ① 首屏 ─────────────────────────────────────────────── -->
    <section class="min-h-screen flex flex-col justify-center items-center
                    text-center px-8 relative">

      <!-- 品牌标 -->
      <div class="absolute top-6 left-6 flex items-center gap-2 fade-in"
           style="animation-delay:.1s">
        <span class="w-7 h-7 rounded-lg bg-accent flex items-center justify-center
                     text-black font-black text-sm select-none">D</span>
        <span class="font-semibold text-sm tracking-wide">Deepay</span>
      </div>

      <!-- 主标题 -->
      <h1 class="text-[2.6rem] font-semibold leading-[1.15] tracking-tight mb-5 fade-in"
          style="animation-delay:.2s">
        AI驱动的<br>
        <span class="text-accent">服装爆款发现</span>
      </h1>

      <!-- 副标题 -->
      <p class="text-muted text-base leading-relaxed mb-10 fade-in"
         style="animation-delay:.35s">
        让设计更快成为销量
      </p>

      <!-- 主CTA -->
      <div class="w-full max-w-[260px] fade-in" style="animation-delay:.5s">
        <button
          class="bg-accent text-black font-bold h-12 w-full rounded-full
                 active:scale-95 transition-transform duration-100 text-sm tracking-wide"
          @click="router.push('/generate')"
        >
          开始生成
        </button>
      </div>

      <p class="text-muted/60 text-xs mt-4 fade-in" style="animation-delay:.65s">
        免费体验 · 无需注册
      </p>

      <!-- 向下提示 -->
      <div class="absolute bottom-8 flex flex-col items-center gap-1.5 opacity-40">
        <span class="text-xs text-muted">向下滑动</span>
        <svg class="h-3.5 w-3.5 text-muted animate-bounce" fill="none"
             viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.5">
          <path stroke-linecap="round" stroke-linejoin="round" d="M19 9l-7 7-7-7"/>
        </svg>
      </div>
    </section>

    <!-- ② 视觉展示 ──────────────────────────────────────────── -->
    <section class="px-4 pb-14 max-w-[480px] mx-auto">
      <div class="grid grid-cols-2 gap-3">
        <div
          v-for="item in previews" :key="item.id"
          class="relative aspect-[3/4] rounded-2xl overflow-hidden cursor-pointer
                 opacity-80 hover:opacity-100 active:scale-95
                 transition-all duration-200"
          :style="{ background: item.gradient }"
          @click="router.push('/generate')"
        >
          <span class="absolute top-3 left-3 bg-black/50 text-white/90 text-xs
                       font-medium px-2 py-0.5 rounded-full backdrop-blur-sm">
            {{ item.tag }}
          </span>
          <div class="absolute bottom-0 left-0 right-0 p-3
                      bg-gradient-to-t from-black/70 to-transparent">
            <p class="text-sm font-medium tracking-tight">{{ item.label }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- ③ 信任文案 + CTA ─────────────────────────────────────── -->
    <section class="px-6 pb-20 text-center max-w-[480px] mx-auto">

      <p class="text-muted text-sm leading-loose mb-10">
        基于真实市场趋势 · 自动生成设计方向 · 帮助你快速选款
      </p>

      <button
        class="bg-accent text-black font-bold h-12 px-10 rounded-full
               active:scale-95 transition-transform duration-100 text-sm tracking-wide"
        @click="router.push('/generate')"
      >
        立即开始
      </button>
    </section>

  </div>
</template>

<style scoped>
.fade-in {
  opacity: 0;
  animation: fade-up 0.7s ease forwards;
}
@keyframes fade-up {
  from { opacity: 0; transform: translateY(14px); }
  to   { opacity: 1; transform: translateY(0);    }
}
</style>
