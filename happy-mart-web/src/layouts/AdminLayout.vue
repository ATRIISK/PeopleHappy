<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()

const menuItems = [
  { path: '/admin/dashboard', name: '仪表盘', icon: 'Odometer' },
  { path: '/admin/products', name: '商品管理', icon: 'Goods' },
  { path: '/admin/orders', name: '订单管理', icon: 'List' },
  { path: '/admin/users', name: '用户管理', icon: 'User' }
]

function handleLogout() {
  router.push('/')
}
</script>

<template>
  <el-container style="min-height: 100vh">
    <el-aside width="220px" style="background: #304156">
      <div style="height: 60px; display: flex; align-items: center; justify-content: center; color: #fff; font-size: 18px; font-weight: bold; border-bottom: 1px solid rgba(255,255,255,0.1)">
        众乐商城 · 管理
      </div>
      <el-menu
        :default-active="router.currentRoute.value.path"
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409eff"
        router
        style="border-right: none"
      >
        <el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.name }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header style="background: #fff; border-bottom: 1px solid #e6e6e6; display: flex; align-items: center; justify-content: flex-end; padding: 0 20px">
        <el-button type="primary" link @click="handleLogout">返回前台</el-button>
      </el-header>
      <el-main style="background: #f0f2f5">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>
