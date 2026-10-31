<script setup>
/**
 * 管理后台：用户管理页
 *
 * 功能：搜索 / 分页查看用户列表、禁用/启用用户、重置用户密码
 * 数据来源：GET /api/admin/user/list + PUT /api/admin/user/status/{id} + PUT /api/admin/user/resetPwd/{id}
 *
 * 禁用后效果（后端拦截器实现）：
 * - 该用户下次登录提示"账号已被禁用"
 * - 该用户已登录的 token 访问任何需要登录的接口都会被 401 踢出
 */
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAdminUsers, updateAdminUserStatus, resetUserPassword } from '@/api/admin'

// ==================== 响应式状态 ====================

/** 用户列表数据 */
const userList = ref([])

/** 加载状态 */
const loading = ref(false)

/** 搜索关键词 */
const keyword = ref('')

/** 分页参数 */
const currentPage = ref(1)   // 当前页码
const pageSize = ref(10)     // 每页条数
const total = ref(0)         // 总条数

// ==================== 重置密码弹窗 ====================

/** 弹窗是否显示 */
const pwdDialogVisible = ref(false)

/** 弹窗提交 loading */
const submitting = ref(false)

/** 表单引用（用于校验） */
const pwdFormRef = ref(null)

/** 当前要重置密码的用户（整行数据，用来显示用户名） */
const currentUser = ref(null)

/** 重置密码表单 */
const pwdForm = reactive({
  password: ''   // 新密码
})

/** 重置密码表单校验规则（和后端 ResetPwdDTO 一致：至少 6 位） */
const pwdRules = {
  password: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' }
  ]
}

// ==================== 数据加载 ====================

/**
 * 加载用户列表（分页 + 可选搜索 username/phone）
 */
async function loadData() {
  loading.value = true
  try {
    const res = await getAdminUsers({
      keyword: keyword.value || undefined,
      page: currentPage.value,
      size: pageSize.value
    })
    userList.value = res.records || []
    total.value = res.total || 0
  } catch (err) {
    console.error('获取用户列表失败:', err)
  } finally {
    loading.value = false
  }
}

/**
 * 搜索（点击搜索按钮 / 回车）
 */
function handleSearch() {
  currentPage.value = 1
  loadData()
}

/**
 * 每页条数变化时：回到第一页重新加载
 */
function handleSizeChange() {
  currentPage.value = 1
  loadData()
}

// ==================== 禁用/启用 ====================

/**
 * 禁用/启用用户（二次确认）
 * 根据当前状态切换：正常(0)→禁用(1)，禁用(1)→启用(0)
 * 后端保护：不能禁用自己、不能禁用管理员账号（会返回错误提示）
 */
async function handleToggleStatus(row) {
  const newStatus = row.status === 0 ? 1 : 0
  const actionText = newStatus === 1 ? '禁用' : '启用'
  try {
    await ElMessageBox.confirm(
      `确定要${actionText}用户「${row.username}」吗？` +
      (newStatus === 1 ? '禁用后该用户将无法登录和操作。' : ''),
      `确认${actionText}`,
      {
        confirmButtonText: `确定${actionText}`,
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    await updateAdminUserStatus(row.id, newStatus)
    ElMessage.success(`用户已${actionText}`)
    await loadData()
  } catch {
    // 用户取消操作，不处理
  }
}

// ==================== 重置密码 ====================

/**
 * 打开重置密码弹窗
 */
function handleOpenResetPwd(row) {
  currentUser.value = row
  pwdForm.password = ''
  pwdDialogVisible.value = true
}

/**
 * 提交重置密码
 * 校验通过后调接口，成功后关弹窗提示
 */
async function handleSubmitPwd() {
  if (!pwdFormRef.value) return
  // 表单校验：校验失败（reject）→ 返回 false 阻止提交（code-review 修复）
  const valid = await pwdFormRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    await resetUserPassword(currentUser.value.id, pwdForm.password)
    ElMessage.success(`已重置用户「${currentUser.value.username}」的密码`)
    pwdDialogVisible.value = false
  } catch (err) {
    console.error('重置密码失败:', err)
  } finally {
    submitting.value = false
  }
}

// ==================== 生命周期 ====================

onMounted(() => {
  loadData()
})
</script>

<template>
  <div class="user-page">
    <!-- ==================== 页面标题 + 工具栏 ==================== -->
    <div class="hm-page-header">
      <div>
        <h1 class="hm-page-header__title">用户管理</h1>
        <p class="hm-page-header__desc">查询注册用户，维护账号状态与登录密码</p>
      </div>
      <span class="user-summary">共 {{ total }} 位用户</span>
    </div>

    <el-card class="filter-card" shadow="never">
      <div class="filter-toolbar">
        <div class="filter-copy">
          <strong>用户检索</strong>
          <span>支持用户名或手机号查询</span>
        </div>
        <div class="toolbar-right">
          <!-- 搜索框（用户名/手机号） -->
          <el-input
            v-model="keyword"
            class="search-input"
            placeholder="搜索用户名/手机号"
            clearable
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          />
          <el-button type="primary" @click="handleSearch">搜索</el-button>
        </div>
      </div>
    </el-card>

    <!-- ==================== 用户表格 ==================== -->
    <el-card class="data-card" shadow="never">
      <template #header>
        <div class="data-card__head">
          <div>
            <h2>用户列表</h2>
            <p>查看用户角色、状态及注册信息</p>
          </div>
          <span class="record-count">{{ total }} 条记录</span>
        </div>
      </template>
      <div class="table-scroll">
      <el-table :data="userList" v-loading="loading" empty-text="暂无用户">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="username" label="用户名" min-width="150">
          <template #default="{ row }">
            <div class="user-identity">
              <span class="user-avatar">{{ row.username?.charAt(0) }}</span>
              <span class="user-name">{{ row.username }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="phone" label="手机号" width="140">
          <!-- 没填手机号显示占位符 -->
          <template #default="{ row }">{{ row.phone || '-' }}</template>
        </el-table-column>
        <el-table-column label="角色" width="100">
          <!-- 管理员用金色标签突出 -->
          <template #default="{ row }">
            <el-tag :type="row.role === 'ADMIN' ? 'warning' : 'info'" size="small" effect="light" round>
              {{ row.role === 'ADMIN' ? '管理员' : '用户' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <!-- 正常/禁用标签 -->
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'success' : 'danger'" size="small" effect="light" round>
              {{ row.status === 0 ? '正常' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="注册时间" min-width="180" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <div class="action-group">
              <!-- 禁用/启用：管理员账号不显示按钮（后端也会拦截，这里隐藏更友好） -->
              <template v-if="row.role !== 'ADMIN'">
                <el-button link :type="row.status === 0 ? 'danger' : 'success'" class="action-button" @click="handleToggleStatus(row)">
                  {{ row.status === 0 ? '禁用' : '启用' }}
                </el-button>
              </template>
              <!-- 重置密码 -->
              <el-button link type="primary" class="action-button" @click="handleOpenResetPwd(row)">重置密码</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      </div>

      <!-- ==================== 分页 ==================== -->
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 30, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="loadData"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>

    <!-- ==================== 重置密码弹窗 ==================== -->
    <el-dialog v-model="pwdDialogVisible" class="password-dialog" title="重置密码" width="420px" destroy-on-close>
      <div class="dialog-intro">为当前用户设置新密码，至少输入 6 位字符</div>
      <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="80px">
        <el-form-item label="用户名">
          <el-input :model-value="currentUser?.username" disabled />
        </el-form-item>
        <el-form-item label="新密码" prop="password">
          <el-input v-model="pwdForm.password" type="password" placeholder="请输入新密码（至少 6 位）" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="pwdDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="handleSubmitPwd">确认重置</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
/* ==================== 工具栏（标题 + 右侧操作） ==================== */
.user-page .hm-page-header {
  margin-bottom: var(--hm-space-5);
}
.user-summary,
.record-count {
  padding: 5px 11px;
  border: 1px solid var(--hm-primary-border);
  border-radius: var(--hm-radius-pill);
  background: var(--hm-primary-soft);
  color: var(--hm-primary);
  font-size: var(--hm-font-xs);
  white-space: nowrap;
}
.filter-card {
  margin-bottom: var(--hm-space-4);
  border-radius: var(--hm-radius-lg);
  background: linear-gradient(110deg, var(--hm-white), var(--hm-bg-subtle));
}
.filter-card :deep(.el-card__body) {
  padding: var(--hm-space-4) var(--hm-space-5);
}
.filter-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--hm-space-4);
}
.filter-copy {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.filter-copy strong {
  color: var(--hm-ink-800);
  font-size: var(--hm-font-sm);
}
.filter-copy span {
  color: var(--hm-ink-500);
  font-size: var(--hm-font-xs);
}
.toolbar-right {
  display: flex;
  align-items: center;
  gap: var(--hm-space-2);
}
.search-input {
  width: 250px;
}
.search-input :deep(.el-input__wrapper) {
  background: var(--hm-white);
}
.data-card {
  border-radius: var(--hm-radius-lg);
}
.data-card :deep(.el-card__header) {
  padding: var(--hm-space-4) var(--hm-space-5);
  border-bottom-color: var(--hm-ink-100);
}
.data-card :deep(.el-card__body) {
  padding: 0;
}
.data-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--hm-space-3);
}
.data-card__head h2 {
  margin: 0;
  color: var(--hm-ink-900);
  font-size: var(--hm-font-md);
}
.data-card__head p {
  margin: 4px 0 0;
  color: var(--hm-ink-500);
  font-size: var(--hm-font-xs);
}

/* 表格容器：窄屏可横向滚动，避免列被挤压 */
.table-scroll {
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}
.table-scroll :deep(.el-table) {
  min-width: 840px; /* 低于此宽度列会挤成一团，宁可横向滚动 */
}
.table-scroll :deep(.el-table__inner-wrapper::before) {
  display: none;
}
.table-scroll :deep(.el-table th.el-table__cell) {
  height: 46px;
}
.table-scroll :deep(.el-table td.el-table__cell) {
  padding: 13px 0;
}
.user-identity {
  display: flex;
  align-items: center;
  gap: var(--hm-space-2);
}
.user-avatar {
  display: grid;
  place-items: center;
  flex: 0 0 32px;
  width: 32px;
  height: 32px;
  border-radius: 10px;
  background: var(--hm-primary-soft);
  color: var(--hm-primary);
  font-size: var(--hm-font-sm);
  font-weight: var(--hm-fw-bold);
}
.user-name {
  color: var(--hm-ink-800);
  font-weight: var(--hm-fw-medium);
}
.action-group {
  display: flex;
  align-items: center;
  gap: 2px;
}
.action-button {
  padding: 4px 6px;
  border-radius: var(--hm-radius-sm);
}
.action-button:hover {
  background: var(--hm-primary-soft);
}

/* 表格内的次要文字 */
.hm-cell-secondary {
  color: var(--hm-ink-500);
  font-size: var(--hm-font-sm);
}

/* 重置密码弹窗 */
.password-dialog :deep(.el-dialog__header) {
  margin: 0;
  padding: var(--hm-space-5);
  border-bottom: 1px solid var(--hm-ink-100);
  background: var(--hm-bg-subtle);
}
.password-dialog :deep(.el-dialog__body) {
  padding: var(--hm-space-5);
}
.dialog-intro {
  margin: -4px 0 var(--hm-space-5);
  padding: 10px 12px;
  border-radius: var(--hm-radius-sm);
  background: var(--hm-primary-soft);
  color: var(--hm-ink-600);
  font-size: var(--hm-font-xs);
}
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--hm-space-2);
}
.password-dialog :deep(.el-dialog__footer) {
  padding: var(--hm-space-4) var(--hm-space-5);
  border-top: 1px solid var(--hm-ink-100);
  background: var(--hm-bg-subtle);
}

/* 分页靠右 */
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  padding: var(--hm-space-4) var(--hm-space-5);
  border-top: 1px solid var(--hm-ink-100);
  background: var(--hm-bg-subtle);
  overflow-x: auto;
}

@media (max-width: 1024px) {
  .filter-toolbar {
    align-items: flex-start;
  }
  .search-input {
    width: 220px;
  }
}

@media (max-width: 768px) {
  .filter-toolbar {
    flex-direction: column;
    align-items: stretch;
  }
  .toolbar-right {
    width: 100%;
  }
  .search-input {
    flex: 1;
    width: auto;
    min-width: 0;
  }
  .data-card__head {
    align-items: flex-start;
  }
  .password-dialog {
    width: calc(100vw - 32px) !important;
  }
}

@media (max-width: 480px) {
  .user-summary,
  .record-count {
    display: none;
  }
  .filter-card :deep(.el-card__body),
  .data-card :deep(.el-card__header),
  .pagination-wrap {
    padding-left: var(--hm-space-4);
    padding-right: var(--hm-space-4);
  }
  .password-dialog :deep(.el-dialog__header),
  .password-dialog :deep(.el-dialog__body),
  .password-dialog :deep(.el-dialog__footer) {
    padding-left: var(--hm-space-4);
    padding-right: var(--hm-space-4);
  }
}

/* ==================== Codex 深色用户管理收口 ==================== */
.user-page {
  animation: hm-reveal 0.56s cubic-bezier(0.22, 1, 0.36, 1) both;
}
.filter-card,
.data-card {
  background: var(--hm-bg-surface);
  border-color: var(--hm-ink-100);
  box-shadow: none;
}
.filter-card {
  background:
    linear-gradient(110deg, rgba(139, 233, 176, 0.04), transparent 46%),
    var(--hm-bg-surface);
}
.search-input :deep(.el-input__wrapper) {
  background: var(--hm-ink-050);
}
.data-card__head h2,
.user-name {
  color: var(--hm-ink-900);
}
.data-card__head p,
.user-email {
  color: var(--hm-ink-500);
}
.data-card :deep(.el-card__header),
.data-card :deep(.el-card__body),
.pagination-wrap {
  background: transparent;
  border-color: var(--hm-ink-100);
}
.password-dialog :deep(.el-dialog__header),
.password-dialog :deep(.el-dialog__footer) {
  background: var(--hm-bg-subtle);
}
.password-dialog :deep(.el-dialog__body) {
  background: var(--hm-bg-surface);
}
</style>
