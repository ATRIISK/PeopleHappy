/**
 * 订单 API 模块
 * 封装订单相关的后端接口调用
 */
import request from '@/utils/request'

/**
 * 创建订单（从购物车创建）
 * POST /api/order/create
 * @param {Object} data - { addressId: 1 }
 * @returns {Promise<Object>} 创建好的订单
 */
export function createOrder(data) {
  return request.post('/order/create', data).then(res => res.data)
}

/**
 * 分页查询订单列表
 * GET /api/order/list
 * @param {Object} params - { status, page, size }
 * @returns {Promise<{records: Array, total: number}>}
 */
export function getOrderList(params) {
  return request.get('/order/list', { params }).then(res => res.data)
}

/**
 * 查询订单详情
 * GET /api/order/detail/{id}
 * @param {number} id - 订单ID
 * @returns {Promise<Object>}
 */
export function getOrderDetail(id) {
  return request.get(`/order/detail/${id}`).then(res => res.data)
}

/**
 * 取消订单
 * PUT /api/order/cancel/{id}
 * @param {number} id - 订单ID
 * @returns {Promise}
 */
export function cancelOrder(id) {
  return request.put(`/order/cancel/${id}`).then(res => res.data)
}

/**
 * 退单退款（已支付未发货一键退单）
 * PUT /api/order/refund/{id}
 * 与取消订单的区别：取消针对未付款，退单针对已付款未发货。
 * 退单后恢复商品库存。
 * @param {number} id - 订单ID
 * @returns {Promise}
 */
export function refundOrder(id) {
  return request.put(`/order/refund/${id}`).then(res => res.data)
}

/**
 * 确认收货
 * PUT /api/order/confirm/{id}
 * @param {number} id - 订单ID
 * @returns {Promise}
 */
export function confirmOrder(id) {
  return request.put(`/order/confirm/${id}`).then(res => res.data)
}

/**
 * 修改订单收货地址
 * PUT /api/order/updateAddress/{id}
 * 只能在"待付款"或"待发货"状态下修改
 * @param {number} orderId - 订单ID
 * @param {number} addressId - 新地址ID
 * @returns {Promise}
 */
export function updateOrderAddress(orderId, addressId) {
  return request.put(`/order/updateAddress/${orderId}`, { addressId }).then(res => res.data)
}

/**
 * 发起支付宝扫码支付
 * POST /api/order/pay/{id}
 * 只能对"待付款"的订单发起，返回含 codeUrl（二维码内容）的 PayVO
 * @param {number} id - 订单ID
 * @returns {Promise<{codeUrl: string}>} { codeUrl: "https://qr.alipay.com/..." }
 */
export function payOrder(id) {
  return request.post(`/order/pay/${id}`).then(res => res.data)
}

/**
 * 查询订单支付状态（前端轮询用）
 * GET /api/order/status/{id}
 * @param {number} id - 订单ID
 * @returns {Promise<number>} 订单状态：0待支付 1已支付 2已发货 3已完成 4已取消 5已退款
 */
export function getOrderStatus(id) {
  return request.get(`/order/status/${id}`).then(res => res.data)
}

/**
 * 【开发调试用】模拟支付宝回调，直接标记订单为已支付
 * POST /api/pay/simulate/{orderId}
 * 绕过支付宝回调通知，用于本地开发调试支付成功流程
 * @param {number} orderId - 订单ID
 * @returns {Promise}
 */
export function simulatePayment(orderId) {
  return request.post(`/pay/simulate/${orderId}`)
}
