/**
 * ai.js — AI 全链路生成接口
 *
 * generateProduct    POST /api/ai/product      → 商品标题 + 卖点 + 价格建议 + 标签
 * generateShop       POST /api/ai/shop         → 完整店铺（banner + 商品列表 + 名称 + 风格）
 * optimizeCopy       POST /api/ai/optimize     → AI 优化文案（标题 / 描述 / 价格 / 风格）
 * redesignImages     POST /api/ai/redesign     → 参考图 → N 张新款
 * editImage          POST /api/ai/edit         → 微调：图片 + 指令 → 新图
 * scoreImages        POST /api/ai/score        → 每张图打分 0-100
 * selectBestImages   POST /api/ai/select       → 自动选 Top-3
 * deduplicateImages  POST /api/ai/deduplicate  → 防撞款过滤
 * saveDesign         POST /api/design/save     → 保存到款库
 * getMyDesigns       GET  /api/design/my       → 我的款库
 */
import http from '@/utils/request'

/**
 * 根据图片 + 品类/风格/市场 生成商品信息
 * @param {{ image: string, category: string, style: string, market: string, prompt?: string }} params
 * @returns {Promise<{
 *   title: string,
 *   points: string[],
 *   price: number,
 *   tags: string[],
 *   estimatedMonthlyEarn: number,
 * }>}
 */
export function generateProduct(params) {
  return http.post('/api/ai/product', params)
}

/**
 * 一键生成完整店铺（banner + 商品 + 名称 + 统一风格）
 */
export function generateShop(params) {
  return http.post('/api/ai/shop', params)
}

/**
 * AI 优化文案（已有商品 → 更吸引人的版本）
 */
export function optimizeCopy(params) {
  return http.post('/api/ai/optimize', params)
}

/**
 * AI 改款：上传参考图（URL 列表）+ 风格 → 生成 N 张新款（默认 6 张）
 * @param {{
 *   images: string[],    // 参考图 URL 列表（支持多图）
 *   style: string,       // 'minimal' | 'trendy' | 'luxury' | 'streetwear' | 'elegant'
 *   strength?: number,   // 改动强度 0~1，默认 0.6
 *   count?: number,      // 生成数量，默认 6，最多 8
 *   userId?: string,
 * }} params
 * @returns {Promise<{ images: string[], count: number, style: string }>}
 */
export function redesignImages(params) {
  return http.post('/api/ai/redesign', params)
}

/**
 * AI 微调：对选中图片执行文字指令修改
 * @param {{ image: string, instruction: string, userId?: string }} params
 * @returns {Promise<{ image: string, instruction: string }>}
 */
export function editImage(params) {
  return http.post('/api/ai/edit', params)
}

/**
 * AI 设计评分：对每张图打 0-100 分
 * @param {{ images: string[], style?: string }} params
 * @returns {Promise<{ scores: Array<{ url: string, score: number }>, style: string }>}
 */
export function scoreImages(params) {
  return http.post('/api/ai/score', params)
}

/**
 * AI 自动选款：按评分取 Top-3
 * @param {{ images: string[], style?: string }} params
 * @returns {Promise<{
 *   best: string[],
 *   scores: Array<{ url: string, score: number, recommended: boolean }>
 * }>}
 */
export function selectBestImages(params) {
  return http.post('/api/ai/select', params)
}

/**
 * AI 防撞款：过滤与参考图或彼此高度相似的图片
 * @param {{ images: string[], reference?: string[], threshold?: number }} params
 * @returns {Promise<{
 *   filtered: string[],
 *   originalCount: number,
 *   filteredCount: number,
 *   removedCount: number,
 * }>}
 */
export function deduplicateImages(params) {
  return http.post('/api/ai/deduplicate', params)
}

/**
 * 保存选中图片到用户款库
 * @param {{ imageUrl: string, category?: string, style?: string, userId?: string, source?: string }} params
 * @returns {Promise<{ id: number, imageUrl: string, status: string }>}
 */
export function saveDesign(params) {
  return http.post('/api/design/save', params)
}

/**
 * 获取用户保存的款库列表
 * @param {{ userId?: string, limit?: number }} params
 * @returns {Promise<{ designs: object[], count: number }>}
 */
export function getMyDesigns(params = {}) {
  return http.get('/api/design/my', { params })
}
