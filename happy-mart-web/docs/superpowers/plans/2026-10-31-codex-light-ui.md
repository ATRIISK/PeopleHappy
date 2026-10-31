# Codex 经典灰白工作台实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将现有黑绿深色商城替换为 Codex 经典灰白工作台，并把前台改成侧栏导航、紧凑顶栏和白底内容布局。

**Architecture:** 先把全局设计令牌和 Element Plus 变量恢复为灰白体系，再重构前台壳层为左侧栏加主内容区，随后集中清理页面中的深色硬编码，最后验证桌面、移动端和管理后台。

**Tech Stack:** Vue 3、Vite、Element Plus、原生 CSS

## Global Constraints

- 页面底色 `#f7f7f8`，侧栏底色 `#f2f2f3`，内容表面 `#ffffff`。
- 普通描边 `#e5e5e7`，主文字 `#202124`，次文字 `#5f6368`。
- 主按钮 `#202124`，焦点色 `#4f8ef7`，危险色 `#d93025`。
- 不改 `src/api/`、`src/stores/`、`src/router/`、`src/utils/`、`happy_mart/`。
- 不新增第三方依赖。
- 只保留 160 至 220 毫秒淡入、轻微悬停和焦点反馈。
- 390 像素视口不能出现横向溢出。
- 构建命令为 `npm run build`，工作目录为 `E:\PeopleHappy\happy-mart-web`。

---

### Task 1: 全局灰白主题与前台工作台布局

**Files:**
- Modify: `E:\PeopleHappy\happy-mart-web\src\styles\design-system.css`
- Modify: `E:\PeopleHappy\happy-mart-web\src\layouts\FrontLayout.vue`
- Modify: `E:\PeopleHappy\happy-mart-web\src\layouts\AdminLayout.vue`
- Modify: `E:\PeopleHappy\happy-mart-web\index.html`

**Interfaces:**
- Consumes: 现有 `.hm-*` 组件类和 Element Plus。
- Produces: 灰白版 `--hm-*`、`--el-*` 令牌；桌面侧栏和移动端抽屉。

- [ ] **Step 1: 替换全局颜色令牌**

```css
--hm-primary: #202124;
--hm-primary-hover: #34363a;
--hm-primary-active: #111214;
--hm-primary-soft: #f0f1f3;
--hm-primary-border: #d7d8dc;
--hm-on-primary: #ffffff;
--hm-accent: #202124;
--hm-accent-hover: #34363a;
--hm-accent-soft: #f2f2f3;
--hm-success: #188038;
--hm-success-soft: #eef7f0;
--hm-warning: #b06000;
--hm-warning-soft: #fdf6e8;
--hm-danger: #d93025;
--hm-danger-soft: #fceeed;
--hm-info: #4f8ef7;
--hm-info-soft: #eef4ff;
--hm-ink-900: #202124;
--hm-ink-800: #34363a;
--hm-ink-700: #4a4d52;
--hm-ink-600: #5f6368;
--hm-ink-500: #7b7f87;
--hm-ink-400: #9aa0a6;
--hm-ink-300: #c4c7cc;
--hm-ink-200: #d4d4d8;
--hm-ink-100: #e5e5e7;
--hm-ink-050: #f2f2f3;
--hm-white: #ffffff;
--hm-bg-page: #f7f7f8;
--hm-bg-surface: #ffffff;
--hm-bg-subtle: #f9f9fa;
--hm-bg-raised: #ffffff;
--hm-bg-dark: #202124;
```

- [ ] **Step 2: 替换 Element Plus 表面和阴影**

```css
--el-bg-color: #ffffff;
--el-bg-color-page: #f7f7f8;
--el-bg-color-overlay: #ffffff;
--el-text-color-primary: #202124;
--el-text-color-regular: #4a4d52;
--el-text-color-secondary: #7b7f87;
--el-border-color: #e5e5e7;
--el-border-color-light: #ececee;
--el-fill-color: #f2f2f3;
--el-fill-color-light: #f7f7f8;
--el-box-shadow: 0 12px 32px rgba(32, 33, 36, 0.1);
--el-mask-color: rgba(32, 33, 36, 0.34);
```

- [ ] **Step 3: 重构前台壳层**

桌面端使用 224 像素侧栏，主区域包含紧凑顶栏和内容区；移动端侧栏改为抽屉。

```vue
<div class="front-layout">
  <aside class="app-sidebar" :class="{ 'app-sidebar--open': sidebarOpen }">
    <router-link to="/" class="logo">众乐商城</router-link>
    <nav class="sidebar-nav">
      <router-link to="/">首页</router-link>
      <router-link to="/products">商品</router-link>
      <router-link to="/cart">购物车</router-link>
    </nav>
    <div class="sidebar-section">分类</div>
    <nav class="sidebar-categories">
      <router-link v-for="cat in categoryList" :key="cat.id" :to="{ name: 'Products', query: { categoryId: cat.id } }">
        {{ cat.name }}
      </router-link>
    </nav>
  </aside>
  <div class="app-shell">
    <header class="top-header">
      <button class="mobile-menu" @click="sidebarOpen = !sidebarOpen">菜单</button>
      <div class="search-box">
        <el-input v-model="keyword" placeholder="搜索商品..." @keyup.enter="handleSearch" />
        <el-button type="primary" @click="handleSearch">搜索</el-button>
      </div>
      <div class="header-actions">
        <router-link to="/cart" class="cart-link">{{ cartStore.totalCount }} 购物车</router-link>
        <template v-if="!userStore.isLoggedIn">
          <router-link to="/login" class="header-link">登录</router-link>
          <router-link to="/register" class="header-link">注册</router-link>
        </template>
        <el-dropdown v-else @command="handleDropdownCommand">
          <span class="user-dropdown-trigger">{{ userStore.userInfo?.nickname || '用户' }}</span>
        </el-dropdown>
      </div>
    </header>
    <main class="main-content">
      <div v-if="route.name !== 'Home'" class="breadcrumb-wrapper">
        <button type="button" class="back-btn" @click="goBack">← 返回</button>
      </div>
      <router-view />
    </main>
    <footer class="footer">
      <div class="footer-inner">
        <div class="footer-brand">众乐电子商城</div>
        <div class="footer-copyright">© 2026 众乐电子商城</div>
      </div>
    </footer>
  </div>
</div>
```

- [ ] **Step 4: 改造后台壳层**

把后台侧栏背景改回 `#f2f2f3`，选中项使用白底、深色文字和左侧深灰指示条；移除黑绿渐变。

- [ ] **Step 5: 构建并提交**

Run: `npm run build`

Expected: exit 0。

```bash
git add happy-mart-web/src/styles/design-system.css happy-mart-web/src/layouts/FrontLayout.vue happy-mart-web/src/layouts/AdminLayout.vue happy-mart-web/index.html
git commit -m "feat(web): add Codex light workspace shell"
```

### Task 2: 首页、商品页和通用商品组件

**Files:**
- Modify: `E:\PeopleHappy\happy-mart-web\src\views\front\Home.vue`
- Modify: `E:\PeopleHappy\happy-mart-web\src\views\front\ProductList.vue`
- Modify: `E:\PeopleHappy\happy-mart-web\src\views\front\ProductDetail.vue`
- Modify: `E:\PeopleHappy\happy-mart-web\src\views\front\NotFound.vue`

**Interfaces:**
- Consumes: Task 1 的灰白令牌和侧栏壳层。
- Produces: 白底编辑式 Hero、灰白商品卡片和浅色详情页。

- [ ] **Step 1: 改造首页 Hero**

把黑绿网格 Banner 改成白底大卡片：左侧标题和按钮，右侧商品图；使用 1 像素浅灰边框和极浅阴影。

```css
.banner-item {
  border: 1px solid var(--hm-ink-100);
  border-radius: 12px;
  background: var(--hm-bg-surface);
  box-shadow: 0 1px 2px rgba(32, 33, 36, 0.04);
}
.hero-status {
  color: var(--hm-ink-500);
  background: var(--hm-bg-subtle);
  border-color: var(--hm-ink-100);
}
```

- [ ] **Step 2: 商品卡片改为灰白**

```css
.hm-product-card {
  background: var(--hm-bg-surface);
  border-color: var(--hm-ink-100);
  box-shadow: 0 1px 2px rgba(32, 33, 36, 0.04);
}
.hm-product-card:hover {
  transform: translateY(-2px);
  border-color: var(--hm-ink-200);
  box-shadow: 0 8px 20px rgba(32, 33, 36, 0.08);
}
```

- [ ] **Step 3: 详情页和 404 改浅色**

去掉深色网格、黑底价格块和绿色光晕；价格使用近黑文字，危险操作使用红色。

- [ ] **Step 4: 构建并提交**

Run: `npm run build`

Expected: exit 0。

```bash
git add happy-mart-web/src/views/front/Home.vue happy-mart-web/src/views/front/ProductList.vue happy-mart-web/src/views/front/ProductDetail.vue happy-mart-web/src/views/front/NotFound.vue
git commit -m "feat(web): restore light product browsing"
```

### Task 3: 账号、交易页和 AI 助手

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
- Consumes: Task 1 的全局令牌。
- Produces: 一致的浅色认证、交易和助手界面。

- [ ] **Step 1: 认证页改灰白**

登录和注册使用白色卡片、浅灰背景和深色品牌侧栏；按钮使用近黑主色，焦点使用蓝色描边。

- [ ] **Step 2: 交易页清理深色硬编码**

把 `var(--hm-white)` 用作背景的位置改为 `var(--hm-bg-surface)`，表格头使用 `var(--hm-bg-subtle)`，价格使用 `var(--hm-ink-900)`。

- [ ] **Step 3: AI 助手改成右侧浅色面板**

```css
.panel {
  background: var(--hm-bg-surface);
  border: 1px solid var(--hm-ink-100);
  box-shadow: 0 20px 48px rgba(32, 33, 36, 0.14);
}
.message-list {
  background: var(--hm-bg-subtle);
}
.message.user .bubble {
  background: var(--hm-primary);
  color: var(--hm-on-primary);
}
```

- [ ] **Step 4: 构建并提交**

Run: `npm run build`

Expected: exit 0。

```bash
git add happy-mart-web/src/views/front/Login.vue happy-mart-web/src/views/front/Register.vue happy-mart-web/src/views/front/Cart.vue happy-mart-web/src/views/front/OrderList.vue happy-mart-web/src/views/front/OrderDetail.vue happy-mart-web/src/views/front/AddressManager.vue happy-mart-web/src/views/front/Pay.vue happy-mart-web/src/components/AiAssistant.vue
git commit -m "feat(web): restore light commerce surfaces"
```

### Task 4: 管理后台与完整验收

**Files:**
- Modify: `E:\PeopleHappy\happy-mart-web\src\views\admin\Dashboard.vue`
- Modify: `E:\PeopleHappy\happy-mart-web\src\views\admin\ProductManage.vue`
- Modify: `E:\PeopleHappy\happy-mart-web\src\views\admin\OrderManage.vue`
- Modify: `E:\PeopleHappy\happy-mart-web\src\views\admin\UserManage.vue`

**Interfaces:**
- Consumes: 前三项任务的全部视觉层。
- Produces: 灰白管理后台和完整验证证据。

- [ ] **Step 1: 后台页面改灰白**

卡片、筛选区、表格、分页和弹窗使用白色表面、浅灰边框；移除黑色表头和绿色光晕。

- [ ] **Step 2: 构建**

Run: `npm run build`

Expected: `vite build` exit 0。

- [ ] **Step 3: 桌面浏览器验收**

打开首页、商品列表、商品详情、购物车、登录和后台仪表盘，确认页面底色为灰白、侧栏存在、文字可读、焦点可见。

- [ ] **Step 4: 移动端验收**

把视口设为 `390 x 844`，检查首页、商品列表、商品详情和后台仪表盘；确认 `document.documentElement.scrollWidth <= window.innerWidth`。

- [ ] **Step 5: 确认边界并提交**

Run: `git diff --name-only -- happy-mart-web/src/api happy-mart-web/src/stores happy-mart-web/src/router happy-mart-web/src/utils happy_mart`

Expected: 无输出。

```bash
git add happy-mart-web/src/views/admin/Dashboard.vue happy-mart-web/src/views/admin/ProductManage.vue happy-mart-web/src/views/admin/OrderManage.vue happy-mart-web/src/views/admin/UserManage.vue
git commit -m "feat(web): finish light admin workspace"
```
