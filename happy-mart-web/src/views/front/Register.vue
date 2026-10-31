<script setup>
// ==================== 导入 ====================
import { ref, reactive } from 'vue'                      // Vue 响应式 API
import { useRouter } from 'vue-router'                   // 路由跳转
import { ElMessage } from 'element-plus'                  // Element Plus 消息提示
import { User, Phone, Lock } from '@element-plus/icons-vue' // 图标组件
import { useUserStore } from '@/stores/user'              // 用户状态管理（Pinia）

// ==================== 路由 & 状态 ====================
const router = useRouter()                               // 路由实例
const userStore = useUserStore()                         // 用户 Store 实例

// ==================== 表单响应式变量 ====================
const registerFormRef = ref(null)                        // 注册表单的 el-form 引用，用于调用 validate
const loading = ref(false)                               // 提交按钮 loading 状态，防重复提交

// 注册表单数据模型
const registerForm = reactive({
  username: '',         // 用户名
  phone: '',            // 手机号（选填）
  password: '',         // 密码
  confirmPassword: ''   // 确认密码
})

// ==================== 自定义校验函数 ====================
// 确认密码校验器：校验两次输入的密码是否一致
const validateConfirmPassword = (_rule, value, callback) => {
  if (value === '') {
    callback(new Error('请再次输入密码'))
  } else if (value !== registerForm.password) {
    callback(new Error('两次输入密码不一致'))
  } else {
    callback()
  }
}

// ==================== 表单校验规则 ====================
const rules = {
  // 用户名：必填，长度 2-20 个字符
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 2, max: 20, message: '用户名长度在 2 到 20 个字符', trigger: 'blur' }
  ],
  // 手机号：选填，若填写则需符合手机号格式
  phone: [
    {
      pattern: /^1[3-9]\d{9}$/,
      message: '请输入正确的手机号格式',
      trigger: 'blur'
    }
  ],
  // 密码：必填，至少 6 位
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' }
  ],
  // 确认密码：必填，且需与密码一致（使用自定义校验器）
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ]
}

// ==================== 提交注册 ====================
async function handleRegister() {
  // 确保表单引用已挂载
  if (!registerFormRef.value) return

  // 开启 loading，防止重复提交
  loading.value = true

  try {
    // 第一步：表单校验（同步校验失败会抛出异常）
    await registerFormRef.value.validate()

    // 第二步：调用注册接口 POST /api/user/register
    // userStore.register 返回 Promise，Mock 数据函数亦是异步的
    await userStore.register({
      username: registerForm.username,
      password: registerForm.password,
      phone: registerForm.phone
    })

    // 第三步：注册成功提示并跳转登录页
    ElMessage.success('注册成功！请登录')
    router.push('/login')
  } catch (error) {
    // 表单校验失败：validate() 内部已展示错误信息，无需额外处理
    // 接口异常（如 code=1001）：请求拦截器已统一展示 "该用户名已被注册"
    // 此处只重置 loading 状态
    loading.value = false
  }
}
</script>

<template>
  <!-- 全屏浅灰背景，flex 居中布局 -->
  <div class="register-page">
    <!-- 白色注册卡片 -->
    <div class="register-card">
      <section class="register-brand" aria-label="众乐电子商城品牌介绍">
        <span class="brand-badge">暖橙市集 · 品质生活</span>
        <div class="brand-mark" aria-hidden="true">众</div>
        <!-- 标题 -->
        <h2 class="register-title">众乐电子商城</h2>
        <p class="brand-desc">从这里开始，发现值得信赖的日常好物。</p>
        <div class="brand-note" aria-hidden="true">
          <span class="brand-note__icon">✓</span>
          <span>简单注册，开启轻松购物之旅</span>
        </div>
      </section>

      <section class="register-content">
        <div class="register-heading">
          <span class="register-eyebrow">JOIN THE MARKET</span>
          <h1>创建账户</h1>
          <p>填写基本信息，即可加入众乐市集</p>
        </div>

        <!-- 注册表单 -->
        <el-form
          ref="registerFormRef"
          :model="registerForm"
          :rules="rules"
          label-width="0"
          size="large"
          @keyup.enter="handleRegister"
        >
          <!-- 用户名输入框 -->
          <el-form-item prop="username">
            <el-input
              v-model="registerForm.username"
              placeholder="请输入用户名"
              :prefix-icon="User"
            />
          </el-form-item>

          <!-- 手机号输入框 -->
          <el-form-item prop="phone">
            <el-input
              v-model="registerForm.phone"
              placeholder="请输入手机号（选填）"
              :prefix-icon="Phone"
            />
          </el-form-item>

          <!-- 密码输入框 -->
          <el-form-item prop="password">
            <el-input
              v-model="registerForm.password"
              type="password"
              placeholder="请输入密码"
              :prefix-icon="Lock"
              show-password
            />
          </el-form-item>

          <!-- 确认密码输入框 -->
          <el-form-item prop="confirmPassword">
            <el-input
              v-model="registerForm.confirmPassword"
              type="password"
              placeholder="请再次输入密码"
              :prefix-icon="Lock"
              show-password
            />
          </el-form-item>

          <!-- 提交按钮：100% 宽度，loading 状态防重复提交 -->
          <el-form-item>
            <el-button
              type="primary"
              :loading="loading"
              style="width: 100%"
              class="register-submit"
              @click="handleRegister"
            >
              注册
            </el-button>
          </el-form-item>
        </el-form>

        <!-- 底部：已有账号，跳转登录页 -->
        <div class="register-footer">
          已有账号？
          <router-link to="/login" class="login-link">立即登录</router-link>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
/* 全屏浅灰背景，flex 居中 */
.register-page {
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

/* 白色卡片容器 */
.register-card {
  width: min(900px, 100%);
  background: var(--hm-white);
  border: 1px solid var(--hm-ink-100);
  border-radius: 24px;
  box-shadow: var(--hm-shadow-lg);
  display: grid;
  grid-template-columns: minmax(0, 0.92fr) minmax(0, 1.08fr);
  overflow: hidden;
}

.register-brand {
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
.register-title {
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

.brand-note {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 42px;
  padding: 14px;
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: var(--hm-radius-md);
  background: rgba(255, 255, 255, 0.1);
  color: rgba(255, 255, 255, 0.9);
  font-size: var(--hm-font-xs);
}

.brand-note__icon {
  width: 22px;
  height: 22px;
  flex-shrink: 0;
  border-radius: 50%;
  background: var(--hm-white);
  color: var(--hm-primary);
  display: grid;
  place-items: center;
  font-weight: 700;
}

.register-content {
  box-sizing: border-box;
  padding: 40px 52px 32px;
}

.register-heading {
  margin-bottom: 24px;
}

.register-eyebrow {
  color: var(--hm-primary);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 2px;
}

.register-heading h1 {
  margin: 7px 0 8px;
  color: var(--hm-ink-900);
  font-size: 28px;
  line-height: 1.35;
}

.register-heading p {
  margin: 0;
  color: var(--hm-ink-500);
}

.register-content :deep(.el-form-item) {
  margin-bottom: 17px;
}

.register-content :deep(.el-input__wrapper) {
  min-height: 44px;
  padding: 1px 15px;
}

.register-content :deep(.el-input__prefix) {
  color: var(--hm-ink-400);
}

.register-submit {
  height: 46px;
  margin-top: 3px;
  border: none;
  font-size: 16px;
  letter-spacing: 4px;
  box-shadow: 0 8px 18px rgba(232, 80, 30, 0.22);
}

/* 底部链接容器 */
.register-footer {
  margin-top: 2px;
  padding-top: 18px;
  border-top: 1px solid var(--hm-ink-100);
  text-align: center;
  font-size: var(--hm-font-base);
  color: var(--hm-ink-500);
}

/* 登录链接样式 */
.login-link {
  margin-left: 4px;
  color: var(--hm-primary);
  font-weight: 600;
  text-decoration: none;
}

.login-link:hover {
  color: var(--hm-primary-active);
  text-decoration: underline;
}

@media (max-width: 1024px) {
  .register-card {
    width: min(820px, 100%);
  }

  .register-brand {
    padding: 40px 32px;
  }

  .register-content {
    padding: 36px 40px 30px;
  }
}

@media (max-width: 768px) {
  .register-page {
    padding: 28px 16px;
  }

  .register-card {
    width: min(520px, 100%);
    grid-template-columns: 1fr;
  }

  .register-brand {
    padding: 28px 32px;
  }

  .brand-mark {
    width: 48px;
    height: 48px;
    margin: 22px 0 14px;
    border-radius: 14px;
    font-size: 24px;
  }

  .register-title {
    font-size: 24px;
  }

  .brand-desc {
    max-width: none;
  }

  .brand-note {
    align-self: flex-start;
    margin-top: 20px;
  }
}

@media (max-width: 480px) {
  .register-page {
    padding: 16px 12px;
    align-items: flex-start;
  }

  .register-card {
    border-radius: 18px;
  }

  .register-brand {
    padding: 24px;
  }

  .brand-badge,
  .brand-note,
  .brand-desc {
    display: none;
  }

  .brand-mark {
    margin: 0 0 14px;
  }

  .register-title {
    margin: 0;
    font-size: 22px;
  }

  .register-content {
    padding: 28px 24px 24px;
  }

  .register-heading {
    margin-bottom: 22px;
  }

  .register-heading h1 {
    font-size: 24px;
  }
}

/* ==================== Codex 深色注册页收口 ==================== */
.register-page {
  background:
    radial-gradient(circle at 12% 18%, rgba(139, 233, 176, 0.1), transparent 30%),
    radial-gradient(circle at 88% 82%, rgba(109, 200, 232, 0.07), transparent 32%),
    linear-gradient(rgba(255, 255, 255, 0.03) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.03) 1px, transparent 1px),
    var(--hm-bg-page);
  background-size: auto, auto, 32px 32px, 32px 32px, auto;
}
.register-card {
  background: var(--hm-bg-surface);
  border-color: var(--hm-ink-100);
  box-shadow: 0 30px 80px rgba(0, 0, 0, 0.46);
}
.register-brand {
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
.register-submit {
  color: var(--hm-on-primary);
  box-shadow: 0 8px 24px rgba(139, 233, 176, 0.18);
}
</style>
