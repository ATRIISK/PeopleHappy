<script setup>
/**
 * 购物车页组件
 * 展示购物车商品列表，支持全选/勾选、数量修改、删除、结算操作
 * 套用 FrontLayout 布局，作为路由 /cart 的子页面
 */
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Plus } from '@element-plus/icons-vue'
import { useCartStore } from '@/stores/cart'
/**
 * 地址 API
 * getAddressList → 获取所有地址（下单时选地址用）
 * addAddress     → 新增地址（弹窗中直接添加新地址）
 */
import { getAddressList, addAddress } from '@/api/address'
// 数量规范化公共工具（与商品详情页共用，code-review 二轮修复去重）
import { normalizeQuantity } from '@/utils/quantity'

// 路由实例，用于跳转
const router = useRouter()

// ==================== Pinia 状态 ====================

/** 购物车 store，提供完整的 CRUD 方法 */
const cartStore = useCartStore()

// ==================== 响应式状态 ====================

/** 已勾选商品 ID 集合（存储 productId） */
const checkedIds = ref(new Set())

// ==================== 地址选择弹窗状态 ====================
// 点"去结算"时弹出，让用户手选收货地址

/** 地址选择弹窗是否显示 */
const showAddressDialog = ref(false)

/** 收货地址列表（从后端加载） */
const addressList = ref([])

/** 当前选中的地址 ID（默认地址预选中） */
const selectedAddressId = ref(null)

/** 地址列表加载中 */
const addressLoading = ref(false)

/**
 * 弹窗当前视图
 * 'select' → 地址列表选择（默认）
 * 'add'    → 新增地址表单
 */
const dialogView = ref('select')

/** 新增地址表单数据（和 AddressManager.vue 保持一致） */
const addrForm = reactive({
  name: '',
  phone: '',
  province: '',
  city: '',
  district: '',
  detail: '',
  isDefault: 0
})

/** 新增地址表单校验规则 */
const addrRules = {
  name: [{ required: true, message: '请输入收件人姓名', trigger: 'blur' }],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式错误', trigger: 'blur' }
  ],
  province: [{ required: true, message: '请输入省份', trigger: 'blur' }],
  city: [{ required: true, message: '请输入城市', trigger: 'blur' }],
  detail: [{ required: true, message: '请输入详细地址', trigger: 'blur' }]
}

/** 新增地址表单 ref（用于校验） */
const addrFormRef = ref(null)

/** 新增地址保存中 */
const addrSaving = ref(false)

// ==================== 计算属性 ====================

/** 购物车商品列表（简化引用） */
const items = computed(() => cartStore.items)

/** 是否全选：所有商品都被勾选且列表不为空 */
const isAllChecked = computed(() => {
  if (items.value.length === 0) return false
  return items.value.every(item => checkedIds.value.has(item.productId))
})

/** 已勾选的商品列表 */
const checkedItems = computed(() => {
  return items.value.filter(item => checkedIds.value.has(item.productId))
})

/** 已勾选商品数量 */
const checkedCount = computed(() => checkedItems.value.length)

/** 已勾选商品总金额 */
const checkedTotal = computed(() => {
  return checkedItems.value.reduce((sum, item) => sum + item.price * item.quantity, 0)
})

// ==================== 事件处理 ====================

/**
 * 切换单个商品的勾选状态
 * @param {number} productId - 商品ID
 */
function toggleCheck(productId) {
  const newSet = new Set(checkedIds.value)
  if (newSet.has(productId)) {
    newSet.delete(productId)
  } else {
    newSet.add(productId)
  }
  checkedIds.value = newSet
}

/**
 * 切换全选/取消全选
 * 全选时勾选所有商品，否则清空勾选
 */
function toggleAllCheck() {
  if (isAllChecked.value) {
    // 当前全选 -> 取消全选
    checkedIds.value = new Set()
  } else {
    // 当前未全选 -> 全部勾选
    checkedIds.value = new Set(items.value.map(item => item.productId))
  }
}

/**
 * 修改商品数量（选购商品数量）
 * <p>
 * 由数量列的 el-input-number 触发：支持两种方式改数量——
 * 1. 点击输入框直接输入数字（键盘输入，回车/失焦生效）
 * 2. 点击步进器 ± 加减
 * 输入值会被 el-input-number 自动钳制到 [min=1, max=商品库存] 范围，
 * 这里调用 cartStore.updateQuantity 同步到后端。
 * @param {Object} item - 购物车商品对象
 * @param {number} newQuantity - 新数量（已是钳制后的合法值）
 */
async function handleQuantityChange(item, newQuantity) {
  // 用公共工具（utils/quantity.js）规范化数量：取整 + 至少 1（不在此钳制库存）
  const qty = normalizeQuantity(newQuantity)

  // ★ 超过库存 → 提示"库存不足"，不静默钳制成最大库存数
  if (item.stock > 0 && qty > item.stock) {
    ElMessage.error(`「${item.name}」库存不足，仅剩 ${item.stock} 件`)
    return
  }

  try {
    // 同步到后端 PUT /api/cart/update
    await cartStore.updateQuantity(item.productId, qty)
  } catch (err) {
    console.error('修改数量失败:', err)
    ElMessage.error('修改数量失败，请稍后重试')
  }
}

/**
 * 删除单个商品
 * 弹出确认对话框，确认后调用 cartStore.removeItem
 * @param {Object} item - 要删除的购物车商品
 */
async function handleRemoveItem(item) {
  try {
    await ElMessageBox.confirm(`确定要从购物车中删除「${item.name}」吗？`, '确认删除', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await cartStore.removeItem(item.productId)
    // 同时从勾选集合中移除
    const newSet = new Set(checkedIds.value)
    newSet.delete(item.productId)
    checkedIds.value = newSet
    ElMessage.success(`已删除「${item.name}」`)
  } catch (err) {
    // 用户取消操作，不处理
  }
}

/**
 * 批量删除选中商品
 * 弹出确认对话框，确认后遍历调用 cartStore.removeItem
 */
async function handleBatchDelete() {
  if (checkedCount.value === 0) {
    ElMessage.warning('请先选择要删除的商品')
    return
  }

  try {
    await ElMessageBox.confirm(
      `确定要删除选中的 ${checkedCount.value} 件商品吗？`,
      '批量删除',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    // 遍历删除已勾选的商品
    const deletePromises = checkedItems.value.map(item =>
      cartStore.removeItem(item.productId)
    )
    await Promise.all(deletePromises)

    // 清空勾选集合
    checkedIds.value = new Set()
    ElMessage.success(`已成功删除 ${checkedCount.value} 件商品`)
  } catch (err) {
    // 用户取消操作，不处理
  }
}

/**
 * 去结算
 *
 * 流程：检查勾选 → 弹出地址选择弹窗 → 用户手选地址（或新增）→ 确认 → 创建订单
 *
 * 不再自动拿默认地址下单，而是让用户手动选择收货地址，
 * 默认地址只是预选中，用户可以换别的地址或者新增地址。
 */
async function handleCheckout() {
  // 检查是否勾选了商品
  if (checkedCount.value === 0) {
    ElMessage.warning('请先选择要结算的商品')
    return
  }

  // 打开地址选择弹窗，让用户手选地址
  await openAddressDialog()
}

// ==================== 地址选择弹窗方法 ====================

/**
 * 打开地址选择弹窗
 *
 * 1. 加载用户的地址列表
 * 2. 默认地址自动预选中（没有默认就选第一个）
 * 3. 显示弹窗让用户手动选择
 */
async function openAddressDialog() {
  addressLoading.value = true
  dialogView.value = 'select'      // 默认显示地址列表视图
  showAddressDialog.value = true   // 显示弹窗

  try {
    // 获取用户所有收货地址
    const res = await getAddressList()
    addressList.value = res || []

    // 如果有地址，默认选中"默认地址"
    // 没有默认地址就选第一个
    if (addressList.value.length > 0) {
      const defaultAddr = addressList.value.find(a => a.isDefault === 1)
      selectedAddressId.value = defaultAddr ? defaultAddr.id : addressList.value[0].id
    } else {
      // 一个地址都没有，selectedAddressId 置空
      selectedAddressId.value = null
    }
  } catch (err) {
    console.error('获取地址列表失败:', err)
    ElMessage.error('获取地址列表失败')
    showAddressDialog.value = false   // 出错就关掉弹窗
  } finally {
    addressLoading.value = false
  }
}

/**
 * 确认选中的地址 → 创建订单
 *
 * 用户选好地址后点"确认下单"，这里调后端接口创建订单。
 * 创建成功就跳转到订单详情页。
 */
async function handleConfirmAddress() {
  // 安全校验：没选地址不允许提交
  if (!selectedAddressId.value) {
    ElMessage.warning('请选择收货地址')
    return
  }

  try {
    // 动态导入 order API（和文件顶部 import 等价，延迟加载）
    const { createOrder } = await import('@/api/order')

    // 把 checkedIds（Set 类型）转成数组传给后端
    // 后端只处理这些商品，其他留在购物车里（上一轮已改好）
    const productIds = Array.from(checkedIds.value)

    // 调后端创建订单接口
    const order = await createOrder({
      addressId: selectedAddressId.value,  // 用户手选的地址
      productIds                           // 用户勾选的商品
    })

    // 关闭地址选择弹窗
    showAddressDialog.value = false
    // 清空购物车勾选状态
    checkedIds.value = new Set()

    ElMessage.success('订单创建成功！')
    // 跳转到订单详情页
    router.push({ name: 'OrderDetail', params: { id: order.id } })
  } catch (err) {
    console.error('创建订单失败:', err)
    ElMessage.error(err.message || '创建订单失败，请稍后重试')
  }
}

/**
 * 切换到新增地址视图
 *
 * 点击弹窗中的「+ 添加新地址」按钮时调用。
 * 把表单数据重置，然后切换到新增地址的表单视图。
 */
function handleAddNewAddress() {
  // 重置表单数据
  addrForm.name = ''
  addrForm.phone = ''
  addrForm.province = ''
  addrForm.city = ''
  addrForm.district = ''
  addrForm.detail = ''
  addrForm.isDefault = 0

  // 切换到新增地址视图
  dialogView.value = 'add'
}

/**
 * 保存新地址 → 刷新列表 → 自动选中新地址
 *
 * 用户在弹窗中填完地址表单点"保存并使用"时调用。
 * 保存成功后回到地址列表，新地址自动选中。
 */
async function handleSaveNewAddress() {
  // 表单校验（和 AddressManager.vue 规则一致）
  const valid = await addrFormRef.value.validate().catch(() => false)
  if (!valid) return

  addrSaving.value = true
  try {
    // 调后端新增地址 API
    await addAddress({ ...addrForm })
    ElMessage.success('地址添加成功')

    // 重新加载地址列表（新地址会自动选中）
    // openAddressDialog 会重新获取地址列表并选中默认地址
    // 但这里我们希望选中刚添加的地址，所以需要特殊处理
    await reloadAddressListAndSelectNew(addrForm.name, addrForm.phone)
  } catch (err) {
    console.error('添加地址失败:', err)
    ElMessage.error(err.message || '添加地址失败，请重试')
  } finally {
    addrSaving.value = false
  }
}

/**
 * 重新加载地址列表，并尝试选中刚添加的地址
 *
 * @param {string} newName  刚添加的收件人姓名（用于定位新地址）
 * @param {string} newPhone 刚添加的手机号
 */
async function reloadAddressListAndSelectNew(newName, newPhone) {
  addressLoading.value = true
  try {
    const res = await getAddressList()
    addressList.value = res || []

    // 尝试选中刚添加的地址（根据姓名+手机号匹配）
    // 如果匹配不到就选默认地址，再不行就选第一个
    const newAddr = addressList.value.find(
      a => a.name === newName && a.phone === newPhone
    )
    if (newAddr) {
      selectedAddressId.value = newAddr.id
    } else {
      const defaultAddr = addressList.value.find(a => a.isDefault === 1)
      selectedAddressId.value = defaultAddr ? defaultAddr.id : addressList.value[0]?.id
    }

    // 切回地址选择视图
    dialogView.value = 'select'
  } catch (err) {
    console.error('刷新地址列表失败:', err)
    ElMessage.error('刷新地址列表失败')
  } finally {
    addressLoading.value = false
  }
}

/**
 * 去地址管理页面
 *
 * 跳转到 AddressManager，用户可以编辑/删除地址。
 * 回来后需要重新点"去结算"。
 */
function goToAddressManager() {
  showAddressDialog.value = false    // 关掉弹窗
  router.push({ name: 'AddressManager' })
}

/**
 * 去逛逛：跳转到商品列表页
 */
function goShopping() {
  router.push({ name: 'Products' })
}

/**
 * 继续购物：跳转到商品列表页
 */
function continueShopping() {
  router.push({ name: 'Products' })
}

// ==================== 生命周期 ====================

// 组件挂载时获取购物车数据
onMounted(async () => {
  try {
    await cartStore.fetchCart()
  } catch (err) {
    console.error('获取购物车数据失败:', err)
    ElMessage.error('获取购物车数据失败，请稍后重试')
  }
})
</script>

<template>
  <div class="cart-page">
    <!-- ==================== 页面标题 ==================== -->
    <h1 class="page-title">我的购物车</h1>

    <!-- ==================== 空状态 ==================== -->
    <div v-if="items.length === 0" class="empty-state">
      <el-empty description="购物车是空的">
        <el-button type="primary" @click="goShopping">
          去逛逛
        </el-button>
      </el-empty>
    </div>

    <!-- ==================== 购物车内容区域 ==================== -->
    <template v-else>
      <div class="cart-table-wrapper">
        <!-- ===== 购物车表格 ===== -->
        <table class="cart-table">
          <!-- 表头行 -->
          <thead>
            <tr class="table-header">
              <th class="col-check">
                <el-checkbox
                  :model-value="isAllChecked"
                  :indeterminate="checkedCount > 0 && !isAllChecked"
                  @change="toggleAllCheck"
                />
              </th>
              <th class="col-info">商品</th>
              <th class="col-price">单价</th>
              <th class="col-quantity">数量</th>
              <th class="col-subtotal">小计</th>
              <th class="col-action">操作</th>
            </tr>
          </thead>

          <!-- 商品列表体 -->
          <tbody>
            <tr
              v-for="item in items"
              :key="item.productId"
              class="cart-item-row"
              :class="{
                'is-checked': checkedIds.has(item.productId)
              }"
            >
              <!-- 复选框列 -->
              <td class="col-check">
                <el-checkbox
                  :model-value="checkedIds.has(item.productId)"
                  @change="toggleCheck(item.productId)"
                />
              </td>

              <!-- 商品信息：缩略图 + 名称 -->
              <td class="col-info">
                <div class="product-info">
                  <el-image
                    :src="item.image"
                    :alt="item.name"
                    class="product-thumb"
                    fit="cover"
                  />
                  <span class="product-name">{{ item.name }}</span>
                </div>
              </td>

              <!-- 单价列 -->
              <td class="col-price">
                <span class="price">¥{{ item.price.toFixed(2) }}</span>
              </td>

              <!-- 数量列：el-input-number 支持直接输入数字 + 步进加减 -->
              <!-- :precision="0" 强制整数（防小数传后端 500）；不设 max 钳制——
                   超过库存的数量由 handleQuantityChange 校验并提示"库存不足"；
                   无库存（stock<=0）时禁用输入 -->
              <td class="col-quantity">
                <el-input-number
                  :model-value="item.quantity"
                  :min="1"
                  :disabled="item.stock <= 0"
                  :precision="0"
                  value-on-clear="1"
                  size="small"
                  controls-position="right"
                  @change="(val) => handleQuantityChange(item, val)"
                />
              </td>

              <!-- 小计列 -->
              <td class="col-subtotal">
                <span class="subtotal">¥{{ (item.price * item.quantity).toFixed(2) }}</span>
              </td>

              <!-- 操作列 -->
              <td class="col-action">
                <el-button
                  type="danger"
                  :icon="Delete"
                  size="small"
                  text
                  @click="handleRemoveItem(item)"
                >
                  删除
                </el-button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- ==================== 底部操作栏 ==================== -->
      <div class="cart-footer">
        <!-- 左侧操作区 -->
        <div class="footer-left">
          <el-checkbox
            :model-value="isAllChecked"
            :indeterminate="checkedCount > 0 && !isAllChecked"
            @change="toggleAllCheck"
          >
            全选
          </el-checkbox>
          <el-button
            type="danger"
            size="small"
            text
            :disabled="checkedCount === 0"
            @click="handleBatchDelete"
          >
            删除选中（{{ checkedCount }}）
          </el-button>
          <el-button size="small" plain @click="continueShopping">
            ← 继续购物
          </el-button>
        </div>

        <!-- 右侧结算区 -->
        <div class="footer-right">
          <span class="total-label">合计：</span>
          <span class="total-amount">¥{{ checkedTotal.toFixed(2) }}</span>
          <el-button
            type="danger"
            size="large"
            :disabled="checkedCount === 0"
            @click="handleCheckout"
          >
            去结算
          </el-button>
        </div>
      </div>
    </template>
  </div>

  <!-- ==================== 地址选择弹窗 ==================== -->
  <!--
    点"去结算"后弹出，让用户手选收货地址。
    默认地址预选中，也可以新增地址或去管理页编辑。
  -->
  <el-dialog
    v-model="showAddressDialog"
    title="选择收货地址"
    width="560px"
    :close-on-click-modal="false"
    destroy-on-close
  >
    <!-- ===== 视图1：地址列表选择 ===== -->
    <template v-if="dialogView === 'select'">
      <!-- 加载中骨架屏 -->
      <div v-if="addressLoading" class="addr-loading">
        <el-skeleton :rows="3" animated />
      </div>

      <!-- 无地址空状态 -->
      <el-empty
        v-else-if="addressList.length === 0"
        description="还没有收货地址，请先添加"
      />

      <!-- 地址列表（radio 选择） -->
      <div v-else class="addr-select-list">
        <div
          v-for="item in addressList"
          :key="item.id"
          class="addr-select-item"
          :class="{ 'is-active': selectedAddressId === item.id }"
          @click="selectedAddressId = item.id"
        >
          <el-radio :value="item.id" v-model="selectedAddressId" class="addr-radio">
            <div class="addr-info">
              <!-- 收件人 + 手机号 + 默认标签 -->
              <div class="addr-name-row">
                <span class="addr-name">{{ item.name }}</span>
                <span class="addr-phone">{{ item.phone }}</span>
                <el-tag
                  v-if="item.isDefault === 1"
                  type="danger"
                  size="small"
                  effect="plain"
                >
                  默认
                </el-tag>
              </div>
              <!-- 详细地址 -->
              <div class="addr-detail-text">
                {{ item.province }}{{ item.city }}{{ item.district ? item.district : '' }}{{ item.detail }}
              </div>
            </div>
          </el-radio>
        </div>
      </div>

      <!-- 弹窗底部操作栏 -->
      <div class="addr-dialog-footer">
        <div class="footer-left">
          <el-button text type="primary" @click="handleAddNewAddress">
            <el-icon><Plus /></el-icon>添加新地址
          </el-button>
          <el-button text @click="goToAddressManager">
            管理地址
          </el-button>
        </div>
        <div class="footer-right">
          <el-button @click="showAddressDialog = false">取消</el-button>
          <el-button
            type="danger"
            :disabled="!selectedAddressId"
            @click="handleConfirmAddress"
          >
            确认下单
          </el-button>
        </div>
      </div>
    </template>

    <!-- ===== 视图2：新增地址表单 ===== -->
    <template v-else>
      <el-form
        ref="addrFormRef"
        :model="addrForm"
        :rules="addrRules"
        label-width="80px"
        class="addr-form"
      >
        <!-- 收件人 -->
        <el-form-item label="收件人" prop="name">
          <el-input v-model="addrForm.name" placeholder="请输入收件人姓名" maxlength="20" />
        </el-form-item>
        <!-- 手机号 -->
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="addrForm.phone" placeholder="请输入手机号" maxlength="11" />
        </el-form-item>
        <!-- 省/市/区 -->
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="省份" prop="province">
              <el-input v-model="addrForm.province" placeholder="省" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="城市" prop="city">
              <el-input v-model="addrForm.city" placeholder="市" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="区县" prop="district">
              <el-input v-model="addrForm.district" placeholder="区/县（选填）" />
            </el-form-item>
          </el-col>
        </el-row>
        <!-- 详细地址 -->
        <el-form-item label="详细地址" prop="detail">
          <el-input
            v-model="addrForm.detail"
            type="textarea"
            :rows="2"
            placeholder="请输入详细地址（街道、门牌号等）"
            maxlength="100"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <!-- 新增地址底部按钮 -->
      <div class="addr-dialog-footer">
        <div class="footer-left">
          <el-button @click="dialogView = 'select'">返回</el-button>
        </div>
        <div class="footer-right">
          <el-button type="primary" @click="handleSaveNewAddress" :loading="addrSaving">
            保存并使用
          </el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
/* ==================== 页面容器 ==================== */
.cart-page {
  background: #fff;
  border-radius: 8px;
  padding: 24px;
  /* 父级 main-content 已有 20px 内边距 */
}

/* ==================== 页面标题 ==================== */
.page-title {
  font-size: 22px;
  font-weight: bold;
  color: #1a1a2e;
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 2px solid #f0f0f0;
}

/* ==================== 空状态 ==================== */
.empty-state {
  padding: 80px 0;
  display: flex;
  justify-content: center;
}

/* ==================== 购物车表格 ==================== */
.cart-table-wrapper {
  overflow-x: auto;
}

.cart-table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
}

/* 表头行 */
.table-header {
  background: #fafafa;
  border-bottom: 1px solid #e8e8e8;
}

.table-header th {
  padding: 14px 12px;
  font-size: 14px;
  font-weight: 600;
  color: #666;
  text-align: center;
  white-space: nowrap;
}

/* 列宽分配 */
.col-check {
  width: 60px;
  text-align: center;
}

.col-info {
  width: auto;
  text-align: left;
}

.col-price {
  width: 120px;
}

.col-quantity {
  width: 160px;
}

.col-subtotal {
  width: 120px;
}

.col-action {
  width: 100px;
}

/* 商品行 */
.cart-item-row {
  border-bottom: 1px solid #f0f0f0;
  transition: background-color 0.2s ease;
}

/* hover 背景色 */
.cart-item-row:hover {
  background-color: #fafafa;
}

/* 已勾选行背景色 */
.cart-item-row.is-checked {
  background-color: #f0f9ff;
}

/* 已勾选且 hover */
.cart-item-row.is-checked:hover {
  background-color: #e6f4ff;
}

.cart-item-row td {
  padding: 16px 12px;
  vertical-align: middle;
  text-align: center;
}

/* ===== 商品信息区域 ===== */
.product-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

/* 商品缩略图 80×80 */
.product-thumb {
  width: 80px;
  height: 80px;
  border-radius: 6px;
  border: 1px solid #eee;
  flex-shrink: 0;
  object-fit: cover;
}

/* 商品名称 */
.product-name {
  font-size: 14px;
  color: #333;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  text-overflow: ellipsis;
  text-align: left;
  flex: 1;
  min-width: 0;
}

/* ===== 单价 ===== */
.price {
  font-size: 14px;
  color: #666;
  font-weight: 500;
}

/* ===== 小计 ===== */
.subtotal {
  font-size: 15px;
  color: #e4393c;
  font-weight: 600;
}

/* ==================== 底部操作栏 ==================== */
.cart-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 0;
  margin-top: 20px;
  border-top: 2px solid #f0f0f0;
}

/* 左侧操作区 */
.footer-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

/* 右侧结算区 */
.footer-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

/* 合计文字标签 */
.total-label {
  font-size: 16px;
  color: #333;
}

/* 合计金额：红色 24px 加粗 */
.total-amount {
  font-size: 24px;
  font-weight: bold;
  color: #e4393c;
  line-height: 1;
}

/* 去结算按钮 */
.footer-right .el-button--large {
  padding: 12px 32px;
  font-size: 16px;
}

/* ==================== 地址选择弹窗 ==================== */
/* 加载中骨架屏 */
.addr-loading {
  padding: 20px 0;
}

/* 地址列表容器（可滚动） */
.addr-select-list {
  max-height: 320px;
  overflow-y: auto;
}

/* 单个地址项（可点击卡片） */
.addr-select-item {
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  padding: 12px 16px;
  margin-bottom: 10px;
  cursor: pointer;
  transition: all 0.2s;
}

/* 地址项 hover */
.addr-select-item:hover {
  border-color: #c0c0c0;
}

/* 选中的地址项（红色边框 + 浅红背景） */
.addr-select-item.is-active {
  border-color: #e4393c;
  background: #fff5f5;
}

/* 地址信息容器 */
.addr-info {
  display: inline-block;
  vertical-align: middle;
}

/* 收件人 + 手机号行 */
.addr-name-row {
  margin-bottom: 4px;
}

/* 收件人姓名 */
.addr-name {
  font-size: 15px;
  font-weight: 600;
  color: #333;
  margin-right: 10px;
}

/* 手机号 */
.addr-phone {
  font-size: 14px;
  color: #666;
  margin-right: 10px;
}

/* 详细地址文字 */
.addr-detail-text {
  font-size: 13px;
  color: #999;
  line-height: 1.4;
}

/* 弹窗底部操作栏 */
.addr-dialog-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid #f0f0f0;
}

/* 弹窗底部左侧（添加新地址等） */
.addr-dialog-footer .footer-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 弹窗底部右侧（确认按钮等） */
.addr-dialog-footer .footer-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

/* 新增地址表单内边距 */
.addr-form {
  padding: 0 8px;
}
</style>
