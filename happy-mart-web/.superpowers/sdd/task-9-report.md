# Task 9 订单列表页 OrderList.vue — 实现报告

## 修改文件
- `E:\PeopleHappy\happy-mart-web\src\views\front\OrderList.vue` — 重写完整组件

## 实现功能

### 布局
- 套用 FrontLayout 布局（作为 `/orders` 子路由自动生效）
- 页面标题「我的订单」

### 状态 Tab 切换
- el-radio-group + el-radio-button 实现五个 Tab：全部 | 待付款 | 待发货 | 待收货 | 已完成
- 切换时根据 `activeStatus` 重新调用 `getOrders(params)` 过滤订单状态

### 订单卡片列表
每个订单卡片（白色背景，border-radius:8px，border，hover shadow）：

**订单头部**（背景 #fafafa，border-bottom）：
- 左侧：订单号 + 下单时间
- 右侧：el-tag 订单状态标签（颜色按状态区分）

**订单商品列表**（padding:16px 20px）：
- 每行：缩略图 60×60 + 商品名称 + 价格×数量
- 商品之间虚线分隔（border-bottom: 1px dashed #e8e8e8）

**订单底部**（背景 #fafafa，border-top）：
- 左侧：实付金额（红色 18px 加粗）
- 右侧：按状态显示操作按钮
  - 待付款：去付款(type=danger) + 取消订单
  - 待收货：确认收货(type=primary)
  - 其他状态：无按钮

### 数据
- 使用 `@/mock/orders` 的 `getOrders()` 异步函数和 `ORDER_STATUS` 常量

### 交互
- **去付款**：ElMessageBox.confirm 确认 → 修改状态为 1（待发货）→ 重新加载
- **取消订单**：ElMessageBox.confirm 确认 → 修改状态为 4（已取消）→ 重新加载
- **确认收货**：ElMessageBox.confirm 确认 → 修改状态为 3（已完成）→ 重新加载

### 状态处理
- loading 状态：el-skeleton 骨架屏
- 空状态：el-empty "暂无订单"

### 状态标签颜色映射
0=danger, 1=warning, 2=primary, 3=success, 4=info

## 自检清单
- [x] 状态 Tab 切换（5 个 Tab）
- [x] 订单卡片结构（头部 + 商品列表 + 底部）
- [x] 订单状态标签（颜色区分）
- [x] 商品列表（缩略图 + 名称 + 价格×数量，虚线分隔）
- [x] 实付金额（红色 18px 加粗）
- [x] 去付款操作（确认弹窗）
- [x] 取消订单操作（确认弹窗）
- [x] 确认收货操作（确认弹窗）
- [x] 每次操作后重新加载数据
- [x] loading 状态（骨架屏）
- [x] 空状态（el-empty）
- [x] hover 阴影效果
- [x] 全部中文注释
- [x] scoped 样式
- [x] 使用 Element Plus 组件
