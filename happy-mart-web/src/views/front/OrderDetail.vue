<template>
  <!-- 订单详情页 -->
  <div class="order-detail">
    <!-- 加载中 -->
    <div v-if="loading" class="loading-wrapper">
      <el-skeleton :rows="5" animated />
    </div>

    <!-- 错误状态 -->
    <el-empty v-else-if="error" :description="error" class="detail-empty" />

    <!-- 订单详情 -->
    <template v-else-if="order">
      <!-- 订单头部 -->
      <div class="order-header">
        <div class="order-header-left">
          <h2>订单详情</h2>
          <span class="order-no">订单号：{{ order.orderNo }}</span>
        </div>
        <el-tag :type="statusType" size="large" effect="light" class="order-status-tag">
          {{ statusText }}
        </el-tag>
      </div>

      <!-- 订单信息卡片 -->
      <div class="info-card hm-card">
        <div class="info-row">
          <span class="info-label">创建时间</span>
          <span class="info-value">{{ order.createTime }}</span>
        </div>
        <div class="info-row">
          <span class="info-label">订单编号</span>
          <span class="info-value">{{ order.orderNo }}</span>
        </div>
        <!-- 支付超时倒计时：仅"待付款"订单显示，提示用户还剩多久会被系统自动取消 -->
        <div v-if="order.status === 0" class="countdown-tip">
          <el-icon><Clock /></el-icon>
          <span>剩余支付时间 <strong>{{ formatPayCountdown }}</strong>，超时自动取消</span>
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
      <div class="address-card hm-card">
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
      <div class="product-list hm-card">
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
          <div class="product-subtotal hm-price">¥{{ (item.price * item.quantity).toFixed(2) }}</div>
        </div>
      </div>

      <!-- 订单合计 -->
      <div class="total-row hm-card">
        <span class="total-label">订单合计</span>
        <span class="total-amount hm-price">¥{{ order.totalAmount }}</span>
      </div>

      <!-- 操作按钮 -->
      <div class="action-bar">
        <el-button v-if="order.status === 0" type="primary" size="large" @click="handlePay">
          去支付
        </el-button>
        <el-button v-if="order.status === 0" size="large" @click="handleCancel">
          取消订单
        </el-button>
        <el-button v-if="order.status === 1" type="danger" size="large" @click="handleRefund">
          申请退单
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
    class="order-address-dialog"
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
import { ref, reactive, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Clock } from '@element-plus/icons-vue'
import { getOrderDetail, cancelOrder, confirmOrder, refundOrder, updateOrderAddress } from '@/api/order'
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
// 0待付款 1已支付 2已发货 3已完成 4已取消 5已退款
const STATUS_MAP = {
  0: { text: '待付款', type: 'danger' },
  1: { text: '已支付', type: 'warning' },
  2: { text: '已发货', type: 'primary' },
  3: { text: '已完成', type: 'success' },
  4: { text: '已取消', type: 'info' },
  5: { text: '已退款', type: 'info' }
}

const statusText = computed(() => STATUS_MAP[order.value?.status]?.text || '未知')
const statusType = computed(() => STATUS_MAP[order.value?.status]?.type || 'info')

// ==================== 支付超时倒计时 ====================

/**
 * 订单超时时间（毫秒）：30 分钟
 * ⚠️ 必须与后端 RabbitMQConfig.ORDER_TIMEOUT_MS = 30 * 60 * 1000 保持一致！
 * 后端在下单那一刻就开始 30 分钟超时计时（RabbitMQ 延迟队列 TTL），
 * 前端也是从订单创建时间 + 30 分钟算剩余时间，两边才能对齐。
 */
const PAY_TIMEOUT_MS = 30 * 60 * 1000

/** 剩余支付秒数（每秒递减） */
const payCountdown = ref(0)
/** 倒计时定时器 */
let payTimer = null

/** 支付截止时间戳 = 订单创建时间 + 30 分钟 */
const payDeadline = computed(() => {
  if (!order.value?.createTime) return 0
  return new Date(order.value.createTime).getTime() + PAY_TIMEOUT_MS
})

/** 格式化倒计时：超过 1 小时显示 HH:MM:SS，否则 MM:SS */
const formatPayCountdown = computed(() => {
  const total = payCountdown.value
  if (total <= 0) return '00:00'
  const h = Math.floor(total / 3600)
  const m = Math.floor((total % 3600) / 60)
  const s = total % 60
  if (h > 0) {
    return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
  }
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
})

/** 启动倒计时：每秒刷新剩余秒数，归零后刷新订单详情（后端已自动取消订单） */
function startPayCountdown() {
  stopPayCountdown()
  const update = () => {
    const remaining = Math.floor((payDeadline.value - Date.now()) / 1000)
    payCountdown.value = Math.max(0, remaining)
    // 倒计时归零 → 订单已被后端超时取消（status=4）→ 重新加载详情刷新显示
    if (payCountdown.value <= 0) {
      stopPayCountdown()
      loadOrderDetail(order.value.id)
    }
  }
  update()
  payTimer = setInterval(update, 1000)
}

/** 停止倒计时 */
function stopPayCountdown() {
  if (payTimer) {
    clearInterval(payTimer)
    payTimer = null
  }
}

// 监听订单状态变化：待付款(0)时启动倒计时，其他状态（已支付/已取消等）停止
watch(
  () => order.value?.status,
  (status) => {
    if (status === 0) {
      startPayCountdown()
    } else {
      stopPayCountdown()
    }
  }
)

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

// 组件销毁时停止倒计时，防止定时器泄漏
onUnmounted(() => {
  stopPayCountdown()
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

/** 退单退款（已支付未发货一键退单，恢复库存） */
async function handleRefund() {
  try {
    await ElMessageBox.confirm(
      '确定要对已支付的订单申请退单退款吗？退款后将恢复商品库存。',
      '确认退单',
      {
        confirmButtonText: '确定退单',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    await refundOrder(order.value.id)
    ElMessage.success('退单成功')
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
  width: 100%;
  max-width: 920px;
  min-width: 0;
  box-sizing: border-box;
  margin: 0 auto;
  padding: 28px 20px 40px;
}

.loading-wrapper,
.detail-empty {
  padding: 40px;
  border: 1px solid var(--hm-ink-100);
  border-radius: var(--hm-radius-lg);
  background: var(--hm-white);
  box-shadow: var(--hm-shadow-sm);
}

.order-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
  margin-bottom: 20px;
  padding: 24px 26px;
  border: 1px solid var(--hm-ink-100);
  border-radius: var(--hm-radius-lg);
  background: var(--hm-white);
  box-shadow: var(--hm-shadow-sm);
}

.order-header-left {
  min-width: 0;
}

.order-header-left h2 {
  margin: 0 0 7px;
  color: var(--hm-ink-900);
  font-size: 26px;
  line-height: 1.3;
  letter-spacing: 1px;
}

.order-no {
  color: var(--hm-ink-500);
  font-size: var(--hm-font-sm);
  overflow-wrap: anywhere;
}

.order-status-tag {
  height: 32px;
  flex-shrink: 0;
  padding: 0 14px;
  border-radius: var(--hm-radius-pill);
}

.info-card {
  padding: 10px 24px 18px;
  margin-bottom: 26px;
  background: var(--hm-white);
}

.info-row {
  display: flex;
  gap: 20px;
  padding: 12px 0;
  border-bottom: 1px solid var(--hm-ink-100);
  font-size: var(--hm-font-base);
}

.info-label {
  width: 76px;
  flex-shrink: 0;
  color: var(--hm-ink-500);
}

.info-value {
  min-width: 0;
  color: var(--hm-ink-800);
  font-weight: 500;
  overflow-wrap: anywhere;
}

/* ==================== 支付超时倒计时 ==================== */
.countdown-tip {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 7px;
  margin-top: 16px;
  padding: 12px 14px;
  border: 1px solid var(--hm-primary-border);
  border-radius: var(--hm-radius-md);
  background: var(--hm-primary-soft);
  color: var(--hm-primary-active);
  font-size: var(--hm-font-base);
}

.countdown-tip strong {
  margin: 0 2px;
  color: var(--hm-primary);
  font-size: 18px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.section-title {
  margin-bottom: 12px;
  padding-left: 12px;
  border-left: 4px solid var(--hm-primary);
  border-radius: 2px;
  color: var(--hm-ink-900);
  font-size: 17px;
  font-weight: 600;
  line-height: 1.5;
}

.section-title :deep(.el-button) {
  vertical-align: 1px;
}

.product-list {
  margin-bottom: 16px;
  border-color: var(--hm-ink-100);
  background: var(--hm-white);
  overflow: hidden;
}

.product-item {
  display: flex;
  align-items: center;
  padding: 18px 22px;
  border-bottom: 1px solid var(--hm-ink-100);
}

.product-item:last-child {
  border-bottom: none;
}

.product-image {
  width: 84px;
  height: 84px;
  flex-shrink: 0;
  margin-right: 18px;
  border: 1px solid var(--hm-ink-100);
  border-radius: var(--hm-radius-md);
  background: var(--hm-ink-050);
}

.product-info {
  flex: 1;
  min-width: 0;
}

.product-name {
  margin-bottom: 8px;
  color: var(--hm-ink-800);
  font-size: 15px;
  font-weight: 500;
  line-height: 1.6;
  overflow-wrap: anywhere;
}

.product-price {
  color: var(--hm-ink-500);
  font-size: var(--hm-font-sm);
}

.product-subtotal {
  flex-shrink: 0;
  margin-left: 16px;
  font-size: 18px;
}

.total-row {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 18px;
  padding: 20px 24px;
  margin-bottom: 18px;
  background: linear-gradient(90deg, var(--hm-white), var(--hm-primary-soft));
}

.total-label {
  color: var(--hm-ink-600);
  font-size: var(--hm-font-base);
}

.total-amount {
  font-size: 28px;
  line-height: 1.2;
}

.action-bar {
  display: flex;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 12px;
  padding: 18px 20px;
  border: 1px solid var(--hm-ink-100);
  border-radius: var(--hm-radius-lg);
  background: var(--hm-white);
  box-shadow: var(--hm-shadow-sm);
}

.action-bar :deep(.el-button) {
  min-width: 108px;
  margin: 0;
  border-radius: var(--hm-radius-md);
}

/* ==================== 收货地址卡片 ==================== */
.address-card {
  position: relative;
  margin-bottom: 26px;
  padding: 20px 24px;
  border-color: var(--hm-primary-border);
  background: linear-gradient(135deg, var(--hm-white), var(--hm-primary-soft));
  overflow: hidden;
}

.address-card::before {
  content: '';
  position: absolute;
  inset: 0 auto 0 0;
  width: 4px;
  background: var(--hm-primary);
}

.address-info {
  line-height: 1.7;
}

.address-name-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px 10px;
  margin-bottom: 7px;
}

.address-card .addr-name {
  margin-right: 0;
  color: var(--hm-ink-900);
  font-size: 17px;
  font-weight: 600;
}

.address-card .addr-phone {
  color: var(--hm-ink-500);
  font-size: var(--hm-font-base);
}

.address-card .addr-detail {
  color: var(--hm-ink-600);
  font-size: var(--hm-font-base);
  overflow-wrap: anywhere;
}

/* ==================== 修改地址弹窗 ==================== */
.addr-loading {
  padding: 20px 0;
}

.addr-select-list {
  max-height: 340px;
  padding: 2px;
  overflow-y: auto;
}

.addr-select-item {
  margin-bottom: 12px;
  padding: 15px 16px;
  border: 1px solid var(--hm-ink-200);
  border-radius: var(--hm-radius-md);
  background: var(--hm-white);
  cursor: pointer;
  transition: border-color var(--hm-transition), background var(--hm-transition), box-shadow var(--hm-transition);
}

.addr-select-item:hover {
  border-color: var(--hm-primary-border);
  box-shadow: var(--hm-shadow-sm);
}

.addr-select-item.is-active {
  border-color: var(--hm-primary);
  background: var(--hm-primary-soft);
  box-shadow: 0 0 0 2px rgba(232, 80, 30, 0.08);
}

.addr-select-item :deep(.el-radio) {
  width: 100%;
  height: auto;
  align-items: flex-start;
  margin-right: 0;
}

.addr-select-item :deep(.el-radio__input) {
  margin-top: 3px;
}

.addr-select-item :deep(.el-radio__label) {
  flex: 1;
  min-width: 0;
  white-space: normal;
}

.addr-info {
  display: inline-block;
  width: 100%;
  vertical-align: middle;
}

.addr-name {
  margin-right: 10px;
  color: var(--hm-ink-800);
  font-size: 15px;
  font-weight: 600;
}

.addr-phone {
  margin-right: 10px;
  color: var(--hm-ink-500);
  font-size: var(--hm-font-base);
}

.addr-detail-text {
  margin-top: 5px;
  color: var(--hm-ink-500);
  font-size: var(--hm-font-sm);
  line-height: 1.6;
  overflow-wrap: anywhere;
}

.addr-dialog-footer {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 12px;
  margin-top: 20px;
  padding-top: 18px;
  border-top: 1px solid var(--hm-ink-100);
}

.addr-dialog-footer :deep(.el-button) {
  margin: 0;
}

:global(.order-address-dialog) {
  max-width: calc(100vw - 32px);
}

@media (max-width: 1024px) {
  .order-detail {
    max-width: 860px;
  }
}

@media (max-width: 768px) {
  .order-detail {
    padding: 24px 16px 32px;
  }

  .order-header {
    padding: 20px 22px;
  }

  .product-item {
    padding: 16px 18px;
  }

  .product-image {
    width: 72px;
    height: 72px;
    margin-right: 14px;
  }
}

@media (max-width: 480px) {
  .order-detail {
    padding: 20px 12px 28px;
  }

  .loading-wrapper {
    padding: 24px 18px;
  }

  .order-header {
    align-items: flex-start;
    flex-direction: column;
    gap: 14px;
    padding: 20px;
  }

  .order-header-left h2 {
    font-size: 23px;
  }

  .info-card {
    padding: 8px 18px 16px;
  }

  .info-row {
    gap: 12px;
  }

  .info-label {
    width: 64px;
  }

  .countdown-tip {
    align-items: flex-start;
  }

  .product-item {
    padding: 14px;
  }

  .product-image {
    width: 60px;
    height: 60px;
    margin-right: 12px;
    border-radius: 10px;
  }

  .product-name {
    font-size: var(--hm-font-base);
  }

  .product-subtotal {
    margin-left: 8px;
    font-size: 15px;
  }

  .total-row {
    padding: 18px;
  }

  .total-amount {
    font-size: 24px;
  }

  .action-bar {
    padding: 14px;
  }

  .action-bar :deep(.el-button) {
    flex: 1 1 calc(50% - 6px);
    min-width: 0;
  }

  .address-card {
    padding: 18px 20px;
  }

  .addr-select-item {
    padding: 13px 12px;
  }
}

/* ==================== Codex 深色订单详情收口 ==================== */
.order-header,
.info-card,
.product-item,
.total-row,
.action-bar,
.address-card,
.loading-wrapper {
  background: var(--hm-bg-surface);
  border-color: var(--hm-ink-100);
}
.order-header {
  background:
    radial-gradient(circle at 100% 0, rgba(139, 233, 176, 0.08), transparent 34%),
    var(--hm-bg-surface);
  box-shadow: none;
}
.info-card,
.address-card {
  background: var(--hm-bg-subtle);
}
.product-item,
.total-row,
.info-row {
  border-color: var(--hm-ink-100);
}
.product-name,
.info-value,
.total-label {
  color: var(--hm-ink-800);
}
.product-price,
.info-label,
.address-card .addr-detail {
  color: var(--hm-ink-500);
}
.product-subtotal,
.total-amount {
  color: var(--hm-accent);
}
.countdown-tip {
  border-color: rgba(240, 184, 110, 0.28);
  background: var(--hm-warning-soft);
}
.countdown-tip strong {
  color: var(--hm-warning);
}
.address-card::before {
  background: var(--hm-primary);
}
.action-bar :deep(.el-button--primary) {
  color: var(--hm-on-primary);
  box-shadow: 0 6px 18px rgba(139, 233, 176, 0.16);
}
</style>
