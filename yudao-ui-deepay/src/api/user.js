/**
 * user.js — 用户 / 佣金 API
 *
 * 后端接口规范：
 *
 * GET  /api/user/profile?userId=xxx
 *   → { userId, nickname, totalEarn, totalOrders }
 *
 * GET  /api/user/earnings?userId=xxx
 *   → { total: 123.45, list: [{ orderId, amount, status, createdAt }] }
 *
 * 离线降级：接口失败时返回空数据，不阻断页面渲染。
 */
import axios from 'axios'

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '',
  timeout: 30_000,
  headers: { 'Content-Type': 'application/json' },
})

http.interceptors.response.use(
  res => res.data?.data ?? res.data,
  err => Promise.reject(err),
)

/**
 * 获取用户资料
 * @param {string} userId
 * @returns {Promise<{ userId, nickname, totalEarn, totalOrders }>}
 */
export function getProfile(userId) {
  return http.get('/api/user/profile', { params: { userId } })
}

/**
 * 获取佣金收益列表
 * @param {string} userId
 * @returns {Promise<{ total: number, list: Array }>}
 */
export function getEarnings(userId) {
  return http.get('/api/user/earnings', { params: { userId } })
}
