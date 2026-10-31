<template>
  <!-- 地址管理页面 -->
  <div class="address-manager">
    <div class="page-header hm-page-header">
      <div class="header-left">
        <div>
          <h2 class="hm-page-header__title">地址管理</h2>
          <p class="hm-page-header__desc">管理常用收货地址，让每一份期待准确抵达</p>
        </div>
      </div>
      <el-button type="primary" class="add-address-btn" @click="showAddDialog">
        <el-icon><Plus /></el-icon>添加新地址
      </el-button>
    </div>

    <!-- 地址列表 -->
    <div v-if="loading" class="loading-wrapper">
      <el-skeleton :rows="3" animated />
    </div>

    <div v-else-if="addressList.length === 0" class="empty-wrapper">
      <el-empty description="暂无地址，点击上方按钮添加" />
    </div>

    <div v-else class="address-list">
      <div
        v-for="item in addressList"
        :key="item.id"
        class="address-card"
        :class="{ 'is-default': item.isDefault === 1 }"
      >
        <div class="address-info">
          <div class="address-name">
            <span class="name">{{ item.name }}</span>
            <span class="phone">{{ item.phone }}</span>
            <!-- 默认地址标签 -->
            <el-tag v-if="item.isDefault === 1" type="primary" size="small" effect="light">默认地址</el-tag>
          </div>
          <div class="address-detail">
            {{ item.province }}{{ item.city }}{{ item.district ? item.district : '' }}{{ item.detail }}
          </div>
        </div>
        <div class="address-actions">
          <el-button text type="primary" @click="showEditDialog(item)">编辑</el-button>
          <el-button text type="danger" @click="handleDelete(item.id)">删除</el-button>
          <el-button
            v-if="item.isDefault !== 1"
            text
            type="default"
            @click="handleSetDefault(item)"
          >
            设为默认
          </el-button>
        </div>
      </div>
    </div>

    <!-- 添加/编辑地址弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEditing ? '编辑地址' : '新增地址'"
      width="520px"
      class="address-dialog"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="80px"
        class="address-form"
      >
        <el-form-item label="收件人" prop="name">
          <el-input v-model="form.name" placeholder="请输入收件人姓名" maxlength="20" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="请输入手机号" maxlength="11" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="省份" prop="province">
              <el-input v-model="form.province" placeholder="省" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="城市" prop="city">
              <el-input v-model="form.city" placeholder="市" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="区县" prop="district">
              <el-input v-model="form.district" placeholder="区/县（选填）" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="详细地址" prop="detail">
          <el-input
            v-model="form.detail"
            type="textarea"
            :rows="2"
            placeholder="请输入详细地址（街道、门牌号等）"
            maxlength="100"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="默认地址">
          <el-switch v-model="form.isDefault" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { getAddressList, addAddress, updateAddress, deleteAddress } from '@/api/address'

// ===== 状态 =====
const addressList = ref([])       // 地址列表
const loading = ref(false)        // 加载中
const saving = ref(false)         // 保存中
const dialogVisible = ref(false)  // 弹窗显示/隐藏
const isEditing = ref(false)      // 是否编辑模式
const formRef = ref(null)         // 表单 ref

// 表单数据
const form = reactive({
  id: null,
  name: '',
  phone: '',
  province: '',
  city: '',
  district: '',
  detail: '',
  isDefault: 0
})

// 表单校验规则（和后端一致）
const rules = {
  name: [{ required: true, message: '请输入收件人姓名', trigger: 'blur' }],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式错误', trigger: 'blur' }
  ],
  province: [{ required: true, message: '请输入省份', trigger: 'blur' }],
  city: [{ required: true, message: '请输入城市', trigger: 'blur' }],
  detail: [{ required: true, message: '请输入详细地址', trigger: 'blur' }]
}

// ===== 生命周期 =====
onMounted(async () => {
  await loadAddressList()
})

// ===== 方法 =====

/** 加载地址列表 */
async function loadAddressList() {
  loading.value = true
  try {
    const res = await getAddressList()
    addressList.value = res || []
  } catch (err) {
    console.error('获取地址列表失败:', err)
    ElMessage.error('获取地址列表失败')
  } finally {
    loading.value = false
  }
}

/** 显示新增弹窗 */
function showAddDialog() {
  isEditing.value = false
  form.id = null
  form.name = ''
  form.phone = ''
  form.province = ''
  form.city = ''
  form.district = ''
  form.detail = ''
  form.isDefault = 0
  dialogVisible.value = true
}

/** 显示编辑弹窗 */
function showEditDialog(item) {
  isEditing.value = true
  form.id = item.id
  form.name = item.name
  form.phone = item.phone
  form.province = item.province
  form.city = item.city
  form.district = item.district || ''
  form.detail = item.detail
  form.isDefault = item.isDefault
  dialogVisible.value = true
}

/** 保存（新增或更新） */
async function handleSave() {
  // 表单校验
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    if (isEditing.value) {
      // 修改地址
      await updateAddress({ ...form })
      ElMessage.success('地址修改成功')
    } else {
      // 新增地址
      await addAddress({ ...form })
      ElMessage.success('地址添加成功')
    }
    dialogVisible.value = false
    await loadAddressList()
  } catch (err) {
    console.error('保存地址失败:', err)
    ElMessage.error(err.message || '保存失败')
  } finally {
    saving.value = false
  }
}

/** 删除地址 */
async function handleDelete(id) {
  try {
    await ElMessageBox.confirm('确定要删除该地址吗？', '确认删除', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteAddress(id)
    ElMessage.success('地址已删除')
    await loadAddressList()
  } catch {
    // 用户取消删除，不做任何操作
  }
}

/** 设为默认地址 */
async function handleSetDefault(item) {
  try {
    await updateAddress({ id: item.id, isDefault: 1 })
    ElMessage.success('默认地址已更新')
    await loadAddressList()
  } catch (err) {
    ElMessage.error(err.message || '设置失败')
  }
}
</script>

<style scoped>
.address-manager {
  width: 100%;
  max-width: 980px;
  min-width: 0;
  box-sizing: border-box;
  margin: 0 auto;
  padding: 28px 20px 40px;
}

.page-header {
  align-items: center;
  margin-bottom: 24px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
  min-width: 0;
}

.page-header h2 {
  margin: 0;
}

.add-address-btn {
  height: 42px;
  padding: 0 20px;
  border-radius: var(--hm-radius-md);
  box-shadow: 0 6px 14px rgba(232, 80, 30, 0.18);
}

.loading-wrapper,
.empty-wrapper {
  padding: 36px;
  border: 1px solid var(--hm-ink-100);
  border-radius: var(--hm-radius-lg);
  background: var(--hm-white);
  box-shadow: var(--hm-shadow-sm);
}

.empty-wrapper {
  padding: 48px 20px;
}

.address-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.address-card {
  position: relative;
  min-width: 0;
  min-height: 178px;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 22px 22px 12px;
  border: 1px solid var(--hm-ink-200);
  border-radius: var(--hm-radius-lg);
  background: var(--hm-white);
  box-shadow: var(--hm-shadow-sm);
  overflow: hidden;
  transition: border-color var(--hm-transition), box-shadow var(--hm-transition), transform var(--hm-transition);
}

.address-card::before {
  content: '';
  position: absolute;
  inset: 0 auto 0 0;
  width: 4px;
  background: transparent;
}

.address-card.is-default {
  border-color: var(--hm-primary-border);
  background: linear-gradient(145deg, var(--hm-white) 55%, var(--hm-primary-soft));
  box-shadow: 0 8px 22px rgba(232, 80, 30, 0.1);
}

.address-card.is-default::before {
  background: var(--hm-primary);
}

.address-card:hover {
  border-color: var(--hm-primary-border);
  box-shadow: var(--hm-shadow-md);
  transform: translateY(-2px);
}

.address-info {
  flex: 1;
  min-width: 0;
}

.address-name {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 12px;
  margin-bottom: 14px;
}

.address-name .name {
  color: var(--hm-ink-900);
  font-size: 18px;
  font-weight: 600;
}

.address-name .phone {
  color: var(--hm-ink-500);
  font-size: var(--hm-font-base);
  font-variant-numeric: tabular-nums;
}

.address-detail {
  color: var(--hm-ink-600);
  font-size: var(--hm-font-base);
  line-height: 1.8;
  overflow-wrap: anywhere;
}

.address-actions {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
  flex-shrink: 0;
  margin: 18px -6px 0;
  padding-top: 8px;
  border-top: 1px solid var(--hm-ink-100);
}

.address-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.address-form :deep(.el-input__wrapper),
.address-form :deep(.el-textarea__inner) {
  background: var(--hm-ink-050);
}

:global(.address-dialog) {
  max-width: calc(100vw - 32px);
}

:global(.address-dialog .el-dialog__footer) {
  padding-top: 16px;
  border-top: 1px solid var(--hm-ink-100);
}

@media (max-width: 1024px) {
  .address-manager {
    max-width: 900px;
  }

  .address-card {
    padding: 20px 18px 12px;
  }
}

@media (max-width: 768px) {
  .address-manager {
    padding: 24px 16px 32px;
  }

  .page-header {
    align-items: flex-start;
  }

  .header-left {
    width: 100%;
  }

  .add-address-btn {
    align-self: flex-end;
  }

  .address-list {
    grid-template-columns: 1fr;
  }

  .address-card {
    min-height: 0;
  }
}

@media (max-width: 480px) {
  .address-manager {
    padding: 20px 12px 28px;
  }

  .header-left {
    align-items: flex-start;
    gap: 12px;
  }

  .add-address-btn {
    width: 100%;
  }

  .loading-wrapper {
    padding: 24px 18px;
  }

  .address-card {
    padding: 20px 18px 10px;
  }

  .address-actions {
    justify-content: flex-start;
  }

  .address-form :deep(.el-row) {
    display: block;
  }

  .address-form :deep(.el-col) {
    width: 100%;
    max-width: 100%;
    padding: 0 !important;
  }
}

/* ==================== Codex 深色地址页收口 ==================== */
.address-manager {
  background:
    radial-gradient(circle at 12% 0, rgba(139, 233, 176, 0.055), transparent 28%),
    transparent;
}
.address-card,
.loading-wrapper {
  background: var(--hm-bg-surface);
  border-color: var(--hm-ink-100);
  box-shadow: none;
}
.address-card {
  background:
    linear-gradient(145deg, var(--hm-bg-surface) 55%, rgba(139, 233, 176, 0.035));
}
.address-card:hover {
  border-color: var(--hm-primary-border);
  box-shadow: 0 16px 40px rgba(0, 0, 0, 0.3), 0 0 0 1px rgba(139, 233, 176, 0.06);
}
.address-actions {
  border-color: var(--hm-ink-100);
}
.add-address-btn {
  color: var(--hm-on-primary);
  box-shadow: 0 6px 20px rgba(139, 233, 176, 0.16);
}
/* ==================== 经典灰白地址页 ==================== */
.address-manager {
  background: transparent;
}
.address-card,
.loading-wrapper {
  background: var(--hm-bg-surface);
  border-color: var(--hm-ink-100);
  box-shadow: none;
}
.address-card {
  background: var(--hm-bg-surface);
}
.address-card:hover {
  border-color: var(--hm-ink-200);
  box-shadow: 0 8px 20px rgba(31, 35, 40, 0.07);
}
.address-actions {
  border-color: var(--hm-ink-100);
}
.add-address-btn {
  background: var(--hm-primary);
  border-color: var(--hm-primary);
  color: var(--hm-on-primary);
  box-shadow: none;
}
</style>
