/**
 * 分类 API 模块
 * 封装商品分类相关的后端接口调用
 */
import request from '@/utils/request'

/**
 * 获取全部分类树
 * GET /api/category/tree
 * @returns {Promise<Array>} 分类树列表（一级分类含 children 子分类）
 */
export function getCategoryTree() {
  return request.get('/category/tree').then(res => res.data)
}