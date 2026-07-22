# Task 3 报告：注册页 Register.vue

## 完成情况

### 修改文件
- `E:\PeopleHappy\happy-mart-web\src\views\front\Register.vue` — 从占位内容重写为完整注册页面

### 实现要点

| 需求 | 实现方式 |
|------|----------|
| 布局 | 全屏浅灰背景（`#f0f2f5`），flex 居中，白色卡片（`420px`、`border-radius: 8px`、`padding: 40px`） |
| 独立于 FrontLayout | 路由配置中原 `/register` 即为独立路由，无需导航栏 |
| 用户名 | `el-input` + `prefix-icon="User"`，必填，2-20 字符校验 |
| 手机号 | `el-input` + `prefix-icon="Phone"`，选填，`/^1[3-9]\d{9}$/` 格式校验 |
| 密码 | `el-input type="password"` + `prefix-icon="Lock"`，必填，至少 6 位 |
| 确认密码 | `el-input type="password"` + `prefix-icon="Lock"`，必填，自定义 validator 校验一致性 |
| 表单校验 | `el-form rules` + 自定义 `validateConfirmPassword` 函数 |
| 提交 | 调用 `userStore.register({ username, password, phone })` → `POST /api/user/register` |
| 成功处理 | `ElMessage.success('注册成功！请登录')` → `router.push('/login')` |
| 失败处理 | 非200状态码由请求拦截器统一提示（code=1001 时后端返回 "该用户名已被注册"） |
| loading 状态 | `loading` 响应式变量控制按钮 `:loading`，`try/catch` 中异常时重置为 `false` |
| 底部链接 | `"已有账号？立即登录"` → `<router-link to="/login">` |
| 注释 | 所有 import、响应式变量、方法均有中文注释 |
| Mock 兼容 | 直接调用 store 方法，其返回 Promise，兼容异步 mock |

### 异常处理说明

- 表单校验不通过：`validate()` 抛出异常，校验器内已展示错误信息
- 接口业务异常（code != 200）：`src/utils/request.js` 响应拦截器拦截，调用 `ElMessage.error(res.message)` 并 `reject`
- 组件 `catch` 块仅重置 `loading = false`，避免重复提示
