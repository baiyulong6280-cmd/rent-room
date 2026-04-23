/**
 * user.js — 用户 / 佣金 API
 * X-User-Id header injected automatically by request.js interceptor.
 */
import http from '@/utils/request'

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
