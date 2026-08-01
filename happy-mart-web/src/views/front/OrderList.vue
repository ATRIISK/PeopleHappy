<script setup>
/**
 * 订单列表页组件
 * 展示当前用户的订单列表，支持按状态筛选、取消、确认收货
 * 真实 API 版本（已替换 Mock 数据）
 */
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getOrderList, cancelOrder, confirmOrder, refundOrder } from '@/api/order'

const router = useRouter()

// ==================== 响应式状态 ====================

/** 当前选中的订单状态筛选值（null 表示全部） */
const activeStatus = ref(null)

/** 订单列表数据 */
const orderList = ref([])

/** 页面加载状态 */
const loading = ref(false)

// ==================== 状态配置 ====================

/**
 * 订单状态文字映射
 * 与数据库 order.status 字段定义、开发文档保持一致：
 * 0待付款 1已支付 2已发货 3已完成 4已取消 5已退款
 *
 * 更新记录：
 * 2026-07-29 新增 status=5 已退款（退单功能）
 */
const ORDER_STATUS = {
  0: '待付款',
  1: '已支付',
  2: '已发货',
  3: '已完成',
  4: '已取消',
  5: '已退款'
}

/** Tab 选项列表（与 ORDER_STATUS 文字保持一致） */
const statusTabs = [
  { label: '全部', value: null },
  { label: '待付款', value: 0 },
  { label: '已支付', value: 1 },
  { label: '已发货', value: 2 },
  { label: '已完成', value: 3 }
]

/**
 * 订单状态标签颜色映射
 * 0待付款(danger红色) 1已支付(warning橙色) 2已发货(primary蓝色)
 * 3已完成(success绿色) 4已取消(info灰色) 5已退款(info灰色)
 */
const statusTypeMap = {
  0: 'danger',
  1: 'warning',
  2: 'primary',
  3: 'success',
  4: 'info',
  5: 'info'
}

// ==================== 数据加载 ====================

/**
 * 加载订单列表
 * 调后端 API getOrderList，支持按状态筛选
 */
async function loadOrders() {
  loading.value = true
  try {
    const params = { page: 1, size: 10 }
    if (activeStatus.value !== null) {
      params.status = activeStatus.value
    }
    const res = await getOrderList(params)
    orderList.value = res.records || []
  } catch (err) {
    console.error('获取订单列表失败:', err)
    ElMessage.error('获取订单列表失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

/**
 * 切换状态 Tab 时重新加载数据
 */
function handleStatusChange(status) {
  activeStatus.value = status
  loadOrders()
}

// ==================== 订单操作 ====================

/**
 * 去付款（跳转到支付宝扫码支付页面）
 */
function handlePay(order) {
  router.push({ name: 'Pay', params: { id: order.id } })
}

/**
 * 取消订单（调后端 API）
 */
async function handleCancel(order) {
  try {
    await ElMessageBox.confirm(
      `确定要取消订单「${order.orderNo}」吗？`,
      '确认取消',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    await cancelOrder(order.id)
    ElMessage.success('订单已取消')
    await loadOrders()
  } catch {
    // 用户取消操作，不处理
  }
}

/**
 * 退单退款（已支付未发货一键退单）
 * 调后端 API，退单后恢复商品库存
 */
async function handleRefund(order) {
  try {
    await ElMessageBox.confirm(
      `确定要对订单「${order.orderNo}」申请退单退款吗？`,
      '确认退单',
      {
        confirmButtonText: '确定退单',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    await refundOrder(order.id)
    ElMessage.success('退单成功')
    await loadOrders()
  } catch {
    // 用户取消操作，不处理
  }
}

/**
 * 确认收货（调后端 API）
 */
async function handleConfirm(order) {
  try {
    await ElMessageBox.confirm(
      `确定已收到订单「${order.orderNo}」的商品吗？`,
      '确认收货',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    await confirmOrder(order.id)
    ElMessage.success('已确认收货')
    await loadOrders()
  } catch {
    // 用户取消操作，不处理
  }
}

/**
 * 查看订单详情
 */
function goToDetail(orderId) {
  router.push({ name: 'OrderDetail', params: { id: orderId } })
}

// ==================== 生命周期 ====================

onMounted(() => {
  loadOrders()
})
</script>

<template>
  <div class="order-list-page">
    <!-- ==================== 页面标题 ==================== -->
    <h1 class="page-title">我的订单</h1>

    <!-- ==================== 状态 Tab 切换 ==================== -->
    <div class="status-tabs">
      <el-radio-group
        :model-value="activeStatus"
        @change="handleStatusChange"
      >
        <el-radio-button
          v-for="tab in statusTabs"
          :key="tab.value"
          :value="tab.value"
        >
          {{ tab.label }}
        </el-radio-button>
      </el-radio-group>
    </div>

    <!-- ==================== 加载中状态 ==================== -->
    <div v-if="loading" class="loading-state">
      <el-skeleton :rows="3" animated />
    </div>

    <!-- ==================== 空状态 ==================== -->
    <div v-else-if="orderList.length === 0" class="empty-state">
      <el-empty description="暂无订单" />
    </div>

    <!-- ==================== 订单卡片列表 ==================== -->
    <template v-else>
      <div
        v-for="order in orderList"
        :key="order.id"
        class="order-card"
      >
        <!-- ===== 订单头部：订单号 + 下单时间 + 状态标签 ===== -->
        <div class="order-header">
          <div class="order-header-left">
            <span class="order-no">订单号：{{ order.orderNo }}</span>
            <span class="order-time">{{ order.createTime }}</span>
          </div>
          <div class="order-header-right">
            <el-tag :type="statusTypeMap[order.status]" size="small">
              {{ ORDER_STATUS[order.status] }}
            </el-tag>
            <el-button text type="primary" size="small" @click="goToDetail(order.id)">
              查看详情
            </el-button>
          </div>
        </div>

        <!-- ===== 订单商品列表 ===== -->
        <div class="order-items">
          <div
            v-for="(item, index) in order.items"
            :key="item.productId"
            class="order-item"
            :class="{ 'item-divider': index < order.items.length - 1 }"
          >
            <!-- 商品缩略图 -->
            <el-image
              :src="item.productImage"
              :alt="item.productName"
              class="item-thumb"
              fit="cover"
            />
            <!-- 商品信息 -->
            <div class="item-info">
              <span class="item-name">{{ item.productName }}</span>
            </div>
            <!-- 价格和数量 -->
            <div class="item-price-qty">
              <span class="item-price">¥{{ item.price.toFixed(2) }}</span>
              <span class="item-qty">x{{ item.quantity }}</span>
            </div>
          </div>
        </div>

        <!-- ===== 订单底部：实付金额 + 操作按钮 ===== -->
        <div class="order-footer">
          <div class="order-footer-left">
            <span class="total-label">实付金额：</span>
            <span class="total-amount">¥{{ order.totalAmount.toFixed(2) }}</span>
          </div>
          <div class="order-footer-right">
            <!-- 待付款：显示去付款 + 取消订单 -->
            <template v-if="order.status === 0">
              <el-button type="danger" @click="handlePay(order)">
                去付款
              </el-button>
              <el-button @click="handleCancel(order)">
                取消订单
              </el-button>
            </template>
            <!-- 已支付未发货：显示申请退单 -->
            <template v-else-if="order.status === 1">
              <el-button type="danger" plain @click="handleRefund(order)">
                申请退单
              </el-button>
            </template>
            <!-- 已发货：显示确认收货 -->
            <template v-else-if="order.status === 2">
              <el-button type="primary" @click="handleConfirm(order)">
                确认收货
              </el-button>
            </template>
            <!-- 其他状态（已完成、已取消、已退款）：无按钮 -->
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
/* ==================== 页面容器 ==================== */
.order-list-page {
  background: transparent;
}

/* ==================== 页面标题 ==================== */
.page-title {
  font-size: 22px;
  font-weight: bold;
  color: #1a1a2e;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 2px solid #f0f0f0;
}

/* ==================== 状态 Tab 切换 ==================== */
.status-tabs {
  margin-bottom: 20px;
}

.status-tabs .el-radio-button__inner {
  font-size: 14px;
  padding: 8px 20px;
}

/* ==================== 加载中状态 ==================== */
.loading-state {
  background: #fff;
  border-radius: 8px;
  padding: 24px;
}

/* ==================== 空状态 ==================== */
.empty-state {
  background: #fff;
  border-radius: 8px;
  padding: 80px 0;
  display: flex;
  justify-content: center;
}

/* ==================== 订单卡片 ==================== */
.order-card {
  background: #fff;
  border-radius: 8px;
  border: 1px solid #e8e8e8;
  margin-bottom: 16px;
  transition: box-shadow 0.25s ease;
}

/* hover 时显示阴影 */
.order-card:hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
}

/* ==================== 订单头部 ==================== */
.order-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 20px;
  background: #fafafa;
  border-bottom: 1px solid #e8e8e8;
  border-radius: 8px 8px 0 0;
}

.order-header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.order-no {
  font-size: 14px;
  font-weight: 600;
  color: #333;
}

.order-time {
  font-size: 13px;
  color: #999;
}

/* ==================== 订单商品列表 ==================== */
.order-items {
  padding: 16px 20px;
}

.order-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 0;
}

/* 商品之间虚线分隔 */
.order-item.item-divider {
  border-bottom: 1px dashed #e8e8e8;
}

/* 商品缩略图 60×60 */
.item-thumb {
  width: 60px;
  height: 60px;
  border-radius: 6px;
  border: 1px solid #eee;
  flex-shrink: 0;
  object-fit: cover;
}

/* 商品信息区域 */
.item-info {
  flex: 1;
  min-width: 0;
}

.item-name {
  font-size: 14px;
  color: #333;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 价格和数量区域 */
.item-price-qty {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.item-price {
  font-size: 14px;
  color: #e4393c;
  font-weight: 600;
}

.item-qty {
  font-size: 13px;
  color: #999;
}

/* ==================== 订单底部 ==================== */
.order-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 20px;
  background: #fafafa;
  border-top: 1px solid #e8e8e8;
  border-radius: 0 0 8px 8px;
}

.order-footer-left {
  display: flex;
  align-items: center;
  gap: 4px;
}

.total-label {
  font-size: 14px;
  color: #666;
}

/* 实付金额：红色 18px 加粗 */
.total-amount {
  font-size: 18px;
  font-weight: bold;
  color: #e4393c;
  line-height: 1;
}

.order-footer-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
</style>
