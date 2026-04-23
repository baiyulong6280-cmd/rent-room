/**
 * ai.js — AI 全链路生成接口
 *
 * generateProduct  POST /api/ai/product   → 商品标题 + 卖点 + 价格建议 + 标签
 * generateShop     POST /api/ai/shop      → 完整店铺（banner + 商品列表 + 名称 + 风格）
 * optimizeCopy     POST /api/ai/optimize  → AI 优化文案（标题 / 描述 / 价格 / 风格）
 */
import http from '@/utils/request'

/**
 * 根据图片 + 品类/风格/市场 生成商品信息
 * @param {{ image: string, category: string, style: string, market: string, prompt?: string }} params
 * @returns {Promise<{
 *   title: string,
 *   points: string[],        // 3 条卖点
 *   price: number,           // 建议售价（EUR）
 *   tags: string[],
 *   estimatedMonthlyEarn: number,  // 预估月收益（10% 佣金 × 预测销量）
 * }>}
 */
export function generateProduct(params) {
  return http.post('/api/ai/product', params)
}

/**
 * 一键生成完整店铺（banner + 商品 + 名称 + 统一风格）
 * @param {{ category: string, style: string, market: string, image: string, productInfo: object }} params
 * @returns {Promise<{
 *   shopName: string,
 *   banner: string,          // banner 图片 URL
 *   products: object[],
 *   theme: object,
 *   gradient: string,
 * }>}
 */
export function generateShop(params) {
  return http.post('/api/ai/shop', params)
}

/**
 * AI 优化文案（已有商品 → 更吸引人的版本）
 * @param {{ title: string, points: string[], price: number, style: string }} params
 * @returns {Promise<{
 *   title: string,
 *   points: string[],
 *   price: number,
 *   styleNote: string,       // 风格调整建议
 * }>}
 */
export function optimizeCopy(params) {
  return http.post('/api/ai/optimize', params)
}
