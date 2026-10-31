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
      <section class="login-brand" aria-label="众乐电子商城品牌介绍">
        <span class="brand-badge">暖橙市集 · 品质生活</span>
        <div class="brand-mark" aria-hidden="true">众</div>
        <!-- 标题 -->
        <h2 class="login-title">众乐电子商城</h2>
        <p class="brand-desc">把日常好物与温暖心意，一起装进购物车。</p>
        <div class="brand-points" aria-hidden="true">
          <span>精选好物</span>
          <span>安心选购</span>
          <span>贴心服务</span>
        </div>
      </section>

      <section class="login-content">
        <div class="login-heading">
          <span class="login-eyebrow">WELCOME BACK</span>
          <h1>欢迎登录</h1>
          <!-- 副标题 -->
          <p class="login-subtitle">欢迎回来，请登录您的账户</p>
        </div>

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
          <el-form-item class="remember-item">
            <el-checkbox v-model="loginForm.rememberMe">记住我</el-checkbox>
          </el-form-item>

          <!-- 提交按钮：100% 宽度，loading 状态防重复提交 -->
          <el-form-item>
            <el-button
              type="primary"
              :loading="loading"
              style="width: 100%"
              class="login-submit"
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
      </section>
    </div>
  </div>
</template>

<style scoped>
/* 全屏浅灰背景，flex 居中 */
.login-page {
  min-height: 100vh;
  box-sizing: border-box;
  padding: 40px 20px;
  background:
    radial-gradient(circle at 12% 18%, rgba(232, 80, 30, 0.1), transparent 30%),
    radial-gradient(circle at 88% 82%, rgba(217, 153, 43, 0.12), transparent 32%),
    var(--hm-bg-page);
  display: flex;
  justify-content: center;
  align-items: center;
}

/* 白色卡片容器 — 与注册页保持一致 */
.login-card {
  width: min(900px, 100%);
  background: var(--hm-white);
  border: 1px solid var(--hm-ink-100);
  border-radius: 24px;
  box-shadow: var(--hm-shadow-lg);
  display: grid;
  grid-template-columns: minmax(0, 0.92fr) minmax(0, 1.08fr);
  overflow: hidden;
}

.login-brand {
  position: relative;
  isolation: isolate;
  padding: 48px 40px;
  color: var(--hm-white);
  background:
    radial-gradient(circle at 90% 8%, rgba(255, 255, 255, 0.2) 0 72px, transparent 73px),
    radial-gradient(circle at 4% 96%, rgba(255, 255, 255, 0.1) 0 110px, transparent 111px),
    linear-gradient(145deg, #f06b36 0%, var(--hm-primary) 52%, #c93f12 100%);
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.brand-badge {
  align-self: flex-start;
  padding: 5px 12px;
  border: 1px solid rgba(255, 255, 255, 0.35);
  border-radius: var(--hm-radius-pill);
  background: rgba(255, 255, 255, 0.12);
  font-size: var(--hm-font-xs);
  letter-spacing: 1px;
}

.brand-mark {
  width: 62px;
  height: 62px;
  margin: 36px 0 22px;
  border-radius: 18px;
  background: var(--hm-white);
  color: var(--hm-primary);
  box-shadow: 0 12px 24px rgba(116, 42, 13, 0.18);
  display: grid;
  place-items: center;
  font-size: 30px;
  font-weight: 700;
}

/* 标题：24px，深色文字 */
.login-title {
  margin: 0 0 12px;
  color: var(--hm-white);
  font-size: 30px;
  line-height: 1.3;
  letter-spacing: 2px;
}

.brand-desc {
  max-width: 260px;
  margin: 0;
  color: rgba(255, 255, 255, 0.84);
  line-height: 1.9;
}

.brand-points {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
  margin-top: 42px;
  color: rgba(255, 255, 255, 0.88);
  font-size: var(--hm-font-xs);
}

.brand-points span::before {
  content: '';
  display: inline-block;
  width: 5px;
  height: 5px;
  margin-right: 6px;
  border-radius: 50%;
  background: #ffd9a8;
  vertical-align: 2px;
}

.login-content {
  box-sizing: border-box;
  padding: 48px 52px 40px;
}

.login-heading {
  margin-bottom: 28px;
}

.login-eyebrow {
  color: var(--hm-primary);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 2px;
}

.login-heading h1 {
  margin: 7px 0 8px;
  color: var(--hm-ink-900);
  font-size: 28px;
  line-height: 1.35;
}

/* 副标题 */
.login-subtitle {
  margin: 0;
  color: var(--hm-ink-500);
  font-size: var(--hm-font-base);
}

.login-content :deep(.el-form-item) {
  margin-bottom: 20px;
}

.login-content :deep(.el-input__wrapper) {
  min-height: 46px;
  padding: 1px 15px;
}

.login-content :deep(.el-input__prefix) {
  color: var(--hm-ink-400);
}

.login-content :deep(.remember-item) {
  margin: -4px 0 18px;
}

.login-content :deep(.remember-item .el-form-item__content) {
  line-height: 1;
}

.login-submit {
  height: 46px;
  border: none;
  font-size: 16px;
  letter-spacing: 4px;
  box-shadow: 0 8px 18px rgba(232, 80, 30, 0.22);
}

/* 底部链接容器 */
.login-footer {
  margin-top: 4px;
  padding-top: 20px;
  border-top: 1px solid var(--hm-ink-100);
  text-align: center;
  font-size: var(--hm-font-base);
  color: var(--hm-ink-500);
}

/* 注册链接样式 */
.register-link {
  margin-left: 4px;
  color: var(--hm-primary);
  font-weight: 600;
  text-decoration: none;
}

.register-link:hover {
  color: var(--hm-primary-active);
  text-decoration: underline;
}

@media (max-width: 1024px) {
  .login-card {
    width: min(820px, 100%);
  }

  .login-brand {
    padding: 40px 32px;
  }

  .login-content {
    padding: 44px 40px 36px;
  }
}

@media (max-width: 768px) {
  .login-page {
    padding: 28px 16px;
  }

  .login-card {
    width: min(520px, 100%);
    grid-template-columns: 1fr;
  }

  .login-brand {
    padding: 28px 32px;
  }

  .brand-mark {
    width: 48px;
    height: 48px;
    margin: 22px 0 14px;
    border-radius: 14px;
    font-size: 24px;
  }

  .login-title {
    font-size: 24px;
  }

  .brand-desc {
    max-width: none;
  }

  .brand-points {
    margin-top: 20px;
  }
}

@media (max-width: 480px) {
  .login-page {
    padding: 16px 12px;
    align-items: flex-start;
  }

  .login-card {
    border-radius: 18px;
  }

  .login-brand {
    padding: 24px;
  }

  .brand-badge,
  .brand-points,
  .brand-desc {
    display: none;
  }

  .brand-mark {
    margin: 0 0 14px;
  }

  .login-title {
    margin: 0;
    font-size: 22px;
  }

  .login-content {
    padding: 28px 24px 24px;
  }

  .login-heading {
    margin-bottom: 22px;
  }

  .login-heading h1 {
    font-size: 24px;
  }
}

/* ==================== Codex 深色登录页收口 ==================== */
.login-page {
  background:
    radial-gradient(circle at 12% 18%, rgba(139, 233, 176, 0.1), transparent 30%),
    radial-gradient(circle at 88% 82%, rgba(109, 200, 232, 0.07), transparent 32%),
    linear-gradient(rgba(255, 255, 255, 0.03) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.03) 1px, transparent 1px),
    var(--hm-bg-page);
  background-size: auto, auto, 32px 32px, 32px 32px, auto;
}
.login-card {
  background: var(--hm-bg-surface);
  border-color: var(--hm-ink-100);
  box-shadow: 0 30px 80px rgba(0, 0, 0, 0.46);
}
.login-brand {
  background:
    radial-gradient(circle at 90% 8%, rgba(139, 233, 176, 0.16) 0 72px, transparent 73px),
    radial-gradient(circle at 4% 96%, rgba(109, 200, 232, 0.1) 0 110px, transparent 111px),
    linear-gradient(145deg, #0a0d10 0%, #11161b 52%, #090b0e 100%);
  border-right: 1px solid var(--hm-ink-100);
}
.brand-mark {
  background: var(--hm-ink-050);
  color: var(--hm-primary);
  border: 1px solid var(--hm-primary-border);
  box-shadow: 0 0 30px rgba(139, 233, 176, 0.14);
}
.login-submit {
  color: var(--hm-on-primary);
  box-shadow: 0 8px 24px rgba(139, 233, 176, 0.18);
}
/* ==================== 经典灰白登录页 ==================== */
.login-page {
  background:
    radial-gradient(circle at 12% 18%, rgba(217, 79, 43, 0.06), transparent 30%),
    linear-gradient(180deg, #fafafa, #f3f3f4);
}
.login-card {
  background: var(--hm-bg-surface);
  border-color: var(--hm-ink-100);
  box-shadow: 0 18px 50px rgba(31, 35, 40, 0.1);
}
.login-brand {
  background:
    radial-gradient(circle at 90% 8%, rgba(217, 79, 43, 0.12) 0 72px, transparent 73px),
    linear-gradient(145deg, #fff8f5 0%, #fffdfc 55%, #f6f6f6 100%);
  border-right: 1px solid var(--hm-ink-100);
  color: var(--hm-ink-900);
}
.brand-mark {
  background: var(--hm-primary);
  color: var(--hm-on-primary);
  border: 1px solid var(--hm-primary);
  box-shadow: 0 6px 16px rgba(217, 79, 43, 0.16);
}
.login-title {
  color: var(--hm-ink-900);
}
.brand-desc,
.brand-points {
  color: var(--hm-ink-600);
}
.login-submit {
  background: var(--hm-primary);
  border-color: var(--hm-primary);
  color: var(--hm-on-primary);
  box-shadow: 0 4px 12px rgba(217, 79, 43, 0.18);
}
.login-submit:hover {
  background: var(--hm-primary-hover);
  border-color: var(--hm-primary-hover);
}
</style>
