/**
 * 商品 API 模块
 * 封装商品相关的后端接口调用
 */
import request from '@/utils/request'

/**
 * 分页查询商品列表
 * GET /api/product/list
 * @param {Object} params 查询参数
 * @param {number} [params.categoryId] - 分类ID
 * @param {string} [params.keyword] - 搜索关键词
 * @param {string} [params.sortBy] - 排序方式：sales/price_asc/price_desc/newest/rating
 * @param {number} [params.page=1] - 页码
 * @param {number} [params.size=12] - 每页条数
 * @returns {Promise<{records: Array, total: number, current: number, size: number}>}
 */
export function getProducts(params) {
  return request.get('/product/list', { params }).then(res => res.data)
}

/**
 * 查询商品详情
 * GET /api/product/detail/{id}
 * @param {number} id - 商品ID
 * @returns {Promise<Object>} 商品详情
 */
export function getProductById(id) {
  return request.get(`/product/detail/${id}`).then(res => res.data)
}

/**
 * 查询热门商品（销量 Top 8）
 * GET /api/product/hot
 * @returns {Promise<Array>} 热门商品列表
 */
export function getHotProducts() {
  return request.get('/product/hot').then(res => res.data)
}