<template>
  <!-- 地址管理页面 -->
  <div class="address-manager">
    <div class="page-header">
      <div class="header-left">
        <el-button text @click="goBack" class="back-btn">← 返回</el-button>
        <h2>地址管理</h2>
      </div>
      <el-button type="primary" @click="showAddDialog">
        <el-icon><Plus /></el-icon>添加新地址
      </el-button>
    </div>

    <!-- 地址列表 -->
    <div v-if="loading" class="loading-wrapper">
      <el-skeleton :rows="3" animated />
    </div>

    <el-empty v-else-if="addressList.length === 0" description="暂无地址，点击上方按钮添加" />

    <div v-else class="address-list">
      <div v-for="item in addressList" :key="item.id" class="address-card">
        <div class="address-info">
          <div class="address-name">
            <span class="name">{{ item.name }}</span>
            <span class="phone">{{ item.phone }}</span>
            <!-- 默认地址标签 -->
            <el-tag v-if="item.isDefault === 1" type="danger" size="small" effect="plain">默认</el-tag>
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
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { getAddressList, addAddress, updateAddress, deleteAddress } from '@/api/address'

// ===== 状态 =====
const router = useRouter()
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

/** 返回上一页 */
function goBack() {
  router.back()
}

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
  max-width: 800px;
  margin: 0 auto;
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.back-btn {
  font-size: 14px;
  color: #666;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  padding: 4px 12px;
}

.back-btn:hover {
  color: #1a1a2e;
  border-color: #1a1a2e;
  background: #f8f8f8;
}

.page-header h2 {
  margin: 0;
  font-size: 22px;
  color: #1a1a2e;
}

.loading-wrapper {
  padding: 40px;
}

.address-card {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 16px 20px;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  margin-bottom: 12px;
  transition: all 0.2s;
}

.address-card:hover {
  border-color: #c0c0c0;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.address-info {
  flex: 1;
}

.address-name {
  margin-bottom: 8px;
}

.address-name .name {
  font-size: 16px;
  font-weight: 600;
  color: #1a1a2e;
  margin-right: 12px;
}

.address-name .phone {
  font-size: 14px;
  color: #666;
  margin-right: 12px;
}

.address-detail {
  font-size: 14px;
  color: #666;
  line-height: 1.5;
}

.address-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
  margin-left: 16px;
}
</style>
