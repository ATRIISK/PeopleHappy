# Task 8 购物车页 Cart.vue — 实现报告

## 修改文件
- `E:\PeopleHappy\happy-mart-web\src\views\front\Cart.vue` — 重写完整组件

## 实现功能

### 布局
- 套用 FrontLayout 布局（作为 `/cart` 子路由自动生效）
- 白色圆角卡片容器，页面标题「我的购物车」

### 购物车商品列表
- 表头行：全选 | 商品 | 单价 | 数量 | 小计 | 操作（6 列）
- 商品行：复选框 + 80×80 缩略图 + 名称 | 单价 | el-input-number(min=1) | 小计 | 删除按钮
- 行 hover 背景色 `#fafafa`
- 已勾选行背景色 `#f0f9ff`

### 底部操作栏
- 左侧：全选复选框 + "删除选中" 按钮（显示已选数量，选中 0 件时禁用）
- 右侧：合计金额（红色 24px 加粗）+ "去结算" 按钮（type=danger，选中 0 件时禁用）

### 交互逻辑
- **全选切换**：el-checkbox + indeterminate 半选状态
- **单个勾选**：点击复选框切换选中状态，使用 Set 存储 productId
- **数量修改**：el-input-number @change 触发 cartStore.updateQuantity()
- **删除单个**：ElMessageBox.confirm 确认后调用 cartStore.removeItem()
- **批量删除**：ElMessageBox.confirm 确认后遍历删除，显示删除数量
- **去结算**：ElMessageBox.alert 提示演示信息，然后清空已勾选商品

### 空状态
- el-empty 显示「购物车是空的」+ "去逛逛" 按钮（跳转 /products）

### 数据
- 使用已有的 cartStore（`@/stores/cart`），解构 items、totalCount、totalAmount
- onMounted 时调用 cartStore.fetchCart() 获取购物车数据

### 开发要求
- 全部中文注释
- scoped 样式
- 异常处理（try-catch 包裹异步操作，错误提示）

## 自检清单
- [x] 空状态显示
- [x] 全选/取消全选
- [x] 单个勾选
- [x] 数量修改
- [x] 删除单个（确认弹窗）
- [x] 批量删除（确认弹窗）
- [x] 去结算（演示提示）
- [x] hover 背景色
- [x] 勾选行背景色
- [x] 底部操作栏
- [x] 金额合计（红色 24px 加粗）
- [x] 中文注释
- [x] scoped 样式
