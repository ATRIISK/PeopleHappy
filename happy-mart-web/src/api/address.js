/**
 * 地址 API 模块
 * 封装地址相关的后端接口调用
 */
import request from '@/utils/request'

/**
 * 新增地址
 * POST /api/address/add
 */
export function addAddress(data) {
  return request.post('/address/add', data).then(res => res.data)
}

/**
 * 获取地址列表
 * GET /api/address/list
 */
export function getAddressList() {
  return request.get('/address/list').then(res => res.data)
}

/**
 * 修改地址
 * PUT /api/address/update
 * @param {Object} data - 必须包含 id
 */
export function updateAddress(data) {
  return request.put('/address/update', data).then(res => res.data)
}

/**
 * 删除地址
 * DELETE /api/address/delete/{id}
 */
export function deleteAddress(id) {
  return request.delete(`/address/delete/${id}`).then(res => res.data)
}
