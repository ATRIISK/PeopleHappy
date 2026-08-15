/**
 * 路由配置文件
 * 包含前台布局路由、独立页面（登录/注册）、后台管理路由、404 页面
 */
import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'           // ← 新增：路由守卫里弹"无权限访问"提示
import { useUserStore } from '@/stores/user'       // ← 新增：判断当前登录用户是否管理员

const routes = [
  // ==================== 前台页面（带 FrontLayout 布局） ====================
  {
    path: '/',
    component: () => import('@/layouts/FrontLayout.vue'),
    children: [
      {
        path: '',
        name: 'Home',
        component: () => import('@/views/front/Home.vue'),
        meta: { title: '众乐电子商城' }
      },
      {
        path: 'products',
        name: 'Products',
        component: () => import('@/views/front/ProductList.vue'),
        meta: { title: '商品列表' }
      },
      {
        path: 'product/:id',
        name: 'ProductDetail',
        component: () => import('@/views/front/ProductDetail.vue'),
        meta: { title: '商品详情' }
      },
      {
        path: 'cart',
        name: 'Cart',
        component: () => import('@/views/front/Cart.vue'),
        meta: { title: '购物车', requireAuth: true }
      },
      {
        path: 'orders',
        name: 'Orders',
        component: () => import('@/views/front/OrderList.vue'),
        meta: { title: '我的订单', requireAuth: true }
      },
      {
        path: 'orders/:id',
        name: 'OrderDetail',
        component: () => import('@/views/front/OrderDetail.vue'),
        meta: { title: '订单详情', requireAuth: true }
      },
      {
        path: 'pay/:id',
        name: 'Pay',
        component: () => import('@/views/front/Pay.vue'),
        meta: { title: '支付', requireAuth: true }
      },
      {
        path: 'address',
        name: 'AddressManager',
        component: () => import('@/views/front/AddressManager.vue'),
        meta: { title: '地址管理', requireAuth: true }
      }
    ]
  },

  // ==================== 独立页面（无 FrontLayout） ====================
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/front/Login.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/front/Register.vue'),
    meta: { title: '注册' }
  },

  // ==================== 后台管理（已有） ====================
  {
    path: '/admin',
    component: () => import('@/layouts/AdminLayout.vue'),
    meta: { requireAuth: true, requiresAdmin: true },   // ← 新增 requiresAdmin：后台页面除登录外还必须管理员角色
    children: [
      {
        path: '',
        redirect: { name: 'AdminDashboard' }
      },
      {
        path: 'dashboard',
        name: 'AdminDashboard',
        component: () => import('@/views/admin/Dashboard.vue'),
        meta: { title: '仪表盘' }
      },
      {
        path: 'products',
        name: 'AdminProducts',
        component: () => import('@/views/admin/ProductManage.vue'),
        meta: { title: '商品管理' }
      },
      {
        path: 'orders',
        name: 'AdminOrders',
        component: () => import('@/views/admin/OrderManage.vue'),
        meta: { title: '订单管理' }
      },
      {
        path: 'users',
        name: 'AdminUsers',
        component: () => import('@/views/admin/UserManage.vue'),
        meta: { title: '用户管理' }
      }
    ]
  },

  // ==================== 404 页面 ====================
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/front/NotFound.vue'),
    meta: { title: '404 - 页面未找到' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

/**
 * 路由守卫
 * 1. 设置页面标题 document.title
 * 2. 检查需要登录的页面（requireAuth），未登录则跳转 /login
 */
router.beforeEach((to, from, next) => {
  // 设置页面标题
  document.title = to.meta.title || '众乐电子商城'

  // 检查是否需要登录
  if (to.meta.requireAuth) {
    const token = localStorage.getItem('token')
    if (!token) {
      // 未登录，跳转到登录页并携带重定向地址
      next({ name: 'Login', query: { redirect: to.fullPath } })
      return
    }
  }

  // 检查是否需要管理员权限（管理后台页面）
  if (to.meta.requiresAdmin) {
    const userStore = useUserStore()   // 拿当前用户信息（userInfo 已持久化，刷新也有效）
    if (!userStore.isAdmin) {
      // 非管理员：提示并跳回首页（后端拦截器 403 是最终防线，这里只是体验层提前拦）
      ElMessage.warning('无权限访问')
      next('/')
      return
    }
  }

  next()
})

export default router
