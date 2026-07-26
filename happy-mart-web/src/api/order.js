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
