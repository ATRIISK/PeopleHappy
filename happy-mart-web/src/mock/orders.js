/**
 * 订单 Mock 数据模块
 * 提供订单状态常量、订单列表数据以及模拟查询函数
 */

/**
 * 订单状态映射常量
 * 0=待付款, 1=待发货, 2=待收货, 3=已完成, 4=已取消
 */
export const ORDER_STATUS = {
  0: '待付款',
  1: '待发货',
  2: '待收货',
  3: '已完成',
  4: '已取消'
}

/**
 * 订单 Mock 数据（5个订单）
 * 每个订单包含 id、orderNo（订单编号）、totalAmount（总金额）、
 * status（状态码）、createTime（创建时间）、items（订单项数组）
 * 每个订单项包含 productId、productName、productImage、price、quantity
 */
export const mockOrders = [
  {
    id: 1,
    orderNo: '202607220001',
    totalAmount: 6999.00,
    status: 0, // 待付款
    createTime: '2026-07-22 10:30:00',
    items: [
      {
        productId: 1,
        productName: '华为 Mate 70 Pro',
        productImage: 'https://picsum.photos/seed/huawei-mate70/200/200',
        price: 6999.00,
        quantity: 1
      }
    ]
  },
  {
    id: 2,
    orderNo: '202607210002',
    totalAmount: 21098.00,
    status: 1, // 待发货
    createTime: '2026-07-21 14:20:00',
    items: [
      {
        productId: 3,
        productName: 'MacBook Pro 16 英寸',
        productImage: 'https://picsum.photos/seed/macbook-pro16/200/200',
        price: 19999.00,
        quantity: 1
      },
      {
        productId: 10,
        productName: '《深入理解计算机系统》',
        productImage: 'https://picsum.photos/seed/csapp-book/200/200',
        price: 139.00,
        quantity: 2
      }
    ]
  },
  {
    id: 3,
    orderNo: '202607200003',
    totalAmount: 3299.00,
    status: 2, // 待收货
    createTime: '2026-07-20 09:15:00',
    items: [
      {
        productId: 5,
        productName: '海尔 1.5匹变频空调',
        productImage: 'https://picsum.photos/seed/haier-ac/200/200',
        price: 3299.00,
        quantity: 1
      }
    ]
  },
  {
    id: 4,
    orderNo: '202607180004',
    totalAmount: 1826.00,
    status: 3, // 已完成
    createTime: '2026-07-18 16:45:00',
    items: [
      {
        productId: 7,
        productName: 'Nike Air Max 270 运动鞋',
        productImage: 'https://picsum.photos/seed/nike-airmax/200/200',
        price: 1099.00,
        quantity: 1
      },
      {
        productId: 9,
        productName: '三只松鼠 坚果零食大礼包',
        productImage: 'https://picsum.photos/seed/three-squirrels/200/200',
        price: 168.00,
        quantity: 3
      },
      {
        productId: 12,
        productName: '李宁 羽毛球拍 风刃900',
        productImage: 'https://picsum.photos/seed/li-ning-racket/200/200',
        price: 359.00,
        quantity: 1
      }
    ]
  },
  {
    id: 5,
    orderNo: '202607150005',
    totalAmount: 1048.00,
    status: 4, // 已取消
    createTime: '2026-07-15 11:00:00',
    items: [
      {
        productId: 8,
        productName: '海澜之家 轻奢商务夹克',
        productImage: 'https://picsum.photos/seed/hla-jacket/200/200',
        price: 599.00,
        quantity: 1
      },
      {
        productId: 11,
        productName: '乐高 城市系列 消防救援',
        productImage: 'https://picsum.photos/seed/lego-fire/200/200',
        price: 449.00,
        quantity: 1
      }
    ]
  }
]

/**
 * 模拟分页查询订单列表
 * @param {Object} params - 查询参数
 * @param {number} [params.status] - 订单状态过滤（0=待付款, 1=待发货, 2=待收货, 3=已完成, 4=已取消）
 * @param {number} [params.page=1] - 当前页码
 * @param {number} [params.size=10] - 每页条数
 * @returns {Promise<{records: Array, total: number, page: number, size: number}>}
 */
export function getOrders(params = {}) {
  const { status, page = 1, size = 10 } = params

  // 按订单状态过滤
  let filtered = [...mockOrders]
  if (status !== undefined && status !== null && status !== '') {
    const statusNum = Number(status)
    filtered = filtered.filter(o => o.status === statusNum)
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
 * 根据订单ID获取单个订单详情
 * @param {number} id - 订单ID
 * @returns {Promise<Object|null>} 订单对象，未找到返回 null
 */
export function getOrderById(id) {
  const order = mockOrders.find(o => o.id === id) || null
  // 模拟网络延迟
  return new Promise(resolve => {
    setTimeout(() => {
      resolve(order)
    }, 30 + Math.random() * 70)
  })
}
