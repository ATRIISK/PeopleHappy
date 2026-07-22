# Task 4 报告 — 登录页 Login.vue

## 修改文件
- `src/views/front/Login.vue` — 从占位页面重写为完整的登录页面

## 实现内容

### 页面布局
- 全屏浅灰背景 (`#f0f2f5`)，flex 居中布局
- 独立于 FrontLayout（无导航栏），与注册页卡片风格一致
- 白色卡片：width 420px, bg #fff, border-radius 8px, padding 40px

### 表单字段
1. **用户名** — el-input, prefix-icon="User", 必填
2. **密码** — el-input type=password, prefix-icon="Lock", 必填, show-password
3. **记住我** — el-checkbox, 选填

### 交互逻辑
- **已登录自动跳转**：script setup 开头判断 `userStore.isLoggedIn`，已登录则 `router.replace('/')`
- **表单校验**：使用 el-form rules（用户名必填，密码必填且至少 6 位）
- **Loading 防重复提交**：`loading` 控制按钮 disabled 状态，catch 中重置
- **登录逻辑**：
  1. `loginFormRef.value.validate()` 同步校验
  2. `userStore.login({ username, password })` → POST /api/user/login
  3. 成功 `ElMessage.success('登录成功')`，跳转 `route.query.redirect` 或首页
  4. 失败（校验未通过 / 接口异常）：重置 loading，让用户重新尝试
- **底部链接**："没有账号？立即注册" → `/register`

### 样式
- 标题 h2 "众乐电子商城"，font-size 24px, color #1a1a2e
- 副标题 "欢迎回来，请登录您的账户"
- 提交按钮 100% 宽度
- 所有样式 scoped
