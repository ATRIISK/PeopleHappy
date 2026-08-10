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
    <!-- ==================== 深色顶栏 ==================== -->
    <header class="top-header">
      <div class="header-inner">
        <!-- Logo + 主导航 -->
        <router-link to="/" class="logo">
          <span class="logo-icon">🛒</span>
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
  </div>
</template>

<style scoped>
/* ==================== 全局 ==================== */
.front-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f5f5f5;
}

/* ==================== 深色顶栏 ==================== */
.top-header {
  position: sticky;
  top: 0;
  z-index: 1000;
  background: #1a1a2e;
  color: #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}

.header-inner {
  max-width: 1400px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  height: 64px;
  padding: 0 20px;
  gap: 20px;
}

/* Logo */
.logo {
  display: flex;
  align-items: center;
  gap: 6px;
  text-decoration: none;
  color: #fff;
  flex-shrink: 0;
}

.logo-icon {
  font-size: 28px;
}

.logo-text {
  font-size: 22px;
  font-weight: bold;
  letter-spacing: 1px;
}

/* 搜索框 */
.search-box {
  flex: 1;
  display: flex;
  max-width: 560px;
  margin: 0 auto;
}

.search-input {
  flex: 1;
}

.search-input :deep(.el-input__wrapper) {
  border-radius: 4px 0 0 4px;
  background: #fff;
}

.search-input :deep(.el-input__inner) {
  color: #333;
}

.search-btn {
  border-radius: 0 4px 4px 0;
  padding: 0 20px;
}

/* 右侧操作区 */
.header-actions {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-shrink: 0;
}

/* 购物车链接 */
.cart-link {
  display: flex;
  align-items: center;
  gap: 4px;
  text-decoration: none;
  color: #fff;
  transition: opacity 0.2s;
}

.cart-link:hover {
  opacity: 0.8;
}

.cart-text {
  font-size: 14px;
}

.cart-badge :deep(.el-badge__content) {
  border: none;
}

/* 登录/注册链接 */
.header-link {
  color: #fff;
  text-decoration: none;
  font-size: 14px;
  transition: opacity 0.2s;
}

.header-link:hover {
  opacity: 0.8;
  text-decoration: underline;
}

/* 用户下拉菜单触发器 */
.user-dropdown-trigger {
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  color: #fff;
  font-size: 14px;
  transition: opacity 0.2s;
}

.user-dropdown-trigger:hover {
  opacity: 0.8;
}

.username {
  max-width: 80px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ==================== 顶部导航 ==================== */
.top-nav {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-left: 8px;
}

.top-nav-link {
  color: rgba(255, 255, 255, 0.8);
  text-decoration: none;
  padding: 0 12px;
  height: 64px;
  line-height: 64px;
  font-size: 15px;
  transition: all 0.2s;
}

.top-nav-link:hover {
  color: #fff;
  background: rgba(255, 255, 255, 0.08);
}

.top-nav-link.active {
  color: #fff;
  font-weight: 600;
  background: rgba(255, 255, 255, 0.12);
}

/* ==================== 分类导航栏 ==================== */
.category-bar {
  background: #f5f5f5;
  border-bottom: 1px solid #e0e0e0;
}

.category-bar-inner {
  max-width: 1400px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  height: 40px;
  padding: 0 20px;
  gap: 2px;
  font-size: 13px;
}

.cat-link {
  color: #666;
  text-decoration: none;
  padding: 0 12px;
  line-height: 40px;
  white-space: nowrap;
  transition: color 0.2s;
}

.cat-link:hover {
  color: #1a1a2e;
}

/* ==================== 主内容区 ==================== */
.main-content {
  flex: 1;
  max-width: 1400px;
  width: 100%;
  margin: 0 auto;
  padding: 20px;
  box-sizing: border-box;
}

/* ==================== 页脚 ==================== */
.footer {
  background: #fff;
  border-top: 1px solid #e8e8e8;
  padding: 24px 20px;
  margin-top: auto;
}

.footer-inner {
  max-width: 1400px;
  margin: 0 auto;
  text-align: center;
}

.footer-links {
  display: flex;
  justify-content: center;
  gap: 24px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.footer-links a {
  color: #666;
  text-decoration: none;
  font-size: 14px;
  transition: color 0.2s;
}

.footer-links a:hover {
  color: #1a1a2e;
}

.footer-copyright {
  color: #999;
  font-size: 13px;
}

/* ==================== 全局面包屑 + 返回按钮 ==================== */
.breadcrumb-wrapper {
  max-width: 1400px;
  width: 100%;
  margin: 0 auto;
  padding: 8px 20px 0;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  gap: 8px;
}

.back-btn {
  font-size: 13px;
  color: #666;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  padding: 4px 12px;
  flex-shrink: 0;
}

.back-btn:hover {
  color: #1a1a2e;
  border-color: #1a1a2e;
  background: #f8f8f8;
}
</style>
