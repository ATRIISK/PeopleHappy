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
  <div>
    <!-- ==================== 页面标题 + 工具栏 ==================== -->
    <div class="toolbar">
      <h1 class="page-title">用户管理</h1>
      <div class="toolbar-right">
        <!-- 搜索框（用户名/手机号） -->
        <el-input
          v-model="keyword"
          placeholder="搜索用户名/手机号"
          clearable
          style="width: 220px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-button type="primary" @click="handleSearch">搜索</el-button>
      </div>
    </div>

    <!-- ==================== 用户表格 ==================== -->
    <el-card shadow="never">
      <el-table :data="userList" v-loading="loading" empty-text="暂无用户" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="username" label="用户名" min-width="140" />
        <el-table-column prop="phone" label="手机号" width="140">
          <!-- 没填手机号显示占位符 -->
          <template #default="{ row }">{{ row.phone || '-' }}</template>
        </el-table-column>
        <el-table-column label="角色" width="90">
          <!-- 管理员用金色标签突出 -->
          <template #default="{ row }">
            <el-tag :type="row.role === 'ADMIN' ? 'warning' : 'info'" size="small">
              {{ row.role === 'ADMIN' ? '管理员' : '用户' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <!-- 正常/禁用标签 -->
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'success' : 'danger'" size="small">
              {{ row.status === 0 ? '正常' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="注册时间" min-width="180" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <!-- 禁用/启用：管理员账号不显示按钮（后端也会拦截，这里隐藏更友好） -->
            <template v-if="row.role !== 'ADMIN'">
              <el-button link :type="row.status === 0 ? 'danger' : 'success'" @click="handleToggleStatus(row)">
                {{ row.status === 0 ? '禁用' : '启用' }}
              </el-button>
            </template>
            <!-- 重置密码 -->
            <el-button link type="primary" @click="handleOpenResetPwd(row)">重置密码</el-button>
          </template>
        </el-table-column>
      </el-table>

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
    <el-dialog v-model="pwdDialogVisible" title="重置密码" width="420px" destroy-on-close>
      <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="80px">
        <el-form-item label="用户名">
          <el-input :model-value="currentUser?.username" disabled />
        </el-form-item>
        <el-form-item label="新密码" prop="password">
          <el-input v-model="pwdForm.password" type="password" placeholder="请输入新密码（至少 6 位）" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmitPwd">确认重置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
/* 工具栏：标题 + 右侧搜索 */
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-title {
  font-size: 22px;
  font-weight: bold;
  color: #1a1a2e;
  margin: 0;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

/* 分页靠右 */
.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
