/**
 * 商品 Mock 数据模块
 * 提供商品分类数据、商品列表数据以及模拟查询函数
 */

/**
 * 商品分类数据
 * 每个分类包含 id（一级分类ID）、name（一级分类名称）、
 * children（子分类数组，每个子分类有 id 和 name）
 */
export const categories = [
  {
    id: 1,
    name: '手机数码',
    children: [
      { id: 101, name: '手机' },
      { id: 102, name: '平板电脑' },
      { id: 103, name: '智能手表' }
    ]
  },
  {
    id: 2,
    name: '电脑办公',
    children: [
      { id: 201, name: '笔记本' },
      { id: 202, name: '台式机' },
      { id: 203, name: '显示器' }
    ]
  },
  {
    id: 3,
    name: '家用电器',
    children: [
      { id: 301, name: '空调' },
      { id: 302, name: '冰箱' },
      { id: 303, name: '洗衣机' }
    ]
  },
  {
    id: 4,
    name: '服装鞋帽',
    children: [
      { id: 401, name: '男装' },
      { id: 402, name: '女装' },
      { id: 403, name: '运动鞋' }
    ]
  },
  {
    id: 5,
    name: '食品生鲜',
    children: [
      { id: 501, name: '休闲零食' },
      { id: 502, name: '生鲜水果' }
    ]
  },
  {
    id: 6,
    name: '图书教育',
    children: [
      { id: 601, name: '计算机' },
      { id: 602, name: '文学' }
    ]
  },
  {
    id: 7,
    name: '母婴玩具',
    children: [
      { id: 701, name: '奶粉' },
      { id: 702, name: '玩具' }
    ]
  },
  {
    id: 8,
    name: '运动户外',
    children: [
      { id: 801, name: '健身器材' },
      { id: 802, name: '户外用品' }
    ]
  }
]

/**
 * 商品 Mock 数据（12个商品）
 * 覆盖手机数码、电脑办公、家用电器、服装鞋帽、食品生鲜、图书教育、母婴玩具、运动户外 8个品类
 * 图片统一使用 picsum.photos 占位图，每个商品使用不同的 seed 确保图片不同
 */
export const mockProducts = [
  {
    id: 1,
    name: '华为 Mate 70 Pro',
    description: '搭载麒麟芯片，支持卫星通信，超聚光影像系统，5000万像素超感知摄像头',
    price: 6999.00,
    originalPrice: 7999.00,
    image: 'https://picsum.photos/seed/huawei-mate70/400/400',
    images: [
      'https://picsum.photos/seed/huawei-mate70-1/400/400',
      'https://picsum.photos/seed/huawei-mate70-2/400/400',
      'https://picsum.photos/seed/huawei-mate70-3/400/400'
    ],
    categoryId: 101,
    categoryName: '手机',
    sales: 25800,
    stock: 500,
    rating: 4.9,
    status: 1 // 1=上架, 0=下架
  },
  {
    id: 2,
    name: 'Apple Watch Ultra 2',
    description: '49毫米钛金属表壳，全天候视网膜显示屏，双频GPS，精准双核GPS',
    price: 5999.00,
    originalPrice: 6499.00,
    image: 'https://picsum.photos/seed/apple-ultra2/400/400',
    images: [
      'https://picsum.photos/seed/apple-ultra2-1/400/400',
      'https://picsum.photos/seed/apple-ultra2-2/400/400'
    ],
    categoryId: 103,
    categoryName: '智能手表',
    sales: 12300,
    stock: 300,
    rating: 4.8,
    status: 1
  },
  {
    id: 3,
    name: 'MacBook Pro 16 英寸',
    description: 'M3 Pro芯片，18GB统一内存，512GB固态硬盘，Liquid Retina XDR显示屏',
    price: 19999.00,
    originalPrice: 21999.00,
    image: 'https://picsum.photos/seed/macbook-pro16/400/400',
    images: [
      'https://picsum.photos/seed/macbook-pro16-1/400/400',
      'https://picsum.photos/seed/macbook-pro16-2/400/400',
      'https://picsum.photos/seed/macbook-pro16-3/400/400'
    ],
    categoryId: 201,
    categoryName: '笔记本',
    sales: 8900,
    stock: 200,
    rating: 4.9,
    status: 1
  },
  {
    id: 4,
    name: '戴尔 U2723QX 显示器',
    description: '27英寸4K IPS Black技术，USB-C 90W供电，HDR400认证，专业设计绘图',
    price: 3999.00,
    originalPrice: 4599.00,
    image: 'https://picsum.photos/seed/dell-u2723qx/400/400',
    images: [
      'https://picsum.photos/seed/dell-u2723qx-1/400/400',
      'https://picsum.photos/seed/dell-u2723qx-2/400/400'
    ],
    categoryId: 203,
    categoryName: '显示器',
    sales: 5600,
    stock: 400,
    rating: 4.7,
    status: 1
  },
  {
    id: 5,
    name: '海尔 1.5匹变频空调',
    description: '新一级能效，变频冷暖，自清洁除菌，静音睡眠模式，智能WiFi控制',
    price: 3299.00,
    originalPrice: 3899.00,
    image: 'https://picsum.photos/seed/haier-ac/400/400',
    images: [
      'https://picsum.photos/seed/haier-ac-1/400/400',
      'https://picsum.photos/seed/haier-ac-2/400/400'
    ],
    categoryId: 301,
    categoryName: '空调',
    sales: 18600,
    stock: 600,
    rating: 4.8,
    status: 1
  },
  {
    id: 6,
    name: '美的 双开门冰箱',
    description: '556升大容量对开门，双变频节能静音，PST智能除菌，风冷无霜',
    price: 3999.00,
    originalPrice: 4599.00,
    image: 'https://picsum.photos/seed/midea-fridge/400/400',
    images: [
      'https://picsum.photos/seed/midea-fridge-1/400/400',
      'https://picsum.photos/seed/midea-fridge-2/400/400'
    ],
    categoryId: 302,
    categoryName: '冰箱',
    sales: 14200,
    stock: 350,
    rating: 4.7,
    status: 1
  },
  {
    id: 7,
    name: 'Nike Air Max 270 运动鞋',
    description: 'Air Max气垫缓震，网面透气鞋面，经典复古造型，舒适百搭',
    price: 1099.00,
    originalPrice: 1399.00,
    image: 'https://picsum.photos/seed/nike-airmax/400/400',
    images: [
      'https://picsum.photos/seed/nike-airmax-1/400/400',
      'https://picsum.photos/seed/nike-airmax-2/400/400'
    ],
    categoryId: 403,
    categoryName: '运动鞋',
    sales: 21500,
    stock: 800,
    rating: 4.6,
    status: 1
  },
  {
    id: 8,
    name: '海澜之家 轻奢商务夹克',
    description: '舒适透气面料，立体剪裁，简约商务风格，适合通勤与休闲场合',
    price: 599.00,
    originalPrice: 899.00,
    image: 'https://picsum.photos/seed/hla-jacket/400/400',
    images: [
      'https://picsum.photos/seed/hla-jacket-1/400/400',
      'https://picsum.photos/seed/hla-jacket-2/400/400'
    ],
    categoryId: 401,
    categoryName: '男装',
    sales: 9800,
    stock: 1200,
    rating: 4.4,
    status: 1
  },
  {
    id: 9,
    name: '三只松鼠 坚果零食大礼包',
    description: '每日坚果混合装，精选巴旦木、腰果、核桃仁，健康非油炸',
    price: 168.00,
    originalPrice: 228.00,
    image: 'https://picsum.photos/seed/three-squirrels/400/400',
    images: [
      'https://picsum.photos/seed/three-squirrels-1/400/400',
      'https://picsum.photos/seed/three-squirrels-2/400/400'
    ],
    categoryId: 501,
    categoryName: '休闲零食',
    sales: 32000,
    stock: 2000,
    rating: 4.5,
    status: 1
  },
  {
    id: 10,
    name: '《深入理解计算机系统》',
    description: '计算机科学经典教材，覆盖处理器架构、存储器层次、链接、异常控制流等核心内容',
    price: 139.00,
    originalPrice: 179.00,
    image: 'https://picsum.photos/seed/csapp-book/400/400',
    images: [
      'https://picsum.photos/seed/csapp-book-1/400/400'
    ],
    categoryId: 601,
    categoryName: '计算机',
    sales: 7600,
    stock: 1500,
    rating: 4.9,
    status: 1
  },
  {
    id: 11,
    name: '乐高 城市系列 消防救援',
    description: '604个颗粒，搭建消防局场景，含消防车、直升机、人偶，锻炼动手能力',
    price: 449.00,
    originalPrice: 599.00,
    image: 'https://picsum.photos/seed/lego-fire/400/400',
    images: [
      'https://picsum.photos/seed/lego-fire-1/400/400',
      'https://picsum.photos/seed/lego-fire-2/400/400'
    ],
    categoryId: 702,
    categoryName: '玩具',
    sales: 5100,
    stock: 900,
    rating: 4.8,
    status: 1
  },
  {
    id: 12,
    name: '李宁 羽毛球拍 风刃900',
    description: '碳纤维拍框，高弹性中杆，攻守兼备，适合专业级进阶选手',
    price: 359.00,
    originalPrice: 499.00,
    image: 'https://picsum.photos/seed/li-ning-racket/400/400',
    images: [
      'https://picsum.photos/seed/li-ning-racket-1/400/400',
      'https://picsum.photos/seed/li-ning-racket-2/400/400'
    ],
    categoryId: 801,
    categoryName: '健身器材',
    sales: 6700,
    stock: 1100,
    rating: 4.6,
    status: 1
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
 * 工具函数：根据分类ID获取所有匹配的二级分类ID列表
 * @param {number} categoryId - 分类ID（一级或二级）
 * @returns {number[]} 二级分类ID列表
 */
function getCategoryIds(categoryId) {
  // 先查找一级分类
  const topCategory = categories.find(c => c.id === Number(categoryId))
  if (topCategory) {
    // 如果是一级分类，返回其所有子分类ID
    return topCategory.children.map(c => c.id)
  }
  // 否则视为二级分类ID，直接返回
  return [Number(categoryId)]
}
