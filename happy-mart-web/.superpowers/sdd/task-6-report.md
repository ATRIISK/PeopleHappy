# Task 6 报告：商品列表页 ProductList.vue

## 修改文件
- `E:\PeopleHappy\happy-mart-web\src\views\front\ProductList.vue`（重写）

## 实现内容

### 左侧分类筛选（el-tree）
- 从 `@/mock/products` 的 `categories` 数据构建分类树，外层套"全部分类"根节点（`id: 'all'`）
- 使用 `el-tree` 组件，`node-key="id"`，`highlight-current` 高亮选中节点，`default-expand-all` 默认展开所有节点
- 点击分类节点时调用 `handleNodeClick()`，更新路由 query 参数（`categoryId`）
- 根节点点击清除 `categoryId` 参数，显示全部分类

### 右侧商品区域
- **顶部工具栏**：左侧显示"共 X 件商品"，右侧排序下拉（`el-select`），支持综合排序/销量优先/价格从低到高/价格从高到低/最新上架
- **商品网格**：4 列网格布局，显示商品图片、名称、现价¥、原价（划线）、评分（`el-rate`）、销量
- **商品卡片**：点击跳转 `/product/:id`
- **分页**：`el-pagination`，支持每页 12/24/36/48 条，切换时重新加载

### 数据加载
- 使用 `@/mock/products` 的 `getProducts()` 异步函数（返回 Promise）
- 监听路由 `route.query` 变化（`watch` + `immediate: true`），当 `categoryId`/`keyword` 变化时自动重新加载
- 构建查询参数时从 route.query 读取 `categoryId` 和 `keyword`，支持搜索框和顶部导航分类跳转

### 状态处理
- **loading 状态**：居中显示旋转的 Loading 图标（CSS 动画 `rotating`）
- **empty 状态**：使用 `el-empty` 组件显示"暂无相关商品"
- **error 状态**：显示错误信息和"重新加载"按钮

### 样式
- 侧边栏：`width: 200px`，`background: #fff`，`border: 1px solid #eee`，`border-radius: 8px`，`padding: 16px`，`position: sticky`
- 卡片 hover：`translateY(-3px)` + `box-shadow` 过渡动画
- 全部使用 `scoped` 样式，中文注释，Element Plus 组件
