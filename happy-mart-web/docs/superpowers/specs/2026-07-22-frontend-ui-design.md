# 众乐电子商城 — 前端界面设计方案

> 日期：2026-07-22
> 状态：待实现

## 概述

完善前端界面，按用户流程逐步实现：注册 → 登录 → 首页 → 商品列表 → 商品详情 → 购物车 → 订单。参考亚马逊布局风格，使用 Element Plus 组件实现，不依赖后端已完成之外的接口（登录/注册调真实接口，其余用 Mock 数据）。

## 技术栈

- Vue 3 + Vite + Pinia + Vue Router
- Element Plus（全局注册 + 全部图标）
- Axios（已封装，含 JWT Token 拦截）
- Mock 数据：直接写在对应视图文件或 `src/mock/` 下

## 布局架构

### FrontLayout.vue（新增）
前台全局布局外壳，所有前台页面（除登录/注册外）套用。

```
┌─────────────────────────────────────────────────────┐
│ 深色顶栏                                             │
│  🏠 众乐  首页│商品  🔍 [搜索框]  🛒购物车  👤▼   │
├─────────────────────────────────────────────────────┤
│ 分类导航栏                                            │
│  手机 > 电脑 > 家电 > 服装 > ...                     │
├─────────────────────────────────────────────────────┤
│  ← 返回   首页 > 商品列表（面包屑，首页不显示）       │
├─────────────────────────────────────────────────────┤
│                                                     │
│              <router-view />                         │
│                                                     │
├─────────────────────────────────────────────────────┤
│ 页脚                                                 │
│  © 2026 众乐电子商城  |  关于我们  |  帮助          │
└─────────────────────────────────────────────────────┘
```

### 登录/注册独立布局
不使用 FrontLayout，居中卡片设计，无导航栏/页脚。

## 页面详细设计

### 1. 注册页 `/register`
- **布局**：居中卡片，浅灰背景
- **表单字段**：
  - 用户名（`v-model="registerForm.username"`，`@NotBlank @Size(min=2,max=20)`）
  - 手机号（`v-model="registerForm.phone"`）
  - 密码（`v-model="registerForm.password"`，`@Size(min=6)`，密码框）
  - 确认密码（`v-model="confirmPwd"`，需与密码一致）
- **交互**：
  - 调用 `userStore.register(registerForm)` → `POST /api/user/register`
  - 成功后 Message 提示 + 跳转 `/login`
  - 失败显示错误消息
- **链接**：底部 "已有账号？立即登录"

### 2. 登录页 `/login`
- **布局**：居中卡片，浅灰背景
- **表单字段**：
  - 用户名（`v-model="loginForm.username"`）
  - 密码（`v-model="loginForm.password"`，密码框）
  - "记住我" 复选框（可选）
- **交互**：
  - 调用 `userStore.login(loginForm)` → `POST /api/user/login`
  - 成功后跳转首页 `/`
  - 失败显示 "用户名或密码错误"
  - 已登录用户自动跳转首页
- **链接**：底部 "没有账号？立即注册"

### 3. 首页 `/`
- **套用布局**：FrontLayout
- **Banner 轮播**：
  - 3-4 张促销横幅，el-carousel
  - 图片用 placeholder 彩图
- **分类快捷入口**：
  - 8 个图标入口（手机、电脑、家电、服装、食品、图书、母婴、运动）
  - 2 行 × 4 列网格，点击跳转 `/products?category=xxx`
- **热门商品推荐**：
  - 标题 "热门推荐"
  - 4 列商品卡片网格（el-card），每卡含：缩略图、名称、价格、评分
  - Mock 8-12 个商品

### 4. 商品列表 `/products`
- **套用布局**：FrontLayout
- **左侧筛选**（el-menu / el-tree）：
  - 全部分类 > 手机数码 > 电脑办公 > ...
  - 点击切换分类
- **右侧内容**：
  - 顶部：搜索结果统计 + 排序下拉（综合/销量/价格↑/价格↓/新品）
  - 商品网格：4 列，每卡含图片、名称、价格、销量
  - 分页 el-pagination
- **数据**：Mock 20+ 个商品

### 5. 商品详情 `/product/:id`
- **套用布局**：FrontLayout
- **左侧**：商品大图 + 下方缩略图切换
- **右侧**：
  - 商品名称（标题）
  - 价格（红色大号）
  - 简介描述
  - 库存状态
  - 数量选择器 + "加入购物车" 按钮
  - 商品详情 Tab（描述/规格/评价）
- **数据**：Mock，根据 `:id` 取对应商品

### 6. 购物车 `/cart`
- **套用布局**：FrontLayout
- **商品列表**（el-table 风格或用 el-card 列表）：
  - 勾选复选框
  - 商品缩略图 + 名称
  - 单价
  - 数量调整（el-input-number，min=1）
  - 小计
  - 删除按钮
- **底部操作栏**：
  - 全选、删除选中
  - 合计金额（加粗、醒目）
  - "去结算" 按钮（el-button type="danger"）
- **空状态**：购物车为空提示 + 去逛逛按钮
- **数据**：Mock

### 7. 订单列表 `/orders`
- **套用布局**：FrontLayout
- **订单卡片列表**（每个订单一个卡片）：
  - 订单编号、下单时间、订单状态（待付款/待发货/待收货/已完成）
  - 商品摘要（缩略图 + 名称 × 数量）
  - 实付金额
  - 操作按钮（付款/取消/确认收货/评价）
- **Tab 切换**：全部 / 待付款 / 待发货 / 待收货 / 已完成
- **空状态**：暂无订单
- **数据**：Mock

### 8. 后台页面（保持不变，后续完善）
- AdminLayout.vue 已有
- Dashboard / ProductManage / OrderManage / UserManage 暂保留占位

## 数据流

```
用户操作 → View 组件 → Pinia Store / 直接 API 调用 → 真实API或Mock数据 → 更新 UI
```

- **登录/注册**：→ `userStore` → 真实后端 `POST /api/user/*`
- **商品/购物车/订单**：→ 组件的 `setup` 中直接调用 Mock 数据（或 `composables/`）

## 文件结构变动

```
src/
├── layouts/
│   ├── FrontLayout.vue       ← 新增：前台布局
│   └── AdminLayout.vue       （已有）
├── views/
│   └── front/
│       ├── Home.vue           ← 重写
│       ├── Login.vue          ← 重写
│       ├── Register.vue       ← 重写
│       ├── ProductList.vue    ← 重写
│       ├── ProductDetail.vue  ← 重写
│       ├── Cart.vue           ← 重写
│       ├── OrderList.vue      ← 重写
│       └── NotFound.vue       （已有）
├── mock/
│   ├── products.js            ← 新增
│   └── orders.js              ← 新增
├── router/
│   └── index.js               ← 更新（注册 frontLayout 路由）
├── stores/
│   ├── user.js                （已有）
│   └── cart.js                （已有）
├── utils/
│   └── request.js             （已有）
├── App.vue                    （已有）
├── main.js                    （已有）
├── style.css                  （已有，少量补充）
```

## 路由更新

```js
// 新增 FrontLayout 路由包裹
{
  path: '/',
  component: () => import('@/layouts/FrontLayout.vue'),
  children: [
    { path: '', name: 'Home', component: () => import('@/views/front/Home.vue') },
    { path: 'products', name: 'Products', component: () => import('@/views/front/ProductList.vue') },
    { path: 'product/:id', name: 'ProductDetail', component: () => import('@/views/front/ProductDetail.vue') },
    { path: 'cart', name: 'Cart', component: () => import('@/views/front/Cart.vue') },
    { path: 'orders', name: 'Orders', component: () => import('@/views/front/OrderList.vue') },
  ]
},
// 登录/注册 保持独立（无 FrontLayout）
{ path: '/login', name: 'Login', component: ... },
{ path: '/register', name: 'Register', component: ... },
```

## 后端接口对照

### 用户模块（已实现，调真实 API）

| 接口 | 方法 | 请求体 | 成功响应 `res.data` | 错误码 |
|------|------|--------|---------------------|--------|
| `/api/user/register` | POST | `{ username, password, phone }` | `UserVO` | 1001(用户已存在) |
| `/api/user/login` | POST | `{ username, password }` | `LoginVO` | 402(用户名或密码错误) |
| `/api/user/info` | GET | Header: `Authorization: Bearer <token>` | `UserVO` | 401(未登录) |

**响应格式**: 统一 `{ code, message, data }`，成功 code=200

**字段定义**:
```js
// UserVO — 用户信息（注册/查信息返回）
{ id: Long, username: String, phone: String, avatar: String, role: String, createTime: String }

// LoginVO — 登录成功返回
{ token: String, userInfo: UserVO }   // ← 注意字段名是 userInfo，不是 user！
```

**注意**: `user.js` store 中 `login()` 已修正为 `res.data.userInfo`

### 商品模块（未实现，前端 Mock）

```
GET  /api/product/list?categoryId=&keyword=&sort=&page=&size=  → PageResult<ProductVO>
GET  /api/product/{id}                                         → ProductVO
```

```js
// ProductVO — 商品信息（前端 Mock）
{
  id: Long,
  name: String,            // 商品名称
  description: String,     // 商品描述
  price: Number,           // 价格（分/元，统一用元）
  originalPrice: Number,   // 原价（划线价）
  image: String,           // 主图 URL
  images: String[],        // 多图列表
  categoryId: Long,        // 分类ID
  categoryName: String,    // 分类名称
  sales: Number,           // 销量
  stock: Number,           // 库存
  rating: Number,          // 评分 1-5
  status: Number           // 1=上架 0=下架
}

// PageResult — 分页结果
{ records: T[], total: Number, page: Number, size: Number }
```

### 购物车模块（未实现，前端 Mock）

```
GET    /api/cart/list     → CartVO[]
POST   /api/cart/add      → { productId, quantity }
PUT    /api/cart/update   → { productId, quantity }
DELETE /api/cart/remove   → { productId }
DELETE /api/cart/clear
```

```js
// CartVO — 购物车条目
{
  id: Long,
  productId: Long,
  productName: String,
  productImage: String,
  price: Number,
  quantity: Number,
  checked: Boolean        // 前端自有字段，不提交后端
}
```

### 订单模块（未实现，前端 Mock）

```
GET  /api/order/list?status=&page=&size=  → PageResult<OrderVO>
POST /api/order/create                    → { items: [{ productId, quantity }], addressId }
```

```js
// OrderVO — 订单
{
  id: Long,
  orderNo: String,              // 订单号
  totalAmount: Number,          // 实付金额
  status: Number,               // 0=待付款 1=待发货 2=待收货 3=已完成 4=已取消
  statusText: String,           // 状态中文（前端计算）
  createTime: String,
  items: [
    {
      productId: Long,
      productName: String,
      productImage: String,
      price: Number,
      quantity: Number
    }
  ]
}
```

### 错误码处理

| code | 含义 | 前端处理 |
|------|------|---------|
| 200 | 成功 | 正常处理 |
| 401 | 未登录/token过期 | `request.js` 已处理 → 清除 token + 跳转 `/login` |
| 402 | 用户名或密码错误 | 登录页显示提示 |
| 403 | 无权限 | `request.js` 已处理 |
| 1001 | 用户已存在 | 注册页显示提示 |
| 其他 | 服务器异常 | `request.js` 已处理 |

## 命名规范

- 组件名：PascalCase（如 `FrontLayout.vue`）
- 变量/函数：camelCase
- CSS 类名：kebab-case
- 全部中文注释（每个 import、每个方法、每个响应式变量）

## 开发顺序

1. Mock 数据文件（products.js, orders.js）
2. FrontLayout.vue（顶栏 + 分类导航 + 页脚）
3. 注册页 Register.vue
4. 登录页 Login.vue
5. 首页 Home.vue
6. 商品列表 ProductList.vue
7. 商品详情 ProductDetail.vue
8. 购物车 Cart.vue
9. 订单列表 OrderList.vue
10. 路由更新 + 导航守卫（已登录/未登录）
