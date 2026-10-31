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

/** 分页参数（code-review 修复：订单列表支持翻页，原来固定 page:1,size:10 看不到后续订单） */
const currentPage = ref(1)   // 当前页码
const pageSize = ref(5)      // 每页条数
const total = ref(0)         // 总条数

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
    const params = { page: currentPage.value, size: pageSize.value }
    if (activeStatus.value !== null) {
      params.status = activeStatus.value
    }
    const res = await getOrderList(params)
    orderList.value = res.records || []
    total.value = res.total || 0   // 记录总条数（分页组件显示用）
  } catch (err) {
    console.error('获取订单列表失败:', err)
    ElMessage.error('获取订单列表失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

/**
 * 切换状态 Tab 时重新加载数据（回到第一页）
 */
function handleStatusChange(status) {
  activeStatus.value = status
  currentPage.value = 1
  loadOrders()
}

/**
 * 每页条数变化时：回到第一页重新加载
 */
function handleSizeChange() {
  currentPage.value = 1
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
    <div class="page-heading hm-page-header">
      <div>
        <span class="page-eyebrow">ORDER CENTER</span>
        <h1 class="page-title hm-page-header__title">我的订单</h1>
        <p class="hm-page-header__desc">查看订单进度，轻松管理每一次购物</p>
      </div>
    </div>

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
        class="order-card hm-card"
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
              <span class="item-price hm-price">¥{{ item.price.toFixed(2) }}</span>
              <span class="item-qty">× {{ item.quantity }}</span>
            </div>
          </div>
        </div>

        <!-- ===== 订单底部：实付金额 + 操作按钮 ===== -->
        <div class="order-footer">
          <div class="order-footer-left">
            <span class="total-label">实付金额</span>
            <span class="total-amount hm-price">¥{{ order.totalAmount.toFixed(2) }}</span>
          </div>
          <div class="order-footer-right">
            <!-- 待付款：显示去付款 + 取消订单 -->
            <template v-if="order.status === 0">
              <el-button type="primary" class="pay-order-btn" @click="handlePay(order)">
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

      <!-- ==================== 分页（有订单时才显示） ==================== -->
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[5, 10, 20]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="loadOrders"
          @size-change="handleSizeChange"
        />
      </div>
    </template>
  </div>
</template>

<style scoped>
/* ==================== 页面容器 ==================== */
.order-list-page {
  width: 100%;
  max-width: 1080px;
  min-width: 0;
  margin: 0 auto;
  background: transparent;
}

/* ==================== 页面标题 ==================== */
.page-heading {
  margin-bottom: 22px;
}

.page-eyebrow {
  display: block;
  margin-bottom: 7px;
  color: var(--hm-primary);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 2px;
}

.page-title {
  margin: 0;
  font-size: 26px;
  letter-spacing: 1px;
}

/* ==================== 状态 Tab 切换 ==================== */
.status-tabs {
  max-width: 100%;
  box-sizing: border-box;
  margin-bottom: 20px;
  padding: 7px;
  border: 1px solid var(--hm-ink-100);
  border-radius: 14px;
  background: var(--hm-white);
  box-shadow: var(--hm-shadow-sm);
  overflow-x: auto;
}

.status-tabs :deep(.el-radio-group) {
  display: inline-flex;
  min-width: max-content;
}

.status-tabs :deep(.el-radio-button__inner) {
  padding: 9px 22px;
  border: 0;
  border-radius: var(--hm-radius-sm) !important;
  background: transparent;
  color: var(--hm-ink-600);
  box-shadow: none !important;
  font-size: var(--hm-font-base);
}

.status-tabs :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  background: var(--hm-primary);
  color: var(--hm-white);
  box-shadow: 0 4px 10px rgba(232, 80, 30, 0.18) !important;
}

.status-tabs :deep(.el-radio-button:first-child .el-radio-button__inner),
.status-tabs :deep(.el-radio-button:last-child .el-radio-button__inner) {
  border-radius: var(--hm-radius-sm);
}

/* ==================== 加载中状态 ==================== */
.loading-state {
  padding: 32px;
  border: 1px solid var(--hm-ink-100);
  border-radius: var(--hm-radius-lg);
  background: var(--hm-white);
  box-shadow: var(--hm-shadow-sm);
}

/* ==================== 空状态 ==================== */
.empty-state {
  padding: 64px 20px;
  border: 1px solid var(--hm-ink-100);
  border-radius: var(--hm-radius-lg);
  background: var(--hm-white);
  box-shadow: var(--hm-shadow-sm);
  display: flex;
  justify-content: center;
}

/* ==================== 订单卡片 ==================== */
.order-card {
  margin-bottom: 18px;
  border-color: var(--hm-ink-100);
  overflow: hidden;
  transition: border-color var(--hm-transition), box-shadow var(--hm-transition), transform var(--hm-transition);
}

/* hover 时显示阴影 */
.order-card:hover {
  border-color: var(--hm-primary-border);
  box-shadow: var(--hm-shadow-md);
  transform: translateY(-2px);
}

/* ==================== 订单头部 ==================== */
.order-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 15px 22px;
  background: var(--hm-ink-050);
  border-bottom: 1px solid var(--hm-ink-100);
}

.order-header-left {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px 18px;
  min-width: 0;
}

.order-header-right {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  gap: 8px;
}

.order-no {
  color: var(--hm-ink-800);
  font-size: var(--hm-font-base);
  font-weight: 600;
  overflow-wrap: anywhere;
}

.order-time {
  color: var(--hm-ink-500);
  font-size: var(--hm-font-sm);
  font-variant-numeric: tabular-nums;
}

.order-header-right :deep(.el-tag) {
  height: 26px;
  padding: 0 10px;
  border-radius: var(--hm-radius-pill);
}

.order-header-right :deep(.el-button) {
  margin: 0;
}

/* ==================== 订单商品列表 ==================== */
.order-items {
  padding: 10px 22px;
}

.order-item {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 16px 0;
}

/* 商品之间虚线分隔 */
.order-item.item-divider {
  border-bottom: 1px dashed var(--hm-ink-200);
}

/* 商品缩略图 60×60 */
.item-thumb {
  width: 72px;
  height: 72px;
  flex-shrink: 0;
  border: 1px solid var(--hm-ink-100);
  border-radius: var(--hm-radius-md);
  background: var(--hm-ink-050);
  object-fit: cover;
}

/* 商品信息区域 */
.item-info {
  flex: 1;
  min-width: 0;
}

.item-name {
  color: var(--hm-ink-800);
  font-size: 15px;
  font-weight: 500;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 价格和数量区域 */
.item-price-qty {
  display: flex;
  align-items: flex-end;
  flex-direction: column;
  gap: 5px;
  flex-shrink: 0;
}

.item-price {
  font-size: 16px;
}

.item-qty {
  color: var(--hm-ink-500);
  font-size: var(--hm-font-sm);
}

/* ==================== 订单底部 ==================== */
.order-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 16px 22px;
  background: linear-gradient(90deg, var(--hm-ink-050), var(--hm-primary-soft));
  border-top: 1px solid var(--hm-ink-100);
}

.order-footer-left {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.total-label {
  color: var(--hm-ink-600);
  font-size: var(--hm-font-base);
}

/* 实付金额：红色 18px 加粗 */
.total-amount {
  font-size: 24px;
  line-height: 1;
}

.order-footer-right {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
}

.order-footer-right :deep(.el-button) {
  margin: 0;
  border-radius: 10px;
}

.order-footer-right :deep(.pay-order-btn) {
  padding-left: 24px;
  padding-right: 24px;
  box-shadow: 0 5px 12px rgba(232, 80, 30, 0.18);
}

/* ==================== 分页 ==================== */
.pagination-wrap {
  max-width: 100%;
  box-sizing: border-box;
  margin-top: 20px;
  padding: 16px 20px;
  border: 1px solid var(--hm-ink-100);
  border-radius: var(--hm-radius-lg);
  background: var(--hm-white);
  box-shadow: var(--hm-shadow-sm);
  display: flex;
  justify-content: flex-end;
  overflow-x: auto;
}

.pagination-wrap :deep(.el-pagination) {
  max-width: 100%;
  flex-wrap: wrap;
  row-gap: 10px;
}

@media (max-width: 1024px) {
  .order-list-page {
    max-width: 960px;
  }
}

@media (max-width: 768px) {
  .page-heading {
    margin-bottom: 18px;
  }

  .status-tabs {
    width: 100%;
  }

  .order-header {
    align-items: flex-start;
    padding: 14px 18px;
  }

  .order-items {
    padding: 8px 18px;
  }

  .order-footer {
    padding: 15px 18px;
  }

  .pagination-wrap {
    justify-content: flex-start;
  }
}

@media (max-width: 480px) {
  .page-title {
    font-size: 23px;
  }

  .status-tabs {
    padding: 5px;
  }

  .status-tabs :deep(.el-radio-button__inner) {
    padding: 8px 15px;
  }

  .loading-state {
    padding: 24px 18px;
  }

  .order-header {
    flex-direction: column;
  }

  .order-header-right {
    width: 100%;
    justify-content: space-between;
  }

  .order-item {
    gap: 12px;
  }

  .item-thumb {
    width: 58px;
    height: 58px;
    border-radius: 10px;
  }

  .item-name {
    font-size: var(--hm-font-base);
  }

  .item-price {
    font-size: var(--hm-font-base);
  }

  .order-footer {
    align-items: flex-start;
    flex-direction: column;
  }

  .order-footer-right {
    width: 100%;
    justify-content: flex-start;
  }

  .total-amount {
    font-size: 22px;
  }

  .pagination-wrap {
    padding: 14px 12px;
  }
}

/* ==================== Codex 深色订单列表收口 ==================== */
.order-list-page,
.order-card,
.status-tabs,
.pagination-wrap {
  background: var(--hm-bg-surface);
  border-color: var(--hm-ink-100);
}
.order-card {
  background:
    linear-gradient(180deg, rgba(139, 233, 176, 0.025), transparent 26%),
    var(--hm-bg-surface);
  box-shadow: none;
}
.order-card:hover {
  border-color: var(--hm-primary-border);
  box-shadow: 0 18px 44px rgba(0, 0, 0, 0.32);
}
.order-header,
.order-items,
.order-footer {
  border-color: var(--hm-ink-100);
}
.order-header {
  background: var(--hm-bg-subtle);
}
.status-tabs :deep(.el-radio-button__inner) {
  background: transparent;
  border-color: var(--hm-ink-100);
  color: var(--hm-ink-500);
}
.status-tabs :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  background: var(--hm-primary-soft);
  border-color: var(--hm-primary-border);
  color: var(--hm-primary);
  box-shadow: none;
}
.item-name,
.total-label {
  color: var(--hm-ink-800);
}
.item-price,
.order-no,
.item-count {
  color: var(--hm-ink-500);
}
.total-amount {
  color: var(--hm-accent);
}
.order-footer :deep(.el-button--primary) {
  color: var(--hm-on-primary);
  box-shadow: 0 5px 16px rgba(139, 233, 176, 0.16);
}
/* ==================== 经典灰白订单列表 ==================== */
.order-list-page,
.order-card,
.status-tabs,
.pagination-wrap {
  background: var(--hm-bg-surface);
  border-color: var(--hm-ink-100);
}
.order-card {
  background: var(--hm-bg-surface);
  box-shadow: none;
}
.order-card:hover {
  border-color: var(--hm-ink-200);
  box-shadow: 0 8px 20px rgba(31, 35, 40, 0.07);
}
.order-header,
.order-items,
.order-footer {
  border-color: var(--hm-ink-100);
}
.order-header {
  background: var(--hm-bg-subtle);
}
.status-tabs :deep(.el-radio-button__inner) {
  background: var(--hm-bg-surface);
  border-color: var(--hm-ink-100);
  color: var(--hm-ink-600);
}
.status-tabs :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  background: var(--hm-primary-soft);
  border-color: var(--hm-primary-border);
  color: var(--hm-primary);
  box-shadow: none;
}
.item-name,
.total-label {
  color: var(--hm-ink-800);
}
.item-price,
.order-no,
.item-count {
  color: var(--hm-ink-500);
}
.total-amount {
  color: var(--hm-primary);
}
</style>
