<script setup>
/**
 * 前台布局组件 — 亚马逊风格
 * 包含：深色顶栏（Logo + 首页/商品导航 + 搜索 + 购物车 + 用户菜单）、分类导航栏、全局面包屑+返回按钮、主内容区、页脚
 */
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, ShoppingCart, User, ArrowDown, List, Location, SwitchButton } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { useCartStore } from '@/stores/cart'
import { getCategoryTree } from '@/api/category'
import AiAssistant from '@/components/AiAssistant.vue' // AI 购物助手悬浮组件（v1.11，阶段四）

// 路由
const router = useRouter()
const route = useRoute()

// Pinia 状态
const userStore = useUserStore()
const cartStore = useCartStore()

// 搜索关键词（双向绑定到搜索框）
const keyword = ref('')

/**
 * 执行搜索：跳转到 /products?keyword=xxx
 * 回车触发或点击搜索按钮触发
 */
function handleSearch() {
  if (keyword.value && keyword.value.trim()) {
    router.push({ name: 'Products', query: { keyword: keyword.value.trim() } })
  } else {
    router.push({ name: 'Products' })
  }
}

/**
 * 点击分类链接跳转到商品列表
 * @param {number} categoryId - 分类ID
 */
function goToCategory(categoryId) {
  router.push({ name: 'Products', query: { categoryId } })
}

/**
 * 下拉菜单命令处理
 * @param {string} command - 命令名称：orders（我的订单）、logout（退出登录）
 */
function handleDropdownCommand(command) {
  if (command === 'orders') {
    router.push({ name: 'Orders' })
  } else if (command === 'address') {
    router.push({ name: 'AddressManager' })
  } else if (command === 'logout') {
    handleLogout()
  }
}

/**
 * 退出登录
 * 清除用户信息后跳转到首页
 */
async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    userStore.logout()
    ElMessage.success('已退出登录')
    router.push('/')
  } catch {
    // 用户取消操作，不处理
  }
}

/**
 * 分类列表（从后端 API 获取，替代原先的 mock 数据）
 * 用于顶部分类导航栏展示
 */
const categoryList = ref([])

/** 返回上一页 */
function goBack() {
  router.back()
}

/**
 * 获取分类列表
 * 调用后端 API 获取全部分类树，取一级分类用于导航栏展示
 * 注意：getCategoryTree() 在 category.js 中已 .then(res => res.data) 解包为数组，直接赋值即可
 */
async function fetchCategories() {
  try {
    categoryList.value = await getCategoryTree()
  } catch {
    // 分类加载失败不影响页面主体功能，静默处理
  }
}

// 组件挂载时，获取分类列表 + 如果已登录则获取用户信息
onMounted(async () => {
  // 获取分类数据（替换原来的 mock 导入）
  fetchCategories()

  if (userStore.isLoggedIn) {
    try {
      await userStore.getUserInfo()
    } catch {
      // token 可能过期，静默处理
    }
  }
})
</script>

<template>
  <div class="front-layout">
    <!-- ==================== 顶栏 ==================== -->
    <header class="top-header">
      <div class="header-inner">
        <!-- Logo + 主导航 -->
        <router-link to="/" class="logo">
          <span class="logo-mark">众</span>
          <span class="logo-text">众乐商城</span>
        </router-link>
        <div class="top-nav">
          <router-link to="/" class="top-nav-link" :class="{ active: route.name === 'Home' }">首页</router-link>
          <router-link to="/products" class="top-nav-link" :class="{ active: ['Products', 'ProductDetail'].includes(route.name) }">商品</router-link>
        </div>

        <!-- 搜索框 -->
        <div class="search-box">
          <el-input
            v-model="keyword"
            placeholder="搜索商品..."
            class="search-input"
            clearable
            @keyup.enter="handleSearch"
          />
          <el-button type="primary" class="search-btn" @click="handleSearch">
            <el-icon><Search /></el-icon>
            <span>搜索</span>
          </el-button>
        </div>

        <!-- 右侧操作区 -->
        <div class="header-actions">
          <!-- 购物车 -->
          <router-link to="/cart" class="cart-link">
            <el-badge :value="cartStore.totalCount" :hidden="cartStore.totalCount === 0" class="cart-badge">
              <el-icon :size="22"><ShoppingCart /></el-icon>
            </el-badge>
            <span class="cart-text">购物车</span>
          </router-link>

          <!-- 未登录：显示登录/注册 -->
          <template v-if="!userStore.isLoggedIn">
            <router-link to="/login" class="header-link">登录</router-link>
            <router-link to="/register" class="header-link">注册</router-link>
          </template>

          <!-- 已登录：显示用户下拉菜单 -->
          <template v-else>
            <el-dropdown trigger="click" @command="handleDropdownCommand">
              <span class="user-dropdown-trigger">
                <el-icon><User /></el-icon>
                <span class="username">{{ userStore.userInfo?.username || '用户' }}</span>
                <el-icon><ArrowDown /></el-icon>
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="orders">
                    <el-icon><List /></el-icon>
                    我的订单
                  </el-dropdown-item>
                  <el-dropdown-item command="address">
                    <el-icon><Location /></el-icon>
                    地址管理
                  </el-dropdown-item>
                  <el-dropdown-item command="logout" divided>
                    <el-icon><SwitchButton /></el-icon>
                    退出登录
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </div>
      </div>
    </header>

    <!-- ==================== 分类导航栏 ==================== -->
    <nav class="category-bar">
      <div class="category-bar-inner">
        <router-link
          v-for="cat in categoryList"
          :key="cat.id"
          :to="{ name: 'Products', query: { categoryId: cat.id } }"
          class="cat-link"
        >
          {{ cat.name }}
        </router-link>
      </div>
    </nav>

    <!-- ==================== 全局面包屑 + 返回按钮（首页不显示） ==================== -->
    <div v-if="route.name !== 'Home'" class="breadcrumb-wrapper">
      <el-button text class="back-btn" @click="goBack">← 返回</el-button>
      <el-breadcrumb v-if="route.name !== 'ProductDetail'" separator="/">
        <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
        <el-breadcrumb-item v-if="route.name === 'Products'">商品列表</el-breadcrumb-item>
        <el-breadcrumb-item v-else-if="route.name === 'Cart'">购物车</el-breadcrumb-item>
        <el-breadcrumb-item v-else-if="route.name === 'Orders'">我的订单</el-breadcrumb-item>
        <el-breadcrumb-item v-else-if="route.name === 'OrderDetail'">我的订单</el-breadcrumb-item>
        <el-breadcrumb-item v-else-if="route.name === 'AddressManager'">地址管理</el-breadcrumb-item>
      </el-breadcrumb>
    </div>

    <!-- ==================== 主内容区 ==================== -->
    <main class="main-content">
      <router-view />
    </main>

    <!-- ==================== 页脚 ==================== -->
    <footer class="footer">
      <div class="footer-inner">
        <div class="footer-brand">
          <span class="logo-mark">众</span>
          <span>众乐电子商城</span>
        </div>
        <div class="footer-links">
          <router-link to="/">首页</router-link>
          <router-link to="/products">所有商品</router-link>
          <a href="#">关于我们</a>
          <a href="#">联系客服</a>
          <a href="#">帮助中心</a>
        </div>
        <div class="footer-copyright">
          &copy; 2026 众乐电子商城 — 用心服务每一位客户
        </div>
      </div>
    </footer>

    <!-- AI 购物助手悬浮组件（v1.11，阶段四）→ 全前台页面右下角可问商品（游客也能用） -->
    <AiAssistant />
  </div>
</template>

<style scoped>
.front-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--hm-bg-page);
}

/* ==================== 顶栏：米白 + 细分割线 + 毛玻璃 ==================== */
.top-header {
  position: sticky;
  top: 0;
  z-index: var(--hm-z-header);
  background: rgba(246, 242, 236, 0.92);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid var(--hm-ink-200);
}

.header-inner {
  max-width: var(--hm-container);
  margin: 0 auto;
  display: flex;
  align-items: center;
  min-height: var(--hm-header-h);
  padding: 0 var(--hm-space-5);
  gap: var(--hm-space-6);
}

/* Logo：朱橙方块字标 */
.logo {
  display: flex;
  align-items: center;
  gap: 10px;
  text-decoration: none;
  flex-shrink: 0;
}
.logo-mark {
  width: 34px;
  height: 34px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--hm-primary);
  color: #fff;
  font-size: 17px;
  font-weight: 700;
  border-radius: 10px;
  box-shadow: 0 4px 10px rgba(232, 80, 30, 0.25);
  flex-shrink: 0;
}
.logo-text {
  font-size: 19px;
  font-weight: 700;
  letter-spacing: 1px;
  color: var(--hm-ink-900);
  white-space: nowrap;
  transition: color var(--hm-transition);
}
.logo:hover .logo-text {
  color: var(--hm-primary);
}

/* 主导航 */
.top-nav {
  display: flex;
  align-items: center;
  gap: var(--hm-space-2);
  flex-shrink: 0;
}
.top-nav-link {
  position: relative;
  color: var(--hm-ink-500);
  text-decoration: none;
  padding: 6px 12px;
  border-radius: var(--hm-radius-pill);
  display: inline-flex;
  align-items: center;
  font-size: var(--hm-font-md);
  transition: all var(--hm-transition);
  white-space: nowrap;
}
.top-nav-link:hover {
  color: var(--hm-primary);
  background: var(--hm-primary-soft);
}
.top-nav-link.active {
  color: var(--hm-primary);
  font-weight: var(--hm-fw-bold);
  background: var(--hm-primary-soft);
}

/* 胶囊搜索框 */
.search-box {
  flex: 1;
  display: flex;
  min-width: 0;
  max-width: 420px;
  margin: 0 auto;
  background: var(--hm-white);
  border: 1px solid var(--hm-ink-200);
  border-radius: var(--hm-radius-pill);
  padding: 3px;
  transition: border-color var(--hm-transition), box-shadow var(--hm-transition);
}
.search-box:focus-within {
  border-color: var(--hm-primary-border);
  box-shadow: 0 0 0 3px var(--hm-primary-soft);
}
.search-input {
  flex: 1;
  min-width: 0;
}
.search-input :deep(.el-input__wrapper) {
  background: transparent;
  box-shadow: none;
  padding-left: 14px;
}
.search-input :deep(.el-input__wrapper.is-focus) {
  box-shadow: none;
}
.search-input :deep(.el-input__inner) {
  color: var(--hm-ink-800);
  font-size: var(--hm-font-base);
}
.search-input :deep(.el-input__inner::placeholder) {
  color: var(--hm-ink-400);
}
.search-btn {
  border-radius: var(--hm-radius-pill);
  padding: 0 var(--hm-space-5);
  height: 32px;
  background: var(--hm-ink-900);
  border-color: var(--hm-ink-900);
  color: var(--hm-white);
  font-weight: var(--hm-fw-medium);
}
.search-btn:hover {
  background: var(--hm-primary);
  border-color: var(--hm-primary);
  color: #fff;
}

/* 右侧操作区 */
.header-actions {
  display: flex;
  align-items: center;
  gap: var(--hm-space-4);
  flex-shrink: 0;
}
.cart-link {
  display: flex;
  align-items: center;
  gap: 6px;
  text-decoration: none;
  color: var(--hm-ink-800);
  padding: 6px 10px;
  border-radius: var(--hm-radius-pill);
  transition: all var(--hm-transition);
}
.cart-link:hover {
  background: var(--hm-primary-soft);
  color: var(--hm-primary);
}
.cart-text {
  font-size: var(--hm-font-sm);
}
.cart-badge :deep(.el-badge__content) {
  border: none;
  background: var(--hm-primary);
  box-shadow: 0 0 0 2px var(--hm-bg-page);
}

.header-link {
  color: var(--hm-ink-600);
  text-decoration: none;
  font-size: var(--hm-font-sm);
  padding: 6px 10px;
  border-radius: var(--hm-radius-pill);
  white-space: nowrap;
  transition: all var(--hm-transition);
}
.header-link:hover {
  color: var(--hm-primary);
  background: var(--hm-primary-soft);
}
/* 注册按钮：深色胶囊，与登录形成主次 */
.header-actions .header-link:last-child {
  background: var(--hm-ink-900);
  color: #fff;
  padding: 7px 18px;
}
.header-actions .header-link:last-child:hover {
  background: var(--hm-primary);
  color: #fff;
}

.user-dropdown-trigger {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  color: var(--hm-ink-800);
  font-size: var(--hm-font-sm);
  padding: 6px 12px;
  border-radius: var(--hm-radius-pill);
  background: var(--hm-white);
  border: 1px solid var(--hm-ink-200);
  max-width: 170px;
  transition: all var(--hm-transition);
}
.user-dropdown-trigger:hover {
  border-color: var(--hm-primary-border);
  color: var(--hm-primary);
}
.username {
  max-width: 88px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ==================== 分类导航：chips 横排 ==================== */
.category-bar {
  background: rgba(246, 242, 236, 0.92);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid var(--hm-ink-100);
  position: sticky;
  top: var(--hm-header-h);
  z-index: calc(var(--hm-z-header) - 1);
}
.category-bar-inner {
  max-width: var(--hm-container);
  margin: 0 auto;
  display: flex;
  align-items: center;
  min-height: var(--hm-category-h);
  padding: 0 var(--hm-space-5);
  gap: var(--hm-space-1);
  font-size: var(--hm-font-sm);
  overflow-x: auto;
  scrollbar-width: none;
  -webkit-overflow-scrolling: touch;
}
.category-bar-inner::-webkit-scrollbar {
  display: none;
}
.cat-link {
  color: var(--hm-ink-500);
  text-decoration: none;
  padding: 5px 14px;
  line-height: 1.4;
  white-space: nowrap;
  border-radius: var(--hm-radius-pill);
  transition: all var(--hm-transition);
}
.cat-link:hover {
  color: var(--hm-primary);
  background: var(--hm-primary-soft);
}

/* ==================== 主内容区 ==================== */
.main-content {
  flex: 1;
  max-width: var(--hm-container);
  width: 100%;
  margin: 0 auto;
  padding: var(--hm-space-5) var(--hm-space-5) var(--hm-space-12);
  box-sizing: border-box;
  min-width: 0;
}

/* ==================== 页脚 ==================== */
.footer {
  background: var(--hm-bg-subtle);
  border-top: 1px solid var(--hm-ink-200);
  padding: var(--hm-space-10) var(--hm-space-5) var(--hm-space-8);
  margin-top: auto;
}
.footer-inner {
  max-width: var(--hm-container);
  margin: 0 auto;
  text-align: center;
}
.footer-brand {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: var(--hm-ink-900);
  font-weight: var(--hm-fw-bold);
  font-size: var(--hm-font-md);
  letter-spacing: 1px;
  margin-bottom: var(--hm-space-4);
}
.footer-brand .logo-mark {
  width: 28px;
  height: 28px;
  font-size: 14px;
  border-radius: 8px;
  box-shadow: none;
}
.footer-links {
  display: flex;
  justify-content: center;
  gap: var(--hm-space-8);
  margin-bottom: var(--hm-space-4);
  flex-wrap: wrap;
}
.footer-links a {
  color: var(--hm-ink-500);
  text-decoration: none;
  font-size: var(--hm-font-sm);
  transition: color var(--hm-transition);
}
.footer-links a:hover {
  color: var(--hm-primary);
}
.footer-copyright {
  color: var(--hm-ink-400);
  font-size: var(--hm-font-xs);
  letter-spacing: 0.5px;
}

/* ==================== 面包屑 + 返回按钮 ==================== */
.breadcrumb-wrapper {
  max-width: var(--hm-container);
  width: 100%;
  margin: 0 auto;
  padding: var(--hm-space-4) var(--hm-space-5) 0;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  gap: var(--hm-space-3);
  flex-wrap: wrap;
}
.back-btn {
  font-size: var(--hm-font-sm);
  color: var(--hm-ink-600);
  border: 1px solid var(--hm-ink-200);
  border-radius: var(--hm-radius-pill);
  padding: 4px 14px;
  flex-shrink: 0;
  background: var(--hm-white);
  transition: all var(--hm-transition);
}
.back-btn:hover {
  color: var(--hm-primary);
  border-color: var(--hm-primary-border);
  background: var(--hm-primary-soft);
}

/* ==================== 响应式 ==================== */
@media (max-width: 1024px) {
  .header-inner {
    gap: var(--hm-space-4);
  }
  .cart-text {
    display: none;
  }
}

@media (max-width: 768px) {
  .header-inner {
    flex-wrap: wrap;
    padding: var(--hm-space-2) var(--hm-space-4);
    gap: var(--hm-space-3);
    row-gap: var(--hm-space-2);
  }
  .top-nav {
    order: 3;
    width: 100%;
    justify-content: center;
    padding-top: 2px;
  }
  .top-nav-link {
    font-size: var(--hm-font-base);
  }
  .search-box {
    order: 2;
    flex: 0 0 100%;
    width: 100%;
    max-width: none;
    margin: 0;
  }
  .header-actions {
    order: 1;
    margin-left: auto;
    gap: var(--hm-space-2);
  }
  .category-bar {
    top: 0;
    position: static;
  }
  .main-content {
    padding: var(--hm-space-4) var(--hm-space-4) var(--hm-space-10);
  }
  .footer {
    padding: var(--hm-space-8) var(--hm-space-4);
  }
  .footer-links {
    gap: var(--hm-space-4);
  }
  .breadcrumb-wrapper {
    padding: var(--hm-space-3) var(--hm-space-4) 0;
  }
  .username {
    max-width: 64px;
  }
}

@media (max-width: 480px) {
  .logo-text {
    font-size: 17px;
  }
  .logo-mark {
    width: 30px;
    height: 30px;
    font-size: 15px;
  }
  .header-link {
    padding: 6px 8px;
  }
  .header-actions .header-link:last-child {
    padding: 6px 12px;
  }
  .search-btn {
    padding: 0 var(--hm-space-4);
  }
  .main-content {
    padding: var(--hm-space-3) var(--hm-space-3) var(--hm-space-8);
  }
  .breadcrumb-wrapper {
    padding: var(--hm-space-2) var(--hm-space-3) 0;
  }
}
</style>
