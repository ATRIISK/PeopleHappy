# Codex 风格深色 UI 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将众乐商城前端统一改造成 Codex 工作台风格的深色界面，并加入克制的进入、悬停、焦点和加载动效。

**Architecture:** 先替换全局设计令牌和 Element Plus 变量，使所有 `.hm-*` 组件和组件库自动进入深色模式；再改造前后台布局与高频页面中的旧浅色硬编码；最后做构建、路由和响应式实拍验证。全程只改展示层，不触碰接口、状态、路由和后端。

**Tech Stack:** Vue 3、Vite、Element Plus、原生 CSS

## Global Constraints

- 主背景 `#0B0D10`，基础表面 `#111318`，浮层表面 `#171A20`，描边 `#282D35`。
- 主文字 `#F2F4F7`，次文字 `#AAB2BF`，主强调色 `#8BE9B0`。
- 不改 `src/api/`、`src/stores/`、`src/router/`、`src/utils/`、`happy_mart/` 和后端配置。
- 不新增第三方依赖。
- 所有动画支持 `prefers-reduced-motion: reduce`。
- 390 像素视口不能出现横向溢出。
- 构建命令为 `npm run build`，工作目录为 `E:\PeopleHappy\happy-mart-web`。

---

### Task 1: 全局深色令牌与 Element Plus 主题

**Files:**
- Modify: `E:\PeopleHappy\happy-mart-web\src\styles\design-system.css`
- Modify: `E:\PeopleHappy\happy-mart-web\src\style.css`

**Interfaces:**
- Consumes: 现有 `.hm-*` 组件类。
- Produces: 深色版 `--hm-*` 和 `--el-*` 变量、`.hm-reveal`、`.hm-sweep`、`.hm-stagger` 动效类。

- [ ] **Step 1: 替换令牌层**

把品牌、功能色、石墨中性色、背景、阴影和过渡时间替换为设计说明中的值。保留现有变量名，避免页面选择器失效。

```css
--hm-primary: #8BE9B0;
--hm-primary-hover: #5DD79A;
--hm-primary-active: #3DBF7B;
--hm-primary-soft: rgba(139, 233, 176, 0.12);
--hm-primary-border: rgba(139, 233, 176, 0.38);
--hm-bg-page: #0B0D10;
--hm-bg-surface: #111318;
--hm-bg-subtle: #171A20;
--hm-bg-dark: #050608;
--hm-ink-900: #F2F4F7;
--hm-ink-800: #E2E6EC;
--hm-ink-700: #C5CBD5;
--hm-ink-600: #AAB2BF;
--hm-ink-500: #85909F;
--hm-ink-400: #737D8C;
--hm-ink-300: #4B535F;
--hm-ink-200: #353C47;
--hm-ink-100: #282D35;
--hm-ink-050: #1D2129;
```

- [ ] **Step 2: 替换 Element Plus 映射**

把 `--el-bg-color`、`--el-bg-color-page`、`--el-bg-color-overlay`、文字、边框、填充、阴影和遮罩映射到 `--hm-*`。输入框、文本域、选择器、弹窗、消息框、表格、分页、下拉菜单和加载遮罩的背景统一为深色。

```css
--el-bg-color: var(--hm-bg-surface);
--el-bg-color-page: var(--hm-bg-page);
--el-bg-color-overlay: var(--hm-bg-subtle);
--el-text-color-primary: var(--hm-ink-900);
--el-text-color-regular: var(--hm-ink-700);
--el-border-color: var(--hm-ink-100);
--el-mask-color: rgba(5, 6, 8, 0.72);
```

- [ ] **Step 3: 新增动效原语**

添加区块淡入、按钮扫光、卡片错峰和低对比加载脉冲。所有无限动画只使用低不透明度，并在 reduced-motion 中禁用。

```css
@keyframes hm-reveal {
  from { opacity: 0; transform: translateY(14px); }
  to { opacity: 1; transform: translateY(0); }
}
.hm-reveal { animation: hm-reveal 0.56s cubic-bezier(0.22, 1, 0.36, 1) both; }
.hm-sweep { position: relative; overflow: hidden; }
.hm-sweep::after {
  content: '';
  position: absolute;
  inset: 0;
  transform: translateX(-110%);
  background: linear-gradient(100deg, transparent 20%, rgba(255, 255, 255, 0.16) 50%, transparent 80%);
  transition: transform 0.45s ease;
}
.hm-sweep:hover::after { transform: translateX(110%); }
```

- [ ] **Step 4: 更新全局基础层**

确保 `body` 使用深色背景、浅色文字和石墨滚动条；`::selection` 使用半透明薄荷绿；链接、焦点和图片行为保持不变。

- [ ] **Step 5: 构建并扫描令牌**

Run: `npm run build`

Expected: exit 0，且 `design-system.css` 不再包含旧背景 `#f6f2ec`、`#ffffff` 作为页面主表面。

- [ ] **Step 6: 提交**

Run: `git add happy-mart-web/src/styles/design-system.css happy-mart-web/src/style.css && git commit -m "feat(web): add Codex dark design tokens"`

### Task 2: 前台布局与后台布局

**Files:**
- Modify: `E:\PeopleHappy\happy-mart-web\src\layouts\FrontLayout.vue`
- Modify: `E:\PeopleHappy\happy-mart-web\src\layouts\AdminLayout.vue`

**Interfaces:**
- Consumes: Task 1 的 `--hm-*` 令牌和 `.hm-reveal`。
- Produces: 全站深色顶栏、分类栏、页脚、后台侧栏和内容容器。

- [ ] **Step 1: 改造前台顶栏**

把顶栏背景改为 `rgba(11, 13, 16, 0.82)`，启用 `backdrop-filter: blur(18px)`，添加底部细线。Logo 标记改为深色圆角方块内的薄荷绿终端字符，导航选中态改为薄荷绿文字和柔光背景。

- [ ] **Step 2: 改造搜索和操作区**

搜索框背景改为 `var(--hm-ink-050)`，聚焦时使用薄荷绿内描边和外发光。登录、注册和用户菜单按钮统一深色表面；主操作按钮保留薄荷绿实色。

- [ ] **Step 3: 改造分类栏与页脚**

分类栏继承顶栏毛玻璃，分类选中标签使用半透明薄荷绿。页脚使用更深背景、顶部细线和低对比文字，Logo 与导航保持可点击。

- [ ] **Step 4: 改造后台布局**

侧栏背景设为 `#080A0D`，内容区设为页面底色。选中菜单使用薄荷绿左侧细条、半透明背景和浅色文字；顶部用户区与主内容区使用层级边框。

- [ ] **Step 5: 构建检查**

Run: `npm run build`

Expected: exit 0，Layout 文件仅发生模板类名和 CSS 变化，无 API 或 store 变化。

- [ ] **Step 6: 提交**

Run: `git add happy-mart-web/src/layouts/FrontLayout.vue happy-mart-web/src/layouts/AdminLayout.vue && git commit -m "feat(web): restyle front and admin shells"`

### Task 3: 首页和商品浏览

**Files:**
- Modify: `E:\PeopleHappy\happy-mart-web\src\views\front\Home.vue`
- Modify: `E:\PeopleHappy\happy-mart-web\src\views\front\ProductList.vue`
- Modify: `E:\PeopleHappy\happy-mart-web\src\views\front\ProductDetail.vue`

**Interfaces:**
- Consumes: Task 2 的深色布局和 Task 1 的动效类。
- Produces: 深色 Hero、分类磁贴、商品卡片和详情信息面板。

- [ ] **Step 1: 改造首页 Hero**

把暖色渐变改为石墨层加薄荷绿径向光，加入细网格、终端状态标签、动态光标和主按钮扫光。保留原有轮播数据、链接和点击行为。

- [ ] **Step 2: 改造分类和商品卡片**

分类磁贴使用深色浮层与圆角边框，悬停时边框和图标变为薄荷绿。商品卡片保留图片亮度，内容区使用 `--hm-bg-surface`，价格使用 `--hm-ink-900`。

- [ ] **Step 3: 添加错峰入场**

在首页热门商品和列表商品卡片上添加 `hm-reveal`，通过现有 `v-for` 索引设置 `animation-delay`。不得改变商品点击或加购事件。

- [ ] **Step 4: 改造详情页**

详情图片区域保持中性深色边框，价格、标题、销量和库存使用深色语义色。规格选择与加购按钮沿用现有 Element Plus 组件。

- [ ] **Step 5: 构建并检查**

Run: `npm run build`

Expected: exit 0，页面中旧暖橙渐变和白色商品卡背景被替换。

- [ ] **Step 6: 提交**

Run: `git add happy-mart-web/src/views/front/Home.vue happy-mart-web/src/views/front/ProductList.vue happy-mart-web/src/views/front/ProductDetail.vue && git commit -m "feat(web): restyle home and product browsing"`

### Task 4: 账号、购物车、订单与 AI 助手

**Files:**
- Modify: `E:\PeopleHappy\happy-mart-web\src\views\front\Login.vue`
- Modify: `E:\PeopleHappy\happy-mart-web\src\views\front\Register.vue`
- Modify: `E:\PeopleHappy\happy-mart-web\src\views\front\Cart.vue`
- Modify: `E:\PeopleHappy\happy-mart-web\src\views\front\OrderList.vue`
- Modify: `E:\PeopleHappy\happy-mart-web\src\views\front\OrderDetail.vue`
- Modify: `E:\PeopleHappy\happy-mart-web\src\views\front\AddressManager.vue`
- Modify: `E:\PeopleHappy\happy-mart-web\src\views\front\Pay.vue`
- Modify: `E:\PeopleHappy\happy-mart-web\src\components\AiAssistant.vue`

**Interfaces:**
- Consumes: Task 1 的全局深色变量。
- Produces: 一致深色的认证页、交易页和 AI 对话面板。

- [ ] **Step 1: 替换旧浅色硬编码**

把 `#fff`、`#fafafa`、`#f0f0f0`、`#e8e8e8`、`#333`、`#666`、`#999` 分别映射到对应的 `--hm-*` 变量。危险色 `#e4393c` 映射为 `--hm-danger`。

- [ ] **Step 2: 替换暖橙阴影**

把旧 `rgba(232, 80, 30, ...)` 阴影改为 `rgba(139, 233, 176, ...)` 或删除；促销价格的可读性使用 `--hm-warning`，不要误改成绿色。

- [ ] **Step 3: 统一认证页**

登录和注册左侧品牌区改为深色渐变和网格纹，表单卡片使用 `--hm-bg-surface`、细边框和聚焦发光。

- [ ] **Step 4: 统一交易页**

购物车、订单、地址和支付页的表格、卡片、摘要区和弹窗使用深色表面。状态标签保留原有语义色，只调整背景不透明度和边框。

- [ ] **Step 5: 统一 AI 助手**

悬浮按钮使用薄荷绿主色，对话面板使用深色毛玻璃，用户和助手消息使用不同石墨层级。保留现有请求、消息和打字逻辑。

- [ ] **Step 6: 构建并检查**

Run: `npm run build`

Expected: exit 0，所有列出的页面不再出现亮白主背景。

- [ ] **Step 7: 提交**

Run: `git add happy-mart-web/src/views/front/Login.vue happy-mart-web/src/views/front/Register.vue happy-mart-web/src/views/front/Cart.vue happy-mart-web/src/views/front/OrderList.vue happy-mart-web/src/views/front/OrderDetail.vue happy-mart-web/src/views/front/AddressManager.vue happy-mart-web/src/views/front/Pay.vue happy-mart-web/src/components/AiAssistant.vue && git commit -m "feat(web): restyle commerce and assistant surfaces"`

### Task 5: 管理页兼容与完整验证

**Files:**
- Modify: `E:\PeopleHappy\happy-mart-web\src\views\admin\Dashboard.vue`
- Modify: `E:\PeopleHappy\happy-mart-web\src\views\admin\ProductManage.vue`
- Modify: `E:\PeopleHappy\happy-mart-web\src\views\admin\OrderManage.vue`
- Modify: `E:\PeopleHappy\happy-mart-web\src\views\admin\UserManage.vue`

**Interfaces:**
- Consumes: 前四个任务的全部视觉层。
- Produces: 管理后台深色表格、图表、表单和筛选区。

- [ ] **Step 1: 替换后台页面旧浅色**

用与 Task 4 相同的变量映射替换后台页面中的白色卡片、浅灰表格和旧黑色文字硬编码。

- [ ] **Step 2: 完成构建**

Run: `npm run build`

Expected: `vite build` exit 0，无 SFC 编译错误。

- [ ] **Step 3: 浏览器桌面检查**

打开首页、商品列表、商品详情、购物车、登录、订单列表和后台仪表盘。检查深色层级、文字对比、焦点外框、卡片悬停和 AI 助手。

- [ ] **Step 4: 浏览器移动检查**

把视口设为 `390 x 844`，检查首页、商品列表、商品详情、购物车和后台仪表盘。运行 `document.documentElement.scrollWidth === window.innerWidth`，Expected: true。

- [ ] **Step 5: 确认业务边界**

Run: `git diff --stat` 和 `git status --short`。

Expected: 无 `src/api/`、`src/stores/`、`src/router/`、`src/utils/` 或后端文件改动；未跟踪 `.claude/` 保持不提交。

- [ ] **Step 6: 提交**

Run: `git add happy-mart-web/src/views/admin/Dashboard.vue happy-mart-web/src/views/admin/ProductManage.vue happy-mart-web/src/views/admin/OrderManage.vue happy-mart-web/src/views/admin/UserManage.vue && git commit -m "feat(web): complete dark admin surfaces"`
