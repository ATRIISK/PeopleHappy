# Task 5 报告：首页 Home.vue

## 修改文件

- `E:\PeopleHappy\happy-mart-web\src\views\front\Home.vue` — 首页组件（完整重写）

## 实现功能

### 1. Banner 轮播区
- 使用 `el-carousel` 组件，高度 360px，自动轮播间隔 4 秒
- 3 张促销横幅，使用 picsum.photos 占位图（`seed/homebanner1~3`）
- 背景图展示，标题白色文字带阴影（`text-shadow: 2px 2px 8px rgba(0,0,0,0.6)`）
- 负边距抵消父级 padding 实现满宽显示

### 2. 分类快捷入口
- 从 `@/mock/products` 的 `categories` 取前 8 个
- 2 行 x 4 列网格布局（`grid-template-columns: repeat(8, 1fr)`）
- 每个分类显示 Element Plus 图标 + 名称，图标通过 `<component :is>` 动态渲染
- 点击跳转 `/products?categoryId=X`
- Hover 效果：背景变 `#e4393c`，图标和文字变白，上移 2px，带红色阴影
- 图标映射：`手机数码->Smartphone`、`电脑办公->Monitor`、`家用电器->Refresh`、`服装鞋帽->Tickets`、`食品生鲜->Apple`、`图书教育->Reading`、`母婴玩具->Present`、`运动户外->TrendCharts`

### 3. 热门商品推荐
- 标题 "热门推荐"（22px bold, #1a1a2e, 左侧红色边框线 4px #e4393c）
- 4 列网格，展示前 8 个商品（`mockProducts.slice(0, 8)`）
- 每个卡片：图片（aspect-ratio:1）、名称（单行省略）、价格（红色 20px 粗体）、原价（划线）、评分（el-rate disabled）、销量
- 点击跳转 `/product/:id`
- Hover 效果：上移 4px，阴影 `0 8px 24px rgba(0,0,0,0.12)`

## 开发要点
- 所有 import、方法、变量均包含中文注释
- Element Plus 组件 + scoped 样式
- 使用 async/await 处理数据加载（onMounted 异步钩子）
- 图标通过 `el-icon > component :is` 渲染
- 路由命名引用：`Products`、`ProductDetail`
