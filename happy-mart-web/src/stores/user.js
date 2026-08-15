import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import request from '@/utils/request'

export const useUserStore = defineStore('user', () => {
  // token 持久化到 localStorage（原来就有）
  const token = ref(localStorage.getItem('token') || '')

  // 用户信息也持久化到 localStorage：刷新页面不丢
  // 为什么必须持久化？
  //   路由守卫要判断"当前用户是不是 ADMIN"，如果 userInfo 只在内存里，
  //   一刷新页面就变成 null，后台页面刷新一下就被守卫踢回首页了。
  // 安全解析（code-review 修复）：localStorage 可能被手改/写坏成非 JSON，
  // 直接 JSON.parse 会抛 SyntaxError 导致整个应用白屏，用 try-catch 兜底。
  const userInfo = ref(parseStoredUserInfo())

  /**
   * 安全读取 localStorage 里的用户信息
   * 解析失败（非 JSON 脏数据）→ 清掉脏数据返回 null，不让异常崩掉应用
   */
  function parseStoredUserInfo() {
    try {
      return JSON.parse(localStorage.getItem('userInfo') || 'null')
    } catch {
      localStorage.removeItem('userInfo')
      return null
    }
  }

  // 是否已登录：有 token 就算已登录
  const isLoggedIn = computed(() => !!token.value)

  // 是否管理员：根据用户信息里的 role 判断（userInfo 持久化后刷新也有效）
  // 路由守卫判断后台权限用（后端拦截器是最终防线，这里是体验层提前拦一下）
  const isAdmin = computed(() => userInfo.value?.role === 'ADMIN')

  async function login(loginData) {
    const res = await request.post('/user/login', loginData)
    token.value = res.data.token
    userInfo.value = res.data.userInfo   // ← 后端 LoginVO 中返回的是 userInfo 字段
    localStorage.setItem('token', res.data.token)
    localStorage.setItem('userInfo', JSON.stringify(res.data.userInfo))
    return res
  }

  async function register(registerData) {
    return request.post('/user/register', registerData)
  }

  async function getUserInfo() {
    const res = await request.get('/user/info')
    userInfo.value = res.data
    localStorage.setItem('userInfo', JSON.stringify(res.data))
    return res
  }

  function logout() {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
  }

  return { token, userInfo, isLoggedIn, isAdmin, login, register, getUserInfo, logout }
})
