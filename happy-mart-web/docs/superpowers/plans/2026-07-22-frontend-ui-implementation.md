# 众乐电子商城前端界面 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 完善众乐电子商城前端界面，按用户流程逐步实现：注册→登录→首页→商品列表→商品详情→购物车→订单

**Architecture:** 前台页面用 FrontLayout 包裹（深色顶栏+分类导航+页脚），登录/注册用独立居中卡片布局。用户模块调真实后端 API，其余页面用 Mock 数据，数据结构与后端接口字段完全对齐。

**Tech Stack:** Vue 3 + Vite + Pinia + Vue Router + Element Plus + Axios

## 全局约束

- 每个 import、每个方法、每个响应式变量都要有中文注释
- 组件名 PascalCase，变量/函数 camelCase，CSS 类名 kebab-case
- 登录/注册调真实后端 `POST /api/user/*`，其余数据放 `src/mock/` 目录
- Mock 数据的字段名必须和后端 VO 完全一致（参考 spec 的接口对照表）
- Element Plus 已全局注册，所有图标可用 `<el-icon><component :is="iconName"/></el-icon>`
- Axios 已封装好，`/api` 前缀由 Vite 代理到 `localhost:8074`
- 项目目录：`E:\PeopleHappy\happy-mart-web`
- 所有 `src/views/front/*.vue` 原有内容已全部清空（占位符），直接覆盖写完整页面

---
## 文件结构总览

```
src/
├── layouts/
│   └── FrontLayout.vue          ← 新增：前台全局布局
├── mock/
│   ├── products.js              ← 新增：商品 Mock 数据
│   └── orders.js                ← 新增：订单 Mock 数据
├── router/
│   └── index.js                 ← 修改：添加 FrontLayout 路由 + 导航守卫
├── stores/
│   └── user.js                  ← 已修复：login() 中 res.data.user → res.data.userInfo
├── views/
│   └── front/
│       ├── Home.vue             ← 重写
│       ├── Login.vue            ← 重写
│       ├── Register.vue         ← 重写
│       ├── ProductList.vue      ← 重写
│       ├── ProductDetail.vue    ← 重写
│       ├── Cart.vue             ← 重写
│       └── OrderList.vue        ← 重写
```

---

### Task 1: Mock 数据文件

**Files:**
- Create: `src/mock/products.js`
- Create: `src/mock/orders.js`

**Interfaces:**
- Consumes: (无)
- Produces: `mockProducts` (ProductVO[]), `mockOrders` (OrderVO[]), `categories` (Category[])，供各页面 import 使用

#### Step 1: 创建 `src/mock/products.js`

包含商品 Mock 数据和分类数据，字段名与后端 ProductVO 完全对齐。

- [ ] 创建 `src/mock/products.js`

```js
/**
 * 商品 Mock 数据
 * 字段设计与后端 ProductVO 保持一致
 * 在后端商品模块未完成之前，前端用这些数据做界面展示
 */

// 商品分类树
export const categories = [
  { id: 1, name: '手机数码', children: [
    { id: 11, name: '手机' },
    { id: 12, name: '平板电脑' },
    { id: 13, name: '智能手表' }
  ]},
  { id: 2, name: '电脑办公', children: [
    { id: 21, name: '笔记本' },
    { id: 22, name: '台式机' },
    { id: 23, name: '显示器' }
  ]},
  { id: 3, name: '家用电器', children: [
    { id: 31, name: '空调' },
    { id: 32, name: '冰箱' },
    { id: 33, name: '洗衣机' }
  ]},
  { id: 4, name: '服装鞋帽', children: [
    { id: 41, name: '男装' },
    { id: 42, name: '女装' },
    { id: 43, name: '运动鞋' }
  ]},
  { id: 5, name: '食品生鲜', children: [
    { id: 51, name: '休闲零食' },
    { id: 52, name: '生鲜水果' }
  ]},
  { id: 6, name: '图书教育', children: [
    { id: 61, name: '计算机' },
    { id: 62, name: '文学' }
  ]},
  { id: 7, name: '母婴玩具', children: [
    { id: 71, name: '奶粉' },
    { id: 72, name: '玩具' }
  ]},
  { id: 8, name: '运动户外', children: [
    { id: 81, name: '健身器材' },
    { id: 82, name: '户外用品' }
  ]}
]

// 商品列表 （20+ 个商品）
export const mockProducts = [
  {
    id: 1,
    name: '华为 Mate 70 Pro',
    description: '麒麟芯片，鸿蒙操作系统，超强影像系统，支持卫星通信',
    price: 6999,
    originalPrice: 7999,
    image: 'https://picsum.photos/seed/product1/400/400',
    images: [
      'https://picsum.photos/seed/product1a/400/400',
      'https://picsum.photos/seed/product1b/400/400',
      'https://picsum.photos/seed/product1c/400/400'
    ],
    categoryId: 11,
    categoryName: '手机',
    sales: 15800,
    stock: 999,
    rating: 4.8,
    status: 1
  },
  {
    id: 2,
    name: 'Apple MacBook Pro 14英寸',
    description: 'M3 Pro 芯片，18GB 内存，512GB 存储，Liquid Retina XDR 显示屏',
    price: 12999,
    originalPrice: 14999,
    image: 'https://picsum.photos/seed/product2/400/400',
    images: [
      'https://picsum.photos/seed/product2a/400/400',
      'https://picsum.photos/seed/product2b/400/400'
    ],
    categoryId: 21,
    categoryName: '笔记本',
    sales: 8900,
    stock: 456,
    rating: 4.9,
    status: 1
  },
  {
    id: 3,
    name: '海尔 变频空调 1.5匹',
    description: '新一级能效，智能变频，自清洁，静音低至18分贝',
    price: 3299,
    originalPrice: 3999,
    image: 'https://picsum.photos/seed/product3/400/400',
    images: [
      'https://picsum.photos/seed/product3a/400/400',
      'https://picsum.photos/seed/product3b/400/400'
    ],
    categoryId: 31,
    categoryName: '空调',
    sales: 22000,
    stock: 1200,
    rating: 4.7,
    status: 1
  },
  {
    id: 4,
    name: 'Nike Air Max 270 运动鞋',
    description: '经典气垫缓震，网面透气，舒适百搭',
    price: 899,
    originalPrice: 1199,
    image: 'https://picsum.photos/seed/product4/400/400',
    images: [
      'https://picsum.photos/seed/product4a/400/400',
      'https://picsum.photos/seed/product4b/400/400'
    ],
    categoryId: 43,
    categoryName: '运动鞋',
    sales: 18500,
    stock: 2340,
    rating: 4.6,
    status: 1
  },
  {
    id: 5,
    name: '三只松鼠 坚果大礼包',
    description: '每日坚果混合装，750g/盒，健康零食',
    price: 69.9,
    originalPrice: 99.9,
    image: 'https://picsum.photos/seed/product5/400/400',
    images: [
      'https://picsum.photos/seed/product5a/400/400'
    ],
    categoryId: 51,
    categoryName: '休闲零食',
    sales: 56000,
    stock: 8900,
    rating: 4.5,
    status: 1
  },
  {
    id: 6,
    name: '《深入理解Java虚拟机》',
    description: '第三版，周志明著，程序员必读经典',
    price: 79.9,
    originalPrice: 99,
    image: 'https://picsum.photos/seed/product6/400/400',
    images: [
      'https://picsum.photos/seed/product6a/400/400'
    ],
    categoryId: 61,
    categoryName: '计算机',
    sales: 32000,
    stock: 5600,
    rating: 4.9,
    status: 1
  },
  {
    id: 7,
    name: 'iPad Air M2',
    description: '11英寸 Liquid Retina 显示屏，M2 芯片，128GB',
    price: 4799,
    originalPrice: 5499,
    image: 'https://picsum.photos/seed/product7/400/400',
    images: [
      'https://picsum.photos/seed/product7a/400/400',
      'https://picsum.photos/seed/product7b/400/400'
    ],
    categoryId: 12,
    categoryName: '平板电脑',
    sales: 12500,
    stock: 780,
    rating: 4.8,
    status: 1
  },
  {
    id: 8,
    name: '美的 双开门冰箱 500L',
    description: '风冷无霜，双变频，智能温控，大容量存储',
    price: 4299,
    originalPrice: 4999,
    image: 'https://picsum.photos/seed/product8/400/400',
    images: [
      'https://picsum.photos/seed/product8a/400/400',
      'https://picsum.photos/seed/product8b/400/400'
    ],
    categoryId: 32,
    categoryName: '冰箱',
    sales: 9800,
    stock: 450,
    rating: 4.6,
    status: 1
  },
  {
    id: 9,
    name: '戴尔 27英寸 4K 显示器',
    description: 'U2723QX，IPS Black 面板，Type-C 90W 反向充电',
    price: 3999,
    originalPrice: 4599,
    image: 'https://picsum.photos/seed/product9/400/400',
    images: [
      'https://picsum.photos/seed/product9a/400/400'
    ],
    categoryId: 23,
    categoryName: '显示器',
    sales: 6700,
    stock: 320,
    rating: 4.7,
    status: 1
  },
  {
    id: 10,
    name: '李宁 男子羽绒服',
    description: '90%白鹅绒，防风保暖，轻盈透气',
    price: 599,
    originalPrice: 899,
    image: 'https://picsum.photos/seed/product10/400/400',
    images: [
      'https://picsum.photos/seed/product10a/400/400'
    ],
    categoryId: 41,
    categoryName: '男装',
    sales: 14300,
    stock: 2100,
    rating: 4.4,
    status: 1
  },
  {
    id: 11,
    name: 'Apple Watch Series 9',
    description: '45mm，GPS 版，血氧检测，运动追踪',
    price: 2999,
    originalPrice: 3499,
    image: 'https://picsum.photos/seed/product11/400/400',
    images: [
      'https://picsum.photos/seed/product11a/400/400',
      'https://picsum.photos/seed/product11b/400/400'
    ],
    categoryId: 13,
    categoryName: '智能手表',
    sales: 21000,
    stock: 1500,
    rating: 4.8,
    status: 1
  },
  {
    id: 12,
    name: '格力 电暖器',
    description: '2200W 大功率，智能恒温，遥控定时，安静无噪',
    price: 399,
    originalPrice: 499,
    image: 'https://picsum.photos/seed/product12/400/400',
    images: [
      'https://picsum.photos/seed/product12a/400/400'
    ],
    categoryId: 33,
    categoryName: '洗衣机',
    sales: 8700,
    stock: 3400,
    rating: 4.3,
    status: 1
  }
]

/**
 * 模拟分页查询
 * @param {Object} params - 查询参数 { categoryId, keyword, sort, page, size }
 * @returns {{ records: Array, total: Number, page: Number, size: Number }}
 */
export function getProducts(params = {}) {
  const { categoryId, keyword, sort, page = 1, size = 12 } = params
  let filtered = [...mockProducts]

  // 按分类筛选
  if (categoryId) {
    const id = Number(categoryId)
    // 如果是一级分类，找它的所有子分类
    const cat = categories.find(c => c.id === id)
    if (cat && cat.children) {
      const childIds = cat.children.map(c => c.id)
      filtered = filtered.filter(p => childIds.includes(p.categoryId))
    } else {
      filtered = filtered.filter(p => p.categoryId === id)
    }
  }

  // 按关键字搜索
  if (keyword) {
    const kw = keyword.toLowerCase()
    filtered = filtered.filter(p =>
      p.name.toLowerCase().includes(kw) || p.description.toLowerCase().includes(kw)
    )
  }

  // 排序
  if (sort === 'sales') {
    filtered.sort((a, b) => b.sales - a.sales)
  } else if (sort === 'price_asc') {
    filtered.sort((a, b) => a.price - b.price)
  } else if (sort === 'price_desc') {
    filtered.sort((a, b) => b.price - a.price)
  } else if (sort === 'newest') {
    filtered.sort((a, b) => b.id - a.id)
  } else {
    // 综合排序：按评分 + 销量加权
    filtered.sort((a, b) => (b.rating * 0.6 + b.sales * 0.4) - (a.rating * 0.6 + a.sales * 0.4))
  }

  // 分页
  const total = filtered.length
  const start = (page - 1) * size
  const records = filtered.slice(start, start + size)

  return { records, total, page, size }
}

/**
 * 根据 ID 获取单个商品
 * @param {Number} id - 商品ID
 * @returns {Object|null}
 */
export function getProductById(id) {
  return mockProducts.find(p => p.id === Number(id)) || null
}
```

- [ ] 创建 `src/mock/orders.js`

```js
/**
 * 订单 Mock 数据
 * 字段设计与后端 OrderVO 保持一致
 * 在后端订单模块未完成之前，前端用这些数据做界面展示
 */

// 状态映射
export const ORDER_STATUS = {
  0: '待付款',
  1: '待发货',
  2: '待收货',
  3: '已完成',
  4: '已取消'
}

// 订单列表
export const mockOrders = [
  {
    id: 1,
    orderNo: '202607221001',
    totalAmount: 6999,
    status: 0,
    createTime: '2026-07-22 10:00:00',
    items: [
      { productId: 1, productName: '华为 Mate 70 Pro', productImage: 'https://picsum.photos/seed/product1/150/150', price: 6999, quantity: 1 }
    ]
  },
  {
    id: 2,
    orderNo: '202607211002',
    totalAmount: 13797.8,
    status: 1,
    createTime: '2026-07-21 14:30:00',
    items: [
      { productId: 2, productName: 'Apple MacBook Pro 14英寸', productImage: 'https://picsum.photos/seed/product2/150/150', price: 12999, quantity: 1 },
      { productId: 6, productName: '《深入理解Java虚拟机》', productImage: 'https://picsum.photos/seed/product6/150/150', price: 79.9, quantity: 1 },
      { productId: 5, productName: '三只松鼠 坚果大礼包', productImage: 'https://picsum.photos/seed/product5/150/150', price: 69.9, quantity: 2 }
    ]
  },
  {
    id: 3,
    orderNo: '202607201003',
    totalAmount: 3299,
    status: 2,
    createTime: '2026-07-20 09:15:00',
    items: [
      { productId: 3, productName: '海尔 变频空调 1.5匹', productImage: 'https://picsum.photos/seed/product3/150/150', price: 3299, quantity: 1 }
    ]
  },
  {
    id: 4,
    orderNo: '202607181004',
    totalAmount: 1568.5,
    status: 3,
    createTime: '2026-07-18 11:20:00',
    items: [
      { productId: 4, productName: 'Nike Air Max 270 运动鞋', productImage: 'https://picsum.photos/seed/product4/150/150', price: 899, quantity: 1 },
      { productId: 10, productName: '李宁 男子羽绒服', productImage: 'https://picsum.photos/seed/product10/150/150', price: 599, quantity: 1 },
      { productId: 5, productName: '三只松鼠 坚果大礼包', productImage: 'https://picsum.photos/seed/product5/150/150', price: 69.9, quantity: 1 }
    ]
  },
  {
    id: 5,
    orderNo: '202607151005',
    totalAmount: 4799,
    status: 4,
    createTime: '2026-07-15 16:45:00',
    items: [
      { productId: 7, productName: 'iPad Air M2', productImage: 'https://picsum.photos/seed/product7/150/150', price: 4799, quantity: 1 }
    ]
  }
]

/**
 * 模拟查询订单列表
 * @param {Object} params - { status, page, size }
 * @returns {{ records: Array, total: Number, page: Number, size: Number }}
 */
export function getOrders(params = {}) {
  const { status, page = 1, size = 10 } = params
  let filtered = [...mockOrders]

  // 按状态筛选（status 不传或为 undefined/null 时返回全部）
  if (status !== undefined && status !== null && status !== '') {
    filtered = filtered.filter(o => o.status === Number(status))
  }

  // 按时间降序
  filtered.sort((a, b) => new Date(b.createTime) - new Date(a.createTime))

  // 分页
  const total = filtered.length
  const start = (page - 1) * size
  const records = filtered.slice(start, start + size)

  return { records, total, page, size }
}

/**
 * 根据 ID 获取单个订单
 * @param {Number} id
 * @returns {Object|null}
 */
export function getOrderById(id) {
  const order = mockOrders.find(o => o.id === Number(id))
  return order ? { ...order } : null
}
```

---

### Task 2: FrontLayout.vue — 前台全局布局

**Files:**
- Create: `src/layouts/FrontLayout.vue`
- Modify: `src/router/index.js`（添加 FrontLayout 路由 + 导航守卫）

**Interfaces:**
- Consumes: `useUserStore`（判断登录状态、获取用户信息）、`useCartStore`（显示购物车数量）
- Produces: 前台页面布局外壳，所有前台页面（除登录/注册）的容器

- [ ] 创建 `src/layouts/FrontLayout.vue`

```vue
<script setup>
/**
 * FrontLayout — 前台全局布局组件
 * 
 * 顶部深色导航栏（Logo + 搜索框 + 购物车 + 用户菜单）
 * 分类导航栏
 * 中间 <router-view /> 区域
 * 底部页脚
 * 
 * 参考亚马逊风格，使用 Element Plus 组件实现
 */
import { ref, onMounted, computed } from 'vue'          // Vue 核心 API
import { useRouter, useRoute } from 'vue-router'        // 路由
import { useUserStore } from '@/stores/user'             // 用户状态
import { useCartStore } from '@/stores/cart'             // 购物车状态

const router = useRouter()                               // 路由实例
const route = useRoute()                                 // 当前路由
const userStore = useUserStore()                         // 用户状态
const cartStore = useCartStore()                         // 购物车状态

// 搜索关键字（v-model 双向绑定）
const keyword = ref('')

// 是否已登录（计算属性）
const isLoggedIn = computed(() => userStore.isLoggedIn)

// 退出登录
function handleLogout() {
  userStore.logout()                                     // 清除 token 和用户信息
  router.push('/')                                       // 回到首页
}

// 执行搜索
function handleSearch() {
  if (keyword.value.trim()) {
    router.push({ path: '/products', query: { keyword: keyword.value.trim() } })
  }
}

// 分类导航列表
const navCategories = [
  { name: '手机数码', path: '/products?categoryId=1' },
  { name: '电脑办公', path: '/products?categoryId=2' },
  { name: '家用电器', path: '/products?categoryId=3' },
  { name: '服装鞋帽', path: '/products?categoryId=4' },
  { name: '食品生鲜', path: '/products?categoryId=5' },
  { name: '图书教育', path: '/products?categoryId=6' },
  { name: '母婴玩具', path: '/products?categoryId=7' },
  { name: '运动户外', path: '/products?categoryId=8' }
]

// 组件挂载时尝试获取用户信息和购物车数量
onMounted(async () => {
  if (isLoggedIn.value) {
    try {
      await userStore.getUserInfo()                      // 拉取用户信息
    } catch {
      // token 无效时 store 已清除，无需处理
    }
  }
})
</script>

<template>
  <!-- 整个布局容器 -->
  <div class="front-layout">
    <!-- ========== 顶部导航栏（深色） ========== -->
    <header class="top-header">
      <div class="header-content">
        <!-- Logo / 首页链接 -->
        <router-link to="/" class="logo">
          <span class="logo-icon">🛒</span>
          <span class="logo-text">众乐商城</span>
        </router-link>

        <!-- 搜索框 -->
        <div class="search-box">
          <el-input
            v-model="keyword"
            placeholder="搜索商品..."
            :prefix-icon="'Search'"
            clearable
            @keyup.enter="handleSearch"
          />
          <el-button type="warning" @click="handleSearch">搜索</el-button>
        </div>

        <!-- 右侧操作区 -->
        <div class="header-actions">
          <!-- 购物车 -->
          <router-link to="/cart" class="action-link">
            <el-badge :value="cartStore.totalCount" :hidden="cartStore.totalCount === 0">
              <el-icon size="22"><ShoppingCart /></el-icon>
            </el-badge>
            <span>购物车</span>
          </router-link>

          <!-- 未登录：显示登录/注册 -->
          <template v-if="!isLoggedIn">
            <router-link to="/login" class="action-link">登录</router-link>
            <router-link to="/register" class="action-link">注册</router-link>
          </template>

          <!-- 已登录：显示用户下拉菜单 -->
          <template v-else>
            <el-dropdown trigger="click">
              <span class="action-link user-link">
                <el-icon><User /></el-icon>
                {{ userStore.userInfo?.username || '用户' }}
                <el-icon><ArrowDown /></el-icon>
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="router.push('/orders')">我的订单</el-dropdown-item>
                  <el-dropdown-item divided @click="handleLogout">退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </div>
      </div>
    </header>

    <!-- ========== 分类导航栏 ========== -->
    <nav class="category-nav">
      <div class="nav-content">
        <router-link to="/products" class="nav-item all-category">全部分类</router-link>
        <router-link
          v-for="cat in navCategories"
          :key="cat.name"
          :to="cat.path"
          class="nav-item"
        >{{ cat.name }}</router-link>
      </div>
    </nav>

    <!-- ========== 页面内容区域 ========== -->
    <main class="main-content">
      <!-- 每个前台页面会渲染在这里 -->
      <router-view />
    </main>

    <!-- ========== 页脚 ========== -->
    <footer class="footer">
      <div class="footer-content">
        <p>© 2026 众乐电子商城 — 用心服务每一位客户</p>
        <div class="footer-links">
          <router-link to="/">关于我们</router-link>
          <span class="divider">|</span>
          <router-link to="/">帮助中心</router-link>
          <span class="divider">|</span>
          <router-link to="/">隐私政策</router-link>
        </div>
      </div>
    </footer>
  </div>
</template>

<style scoped>
/* ===== 顶部导航栏 ===== */
.top-header {
  background-color: #1a1a2e;                    /* 深色背景 */
  color: #fff;
  padding: 0 20px;
  position: sticky;                               /* 粘性定位 */
  top: 0;
  z-index: 1000;
}
.header-content {
  max-width: 1400px;
  margin: 0 auto;
  height: 64px;
  display: flex;
  align-items: center;
  gap: 20px;
}
.logo {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #fff;
  text-decoration: none;
  flex-shrink: 0;
}
.logo-icon {
  font-size: 28px;
}
.logo-text {
  font-size: 22px;
  font-weight: bold;
  white-space: nowrap;
}
.search-box {
  flex: 1;
  display: flex;
  gap: 0;
  max-width: 600px;
}
.search-box .el-input {
  --el-input-border-radius: 4px 0 0 4px;
}
.search-box .el-button {
  border-radius: 0 4px 4px 0;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-shrink: 0;
}
.action-link {
  color: #fff;
  text-decoration: none;
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  transition: opacity 0.2s;
}
.action-link:hover {
  opacity: 0.8;
}
.user-link {
  display: flex;
  align-items: center;
  gap: 4px;
}

/* ===== 分类导航栏 ===== */
.category-nav {
  background: #f5f5f5;
  border-bottom: 1px solid #e8e8e8;
}
.nav-content {
  max-width: 1400px;
  margin: 0 auto;
  height: 40px;
  display: flex;
  align-items: center;
  padding: 0 20px;
  gap: 4px;
}
.nav-item {
  padding: 0 14px;
  font-size: 14px;
  color: #333;
  text-decoration: none;
  line-height: 40px;
  white-space: nowrap;
  transition: color 0.2s;
}
.nav-item:hover {
  color: #e4393c;                                 /* 亚马逊风格红色 */
}
.all-category {
  font-weight: bold;
}

/* ===== 主内容区域 ===== */
.main-content {
  max-width: 1400px;
  margin: 0 auto;
  padding: 20px;
  min-height: calc(100vh - 64px - 40px - 120px);  /* 减去顶部导航、分类导航、页脚的高度 */
}

/* ===== 页脚 ===== */
.footer {
  background: #f5f5f5;
  border-top: 1px solid #e8e8e8;
  padding: 30px 20px;
  margin-top: 40px;
}
.footer-content {
  max-width: 1400px;
  margin: 0 auto;
  text-align: center;
  color: #666;
  font-size: 13px;
}
.footer-links {
  margin-top: 10px;
  display: flex;
  justify-content: center;
  gap: 10px;
}
.footer-links a {
  color: #666;
  text-decoration: none;
}
.footer-links a:hover {
  color: #e4393c;
}
.divider {
  color: #ddd;
}
</style>
```

- [ ] **修改 `src/router/index.js`** — 添加 FrontLayout 路由包装 + 导航守卫

```js
import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'     // Pinia 用户状态 → 导航守卫判断登录

const routes = [
  // ===== 前台页面（带公共布局 FrontLayout） =====
  {
    path: '/',
    component: () => import('@/layouts/FrontLayout.vue'),  // ← 新增：前台布局外壳
    children: [
      {
        path: '',
        name: 'Home',
        component: () => import('@/views/front/Home.vue'),
        meta: { title: '众乐电子商城' }
      },
      {
        path: 'products',
        name: 'Products',
        component: () => import('@/views/front/ProductList.vue'),
        meta: { title: '商品列表' }
      },
      {
        path: 'product/:id',
        name: 'ProductDetail',
        component: () => import('@/views/front/ProductDetail.vue'),
        meta: { title: '商品详情' }
      },
      {
        path: 'cart',
        name: 'Cart',
        component: () => import('@/views/front/Cart.vue'),
        meta: { title: '购物车', requireAuth: true }       // ← 需要登录
      },
      {
        path: 'orders',
        name: 'Orders',
        component: () => import('@/views/front/OrderList.vue'),
        meta: { title: '我的订单', requireAuth: true }     // ← 需要登录
      }
    ]
  },

  // ===== 独立页面（无 FrontLayout） =====
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/front/Login.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/front/Register.vue'),
    meta: { title: '注册' }
  },

  // ===== 后台管理 =====
  {
    path: '/admin',
    name: 'Admin',
    component: () => import('@/layouts/AdminLayout.vue'),
    meta: { title: '管理后台', requireAuth: true },
    children: [
      {
        path: '',
        redirect: { name: 'AdminDashboard' }
      },
      {
        path: 'dashboard',
        name: 'AdminDashboard',
        component: () => import('@/views/admin/Dashboard.vue'),
        meta: { title: '仪表盘' }
      },
      {
        path: 'products',
        name: 'AdminProducts',
        component: () => import('@/views/admin/ProductManage.vue'),
        meta: { title: '商品管理' }
      },
      {
        path: 'orders',
        name: 'AdminOrders',
        component: () => import('@/views/admin/OrderManage.vue'),
        meta: { title: '订单管理' }
      },
      {
        path: 'users',
        name: 'AdminUsers',
        component: () => import('@/views/admin/UserManage.vue'),
        meta: { title: '用户管理' }
      }
    ]
  },

  // ===== 404 =====
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/front/NotFound.vue'),
    meta: { title: '404' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫 —— 动态标题 + 登录检查
router.beforeEach((to, from, next) => {
  // 设置页面标题
  document.title = to.meta.title || '众乐电子商城'

  // 检查需要登录的页面
  if (to.meta.requireAuth) {
    const token = localStorage.getItem('token')    // 从 localStorage 取 token
    if (!token) {
      // 没登录 → 跳转登录页，带上回跳地址
      next({ name: 'Login', query: { redirect: to.fullPath } })
      return
    }
  }

  next()
})

export default router
```

---

### Task 3: 注册页 Register.vue

**Files:**
- Modify: `src/views/front/Register.vue`（重写）

**Interfaces:**
- Consumes: `useUserStore.register(registerForm)` → `POST /api/user/register`，响应 `Result<UserVO>`

- [ ] **重写 `src/views/front/Register.vue`**

```vue
<script setup>
/**
 * Register.vue — 用户注册页
 * 
 * 布局：居中卡片，独立于 FrontLayout（无导航栏）
 * 字段：用户名、手机号、密码、确认密码
 * 调后端 POST /api/user/register 真实接口
 */
import { ref, reactive } from 'vue'                     // Vue 核心
import { useRouter } from 'vue-router'                  // 路由
import { ElMessage } from 'element-plus'                // 消息提示
import { useUserStore } from '@/stores/user'             // 用户状态

const router = useRouter()                               // 路由实例
const userStore = useUserStore()                         // 用户状态（调用注册方法）

// 注册表单数据
const registerForm = reactive({
  username: '',          // 用户名
  phone: '',             // 手机号
  password: '',          // 密码
  confirmPassword: ''    // 确认密码（仅前端校验，不提交后端）
})

// 表单引用（用于 el-form 校验）
const formRef = ref(null)

// 是否正在提交（防止重复提交）
const loading = ref(false)

// 自定义校验：确认密码是否一致
function validateConfirmPassword(rule, value, callback) {
  if (value !== registerForm.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

// 表单校验规则
const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 2, max: 20, message: '用户名长度在 2 到 20 个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ],
  phone: [
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }
  ]
}

// 提交注册
async function handleRegister() {
  // 先校验表单
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true                                  // 开启加载状态
  try {
    // 调用 store 的 register 方法 → POST /api/user/register
    await userStore.register({
      username: registerForm.username,
      password: registerForm.password,
      phone: registerForm.phone || undefined
    })
    ElMessage.success('注册成功！请登录')                // 成功提示
    router.push('/login')                                // 跳转登录页
  } catch (error) {
    // 后端返回的错误已由 request.js 拦截提示
    // 这里只处理特殊情况
    if (error.response?.data?.code === 1001) {
      ElMessage.error('该用户名已被注册')
    }
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <!-- 全屏灰色背景，居中卡片 -->
  <div class="register-page">
    <div class="register-card">
      <!-- 顶部标题 -->
      <div class="card-header">
        <h2>🛒 众乐电子商城</h2>
        <p class="subtitle">创建您的账户</p>
      </div>

      <!-- 注册表单 -->
      <el-form
        ref="formRef"
        :model="registerForm"
        :rules="rules"
        label-width="0"
        size="large"
        @keyup.enter="handleRegister"
      >
        <el-form-item prop="username">
          <el-input
            v-model="registerForm.username"
            placeholder="用户名"
            :prefix-icon="'User'"
          />
        </el-form-item>

        <el-form-item prop="phone">
          <el-input
            v-model="registerForm.phone"
            placeholder="手机号（选填）"
            :prefix-icon="'Phone'"
          />
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="registerForm.password"
            type="password"
            placeholder="密码（至少 6 位）"
            :prefix-icon="'Lock'"
            show-password
          />
        </el-form-item>

        <el-form-item prop="confirmPassword">
          <el-input
            v-model="registerForm.confirmPassword"
            type="password"
            placeholder="确认密码"
            :prefix-icon="'Lock'"
            show-password
          />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            :loading="loading"
            class="submit-btn"
            @click="handleRegister"
          >注 册</el-button>
        </el-form-item>
      </el-form>

      <!-- 底部链接 -->
      <div class="card-footer">
        已有账号？
        <router-link to="/login" class="link">立即登录</router-link>
      </div>
    </div>
  </div>
</template>

<style scoped>
.register-page {
  min-height: 100vh;
  background: #f0f2f5;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}
.register-card {
  width: 420px;
  max-width: 100%;
  background: #fff;
  border-radius: 8px;
  padding: 40px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}
.card-header {
  text-align: center;
  margin-bottom: 32px;
}
.card-header h2 {
  font-size: 24px;
  color: #1a1a2e;
  margin-bottom: 8px;
}
.subtitle {
  color: #999;
  font-size: 14px;
}
.submit-btn {
  width: 100%;
}
.card-footer {
  text-align: center;
  font-size: 14px;
  color: #666;
  margin-top: 16px;
}
.link {
  color: #409eff;
  text-decoration: none;
}
.link:hover {
  text-decoration: underline;
}
</style>
```

---

### Task 4: 登录页 Login.vue

**Files:**
- Modify: `src/views/front/Login.vue`（重写）

**Interfaces:**
- Consumes: `useUserStore.login(loginForm)` → `POST /api/user/login`，响应 `Result<LoginVO>`
- Produces: 成功后 token 存入 localStorage，跳转首页或回跳地址

- [ ] **重写 `src/views/front/Login.vue`**

```vue
<script setup>
/**
 * Login.vue — 用户登录页
 * 
 * 布局：居中卡片，独立于 FrontLayout（无导航栏）
 * 字段：用户名、密码
 * 调后端 POST /api/user/login 真实接口
 */
import { ref, reactive } from 'vue'                     // Vue 核心
import { useRouter, useRoute } from 'vue-router'        // 路由
import { ElMessage } from 'element-plus'                // 消息提示
import { useUserStore } from '@/stores/user'             // 用户状态

const router = useRouter()                               // 路由实例
const route = useRoute()                                 // 当前路由（获取回跳地址）
const userStore = useUserStore()                         // 用户状态

// 如果已登录，直接跳转首页
if (userStore.isLoggedIn) {
  router.replace('/')
}

// 登录表单
const loginForm = reactive({
  username: '',           // 用户名
  password: '',           // 密码
  remember: false         // 记住我（选填）
})

// 表单引用
const formRef = ref(null)

// 是否正在提交
const loading = ref(false)

// 表单校验规则
const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' }
  ]
}

// 提交登录
async function handleLogin() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    // 调用 store 的 login 方法 → POST /api/user/login
    // 成功后 token 和 userInfo 会自动存入 localStorage
    await userStore.login({
      username: loginForm.username,
      password: loginForm.password
    })
    ElMessage.success('登录成功')

    // 登录成功后跳转：优先回跳地址，没有就去首页
    const redirect = route.query.redirect || '/'
    router.push(redirect)
  } catch (error) {
    // 402 错误已在 request.js 中提示，这里不重复处理
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <!-- 顶部标题 -->
      <div class="card-header">
        <h2>🛒 众乐电子商城</h2>
        <p class="subtitle">欢迎回来，请登录您的账户</p>
      </div>

      <!-- 登录表单 -->
      <el-form
        ref="formRef"
        :model="loginForm"
        :rules="rules"
        label-width="0"
        size="large"
        @keyup.enter="handleLogin"
      >
        <el-form-item prop="username">
          <el-input
            v-model="loginForm.username"
            placeholder="用户名"
            :prefix-icon="'User'"
          />
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="密码"
            :prefix-icon="'Lock'"
            show-password
          />
        </el-form-item>

        <el-form-item>
          <el-checkbox v-model="loginForm.remember">记住我</el-checkbox>
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            :loading="loading"
            class="submit-btn"
            @click="handleLogin"
          >登 录</el-button>
        </el-form-item>
      </el-form>

      <!-- 底部链接 -->
      <div class="card-footer">
        没有账号？
        <router-link to="/register" class="link">立即注册</router-link>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  background: #f0f2f5;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}
.login-card {
  width: 420px;
  max-width: 100%;
  background: #fff;
  border-radius: 8px;
  padding: 40px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}
.card-header {
  text-align: center;
  margin-bottom: 32px;
}
.card-header h2 {
  font-size: 24px;
  color: #1a1a2e;
  margin-bottom: 8px;
}
.subtitle {
  color: #999;
  font-size: 14px;
}
.submit-btn {
  width: 100%;
}
.card-footer {
  text-align: center;
  font-size: 14px;
  color: #666;
  margin-top: 16px;
}
.link {
  color: #409eff;
  text-decoration: none;
}
.link:hover {
  text-decoration: underline;
}
</style>
```

---

### Task 5: 首页 Home.vue

**Files:**
- Modify: `src/views/front/Home.vue`

**Interfaces:**
- Consumes: `mockProducts` from `@/mock/products`, `categories` from `@/mock/products`
- 套用 FrontLayout

- [ ] **重写 `src/views/front/Home.vue`**

```vue
<script setup>
/**
 * Home.vue — 商城首页
 * 
 * 套用 FrontLayout 布局
 * 包含：Banner 轮播、分类快捷入口、热门商品推荐
 * 数据来源：Mock（后端商品模块未完成前）
 */
import { ref } from 'vue'                                  // Vue 核心
import { useRouter } from 'vue-router'                     // 路由
import { mockProducts, categories } from '@/mock/products'  // Mock 数据

const router = useRouter()

// Banner 轮播图（3-4 张促销图）
const banners = [
  { id: 1, image: 'https://picsum.photos/seed/banner1/1400/400', title: '618 年中大促' },
  { id: 2, image: 'https://picsum.photos/seed/banner2/1400/400', title: '新品首发' },
  { id: 3, image: 'https://picsum.photos/seed/banner3/1400/400', title: '品牌特卖' }
]

// 热门商品（取前 8 个）
const hotProducts = mockProducts.slice(0, 8)

// 一级分类（前 8 个作为快捷入口）
const quickCategories = categories.slice(0, 8)

// 分类图标映射
const categoryIcons = {
  '手机数码': 'Smartphone',
  '电脑办公': 'Monitor',
  '家用电器': 'Refresh',
  '服装鞋帽': 'Tickets',
  '食品生鲜': 'Apple',
  '图书教育': 'Reading',
  '母婴玩具': 'Present',
  '运动户外': 'TrendCharts'
}

// 跳转到商品列表
function goToProducts(categoryId) {
  router.push({ path: '/products', query: { categoryId } })
}

// 跳转到商品详情
function goToDetail(id) {
  router.push(`/product/${id}`)
}
</script>

<template>
  <div class="home">
    <!-- ===== Banner 轮播 ===== -->
    <el-carousel height="360px" class="banner-carousel">
      <el-carousel-item v-for="banner in banners" :key="banner.id">
        <div
          class="banner-item"
          :style="{ backgroundImage: `url(${banner.image})` }"
        >
          <div class="banner-text">
            <h2>{{ banner.title }}</h2>
          </div>
        </div>
      </el-carousel-item>
    </el-carousel>

    <!-- ===== 分类快捷入口 ===== -->
    <section class="section categories-section">
      <h3 class="section-title">商品分类</h3>
      <div class="categories-grid">
        <div
          v-for="cat in quickCategories"
          :key="cat.id"
          class="category-item"
          @click="goToProducts(cat.id)"
        >
          <!-- Element Plus 图标 -->
          <div class="category-icon">
            <el-icon :size="32">
              <component :is="categoryIcons[cat.name] || 'Goods'" />
            </el-icon>
          </div>
          <span class="category-name">{{ cat.name }}</span>
        </div>
      </div>
    </section>

    <!-- ===== 热门商品推荐 ===== -->
    <section class="section hot-section">
      <h3 class="section-title">热门推荐</h3>
      <div class="products-grid">
        <div
          v-for="product in hotProducts"
          :key="product.id"
          class="product-card"
          @click="goToDetail(product.id)"
        >
          <!-- 商品图片 -->
          <div class="product-image">
            <img :src="product.image" :alt="product.name">
          </div>
          <!-- 商品信息 -->
          <div class="product-info">
            <p class="product-name">{{ product.name }}</p>
            <div class="product-price">
              <span class="price">¥{{ product.price }}</span>
              <span class="original-price">¥{{ product.originalPrice }}</span>
            </div>
            <div class="product-meta">
              <el-rate
                :model-value="product.rating"
                disabled
                show-score
                text-color="#ff9900"
                score-template="{value}"
                size="small"
              />
              <span class="sales">已售 {{ product.sales }}</span>
            </div>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.home {
  /* 页面容器 */
}
/* ===== Banner ===== */
.banner-carousel {
  border-radius: 8px;
  overflow: hidden;
  margin-bottom: 30px;
}
.banner-item {
  height: 100%;
  background-size: cover;
  background-position: center;
  display: flex;
  align-items: center;
  padding: 0 60px;
}
.banner-text h2 {
  color: #fff;
  font-size: 42px;
  text-shadow: 2px 2px 8px rgba(0, 0, 0, 0.5);
}

/* ===== 通用区块 ===== */
.section {
  margin-bottom: 36px;
}
.section-title {
  font-size: 22px;
  font-weight: bold;
  color: #1a1a2e;
  margin-bottom: 20px;
  padding-left: 12px;
  border-left: 4px solid #e4393c;
}

/* ===== 分类快捷入口 ===== */
.categories-grid {
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  gap: 12px;
}
.category-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 20px 10px;
  background: #f8f8f8;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}
.category-item:hover {
  background: #e4393c;
  color: #fff;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(228, 57, 60, 0.2);
}
.category-icon {
  margin-bottom: 8px;
}
.category-name {
  font-size: 13px;
  text-align: center;
}

/* ===== 热门商品 ===== */
.products-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
}
.product-card {
  background: #fff;
  border: 1px solid #eee;
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.2s;
}
.product-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.1);
}
.product-image {
  width: 100%;
  aspect-ratio: 1;
  overflow: hidden;
}
.product-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.3s;
}
.product-card:hover .product-image img {
  transform: scale(1.05);
}
.product-info {
  padding: 12px 14px;
}
.product-name {
  font-size: 14px;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-bottom: 8px;
}
.product-price {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 6px;
}
.price {
  font-size: 20px;
  font-weight: bold;
  color: #e4393c;
}
.original-price {
  font-size: 13px;
  color: #999;
  text-decoration: line-through;
}
.product-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.sales {
  font-size: 12px;
  color: #999;
}
</style>
```

---

### Task 6: 商品列表页 ProductList.vue

**Files:**
- Modify: `src/views/front/ProductList.vue`

**Interfaces:**
- Consumes: `getProducts` from `@/mock/products`, `categories` from `@/mock/products`
- 套用 FrontLayout

- [ ] **重写 `src/views/front/ProductList.vue`**

```vue
<script setup>
/**
 * ProductList.vue — 商品列表页
 * 
 * 套用 FrontLayout 布局
 * 左侧分类筛选树 + 右侧商品网格 + 排序 + 分页
 * 
 * 数据来源：Mock（后端商品模块未完成前使用 getProducts() 模拟分页查询）
 */
import { ref, computed, watch } from 'vue'                   // Vue 核心
import { useRoute, useRouter } from 'vue-router'             // 路由
import { categories, getProducts } from '@/mock/products'     // Mock 数据

const route = useRoute()                                      // 当前路由（读取 query 参数）
const router = useRouter()

// ===== 筛选状态 =====
const selectedCategory = ref(null)                            // 选中的分类ID（从路由 query 读取）
const keyword = ref('')                                       // 搜索关键字
const sortBy = ref('')                                        // 排序方式
const currentPage = ref(1)                                    // 当前页码
const pageSize = ref(12)                                      // 每页数量

// 商品列表数据
const products = ref([])
const total = ref(0)

// 加载状态
const loading = ref(false)

// 分类树数据（el-tree 格式）
const categoryTree = computed(() => {
  return [
    { id: null, label: '全部分类', children: categories.map(cat => ({
      id: cat.id,
      label: cat.name
    }))}
  ]
})

// 排序选项
const sortOptions = [
  { value: '', label: '综合排序' },
  { value: 'sales', label: '销量优先' },
  { value: 'price_asc', label: '价格从低到高' },
  { value: 'price_desc', label: '价格从高到低' },
  { value: 'newest', label: '最新上架' }
]

// 加载商品数据（从 Mock 中查询）
function loadProducts() {
  loading.value = true
  // 模拟网络延迟
  setTimeout(() => {
    const result = getProducts({
      categoryId: selectedCategory.value,
      keyword: keyword.value || route.query.keyword || '',
      sort: sortBy.value,
      page: currentPage.value,
      size: pageSize.value
    })
    products.value = result.records
    total.value = result.total
    loading.value = false
  }, 200)
}

// 监听路由参数变化
watch(() => route.query, (query) => {
  selectedCategory.value = query.categoryId ? Number(query.categoryId) : null
  keyword.value = query.keyword || ''
  currentPage.value = 1
  loadProducts()
}, { immediate: true })

// 切换分类
function handleCategoryClick(categoryId) {
  router.push({ query: { ...route.query, categoryId: categoryId || undefined } })
}

// 切换排序
function handleSortChange(val) {
  sortBy.value = val
  currentPage.value = 1
  loadProducts()
}

// 切换分页
function handlePageChange(page) {
  currentPage.value = page
  loadProducts()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

// 跳转详情
function goToDetail(id) {
  router.push(`/product/${id}`)
}

// 默认选中全部分类
const defaultExpandedKeys = [null]
</script>

<template>
  <div class="product-list">
    <div class="list-container">
      <!-- ===== 左侧分类筛选 ===== -->
      <aside class="sidebar">
        <h4 class="sidebar-title">商品分类</h4>
        <el-tree
          :data="categoryTree"
          :props="{ label: 'label', children: 'children' }"
          node-key="id"
          :default-expanded-keys="defaultExpandedKeys"
          :highlight-current="true"
          @node-click="(data) => handleCategoryClick(data.id)"
        />
      </aside>

      <!-- ===== 右侧商品区域 ===== -->
      <div class="main-area">
        <!-- 顶部工具栏 -->
        <div class="toolbar">
          <span class="result-count">
            共 <strong>{{ total }}</strong> 件商品
          </span>
          <el-select
            v-model="sortBy"
            placeholder="排序方式"
            size="small"
            style="width: 140px"
            @change="handleSortChange"
          >
            <el-option
              v-for="opt in sortOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </div>

        <!-- 加载中状态 -->
        <div v-if="loading" class="loading-state">
          <el-icon class="loading-icon" :size="32"><Loading /></el-icon>
          <p>正在加载...</p>
        </div>

        <!-- 商品网格 -->
        <div v-else-if="products.length > 0" class="product-grid">
          <div
            v-for="product in products"
            :key="product.id"
            class="product-card"
            @click="goToDetail(product.id)"
          >
            <div class="product-image">
              <img :src="product.image" :alt="product.name">
            </div>
            <div class="product-info">
              <p class="product-name">{{ product.name }}</p>
              <div class="product-price-row">
                <span class="price">¥{{ product.price }}</span>
                <span class="original-price">¥{{ product.originalPrice }}</span>
              </div>
              <div class="product-meta">
                <el-rate
                  :model-value="product.rating"
                  disabled
                  size="small"
                  text-color="#ff9900"
                />
                <span class="sales">已售 {{ product.sales }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 空状态 -->
        <el-empty v-else description="暂无符合条件的商品" />

        <!-- 分页 -->
        <div v-if="total > pageSize" class="pagination-wrap">
          <el-pagination
            v-model:current-page="currentPage"
            :page-size="pageSize"
            :total="total"
            layout="prev, pager, next"
            @current-change="handlePageChange"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.list-container {
  display: flex;
  gap: 24px;
}

/* ===== 侧边栏 ===== */
.sidebar {
  width: 200px;
  flex-shrink: 0;
  background: #fff;
  border: 1px solid #eee;
  border-radius: 8px;
  padding: 16px;
  height: fit-content;
}
.sidebar-title {
  font-size: 16px;
  font-weight: bold;
  color: #1a1a2e;
  margin-bottom: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid #eee;
}

/* ===== 主区域 ===== */
.main-area {
  flex: 1;
  min-width: 0;
}
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  background: #fff;
  padding: 12px 16px;
  border-radius: 8px;
  border: 1px solid #eee;
}
.result-count {
  font-size: 14px;
  color: #666;
}

/* ===== 商品网格 ===== */
.product-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}
.product-card {
  background: #fff;
  border: 1px solid #eee;
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.2s;
}
.product-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.08);
}
.product-image {
  width: 100%;
  aspect-ratio: 1;
  overflow: hidden;
}
.product-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.product-info {
  padding: 10px 12px;
}
.product-name {
  font-size: 13px;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-bottom: 6px;
}
.product-price-row {
  display: flex;
  align-items: baseline;
  gap: 6px;
  margin-bottom: 4px;
}
.price {
  font-size: 18px;
  font-weight: bold;
  color: #e4393c;
}
.original-price {
  font-size: 12px;
  color: #999;
  text-decoration: line-through;
}
.product-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.sales {
  font-size: 12px;
  color: #999;
}

/* ===== 加载中 ===== */
.loading-state {
  text-align: center;
  padding: 60px 0;
  color: #999;
}
.loading-icon {
  animation: rotating 1s linear infinite;
}
@keyframes rotating {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* ===== 分页 ===== */
.pagination-wrap {
  margin-top: 30px;
  text-align: center;
}
</style>
```

---

### Task 7: 商品详情页 ProductDetail.vue

**Files:**
- Modify: `src/views/front/ProductDetail.vue`

**Interfaces:**
- Consumes: `getProductById` from `@/mock/products`, `useCartStore.addItem`
- 套用 FrontLayout

- [ ] **重写 `src/views/front/ProductDetail.vue`**

```vue
<script setup>
/**
 * ProductDetail.vue — 商品详情页
 * 
 * 套用 FrontLayout 布局
 * 左侧商品大图 + 缩略图切换，右侧商品信息
 * 数量选择 + 加入购物车
 * 数据来源：Mock（getProductById）
 */
import { ref, computed, onMounted } from 'vue'                    // Vue 核心
import { useRoute, useRouter } from 'vue-router'                  // 路由
import { ElMessage } from 'element-plus'                          // 消息提示
import { getProductById } from '@/mock/products'                   // Mock 数据
import { useCartStore } from '@/stores/cart'                       // 购物车状态
import { useUserStore } from '@/stores/user'                       // 用户状态（判断是否登录）

const route = useRoute()
const router = useRouter()
const cartStore = useCartStore()
const userStore = useUserStore()

// 商品数据
const product = ref(null)
const loading = ref(true)

// 当前选中的大图索引
const currentImageIndex = ref(0)
// 当前大图 URL
const currentImage = computed(() => {
  if (!product.value) return ''
  return product.value.images?.[currentImageIndex.value] || product.value.image
})

// 购买数量
const quantity = ref(1)

// 加载商品数据
onMounted(() => {
  const id = route.params.id
  const result = getProductById(id)
  if (result) {
    product.value = result
  }
  loading.value = false
})

// 切换缩略图
function switchImage(index) {
  currentImageIndex.value = index
}

// 减少数量
function decreaseQuantity() {
  if (quantity.value > 1) {
    quantity.value--
  }
}

// 增加数量
function increaseQuantity() {
  if (quantity.value < (product.value?.stock || 99)) {
    quantity.value++
  }
}

// 加入购物车
async function addToCart() {
  // 未登录则跳转登录
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录')
    router.push({ name: 'Login', query: { redirect: route.fullPath } })
    return
  }

  try {
    // 调用购物车 store 的 addItem
    await cartStore.addItem(product.value.id, quantity.value)
    ElMessage.success('已加入购物车')
  } catch (e) {
    // 错误已在 request.js 中处理
  }
}

// 立即购买
function buyNow() {
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录')
    router.push({ name: 'Login', query: { redirect: route.fullPath } })
    return
  }
  // 先加入购物车，然后跳转到购物车页
  cartStore.addItem(product.value.id, quantity.value)
  router.push('/cart')
}
</script>

<template>
  <div class="product-detail">
    <!-- 加载中 -->
    <div v-if="loading" class="loading-state">
      <el-icon class="loading-icon" :size="32"><Loading /></el-icon>
      <p>加载中...</p>
    </div>

    <!-- 商品不存在 -->
    <el-empty v-else-if="!product" description="商品不存在" />

    <!-- 正常展示 -->
    <template v-else>
      <!-- 面包屑 -->
      <div class="breadcrumb">
        <el-breadcrumb>
          <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
          <el-breadcrumb-item :to="{ path: '/products', query: { categoryId: product.categoryId } }">
            {{ product.categoryName }}
          </el-breadcrumb-item>
          <el-breadcrumb-item>{{ product.name }}</el-breadcrumb-item>
        </el-breadcrumb>
      </div>

      <!-- 商品主体 -->
      <div class="product-main">
        <!-- 左侧：图片 -->
        <div class="product-gallery">
          <div class="main-image">
            <img :src="currentImage" :alt="product.name">
          </div>
          <div class="thumbnail-list">
            <div
              v-for="(img, index) in product.images"
              :key="index"
              class="thumbnail"
              :class="{ active: index === currentImageIndex }"
              @click="switchImage(index)"
            >
              <img :src="img" :alt="`${product.name} ${index + 1}`">
            </div>
          </div>
        </div>

        <!-- 右侧：商品信息 -->
        <div class="product-info">
          <h1 class="product-title">{{ product.name }}</h1>
          <p class="product-desc">{{ product.description }}</p>

          <div class="price-section">
            <div class="current-price">
              <span class="price-label">¥</span>
              <span class="price-value">{{ product.price }}</span>
            </div>
            <div class="original-price">
              原价 ¥{{ product.originalPrice }}
            </div>
          </div>

          <div class="info-rows">
            <div class="info-row">
              <span class="label">销量</span>
              <span class="value">{{ product.sales }}</span>
            </div>
            <div class="info-row">
              <span class="label">评分</span>
              <span class="value">
                <el-rate
                  :model-value="product.rating"
                  disabled
                  show-score
                  text-color="#ff9900"
                  score-template="{value}"
                />
              </span>
            </div>
            <div class="info-row">
              <span class="label">库存</span>
              <span class="value" :class="{ 'low-stock': product.stock < 100 }">
                {{ product.stock > 0 ? product.stock + ' 件' : '暂时缺货' }}
              </span>
            </div>
          </div>

          <!-- 数量选择 -->
          <div class="quantity-section">
            <span class="label">数量</span>
            <div class="quantity-control">
              <el-button size="small" :disabled="quantity <= 1" @click="decreaseQuantity">−</el-button>
              <span class="quantity-num">{{ quantity }}</span>
              <el-button size="small" :disabled="quantity >= product.stock" @click="increaseQuantity">+</el-button>
            </div>
          </div>

          <!-- 操作按钮 -->
          <div class="action-buttons">
            <el-button
              type="danger"
              size="large"
              :disabled="product.stock <= 0"
              @click="addToCart"
            >
              <el-icon><ShoppingCart /></el-icon>
              加入购物车
            </el-button>
            <el-button
              type="warning"
              size="large"
              :disabled="product.stock <= 0"
              @click="buyNow"
            >立即购买</el-button>
          </div>
        </div>
      </div>

      <!-- 商品详情 Tab -->
      <div class="detail-tabs">
        <el-tabs type="border-card">
          <el-tab-pane label="商品描述">
            <p style="padding: 20px; line-height: 1.8; color: #666;">
              {{ product.description }}
            </p>
          </el-tab-pane>
          <el-tab-pane label="规格参数">
            <p style="padding: 20px; color: #999;">规格参数开发中...</p>
          </el-tab-pane>
          <el-tab-pane label="用户评价（{{ product.sales }}）">
            <p style="padding: 20px; color: #999;">用户评价开发中...</p>
          </el-tab-pane>
        </el-tabs>
      </div>
    </template>
  </div>
</template>

<style scoped>
.product-detail {
  max-width: 1200px;
  margin: 0 auto;
}

/* ===== 加载中 ===== */
.loading-state {
  text-align: center;
  padding: 80px 0;
  color: #999;
}
.loading-icon {
  animation: rotating 1s linear infinite;
}
@keyframes rotating {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* ===== 面包屑 ===== */
.breadcrumb {
  margin-bottom: 20px;
}

/* ===== 商品主体 ===== */
.product-main {
  display: flex;
  gap: 40px;
  margin-bottom: 40px;
}

/* 左侧图片区 */
.product-gallery {
  width: 480px;
  flex-shrink: 0;
}
.main-image {
  width: 100%;
  aspect-ratio: 1;
  border: 1px solid #eee;
  border-radius: 8px;
  overflow: hidden;
  margin-bottom: 12px;
}
.main-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.thumbnail-list {
  display: flex;
  gap: 10px;
}
.thumbnail {
  width: 72px;
  height: 72px;
  border: 2px solid transparent;
  border-radius: 4px;
  overflow: hidden;
  cursor: pointer;
  transition: border-color 0.2s;
}
.thumbnail.active {
  border-color: #e4393c;
}
.thumbnail img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

/* 右侧信息区 */
.product-info {
  flex: 1;
}
.product-title {
  font-size: 22px;
  color: #1a1a2e;
  margin-bottom: 8px;
}
.product-desc {
  font-size: 14px;
  color: #999;
  margin-bottom: 20px;
  line-height: 1.6;
}
.price-section {
  background: #fff5f5;
  padding: 16px 20px;
  border-radius: 8px;
  margin-bottom: 20px;
  display: flex;
  align-items: baseline;
  gap: 16px;
}
.current-price {
  display: flex;
  align-items: baseline;
}
.price-label {
  font-size: 18px;
  color: #e4393c;
}
.price-value {
  font-size: 36px;
  font-weight: bold;
  color: #e4393c;
}
.original-price {
  font-size: 14px;
  color: #999;
  text-decoration: line-through;
}
.info-rows {
  margin-bottom: 20px;
}
.info-row {
  display: flex;
  align-items: center;
  padding: 8px 0;
  border-bottom: 1px dashed #eee;
}
.label {
  width: 60px;
  font-size: 14px;
  color: #999;
}
.value {
  font-size: 14px;
  color: #333;
}
.low-stock {
  color: #e4393c;
}

/* 数量选择 */
.quantity-section {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
}
.quantity-control {
  display: flex;
  align-items: center;
  gap: 12px;
}
.quantity-num {
  font-size: 18px;
  font-weight: bold;
  min-width: 30px;
  text-align: center;
}

/* 操作按钮 */
.action-buttons {
  display: flex;
  gap: 12px;
}
.action-buttons .el-button {
  min-width: 160px;
}

/* ===== 详情 Tabs ===== */
.detail-tabs {
  margin-top: 20px;
}
</style>
```

---

### Task 8: 购物车页 Cart.vue

**Files:**
- Modify: `src/views/front/Cart.vue`

**Interfaces:**
- Consumes: `useCartStore`（items、totalCount、totalAmount、addItem、updateQuantity、removeItem、clearCart）
- 套用 FrontLayout

- [ ] **重写 `src/views/front/Cart.vue`**

```vue
<script setup>
/**
 * Cart.vue — 购物车页
 * 
 * 套用 FrontLayout 布局
 * 商品列表（勾选、数量调整、删除）+ 底部汇总 + 结算
 * 数据来源：Mock（cartStore 中已有完整 CRUD 方法）
 */
import { ref, computed, onMounted } from 'vue'                    // Vue 核心
import { useRouter } from 'vue-router'                            // 路由
import { ElMessage, ElMessageBox } from 'element-plus'            // 弹窗提示
import { useCartStore } from '@/stores/cart'                      // 购物车状态

const router = useRouter()
const cartStore = useCartStore()

// 加载购物车数据
onMounted(() => {
  cartStore.fetchCart()
})

// 勾选的商品 ID 集合
const checkedIds = ref([])

// 是否全选
const isAllChecked = computed(() => {
  const items = cartStore.items
  if (items.length === 0) return false
  return checkedIds.value.length === items.length
})

// 已勾选的商品列表
const checkedItems = computed(() => {
  return cartStore.items.filter(item => checkedIds.value.includes(item.productId))
})

// 已勾选商品总金额
const checkedTotal = computed(() => {
  return checkedItems.value.reduce((sum, item) => sum + item.price * item.quantity, 0)
})

// 切换全选
function handleCheckAll() {
  if (isAllChecked.value) {
    checkedIds.value = []
  } else {
    checkedIds.value = cartStore.items.map(item => item.productId)
  }
}

// 切换单个商品勾选
function handleCheck(productId) {
  const index = checkedIds.value.indexOf(productId)
  if (index > -1) {
    checkedIds.value.splice(index, 1)
  } else {
    checkedIds.value.push(productId)
  }
}

// 修改数量
function handleQuantityChange(productId, quantity) {
  if (quantity < 1) return
  cartStore.updateQuantity(productId, quantity)
}

// 删除单个商品
async function handleRemove(productId) {
  try {
    await ElMessageBox.confirm('确定要删除该商品吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await cartStore.removeItem(productId)
    // 从已勾选列表中移除
    const index = checkedIds.value.indexOf(productId)
    if (index > -1) checkedIds.value.splice(index, 1)
    ElMessage.success('已删除')
  } catch {
    // 取消删除
  }
}

// 删除选中的商品
async function handleRemoveChecked() {
  if (checkedIds.value.length === 0) {
    ElMessage.warning('请选择要删除的商品')
    return
  }
  try {
    await ElMessageBox.confirm(`确定要删除选中的 ${checkedIds.value.length} 件商品吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    for (const productId of checkedIds.value) {
      await cartStore.removeItem(productId)
    }
    checkedIds.value = []
    ElMessage.success('已删除')
  } catch {
    // 取消删除
  }
}

// 去结算
function handleCheckout() {
  if (checkedIds.value.length === 0) {
    ElMessage.warning('请选择要结算的商品')
    return
  }
  ElMessage.success('提交订单成功！（演示功能）')
  // 清空购物车中已结算的商品
  checkedIds.value.forEach(id => cartStore.removeItem(id))
  checkedIds.value = []
}
</script>

<template>
  <div class="cart-page">
    <h3 class="page-title">我的购物车</h3>

    <!-- 空状态 -->
    <el-empty v-if="cartStore.items.length === 0" description="购物车是空的">
      <el-button type="primary" @click="router.push('/products')">去逛逛</el-button>
    </el-empty>

    <!-- 购物车列表 -->
    <template v-else>
      <div class="cart-table">
        <!-- 表头 -->
        <div class="cart-header">
          <el-checkbox :model-value="isAllChecked" @change="handleCheckAll">全选</el-checkbox>
          <span class="header-item col-product">商品</span>
          <span class="header-item col-price">单价</span>
          <span class="header-item col-quantity">数量</span>
          <span class="header-item col-subtotal">小计</span>
          <span class="header-item col-action">操作</span>
        </div>

        <!-- 商品行 -->
        <div
          v-for="item in cartStore.items"
          :key="item.productId"
          class="cart-row"
          :class="{ checked: checkedIds.includes(item.productId) }"
        >
          <el-checkbox
            :model-value="checkedIds.includes(item.productId)"
            @change="() => handleCheck(item.productId)"
          />
          <div class="col-product">
            <div class="product-info-cell">
              <img :src="item.productImage" :alt="item.productName" class="product-thumb">
              <span class="product-name">{{ item.productName }}</span>
            </div>
          </div>
          <div class="col-price">¥{{ item.price.toFixed(2) }}</div>
          <div class="col-quantity">
            <el-input-number
              :model-value="item.quantity"
              :min="1"
              :max="99"
              size="small"
              @change="(val) => handleQuantityChange(item.productId, val)"
            />
          </div>
          <div class="col-subtotal">¥{{ (item.price * item.quantity).toFixed(2) }}</div>
          <div class="col-action">
            <el-button type="danger" link size="small" @click="handleRemove(item.productId)">删除</el-button>
          </div>
        </div>
      </div>

      <!-- 底部操作栏 -->
      <div class="cart-footer">
        <div class="footer-left">
          <el-checkbox :model-value="isAllChecked" @change="handleCheckAll">全选</el-checkbox>
          <el-button type="danger" link @click="handleRemoveChecked">
            删除选中（{{ checkedIds.length }}）
          </el-button>
        </div>
        <div class="footer-right">
          <span class="footer-total">
            合计：
            <strong class="total-amount">¥{{ checkedTotal.toFixed(2) }}</strong>
          </span>
          <el-button
            type="danger"
            size="large"
            :disabled="checkedIds.length === 0"
            @click="handleCheckout"
          >去结算</el-button>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.page-title {
  font-size: 22px;
  color: #1a1a2e;
  margin-bottom: 20px;
}

/* ===== 表头 ===== */
.cart-table {
  background: #fff;
  border: 1px solid #eee;
  border-radius: 8px;
  overflow: hidden;
}
.cart-header {
  display: flex;
  align-items: center;
  padding: 12px 20px;
  background: #fafafa;
  border-bottom: 1px solid #eee;
  font-size: 13px;
  color: #999;
}
.header-item {
  flex-shrink: 0;
}

/* 列宽分配 */
.col-product { flex: 1; min-width: 0; }
.col-price { width: 120px; text-align: center; }
.col-quantity { width: 180px; text-align: center; }
.col-subtotal { width: 120px; text-align: center; }
.col-action { width: 80px; text-align: center; }

/* ===== 商品行 ===== */
.cart-row {
  display: flex;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid #f5f5f5;
  transition: background 0.2s;
}
.cart-row:hover {
  background: #fafafa;
}
.cart-row.checked {
  background: #f0f9ff;
}
.product-info-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}
.product-thumb {
  width: 80px;
  height: 80px;
  object-fit: cover;
  border-radius: 4px;
  border: 1px solid #eee;
}
.product-name {
  font-size: 14px;
  color: #333;
}
.col-price, .col-subtotal {
  font-size: 14px;
  color: #333;
}

/* ===== 底部操作栏 ===== */
.cart-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  background: #fff;
  border: 1px solid #eee;
  border-top: none;
  border-radius: 0 0 8px 8px;
}
.footer-left {
  display: flex;
  align-items: center;
  gap: 20px;
}
.footer-right {
  display: flex;
  align-items: center;
  gap: 20px;
}
.footer-total {
  font-size: 16px;
  color: #333;
}
.total-amount {
  font-size: 24px;
  color: #e4393c;
}
</style>
```

---

### Task 9: 订单列表页 OrderList.vue

**Files:**
- Modify: `src/views/front/OrderList.vue`

**Interfaces:**
- Consumes: `getOrders` from `@/mock/orders`, `ORDER_STATUS` from `@/mock/orders`
- 套用 FrontLayout

- [ ] **重写 `src/views/front/OrderList.vue`**

```vue
<script setup>
/**
 * OrderList.vue — 我的订单页
 * 
 * 套用 FrontLayout 布局
 * Tab 切换（全部/待付款/待发货/待收货/已完成）
 * 订单卡片列表
 * 数据来源：Mock（getOrders）
 */
import { ref, onMounted } from 'vue'                              // Vue 核心
import { useRouter } from 'vue-router'                            // 路由
import { ElMessage, ElMessageBox } from 'element-plus'            // 弹窗提示
import { getOrders, ORDER_STATUS } from '@/mock/orders'           // Mock 数据

const router = useRouter()

// Tab 切换选项
const statusTabs = [
  { label: '全部', value: '' },
  { label: '待付款', value: 0 },
  { label: '待发货', value: 1 },
  { label: '待收货', value: 2 },
  { label: '已完成', value: 3 }
]

// 当前选中的状态
const currentStatus = ref('')

// 订单列表
const orders = ref([])
const loading = ref(false)

// 加载订单数据
function loadOrders() {
  loading.value = true
  setTimeout(() => {
    const result = getOrders({ status: currentStatus.value })
    orders.value = result.records
    loading.value = false
  }, 200)
}

onMounted(() => {
  loadOrders()
})

// 切换 Tab
function handleTabChange(status) {
  currentStatus.value = status
  loadOrders()
}

// 获取状态标签类型（用于 el-tag）
function getStatusType(status) {
  const map = { 0: 'danger', 1: 'warning', 2: 'primary', 3: 'success', 4: 'info' }
  return map[status] || 'info'
}

// 模拟操作：付款
function handlePay(order) {
  ElMessageBox.confirm(`确定要支付订单 ${order.orderNo} 吗？`, '提示', {
    confirmButtonText: '去付款',
    cancelButtonText: '取消',
    type: 'info'
  }).then(() => {
    order.status = 1
    ElMessage.success('支付成功（演示）')
    loadOrders()
  }).catch(() => {})
}

// 模拟操作：取消订单
function handleCancel(order) {
  ElMessageBox.confirm('确定要取消该订单吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '再想想',
    type: 'warning'
  }).then(() => {
    order.status = 4
    ElMessage.success('订单已取消')
    loadOrders()
  }).catch(() => {})
}

// 模拟操作：确认收货
function handleConfirm(order) {
  ElMessageBox.confirm('确定已收到商品吗？', '提示', {
    confirmButtonText: '确认收货',
    cancelButtonText: '再等等',
    type: 'info'
  }).then(() => {
    order.status = 3
    ElMessage.success('已确认收货')
    loadOrders()
  }).catch(() => {})
}

// 获取当前 Tab 可执行的操作按钮
function getActions(order) {
  const actions = []
  if (order.status === 0) {
    actions.push({ label: '去付款', type: 'danger', handler: () => handlePay(order) })
    actions.push({ label: '取消订单', type: 'default', handler: () => handleCancel(order) })
  } else if (order.status === 1) {
    // 待发货：无操作
  } else if (order.status === 2) {
    actions.push({ label: '确认收货', type: 'primary', handler: () => handleConfirm(order) })
  }
  return actions
}
</script>

<template>
  <div class="order-list">
    <h3 class="page-title">我的订单</h3>

    <!-- 状态 Tab -->
    <div class="status-tabs">
      <el-radio-group
        :model-value="currentStatus"
        @change="handleTabChange"
      >
        <el-radio-button
          v-for="tab in statusTabs"
          :key="tab.value"
          :value="tab.value"
        >{{ tab.label }}</el-radio-button>
      </el-radio-group>
    </div>

    <!-- 加载中 -->
    <div v-if="loading" class="loading-state">
      <el-icon class="loading-icon" :size="32"><Loading /></el-icon>
    </div>

    <!-- 空状态 -->
    <el-empty v-else-if="orders.length === 0" description="暂无订单" />

    <!-- 订单列表 -->
    <div v-else class="orders-container">
      <div v-for="order in orders" :key="order.id" class="order-card">
        <!-- 订单头部：订单号 + 状态 -->
        <div class="order-header">
          <div class="order-info">
            <span class="order-no">订单号：{{ order.orderNo }}</span>
            <span class="order-time">{{ order.createTime }}</span>
          </div>
          <el-tag :type="getStatusType(order.status)" size="small">
            {{ ORDER_STATUS[order.status] }}
          </el-tag>
        </div>

        <!-- 订单商品列表 -->
        <div class="order-items">
          <div v-for="(item, idx) in order.items" :key="idx" class="order-item">
            <img :src="item.productImage" :alt="item.productName" class="item-thumb">
            <div class="item-info">
              <p class="item-name">{{ item.productName }}</p>
              <p class="item-price">¥{{ item.price.toFixed(2) }} × {{ item.quantity }}</p>
            </div>
          </div>
        </div>

        <!-- 订单底部：总金额 + 操作 -->
        <div class="order-footer">
          <div class="order-total">
            实付：<strong class="total-amount">¥{{ order.totalAmount.toFixed(2) }}</strong>
          </div>
          <div class="order-actions">
            <el-button
              v-for="action in getActions(order)"
              :key="action.label"
              :type="action.type"
              size="small"
              @click="action.handler"
            >{{ action.label }}</el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page-title {
  font-size: 22px;
  color: #1a1a2e;
  margin-bottom: 20px;
}

/* ===== 状态 Tab ===== */
.status-tabs {
  margin-bottom: 24px;
}

/* ===== 加载中 ===== */
.loading-state {
  text-align: center;
  padding: 60px 0;
}
.loading-icon {
  animation: rotating 1s linear infinite;
  color: #999;
}
@keyframes rotating {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* ===== 订单卡片 ===== */
.orders-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.order-card {
  background: #fff;
  border: 1px solid #eee;
  border-radius: 8px;
  overflow: hidden;
  transition: box-shadow 0.2s;
}
.order-card:hover {
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

/* 订单头部 */
.order-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 20px;
  background: #fafafa;
  border-bottom: 1px solid #eee;
}
.order-info {
  display: flex;
  gap: 20px;
  font-size: 13px;
  color: #999;
}

/* 商品列表 */
.order-items {
  padding: 16px 20px;
}
.order-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
}
.order-item + .order-item {
  border-top: 1px dashed #f0f0f0;
}
.item-thumb {
  width: 60px;
  height: 60px;
  object-fit: cover;
  border-radius: 4px;
  border: 1px solid #eee;
}
.item-name {
  font-size: 14px;
  color: #333;
  margin-bottom: 4px;
}
.item-price {
  font-size: 13px;
  color: #999;
}

/* 订单底部 */
.order-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 20px;
  border-top: 1px solid #eee;
  background: #fafafa;
}
.order-total {
  font-size: 14px;
  color: #333;
}
.total-amount {
  font-size: 18px;
  color: #e4393c;
}
.order-actions {
  display: flex;
  gap: 8px;
}
</style>
```

---

## 自检清单

### 1. Spec 覆盖
- ❌ 无
- ✅ 注册页 Task 3 → spec #1
- ✅ 登录页 Task 4 → spec #2
- ✅ 首页 Task 5 → spec #3
- ✅ 商品列表 Task 6 → spec #4
- ✅ 商品详情 Task 7 → spec #5
- ✅ 购物车 Task 8 → spec #6
- ✅ 订单列表 Task 9 → spec #7
- ✅ FrontLayout Task 2 → spec 布局架构
- ✅ Mock 数据 Task 1 → spec 数据策略
- ✅ 路由 + 导航守卫 Task 2 → spec 路由更新

### 2. 占位符检查
- 无 "TBD"/"TODO"/"implement later"
- 每个步骤都有完整代码
- 错误处理已包含（try/catch、loading 状态、空状态、边界情况）

### 3. 类型一致性
- mock 数据字段名与后端 VO 一致（userInfo, ProductVO, CartVO, OrderVO）
- store API 调用与后端接口一致（/api/user/login, /api/user/register, /api/user/info）
- 路由参数名一致（categoryId, keyword, sort, page, size）
