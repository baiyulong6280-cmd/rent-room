/**
 * order.js — order & payment API
 */
import axios from 'axios'

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '',
  timeout: 30_000,
  headers: { 'Content-Type': 'application/json' },
})

// Unwrap CommonResult { code, data, msg }
http.interceptors.response.use(
  res => res.data?.data ?? res.data,
  err => Promise.reject(err)
)

/**
 * Create a product purchase order.
 * Returns { orderId, payUrl }
 * @param {string} shopId
 * @param {number} amount  decimal e.g. 29.99
 * @param {string} currency  e.g. 'EUR'
 */
export function createOrder(shopId, amount, currency = 'EUR') {
  return http.post('/api/order/create', { shopId, amount, currency })
}

/** Create a quota payment order → { payUrl, paymentId, priceEur } */
export function createPayment(userId, planId) {
  return http.post('/api/pay/create', { userId, plan: planId })
}

/** Get shop product page data */
export function getShopPage(id, currency = 'EUR') {
  return http.get(`/api/shop/${id}`, { params: { currency } })
}
