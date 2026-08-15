/**
 * 管理后台 API 接口层
 *
 * 所有接口都需要管理员权限（后端 @Auth(requireAdmin=true) 校验，非 ADMIN 返回 403）
 * 对应开发文档 §5.7 管理后台接口
 *
 * 每个函数 = request.METHOD(...) + .then(res => res.data)
 * - request 是 axios 封装（baseURL=/api），会自动带 Bearer token
 * - res.data 是 Result 里的 data 字段（响应拦截器已经把 code!==200 的错误弹掉了）
 */
import request from '@/utils/request'

/**
 * 获取数据看板统计（商品数/订单数/用户数/总交易额）
 * GET /api/admin/dashboard/stats
 */
export function getDashboardStats() {
  return request.get('/admin/dashboard/stats').then(res => res.data)
}

/**
 * 获取最近订单（最新 5 条，含下单人用户名）
 * GET /api/admin/dashboard/recent-orders
 */
export function getRecentOrders() {
  return request.get('/admin/dashboard/recent-orders').then(res => res.data)
}

/**
 * 分页查询全部商品（含上架+下架）
 * GET /api/admin/product/list
 * @param params { keyword, page, size }
 */
export function getAdminProducts(params) {
  return request.get('/admin/product/list', { params }).then(res => res.data)
}

/**
 * 新增/修改商品（id 为 null=新增，有值=修改）
 * POST /api/admin/product/save
 */
export function saveAdminProduct(data) {
  return request.post('/admin/product/save', data).then(res => res.data)
}

/**
 * 上架/下架商品（status：0=上架，1=下架）
 * PUT /api/admin/product/status/{id}?status=
 * 注意：axios 的 put 第二个参数是 body（这里传 null），第三个参数是 config，query 参数放 params
 */
export function updateAdminProductStatus(id, status) {
  return request.put(`/admin/product/status/${id}`, null, { params: { status } }).then(res => res.data)
}

/**
 * 删除商品
 * DELETE /api/admin/product/{id}
 */
export function deleteAdminProduct(id) {
  return request.delete(`/admin/product/${id}`).then(res => res.data)
}

/**
 * 分页查询全部订单（可按状态筛选）
 * GET /api/admin/order/list
 * @param params { status, page, size }
 */
export function getAdminOrders(params) {
  return request.get('/admin/order/list', { params }).then(res => res.data)
}

/**
 * 修改订单状态（目前仅支持"发货"）
 * PUT /api/admin/order/status
 * @param data { id, status }
 */
export function updateAdminOrderStatus(data) {
  return request.put('/admin/order/status', data).then(res => res.data)
}

/**
 * 分页查询用户列表（可搜索 username/phone）
 * GET /api/admin/user/list
 * @param params { keyword, page, size }
 */
export function getAdminUsers(params) {
  return request.get('/admin/user/list', { params }).then(res => res.data)
}

/**
 * 禁用/启用用户（status：0=启用，1=禁用）
 * PUT /api/admin/user/status/{id}
 */
export function updateAdminUserStatus(id, status) {
  return request.put(`/admin/user/status/${id}`, { status }).then(res => res.data)
}

/**
 * 重置用户密码
 * PUT /api/admin/user/resetPwd/{id}
 */
export function resetUserPassword(id, password) {
  return request.put(`/admin/user/resetPwd/${id}`, { password }).then(res => res.data)
}
