<template>
  <!-- 订单详情页 -->
  <div class="order-detail">
    <!-- 加载中 -->
    <div v-if="loading" class="loading-wrapper">
      <el-skeleton :rows="5" animated />
    </div>

    <!-- 错误状态 -->
    <el-empty v-else-if="error" :description="error" />

    <!-- 订单详情 -->
    <template v-else-if="order">
      <!-- 订单头部 -->
      <div class="order-header">
        <div class="order-header-left">
          <h2>订单详情</h2>
          <span class="order-no">订单号：{{ order.orderNo }}</span>
        </div>
        <el-tag :type="statusType" size="large" effect="dark">
          {{ statusText }}
        </el-tag>
      </div>

      <!-- 订单信息卡片 -->
      <div class="info-card">
        <div class="info-row">
          <span class="info-label">创建时间</span>
          <span class="info-value">{{ order.createTime }}</span>
        </div>
        <div class="info-row">
          <span class="info-label">订单编号</span>
          <span class="info-value">{{ order.orderNo }}</span>
        </div>
      </div>

      <!-- ===== 收货地址卡片 ===== -->
      <div class="section-title">
        收货地址
        <!-- 待付款/待发货状态下可以修改地址 -->
        <el-button
          v-if="order.status === 0 || order.status === 1"
          text
          type="primary"
          size="small"
          style="margin-left: 12px;"
          @click="openAddressDialog"
        >
          修改地址
        </el-button>
      </div>
      <div class="address-card">
        <div class="address-info">
          <div class="address-name-row">
            <span class="addr-name">{{ order.addressName }}</span>
            <span class="addr-phone">{{ order.addressPhone }}</span>
          </div>
          <div class="addr-detail">{{ order.addressDetail }}</div>
        </div>
      </div>

      <!-- 商品列表 -->
      <div class="section-title">商品信息</div>
      <div class="product-list">
        <div v-for="item in order.items" :key="item.id" class="product-item">
          <el-image
            :src="item.productImage || '/placeholder.png'"
            class="product-image"
            fit="cover"
          />
          <div class="product-info">
            <div class="product-name">{{ item.productName }}</div>
            <div class="product-price">¥{{ item.price }} × {{ item.quantity }}</div>
          </div>
          <div class="product-subtotal">¥{{ (item.price * item.quantity).toFixed(2) }}</div>
        </div>
      </div>

      <!-- 订单合计 -->
      <div class="total-row">
        <span class="total-label">合计</span>
        <span class="total-amount">¥{{ order.totalAmount }}</span>
      </div>

      <!-- 操作按钮 -->
      <div class="action-bar">
        <el-button v-if="order.status === 0" type="primary" size="large" @click="handlePay">
          去支付
        </el-button>
        <el-button v-if="order.status === 0" size="large" @click="handleCancel">
          取消订单
        </el-button>
        <el-button v-if="order.status === 2" type="success" size="large" @click="handleConfirm">
          确认收货
        </el-button>
        <el-button size="large" @click="goBack">返回列表</el-button>
      </div>
    </template>
  </div>

  <!-- ==================== 修改地址弹窗 ==================== -->
  <el-dialog
    v-model="showAddressDialog"
    title="修改收货地址"
    width="520px"
    :close-on-click-modal="false"
    destroy-on-close
  >
    <!-- 加载中 -->
    <div v-if="addressLoading" class="addr-loading">
      <el-skeleton :rows="3" animated />
    </div>

    <!-- 地址列表 -->
    <div v-else class="addr-select-list">
      <div
        v-for="item in addressList"
        :key="item.id"
        class="addr-select-item"
        :class="{ 'is-active': selectedAddressId === item.id }"
        @click="selectedAddressId = item.id"
      >
        <el-radio :value="item.id" v-model="selectedAddressId">
          <div class="addr-info">
            <div class="addr-name-row">
              <span class="addr-name">{{ item.name }}</span>
              <span class="addr-phone">{{ item.phone }}</span>
              <el-tag v-if="item.isDefault === 1" type="danger" size="small" effect="plain">默认</el-tag>
            </div>
            <div class="addr-detail-text">
              {{ item.province }}{{ item.city }}{{ item.district ? item.district : '' }}{{ item.detail }}
            </div>
          </div>
        </el-radio>
      </div>
    </div>

    <!-- 底部按钮 -->
    <div class="addr-dialog-footer">
      <el-button @click="showAddressDialog = false">取消</el-button>
      <el-button type="primary" @click="handleConfirmAddressChange" :disabled="!selectedAddressId">
        确认修改
      </el-button>
    </div>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getOrderDetail, cancelOrder, confirmOrder, updateOrderAddress } from '@/api/order'
import { getAddressList } from '@/api/address'

const route = useRoute()
const router = useRouter()

// ===== 状态 =====
const order = ref(null)
const loading = ref(true)
const error = ref('')

// ===== 地址修改弹窗状态 =====
const showAddressDialog = ref(false)   // 地址选择弹窗
const addressList = ref([])            // 所有地址列表
const selectedAddressId = ref(null)    // 选中的地址ID
const addressLoading = ref(false)      // 地址加载中

// 状态映射（和 OrderList.vue 保持一致）
const STATUS_MAP = {
  0: { text: '待付款', type: 'danger' },
  1: { text: '已支付', type: 'warning' },
  2: { text: '已发货', type: 'primary' },
  3: { text: '已完成', type: 'success' },
  4: { text: '已取消', type: 'info' }
}

const statusText = computed(() => STATUS_MAP[order.value?.status]?.text || '未知')
const statusType = computed(() => STATUS_MAP[order.value?.status]?.type || 'info')

// ===== 生命周期 =====
onMounted(async () => {
  const orderId = route.params.id
  if (!orderId) {
    error.value = '订单ID不存在'
    loading.value = false
    return
  }
  await loadOrderDetail(orderId)
})

// ===== 方法 =====

/** 加载订单详情 */
async function loadOrderDetail(id) {
  loading.value = true
  error.value = ''
  try {
    const res = await getOrderDetail(id)
    order.value = res
  } catch (err) {
    console.error('获取订单详情失败:', err)
    error.value = err.message || '订单不存在'
  } finally {
    loading.value = false
  }
}

/** 去支付：跳转到支付宝扫码支付页面 */
function handlePay() {
  router.push({ name: 'Pay', params: { id: order.value.id } })
}

/** 取消订单 */
async function handleCancel() {
  try {
    await ElMessageBox.confirm('确定要取消该订单吗？', '确认取消', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await cancelOrder(order.value.id)
    ElMessage.success('订单已取消')
    // 重新加载订单数据
    await loadOrderDetail(order.value.id)
  } catch {
    // 用户取消操作
  }
}

/** 确认收货 */
async function handleConfirm() {
  try {
    await ElMessageBox.confirm('确定已收到商品吗？', '确认收货', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await confirmOrder(order.value.id)
    ElMessage.success('已确认收货')
    await loadOrderDetail(order.value.id)
  } catch {
    // 用户取消操作
  }
}

// ==================== 地址修改方法 ====================

/**
 * 打开地址选择弹窗
 * 加载所有地址，当前订单的地址预选中
 */
async function openAddressDialog() {
  addressLoading.value = true
  showAddressDialog.value = true
  try {
    const res = await getAddressList()
    addressList.value = res || []
    // 预选中当前订单的地址
    if (order.value?.addressId) {
      selectedAddressId.value = order.value.addressId
    } else if (addressList.value.length > 0) {
      selectedAddressId.value = addressList.value[0].id
    }
  } catch (err) {
    console.error('获取地址列表失败:', err)
    ElMessage.error('获取地址列表失败')
    showAddressDialog.value = false
  } finally {
    addressLoading.value = false
  }
}

/**
 * 确认修改地址
 * 调后端 API 更新订单的地址，然后刷新页面
 */
async function handleConfirmAddressChange() {
  if (!selectedAddressId.value) return

  try {
    await updateOrderAddress(order.value.id, selectedAddressId.value)
    ElMessage.success('收货地址已修改')
    showAddressDialog.value = false
    // 重新加载订单详情（刷新地址显示）
    await loadOrderDetail(order.value.id)
  } catch (err) {
    console.error('修改地址失败:', err)
    ElMessage.error(err.message || '修改地址失败，请重试')
  }
}

/** 返回订单列表 */
function goBack() {
  router.push({ name: 'Orders' })
}
</script>

<style scoped>
.order-detail {
  max-width: 800px;
  margin: 0 auto;
  padding: 20px;
}

.loading-wrapper {
  padding: 40px;
}

.order-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 2px solid #1a1a2e;
}

.order-header-left h2 {
  margin: 0 0 4px 0;
  font-size: 22px;
  color: #1a1a2e;
}

.order-no {
  font-size: 13px;
  color: #999;
}

.info-card {
  background: #f9f9f9;
  border-radius: 8px;
  padding: 16px 20px;
  margin-bottom: 24px;
}

.info-row {
  display: flex;
  padding: 6px 0;
  font-size: 14px;
}

.info-label {
  width: 80px;
  color: #999;
  flex-shrink: 0;
}

.info-value {
  color: #333;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: #1a1a2e;
  margin-bottom: 12px;
  padding-left: 10px;
  border-left: 3px solid #1a1a2e;
}

.product-list {
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  overflow: hidden;
  margin-bottom: 16px;
}

.product-item {
  display: flex;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid #f0f0f0;
}

.product-item:last-child {
  border-bottom: none;
}

.product-image {
  width: 80px;
  height: 80px;
  border-radius: 4px;
  flex-shrink: 0;
  margin-right: 16px;
  background: #f5f5f5;
}

.product-info {
  flex: 1;
}

.product-name {
  font-size: 15px;
  color: #333;
  margin-bottom: 6px;
}

.product-price {
  font-size: 13px;
  color: #999;
}

.product-subtotal {
  font-size: 16px;
  font-weight: 600;
  color: #e4393c;
  flex-shrink: 0;
  margin-left: 16px;
}

.total-row {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  padding: 16px 20px;
  background: #fafafa;
  border-radius: 8px;
  margin-bottom: 24px;
}

.total-label {
  font-size: 14px;
  color: #666;
  margin-right: 12px;
}

.total-amount {
  font-size: 22px;
  font-weight: bold;
  color: #e4393c;
}

.action-bar {
  display: flex;
  justify-content: center;
  gap: 12px;
  padding: 20px 0;
}

/* ==================== 收货地址卡片 ==================== */
.address-card {
  background: #f0f9ff;
  border: 1px solid #b3e0ff;
  border-radius: 8px;
  padding: 16px 20px;
  margin-bottom: 24px;
}

.address-info {
  line-height: 1.6;
}

.address-name-row {
  margin-bottom: 4px;
}

.address-card .addr-name {
  font-size: 15px;
  font-weight: 600;
  color: #333;
  margin-right: 10px;
}

.address-card .addr-phone {
  font-size: 14px;
  color: #666;
}

.address-card .addr-detail {
  font-size: 14px;
  color: #555;
}

/* ==================== 修改地址弹窗 ==================== */
.addr-loading {
  padding: 20px 0;
}

.addr-select-list {
  max-height: 320px;
  overflow-y: auto;
}

.addr-select-item {
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  padding: 12px 16px;
  margin-bottom: 10px;
  cursor: pointer;
  transition: all 0.2s;
}

.addr-select-item:hover {
  border-color: #c0c0c0;
}

.addr-select-item.is-active {
  border-color: #409eff;
  background: #f0f9ff;
}

.addr-info {
  display: inline-block;
  vertical-align: middle;
}

.addr-name {
  font-size: 15px;
  font-weight: 600;
  color: #333;
  margin-right: 10px;
}

.addr-phone {
  font-size: 14px;
  color: #666;
  margin-right: 10px;
}

.addr-detail-text {
  font-size: 13px;
  color: #999;
  line-height: 1.4;
}

.addr-dialog-footer {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 12px;
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid #f0f0f0;
}
</style>
