# Task 2 完成报告：FrontLayout.vue 前台布局 + 路由更新

## 创建的文件

### E:\PeopleHappy\happy-mart-web\src\layouts\FrontLayout.vue

亚马逊风格前台布局组件，包含四个区域：

1. **顶部深色粘性导航栏**（`#1a1a2e` 背景，`position: sticky`）
   - Logo（🛒 众乐商城）链接到首页
   - 搜索框（`el-input` + 搜索按钮），回车/点击跳转 `/products?keyword=xxx`
   - 购物车图标（`el-badge` 显示数量，从 `cartStore.totalCount` 读取）
   - 未登录：显示"登录"、"注册"链接
   - 已登录：显示用户下拉菜单（`el-dropdown`），包含"我的订单"、"退出登录"

2. **分类导航栏**（浅灰背景）
   - 从 `@/mock/products` 的 `categories` 数据渲染 8 个分类链接
   - 每个分类链接到 `/products?categoryId=X`

3. **主内容区**（`<router-view />`）
   - `max-width: 1400px`，居中对齐

4. **页脚**（白色背景）
   - 底部链接 + 版权信息 `© 2026 众乐电子商城`

生命周期：`onMounted` 时如果已登录则调用 `userStore.getUserInfo()`

## 修改的文件

### E:\PeopleHappy\happy-mart-web\src\router\index.js

路由结构重构为：

- **前台页面（带 FrontLayout）**：首页、商品列表、商品详情、购物车、订单列表
- **独立页面**：登录、注册（无 FrontLayout 包裹）
- **后台管理**：保持不变
- **404 页面**：保持不变

导航守卫增强：

1. **页面标题**：`document.title = to.meta.title`
2. **身份验证**：`meta.requireAuth` 页面检查 token，未登录跳转 `/login?redirect=xxx`

路由守卫放在 `router.beforeEach` 中统一处理，购物车和订单页面标记为 `requireAuth: true`。
