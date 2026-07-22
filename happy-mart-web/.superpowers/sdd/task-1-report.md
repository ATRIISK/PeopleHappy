# 任务1报告：创建 Mock 数据文件

## 创建的文件

### 1. `src/mock/products.js`
- **路径**: `E:\PeopleHappy\happy-mart-web\src\mock\products.js`
- **行数**: 275 行
- **功能说明**:
  - `categories` 数组：8个大类（手机数码、电脑办公、家用电器、服装鞋帽、食品生鲜、图书教育、母婴玩具、运动户外），每个大类包含2~3个子分类
  - `mockProducts` 数组：12个商品 Mock 数据，覆盖全部8个品类，商品为常见品牌产品（华为Mate70 Pro、MacBook Pro、海尔空调、Nike运动鞋等），使用 picsum.photos 占位图
  - `getProducts(params)` 函数：模拟分页查询，支持 `categoryId`（一级/二级分类过滤）、`keyword`（名称/描述搜索）、`sort`（sales/price_asc/price_desc/newest/默认综合排序）、`page`、`size` 参数，返回 Promise
  - `getProductById(id)` 函数：根据 ID 获取单个商品，返回 Promise
  - 默认综合排序算法：评分×0.6 + 销量归一化值×0.4

### 2. `src/mock/orders.js`
- **路径**: `E:\PeopleHappy\happy-mart-web\src\mock\orders.js`
- **行数**: 157 行
- **功能说明**:
  - `ORDER_STATUS` 常量映射：`{ 0:'待付款', 1:'待发货', 2:'待收货', 3:'已完成', 4:'已取消' }`
  - `mockOrders` 数组：5个订单，覆盖5种状态（各1个），每个订单包含 items 子数组，关联商品 ID 与 products.js 一致
  - `getOrders(params)` 函数：模拟分页查询，支持 `status`、`page`、`size` 参数，返回 Promise
  - `getOrderById(id)` 函数：根据 ID 获取单个订单，返回 Promise

## 开发规范遵守情况
- 每个 import、函数均包含中文注释
- 字段名遵循后端 VO 命名规范
- 图片使用 `https://picsum.photos/seed/<unique>/<width>/<height>` 占位图
