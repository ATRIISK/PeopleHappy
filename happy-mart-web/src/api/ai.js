/**
 * AI 购物助手接口封装（v1.11，阶段四，见开发文档 §14）
 *
 * 为什么不用 src/utils/request.js？
 * request.js 的超时是 30 秒，而通义千问【非流式】生成回答可能要十几秒甚至更久，
 * 走 request.js 容易被误判超时。这里建一个独立 axios 实例，超时放宽到 120 秒。
 *
 * 后端接口：POST /api/ai/chat（游客可调，无需登录）
 * 请求体：{ message: "推荐适合运动的耳机" }
 * 返回体：{ code: 200, message: "成功", data: { response: "AI 回答", sources: [{productId,title,price,image,url}] } }
 */
import axios from 'axios'

// 独立 axios 实例：baseURL /api（vite 代理到后端 8074），超时 120s（大模型生成慢）
const aiRequest = axios.create({
  baseURL: '/api',
  timeout: 120000
})

// 请求拦截器：带上 Bearer token（AI 接口游客也能调，带上有备无患）
aiRequest.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

/**
 * 发送对话消息给 AI 购物助手
 * @param {string} message 用户的问题，如"推荐适合运动的耳机"
 * @returns {Promise<{code:number, message:string, data:{response:string, sources:Array}}>}
 *   code=200 时 data.response 是 AI 回答文本，data.sources 是参考商品列表（可能为空）
 */
export const sendChat = (message) => {
  return aiRequest.post('/ai/chat', { message }).then((res) => res.data)
}
