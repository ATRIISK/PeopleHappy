import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器
request.interceptors.request.use(
  (config) => {
    // 从 localStorage 获取 token
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// 响应拦截器
request.interceptors.response.use(
  (response) => {
    const res = response.data
    // 业务状态码处理
    if (res.code && res.code !== 200) {
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message))
    }
    return res
  },
  (error) => {
    if (error.response) {
      const { status } = error.response
      switch (status) {
        case 401:
          // 清 token + userInfo（code-review 修复：原来只清 token，残留的 userInfo.role 会让 isAdmin 判断错乱）
          localStorage.removeItem('token')
          localStorage.removeItem('userInfo')
          // 弹出后端返回的提示（如被禁用时"账号已被禁用，请联系管理员"），没有则用默认提示
          ElMessage.error(error.response.data?.message || '登录已过期，请重新登录')
          // 带上 redirect 参数：重新登录后回到原来在看的页面（code-review 修复）
          const redirect = encodeURIComponent(window.location.pathname + window.location.search)
          window.location.href = '/login?redirect=' + redirect
          break
        case 403:
          ElMessage.error('无权限访问')
          break
        case 500:
          ElMessage.error('服务器异常')
          break
        default:
          ElMessage.error(error.message || '网络错误')
      }
    } else {
      ElMessage.error('网络连接失败')
    }
    return Promise.reject(error)
  }
)

export default request
