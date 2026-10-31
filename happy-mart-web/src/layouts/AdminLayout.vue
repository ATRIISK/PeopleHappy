<script setup>
/**
 * 管理后台布局
 *
 * 视觉调整：行内硬编码样式 → class + design-system 令牌；
 * 新增移动端侧边栏折叠（≤768px 时侧栏收窄为图标条，≤480px 时隐藏并可展开）。
 * 仅改布局与样式，不改任何菜单项、路由与登出逻辑。
 */
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { Fold, Expand } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'   // ← 新增：登出时要清 token/userInfo

const router = useRouter()
const userStore = useUserStore()               // ← 新增：用户状态 Store 实例

/** 侧边栏是否展开（移动端用） */
const asideOpen = ref(false)

const menuItems = [
  { path: '/admin/dashboard', name: '仪表盘', icon: 'Odometer' },
  { path: '/admin/products', name: '商品管理', icon: 'Goods' },
  { path: '/admin/orders', name: '订单管理', icon: 'List' },
  { path: '/admin/users', name: '用户管理', icon: 'User' }
]

function handleLogout() {
  // 登出：先清 token + userInfo（localStorage），再跳回前台首页
  // 原来只跳转不清 token，会导致返回后还带着管理员身份、后台页面仍可访问
  userStore.logout()
  router.push('/')
}

/** 移动端点击菜单后自动收起侧边栏 */
function handleMenuClick() {
  if (window.matchMedia('(max-width: 768px)').matches) {
    asideOpen.value = false
  }
}
</script>

<template>
  <el-container class="admin-layout">
    <!-- 移动端顶部条：仅 ≤768px 显示 -->
    <div class="admin-mobile-bar">
      <button
        type="button"
        class="admin-mobile-bar__toggle"
        :aria-expanded="asideOpen"
        aria-label="切换菜单"
        @click="asideOpen = !asideOpen"
      >
        <el-icon><component :is="asideOpen ? Fold : Expand" /></el-icon>
      </button>
      <span class="admin-mobile-bar__title">众乐商城 · 管理</span>
    </div>

    <el-aside
      class="admin-aside"
      :class="{ 'admin-aside--open': asideOpen }"
      width="220px"
    >
      <div class="admin-aside__brand">
        <span class="admin-aside__logo">&gt;_</span>
        <span class="admin-aside__brand-text">
          <strong>众乐商城</strong>
          <small>ADMIN CONSOLE</small>
        </span>
      </div>
      <div class="admin-aside__section-label">管理导航</div>
      <el-menu
        :default-active="router.currentRoute.value.path"
        class="admin-menu"
        router
        @select="handleMenuClick"
      >
        <el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <span class="admin-menu__text">{{ item.name }}</span>
        </el-menu-item>
      </el-menu>
      <div class="admin-aside__footer">
        <span class="admin-aside__footer-dot"></span>
        <span>运营管理后台</span>
      </div>
    </el-aside>

    <!-- 移动端遮罩：点击关闭侧栏 -->
    <div
      v-if="asideOpen"
      class="admin-aside-mask"
      @click="asideOpen = false"
    ></div>

    <el-container class="admin-body">
      <el-header class="admin-header" height="68px">
        <div class="admin-header__context">
          <span class="admin-header__eyebrow">STORE MANAGEMENT</span>
          <strong>运营工作台</strong>
        </div>
        <el-button class="admin-header__home" plain @click="handleLogout">返回前台</el-button>
      </el-header>
      <el-main class="admin-main hm-reveal">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.admin-layout {
  min-height: 100vh;
  background: var(--hm-bg-page);
}

/* ---------- 侧边栏 ---------- */
.admin-aside {
  position: relative;
  background:
    radial-gradient(circle at 20% 0%, rgba(232, 80, 30, 0.18), transparent 34%),
    var(--hm-ink-900);
  border-right: 1px solid rgba(255, 255, 255, 0.06);
  display: flex;
  flex-direction: column;
  transition: width var(--hm-transition), transform var(--hm-transition);
  z-index: var(--hm-z-float);
}

.admin-aside__brand {
  min-height: 84px;
  display: flex;
  align-items: center;
  gap: var(--hm-space-3);
  padding: 0 var(--hm-space-5);
  color: var(--hm-white);
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  flex-shrink: 0;
  user-select: none;
}
.admin-aside__logo {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: 12px;
  background: var(--hm-primary);
  color: var(--hm-white);
  font-size: var(--hm-font-lg);
  font-weight: var(--hm-fw-bold);
  box-shadow: 0 8px 20px rgba(232, 80, 30, 0.28);
}
.admin-aside__brand-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.admin-aside__brand-text strong {
  font-size: var(--hm-font-md);
  letter-spacing: 1px;
}
.admin-aside__brand-text small {
  color: var(--hm-ink-400);
  font-size: 9px;
  letter-spacing: 1.4px;
}
.admin-aside__section-label {
  padding: var(--hm-space-5) var(--hm-space-5) var(--hm-space-2);
  color: var(--hm-ink-400);
  font-size: 10px;
  font-weight: var(--hm-fw-medium);
  letter-spacing: 2px;
}

/* Element Plus 菜单深色化（用 :deep 穿透组件作用域） */
.admin-menu {
  border-right: none;
  background: transparent;
  padding: 0 var(--hm-space-2);
  --el-menu-bg-color: transparent;
  --el-menu-text-color: #cdbfb1;
  --el-menu-active-color: var(--hm-white);
  --el-menu-hover-bg-color: rgba(255, 255, 255, 0.07);
}

.admin-menu :deep(.el-menu-item) {
  height: 48px;
  line-height: 48px;
  font-size: var(--hm-font-base);
  margin: var(--hm-space-1) 0;
  padding: 0 var(--hm-space-4) !important;
  border-radius: var(--hm-radius-md);
  transition: background var(--hm-transition), color var(--hm-transition);
}
.admin-menu :deep(.el-menu-item .el-icon) {
  font-size: 18px;
}
.admin-menu :deep(.el-menu-item.is-active) {
  background: var(--hm-primary);
  font-weight: var(--hm-fw-medium);
  box-shadow: 0 8px 18px rgba(232, 80, 30, 0.2);
}
.admin-menu :deep(.el-menu-item:hover) {
  background: rgba(255, 255, 255, 0.08);
}
.admin-menu :deep(.el-menu-item.is-active:hover) {
  background: var(--hm-primary-hover);
}
.admin-menu__text {
  margin-left: var(--hm-space-2);
  letter-spacing: 0.5px;
}
.admin-aside__footer {
  display: flex;
  align-items: center;
  gap: var(--hm-space-2);
  margin: auto var(--hm-space-5) var(--hm-space-5);
  padding-top: var(--hm-space-4);
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  color: var(--hm-ink-400);
  font-size: var(--hm-font-xs);
}
.admin-aside__footer-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--hm-primary);
  box-shadow: 0 0 0 4px rgba(139, 233, 176, 0.12);
}

/* ---------- 主体 ---------- */
.admin-body {
  min-width: 0;
}

.admin-header {
  background: rgba(255, 255, 255, 0.9);
  border-bottom: 1px solid var(--hm-ink-100);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--hm-space-6);
  backdrop-filter: blur(12px);
}
.admin-header__context {
  display: flex;
  flex-direction: column;
  line-height: var(--hm-lh-tight);
}
.admin-header__context strong {
  color: var(--hm-ink-900);
  font-size: var(--hm-font-md);
}
.admin-header__eyebrow {
  margin-bottom: 4px;
  color: var(--hm-ink-400);
  font-size: 9px;
  letter-spacing: 1.6px;
}
.admin-header__home {
  border-color: var(--hm-ink-200);
  border-radius: var(--hm-radius-md);
  color: var(--hm-ink-700);
}
.admin-header__home:hover {
  border-color: var(--hm-primary-border);
  color: var(--hm-primary);
  background: var(--hm-primary-soft);
}

.admin-main {
  background: var(--hm-bg-page);
  padding: var(--hm-space-6);
  min-width: 0;
}

/* ---------- 移动端顶栏（桌面隐藏）---------- */
.admin-mobile-bar {
  display: none;
  height: 52px;
  align-items: center;
  gap: var(--hm-space-3);
  padding: 0 var(--hm-space-3);
  background: var(--hm-ink-900);
  color: var(--hm-white);
  position: sticky;
  top: 0;
  z-index: var(--hm-z-header);
}
.admin-mobile-bar__toggle {
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.08);
  color: var(--hm-white);
  cursor: pointer;
  padding: var(--hm-space-2);
  display: flex;
  align-items: center;
  border-radius: var(--hm-radius-sm);
  min-width: 40px;
  min-height: 40px;
  justify-content: center;
}
.admin-mobile-bar__toggle:hover {
  background: var(--hm-primary-soft);
}
.admin-mobile-bar__title {
  font-size: var(--hm-font-base);
  font-weight: var(--hm-fw-medium);
}

/* 遮罩默认隐藏 */
.admin-aside-mask {
  display: none;
}

/* ==================== 响应式 ==================== */
@media (max-width: 1024px) {
  .admin-aside {
    width: 204px !important;
  }
  .admin-main {
    padding: var(--hm-space-5);
  }
}

@media (max-width: 768px) {
  .admin-mobile-bar {
    display: flex;
  }

  /* 侧边栏改为抽屉：默认移出视口，展开时滑入 */
  .admin-aside {
    position: fixed;
    top: 52px;
    left: 0;
    bottom: 0;
    width: 232px !important;
    transform: translateX(-100%);
    box-shadow: var(--hm-shadow-lg);
  }
  .admin-aside--open {
    transform: translateX(0);
  }

  .admin-aside-mask {
    display: block;
    position: fixed;
    inset: 52px 0 0 0;
    background: rgba(36, 29, 23, 0.46);
    backdrop-filter: blur(2px);
    z-index: calc(var(--hm-z-float) - 1);
  }

  .admin-header {
    height: 60px;
    padding: 0 var(--hm-space-4);
  }

  .admin-main {
    padding: var(--hm-space-4);
  }
}

@media (max-width: 480px) {
  .admin-main {
    padding: var(--hm-space-3);
  }
  .admin-header__eyebrow {
    display: none;
  }
  .admin-header__context strong {
    font-size: var(--hm-font-base);
  }
}

/* ==================== Codex 深色后台收口 ==================== */
.admin-aside {
  background:
    radial-gradient(circle at 20% 0%, rgba(139, 233, 176, 0.1), transparent 34%),
    #080a0d;
  border-right-color: var(--hm-ink-100);
}
.admin-aside__logo {
  background: var(--hm-ink-050);
  color: var(--hm-primary);
  border: 1px solid var(--hm-primary-border);
  font-family: 'Cascadia Code', 'SFMono-Regular', Consolas, monospace;
  font-size: 15px;
  box-shadow: 0 0 24px rgba(139, 233, 176, 0.12);
}
.admin-menu {
  --el-menu-text-color: var(--hm-ink-500);
  --el-menu-active-color: var(--hm-primary);
  --el-menu-hover-bg-color: var(--hm-bg-raised);
}
.admin-menu :deep(.el-menu-item.is-active) {
  background: var(--hm-primary-soft);
  color: var(--hm-primary);
  box-shadow: inset 2px 0 0 var(--hm-primary);
}
.admin-menu :deep(.el-menu-item.is-active:hover) {
  background: rgba(139, 233, 176, 0.18);
}
.admin-aside__footer-dot {
  box-shadow: 0 0 0 4px rgba(139, 233, 176, 0.12);
}
.admin-header {
  background: rgba(11, 13, 16, 0.8);
  border-bottom-color: var(--hm-ink-100);
}
.admin-main {
  background:
    radial-gradient(circle at 80% -20%, rgba(109, 200, 232, 0.05), transparent 32%),
    var(--hm-bg-page);
}
.admin-mobile-bar {
  background: #080a0d;
  color: var(--hm-ink-900);
  border-bottom: 1px solid var(--hm-ink-100);
}
.admin-mobile-bar__toggle:hover {
  background: var(--hm-primary-soft);
  border-color: var(--hm-primary-border);
  color: var(--hm-primary);
}
.admin-aside-mask {
  background: rgba(5, 6, 8, 0.72);
  backdrop-filter: blur(4px);
}
/* ==================== 经典灰白后台 ==================== */
.admin-layout {
  background: var(--hm-bg-page);
}
.admin-aside {
  background: var(--hm-ink-050);
  border-right: 1px solid var(--hm-ink-100);
}
.admin-aside__brand {
  color: var(--hm-ink-900);
  border-bottom-color: var(--hm-ink-100);
}
.admin-aside__logo {
  background: var(--hm-primary);
  color: var(--hm-on-primary);
  border: 1px solid var(--hm-primary);
  box-shadow: none;
}
.admin-aside__brand-text small,
.admin-aside__section-label,
.admin-aside__footer {
  color: var(--hm-ink-400);
}
.admin-menu {
  --el-menu-text-color: var(--hm-ink-600);
  --el-menu-active-color: var(--hm-primary);
  --el-menu-hover-bg-color: var(--hm-white);
}
.admin-menu :deep(.el-menu-item) {
  color: var(--hm-ink-600);
}
.admin-menu :deep(.el-menu-item:hover) {
  background: var(--hm-white);
  color: var(--hm-ink-900);
}
.admin-menu :deep(.el-menu-item.is-active) {
  background: var(--hm-primary-soft);
  color: var(--hm-primary);
  border: 1px solid var(--hm-primary-border);
  box-shadow: none;
}
.admin-menu :deep(.el-menu-item.is-active:hover) {
  background: var(--hm-primary-soft);
}
.admin-aside__footer {
  border-top-color: var(--hm-ink-100);
}
.admin-aside__footer-dot {
  background: var(--hm-success);
  box-shadow: none;
}
.admin-header {
  background: rgba(255, 255, 255, 0.94);
  border-bottom-color: var(--hm-ink-100);
}
.admin-main {
  background: var(--hm-bg-page);
}
.admin-header__context strong {
  color: var(--hm-ink-900);
}
.admin-header__home {
  background: var(--hm-bg-surface);
  border-color: var(--hm-ink-100);
}
.admin-header__home:hover {
  background: var(--hm-bg-subtle);
  border-color: var(--hm-ink-300);
  color: var(--hm-ink-900);
}
.admin-mobile-bar {
  background: var(--hm-bg-surface);
  color: var(--hm-ink-900);
  border-bottom-color: var(--hm-ink-100);
}
.admin-mobile-bar__toggle {
  background: var(--hm-bg-subtle);
  border-color: var(--hm-ink-100);
  color: var(--hm-ink-700);
}
.admin-mobile-bar__toggle:hover {
  background: var(--hm-ink-050);
  color: var(--hm-ink-900);
}
.admin-aside-mask {
  background: rgba(31, 35, 40, 0.28);
  backdrop-filter: blur(2px);
}
</style>
