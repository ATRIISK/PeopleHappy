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
      <!-- 标题 -->
      <h2 class="register-title">众乐电子商城</h2>

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
    </div>
  </div>
</template>

<style scoped>
/* 全屏浅灰背景，flex 居中 */
.register-page {
  min-height: 100vh;
  background: #f0f2f5;
  display: flex;
  justify-content: center;
  align-items: center;
}

/* 白色卡片容器 */
.register-card {
  width: 420px;
  background: #fff;
  border-radius: 8px;
  padding: 40px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

/* 标题：24px，深色文字 */
.register-title {
  font-size: 24px;
  color: #1a1a2e;
  text-align: center;
  margin-bottom: 30px;
}

/* 底部链接容器 */
.register-footer {
  text-align: center;
  margin-top: 16px;
  font-size: 14px;
  color: #666;
}

/* 登录链接样式 */
.login-link {
  color: #409eff;
  text-decoration: none;
}

.login-link:hover {
  text-decoration: underline;
}
</style>
