/**
 * request.js — 统一 HTTP 客户端
 *
 * 特性：
 *   ① 自动注入 X-User-Id 请求头（从 localStorage 读取）
 *   ② 自动解包 CommonResult { code, data, msg }
 *   ③ 统一 30s 超时
 *   ④ 整个 app 共用一个实例，不重复创建
 *
 * 用法：
 *   import http from '@/utils/request'
 *   const res = await http.get('/api/xxx')
 */
import axios from 'axios'

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '',
  timeout: 30_000,
  headers: { 'Content-Type': 'application/json' },
})

// ── 请求拦截：注入用户身份 ───────────────────────────────────────────
http.interceptors.request.use(config => {
  const uid = localStorage.getItem('deepay_uid')
  if (uid) {
    config.headers['X-User-Id'] = uid
  }
  return config
})

// ── 响应拦截：解包 CommonResult ──────────────────────────────────────
http.interceptors.response.use(
  res => res.data?.data ?? res.data,
  err => Promise.reject(err),
)

export default http
