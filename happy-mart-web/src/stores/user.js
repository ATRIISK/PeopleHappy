import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import request from '@/utils/request'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const userInfo = ref(null)

  const isLoggedIn = computed(() => !!token.value)

  async function login(loginData) {
    const res = await request.post('/user/login', loginData)
    token.value = res.data.token
    userInfo.value = res.data.userInfo   // ← 后端 LoginVO 中返回的是 userInfo 字段
    localStorage.setItem('token', res.data.token)
    return res
  }

  async function register(registerData) {
    return request.post('/user/register', registerData)
  }

  async function getUserInfo() {
    const res = await request.get('/user/info')
    userInfo.value = res.data
    return res
  }

  function logout() {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem('token')
  }

  return { token, userInfo, isLoggedIn, login, register, getUserInfo, logout }
})
