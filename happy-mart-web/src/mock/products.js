/**
 * 商品 Mock 数据模块
 * 提供商品分类数据、商品列表数据以及模拟查询函数
 *
 * ⚠️ 分类 ID 和商品数据与数据库 init-data.sql / 后端 API 保持一致
 *    分类 ID: 1-8 一级分类，9-12 二级分类
 *    商品 status: 0=上架，1=下架（与后端一致）
 */

/**
 * 商品分类数据
 * 每个分类包含 id（分类ID）、name（分类名称）、
 * children（子分类数组，每个子分类有 id 和 name）
 *
 * 对应数据库 category 表的 8 个一级分类 + 4 个子分类
 */
export const categories = [
  {
    id: 1,
    name: '手机数码',
    children: [
      { id: 9, name: '手机' },
      { id: 10, name: '平板' }
    ]
  },
  {
    id: 2,
    name: '电脑办公',
    children: [
      { id: 11, name: '笔记本' },
      { id: 12, name: '台式机' }
    ]
  },
  {
    id: 3,
    name: '家用电器',
    children: []
  },
  {
    id: 4,
    name: '服装鞋帽',
    children: []
  },
  {
    id: 5,
    name: '食品生鲜',
    children: []
  },
  {
    id: 6,
    name: '图书教育',
    children: []
  },
  {
    id: 7,
    name: '母婴玩具',
    children: []
  },
  {
    id: 8,
    name: '运动户外',
    children: []
  }
]

/**
 * 商品 Mock 数据（12个商品）
 * 与数据库 init-data.sql 保持一致：
 *   - categoryId 对应数据库分类 ID（9=手机, 10=平板, 11=笔记本, 12=台式机）
 *   - status: 0=上架, 1=下架（与后端一致）
 *   - 价格、销量、库存数据与数据库一致
 * 图片统一使用 picsum.photos 占位图
 */
export const mockProducts = [
  {
    id: 1,
    name: '华为 Mate 70 Pro',
    description: '麒麟9100芯片，鸿蒙OS 5.0，超聚光影像系统',
    price: 6999.00,
    originalPrice: 7999.00,
    image: 'https://picsum.photos/seed/product1/400',
    images: [
      'https://picsum.photos/seed/product1a/400',
      'https://picsum.photos/seed/product1b/400'
    ],
    categoryId: 9,
    categoryName: '手机',
    sales: 2589,
    stock: 500,
    rating: 4.9,
    status: 0           // 0=上架（与后端一致）
  },
  {
    id: 2,
    name: 'Apple Watch Ultra 2',
    description: '钛金属表壳，49mm，全天候视网膜屏',
    price: 5999.00,
    originalPrice: 6499.00,
    image: 'https://picsum.photos/seed/product2/400',
    images: [
      'https://picsum.photos/seed/product2a/400',
      'https://picsum.photos/seed/product2b/400'
    ],
    categoryId: 9,
    categoryName: '手机',
    sales: 1856,
    stock: 300,
    rating: 4.8,
    status: 0
  },
  {
    id: 3,
    name: 'MacBook Pro 16寸',
    description: 'M3 Max芯片，36GB统一内存，512GB SSD',
    price: 24999.00,
    originalPrice: 27999.00,
    image: 'https://picsum.photos/seed/product3/400',
    images: [
      'https://picsum.photos/seed/product3a/400',
      'https://picsum.photos/seed/product3b/400'
    ],
    categoryId: 11,
    categoryName: '笔记本',
    sales: 3267,
    stock: 200,
    rating: 4.9,
    status: 0
  },
  {
    id: 4,
    name: '戴尔 U2723QX 显示器',
    description: '27英寸 4K IPS Black，Type-C 90W反向充电',
    price: 4299.00,
    originalPrice: 4999.00,
    image: 'https://picsum.photos/seed/product4/400',
    images: [
      'https://picsum.photos/seed/product4a/400',
      'https://picsum.photos/seed/product4b/400'
    ],
    categoryId: 11,
    categoryName: '笔记本',
    sales: 1589,
    stock: 400,
    rating: 4.7,
    status: 0
  },
  {
    id: 5,
    name: '海尔 1.5匹 空调',
    description: '新一级能效，变频冷暖，智能自清洁',
    price: 3299.00,
    originalPrice: 3999.00,
    image: 'https://picsum.photos/seed/product5/400',
    images: [
      'https://picsum.photos/seed/product5a/400',
      'https://picsum.photos/seed/product5b/400'
    ],
    categoryId: 3,
    categoryName: '家用电器',
    sales: 4523,
    stock: 800,
    rating: 4.6,
    status: 0
  },
  {
    id: 6,
    name: '美的 双开门冰箱',
    description: '508升双开门，风冷无霜，变频节能',
    price: 3899.00,
    originalPrice: 4599.00,
    image: 'https://picsum.photos/seed/product6/400',
    images: [
      'https://picsum.photos/seed/product6a/400',
      'https://picsum.photos/seed/product6b/400'
    ],
    categoryId: 3,
    categoryName: '家用电器',
    sales: 3210,
    stock: 350,
    rating: 4.7,
    status: 0
  },
  {
    id: 7,
    name: 'Nike Air Force 1',
    description: '经典复古，纯白百搭运动鞋',
    price: 799.00,
    originalPrice: 999.00,
    image: 'https://picsum.photos/seed/product7/400',
    images: [
      'https://picsum.photos/seed/product7a/400',
      'https://picsum.photos/seed/product7b/400'
    ],
    categoryId: 4,
    categoryName: '服装鞋帽',
    sales: 6789,
    stock: 1200,
    rating: 4.5,
    status: 0
  },
  {
    id: 8,
    name: '海澜之家 轻薄羽绒服',
    description: '90%鸭绒，立领保暖短款外套',
    price: 599.00,
    originalPrice: 899.00,
    image: 'https://picsum.photos/seed/product8/400',
    images: [
      'https://picsum.photos/seed/product8a/400',
      'https://picsum.photos/seed/product8b/400'
    ],
    categoryId: 4,
    categoryName: '服装鞋帽',
    sales: 4567,
    stock: 900,
    rating: 4.4,
    status: 0
  },
  {
    id: 9,
    name: '三只松鼠 坚果礼盒',
    description: '每日坚果750g，混合果仁零食大礼包',
    price: 129.00,
    originalPrice: 169.00,
    image: 'https://picsum.photos/seed/product9/400',
    images: [
      'https://picsum.photos/seed/product9a/400',
      'https://picsum.photos/seed/product9b/400'
    ],
    categoryId: 5,
    categoryName: '食品生鲜',
    sales: 8921,
    stock: 2000,
    rating: 4.8,
    status: 0
  },
  {
    id: 10,
    name: '深入理解计算机系统',
    description: 'CSAPP 第三版，计算机科学经典教材',
    price: 139.00,
    originalPrice: 179.00,
    image: 'https://picsum.photos/seed/product10/400',
    images: [
      'https://picsum.photos/seed/product10a/400',
      'https://picsum.photos/seed/product10b/400'
    ],
    categoryId: 6,
    categoryName: '图书教育',
    sales: 5678,
    stock: 1500,
    rating: 4.9,
    status: 0
  },
  {
    id: 11,
    name: '乐高 消防局套装',
    description: '60321 消防局主题，845粒颗粒，适合6岁+',
    price: 699.00,
    originalPrice: 899.00,
    image: 'https://picsum.photos/seed/product11/400',
    images: [
      'https://picsum.photos/seed/product11a/400',
      'https://picsum.photos/seed/product11b/400'
    ],
    categoryId: 7,
    categoryName: '母婴玩具',
    sales: 2345,
    stock: 600,
    rating: 4.6,
    status: 0
  },
  {
    id: 12,
    name: '李宁 羽毛球拍',
    description: '风刃900C，全碳素专业进攻型',
    price: 1299.00,
    originalPrice: 1599.00,
    image: 'https://picsum.photos/seed/product12/400',
    images: [
      'https://picsum.photos/seed/product12a/400',
      'https://picsum.photos/seed/product12b/400'
    ],
    categoryId: 8,
    categoryName: '运动户外',
    sales: 3456,
    stock: 450,
    rating: 4.7,
    status: 0
  }
]

/**
 * 模拟分页查询商品列表
 * @param {Object} params - 查询参数
 * @param {number} [params.categoryId] - 分类ID（支持一级/二级分类过滤）
 * @param {string} [params.keyword] - 搜索关键词
 * @param {string} [params.sort] - 排序方式：sales（销量）、price_asc（价格低到高）、
 *   price_desc（价格高到低）、newest（最新），默认综合排序
 * @param {number} [params.page=1] - 当前页码
 * @param {number} [params.size=10] - 每页条数
 * @returns {Promise<{records: Array, total: number, page: number, size: number}>}
 */
export function getProducts(params = {}) {
  const { categoryId, keyword, sort, page = 1, size = 10 } = params

  // 根据查询条件过滤商品列表
  let filtered = [...mockProducts]

  // 按分类过滤：支持一级分类ID（如 1=手机数码）和二级分类ID（如 101=手机）
  if (categoryId) {
    // 获取一级分类下的所有二级分类ID
    const categoryIds = getCategoryIds(categoryId)
    filtered = filtered.filter(p => categoryIds.includes(p.categoryId))
  }

  // 按关键词搜索（匹配商品名称或描述）
  if (keyword && keyword.trim()) {
    const kw = keyword.trim().toLowerCase()
    filtered = filtered.filter(p =>
      p.name.toLowerCase().includes(kw) ||
      p.description.toLowerCase().includes(kw)
    )
  }

  // 排序处理
  if (sort) {
    switch (sort) {
      case 'sales':
        // 按销量从高到低
        filtered.sort((a, b) => b.sales - a.sales)
        break
      case 'price_asc':
        // 按价格从低到高
        filtered.sort((a, b) => a.price - b.price)
        break
      case 'price_desc':
        // 按价格从高到低
        filtered.sort((a, b) => b.price - a.price)
        break
      case 'newest':
        // 按最新（ID越大越新）
        filtered.sort((a, b) => b.id - a.id)
        break
      default:
        break
    }
  } else {
    // 默认综合排序：评分×0.6 + 销量归一化值×0.4
    const maxSales = Math.max(...filtered.map(p => p.sales), 1)
    filtered.sort((a, b) => {
      const scoreA = a.rating * 0.6 + (a.sales / maxSales) * 0.4
      const scoreB = b.rating * 0.6 + (b.sales / maxSales) * 0.4
      return scoreB - scoreA
    })
  }

  // 总记录数
  const total = filtered.length

  // 分页处理
  const start = (page - 1) * size
  const end = start + size
  const records = filtered.slice(start, end)

  // 模拟网络延迟（50~150ms）
  return new Promise(resolve => {
    setTimeout(() => {
      resolve({ records, total, page, size })
    }, 50 + Math.random() * 100)
  })
}

/**
 * 根据商品ID获取单个商品详情
 * @param {number} id - 商品ID
 * @returns {Promise<Object|null>} 商品对象，未找到返回 null
 */
export function getProductById(id) {
  const product = mockProducts.find(p => p.id === id) || null
  // 模拟网络延迟
  return new Promise(resolve => {
    setTimeout(() => {
      resolve(product)
    }, 30 + Math.random() * 70)
  })
}

/**
 * 工具函数：根据分类ID获取所有匹配的商品分类ID列表
 * @param {number} categoryId - 分类ID（一级或二级）
 * @returns {number[]} 匹配的分类ID列表
 *
 * 逻辑：
 *   - 一级分类有子分类 → 返回子分类ID（商品挂在子分类上）
 *   - 一级分类无子分类 → 返回自身ID（商品直接挂在一级分类上）
 *   - 二级分类 → 返回自身ID
 */
function getCategoryIds(categoryId) {
  const topCategory = categories.find(c => c.id === Number(categoryId))
  if (topCategory) {
    if (topCategory.children.length > 0) {
      // 有子分类（如手机数码→手机/平板）：返回子分类ID
      return topCategory.children.map(c => c.id)
    }
    // 无子分类（如家用电器、服装鞋帽）：商品直接挂在该分类下
    return [topCategory.id]
  }
  // 二级分类ID，直接返回
  return [Number(categoryId)]
}
