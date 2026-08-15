<script setup>
// ==================== 导入 ====================
import { ref, reactive } from 'vue'                      // Vue 响应式 API
import { useRouter, useRoute } from 'vue-router'         // 路由跳转 & 路由参数
import { ElMessage } from 'element-plus'                  // Element Plus 消息提示
import { User, Lock } from '@element-plus/icons-vue'      // 图标组件
import { useUserStore } from '@/stores/user'              // 用户状态管理（Pinia）

// ==================== 路由 & 状态 ====================
const router = useRouter()                               // 路由实例
const route = useRoute()                                 // 当前路由（取 redirect 查询参数）
const userStore = useUserStore()                         // 用户 Store 实例

// ==================== 已登录自动跳转 ====================
// 如果用户已登录（有 token），直接跳转
// 管理员进后台首页，普通用户进前台首页
if (userStore.isLoggedIn) {
  router.replace(userStore.isAdmin ? '/admin' : '/')
}

// ==================== 表单响应式变量 ====================
const loginFormRef = ref(null)                           // 登录表单的 el-form 引用，用于调用 validate
const loading = ref(false)                               // 提交按钮 loading 状态，防重复提交

// 登录表单数据模型
const loginForm = reactive({
  username: '',     // 用户名
  password: '',     // 密码
  rememberMe: false // 记住我（选填）
})

// ==================== 表单校验规则 ====================
const rules = {
  // 用户名：必填
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' }
  ],
  // 密码：必填，至少 6 位
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' }
  ]
}

// ==================== 提交登录 ====================
async function handleLogin() {
  // 确保表单引用已挂载
  if (!loginFormRef.value) return

  // 开启 loading，防止重复提交
  loading.value = true

  try {
    // 第一步：表单校验（同步校验失败会抛出异常）
    await loginFormRef.value.validate()

    // 第二步：调用登录接口 POST /api/user/login
    // userStore.login 返回 Promise，内部通过 request.post('/user/login', loginData) 发送请求
    await userStore.login({
      username: loginForm.username,
      password: loginForm.password
    })

    // 第三步：登录成功提示
    ElMessage.success('登录成功')

    // 获取重定向地址（导航守卫传过来的，比如从购物车被拦到登录页再回购物车）
    // 没带 redirect 时：管理员跳后台首页，普通用户跳前台首页
    const redirect = route.query.redirect || (userStore.isAdmin ? '/admin' : '/')
    router.push(redirect)
  } catch (error) {
    // 表单校验失败：validate() 内部已展示错误信息，无需额外处理
    // 接口异常（如密码错误）：响应拦截器已统一展示错误消息
    // 此处只重置 loading 状态，让用户重新尝试
    loading.value = false
  }
}
</script>

<template>
  <!-- 全屏浅灰背景，flex 居中布局（独立于 FrontLayout，无导航栏） -->
  <div class="login-page">
    <!-- 白色登录卡片 -->
    <div class="login-card">
      <!-- 标题 -->
      <h2 class="login-title">众乐电子商城</h2>
      <!-- 副标题 -->
      <p class="login-subtitle">欢迎回来，请登录您的账户</p>

      <!-- 登录表单 -->
      <el-form
        ref="loginFormRef"
        :model="loginForm"
        :rules="rules"
        label-width="0"
        size="large"
        @keyup.enter="handleLogin"
      >
        <!-- 用户名输入框 -->
        <el-form-item prop="username">
          <el-input
            v-model="loginForm.username"
            placeholder="请输入用户名"
            :prefix-icon="User"
          />
        </el-form-item>

        <!-- 密码输入框 -->
        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="请输入密码"
            :prefix-icon="Lock"
            show-password
          />
        </el-form-item>

        <!-- 记住我 复选框 -->
        <el-form-item>
          <el-checkbox v-model="loginForm.rememberMe">记住我</el-checkbox>
        </el-form-item>

        <!-- 提交按钮：100% 宽度，loading 状态防重复提交 -->
        <el-form-item>
          <el-button
            type="primary"
            :loading="loading"
            style="width: 100%"
            @click="handleLogin"
          >
            登录
          </el-button>
        </el-form-item>
      </el-form>

      <!-- 底部：没有账号，跳转注册页 -->
      <div class="login-footer">
        没有账号？
        <router-link to="/register" class="register-link">立即注册</router-link>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 全屏浅灰背景，flex 居中 */
.login-page {
  min-height: 100vh;
  background: #f0f2f5;
  display: flex;
  justify-content: center;
  align-items: center;
}

/* 白色卡片容器 — 与注册页保持一致 */
.login-card {
  width: 420px;
  background: #fff;
  border-radius: 8px;
  padding: 40px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

/* 标题：24px，深色文字 */
.login-title {
  font-size: 24px;
  color: #1a1a2e;
  text-align: center;
  margin-bottom: 8px;
}

/* 副标题 */
.login-subtitle {
  text-align: center;
  color: #999;
  font-size: 14px;
  margin-bottom: 30px;
}

/* 底部链接容器 */
.login-footer {
  text-align: center;
  margin-top: 16px;
  font-size: 14px;
  color: #666;
}

/* 注册链接样式 */
.register-link {
  color: #409eff;
  text-decoration: none;
}

.register-link:hover {
  text-decoration: underline;
}
</style>
