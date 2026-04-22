/**
 * Deepay API module — all backend calls in one place.
 * Base URL is proxied via vite.config.js in dev; set VITE_API_BASE in .env for prod.
 */
import axios from 'axios'

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '',
  timeout: 30_000,
  headers: { 'Content-Type': 'application/json' },
})

// Unwrap the yudao CommonResult wrapper { code, data, msg }
http.interceptors.response.use(
  res => res.data?.data ?? res.data,
  err => Promise.reject(err)
)

// ── Design ────────────────────────────────────────────────────────────

/** Create async generate task. Returns { taskId, status, quota } */
export function createGenerateTask(userId, category, style, market, priceLevel) {
  return http.post('/api/design/generate', { userId, category, style, market, priceLevel })
}

/** Poll task result. Returns { status, images?, error? } */
export function getTaskResult(taskId) {
  return http.get(`/api/design/result/${taskId}`)
}

/** Record user selection for feedback learning */
export function selectImage(userId, selectedImage, allImages, chainCode, category, style) {
  return http.post('/api/design/select', { userId, selectedImage, allImages, chainCode, category, style })
}

/** Personalised image recommendations */
export function getRecommend(userId, category, style, limit = 10) {
  return http.get('/api/design/recommend', { params: { userId, category, style, limit } })
}

// ── Quota ────────────────────────────────────────────────────────────

/** Get remaining quota + pricing plans */
export function getQuotaInfo(userId) {
  return http.get('/api/quota/info', { params: { userId } })
}

/** Get pricing plans only (for paywall modal) */
export function getPricingPlans() {
  return http.get('/api/quota/plans')
}

// ── Payment ──────────────────────────────────────────────────────────

/**
 * Create a payment order. Returns { payUrl, paymentId, priceEur, … }
 * planId: PACK_S | PACK_M | PACK_L | DAY_PASS
 */
export function createPayment(userId, planId) {
  return http.post('/api/pay/create', { userId, plan: planId })
}

// ── Shop / Order ─────────────────────────────────────────────────────

/** Get shop page data for a product/chain-code */
export function getShopPage(id, currency = 'EUR') {
  return http.get(`/api/shop/${id}`, { params: { currency } })
}

/** Create order (returns { orderId, paymentId, payUrl }) */
export function createOrder(userId, chainCode, amount) {
  return http.post('/api/order/create', { userId, chainCode, amount })
}
