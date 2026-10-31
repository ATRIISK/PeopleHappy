<script setup>
/**
 * 管理后台首页：数据看板
 *
 * 展示：商品数 / 订单数 / 用户数 / 总交易额 四张统计卡片 + 最近订单表格
 * 数据来源：GET /api/admin/dashboard/stats + /api/admin/dashboard/recent-orders
 */
import { ref, onMounted } from 'vue'
import { getDashboardStats, getRecentOrders } from '@/api/admin'

// ==================== 响应式状态 ====================

/** 统计数据（商品/订单/用户/交易额） */
const stats = ref({ productCount: 0, orderCount: 0, userCount: 0, totalSales: 0 })

/** 最近订单列表 */
const recentOrders = ref([])

/** 加载状态（表格 v-loading 用） */
const loading = ref(false)

// ==================== 订单状态配置（与前台 OrderList.vue 保持一致） ====================

/** 状态文字：0待付款 1已支付 2已发货 3已完成 4已取消 5已退款 */
const ORDER_STATUS = {
  0: '待付款',
  1: '已支付',
  2: '已发货',
  3: '已完成',
  4: '已取消',
  5: '已退款'
}

/** 状态标签颜色：待付款红、已支付橙、已发货蓝、已完成绿、取消/退款灰 */
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
 * 加载看板数据：统计 + 最近订单并行请求
 * Promise.all 同时发两个请求，比串行快
 */
async function loadData() {
  loading.value = true
  try {
    // 用 Promise.allSettled：一个接口失败不影响另一个（code-review 修复）
    // 原来 Promise.all 一个 reject 就全部丢弃，统计卡片全变 0，误导管理员以为店铺没数据
    const [statsRes, recentRes] = await Promise.allSettled([
      getDashboardStats(),
      getRecentOrders()
    ])
    if (statsRes.status === 'fulfilled') {
      stats.value = statsRes.value
    } else {
      console.error('加载统计失败:', statsRes.reason)
    }
    if (recentRes.status === 'fulfilled') {
      recentOrders.value = recentRes.value || []
    } else {
      console.error('加载最近订单失败:', recentRes.reason)
    }
  } catch (err) {
    console.error('加载数据看板失败:', err)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <div class="dashboard-page">
    <!-- ==================== 页面标题 ==================== -->
    <div class="hm-page-header">
      <div>
        <h1 class="hm-page-header__title">数据看板</h1>
        <p class="hm-page-header__desc">汇总店铺经营数据，快速掌握商品、订单与销售表现</p>
      </div>
      <span class="dashboard-date">运营概览</span>
    </div>

    <!-- ==================== 四张统计卡片 ==================== -->
    <el-row :gutter="18" class="stat-row">
      <!-- 商品总数 -->
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card class="stat-card stat-card--product" shadow="never">
          <div class="stat-card__top">
            <span class="stat-label">商品总数</span>
            <span class="stat-icon">商</span>
          </div>
          <div class="stat-value">{{ stats.productCount }}</div>
          <div class="stat-foot">全部商品档案</div>
        </el-card>
      </el-col>
      <!-- 订单总数 -->
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card class="stat-card stat-card--order" shadow="never">
          <div class="stat-card__top">
            <span class="stat-label">订单总数</span>
            <span class="stat-icon">单</span>
          </div>
          <div class="stat-value">{{ stats.orderCount }}</div>
          <div class="stat-foot">累计订单数量</div>
        </el-card>
      </el-col>
      <!-- 用户总数 -->
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card class="stat-card stat-card--user" shadow="never">
          <div class="stat-card__top">
            <span class="stat-label">用户总数</span>
            <span class="stat-icon">客</span>
          </div>
          <div class="stat-value">{{ stats.userCount }}</div>
          <div class="stat-foot">注册用户规模</div>
        </el-card>
      </el-col>
      <!-- 总交易额（红色高亮，单位元） -->
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card class="stat-card stat-card--sales" shadow="never">
          <div class="stat-card__top">
            <span class="stat-label">总交易额</span>
            <span class="stat-icon">额</span>
          </div>
          <div class="stat-value stat-money">¥{{ Number(stats.totalSales).toFixed(2) }}</div>
          <div class="stat-foot">累计成交金额</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- ==================== 最近订单 ==================== -->
    <el-card class="order-panel" shadow="never">
      <template #header>
        <div class="panel-head">
          <div>
            <h2>最近订单</h2>
            <p>按下单时间展示最新交易记录</p>
          </div>
          <span class="panel-badge">实时概览</span>
        </div>
      </template>
      <div class="table-scroll">
        <el-table :data="recentOrders" v-loading="loading" empty-text="暂无订单">
        <el-table-column prop="orderNo" label="订单号" min-width="190">
          <template #default="{ row }"><span class="order-no">{{ row.orderNo }}</span></template>
        </el-table-column>
        <el-table-column prop="username" label="下单人" width="120" />
        <el-table-column label="金额" width="120">
          <!-- 金额用模板格式化：保留两位小数 -->
          <template #default="{ row }"><span class="hm-price">¥{{ Number(row.totalAmount).toFixed(2) }}</span></template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <!-- 状态用彩色标签展示 -->
          <template #default="{ row }">
            <el-tag :type="statusTypeMap[row.status]" size="small" effect="light" round>
              {{ ORDER_STATUS[row.status] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="下单时间" min-width="180" />
      </el-table>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
/* 页面标题 */
.dashboard-page .hm-page-header {
  margin-bottom: var(--hm-space-6);
}
.dashboard-date {
  padding: 6px 12px;
  border: 1px solid var(--hm-ink-200);
  border-radius: var(--hm-radius-pill);
  background: rgba(255, 255, 255, 0.7);
  color: var(--hm-ink-500);
  font-size: var(--hm-font-xs);
}

/* 统计卡片行的下边距 */
.stat-row {
  margin-bottom: var(--hm-space-5);
  row-gap: var(--hm-space-4);
}
.stat-card {
  height: 100%;
  border: 1px solid transparent;
  border-radius: var(--hm-radius-lg);
  box-shadow: var(--hm-shadow-sm);
}
.stat-card :deep(.el-card__body) {
  padding: var(--hm-space-5);
}
.stat-card--product {
  background: #fff7ed;
  border-color: #f5dfca;
}
.stat-card--order {
  background: #f2f7f3;
  border-color: #dce9df;
}
.stat-card--user {
  background: #f2f6f8;
  border-color: #dde7eb;
}
.stat-card--sales {
  background: #fff0e9;
  border-color: #f5d6c7;
}
.stat-card__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--hm-space-3);
  margin-bottom: var(--hm-space-4);
}

/* 卡片里的"指标名称"文字 */
.stat-label {
  font-size: var(--hm-font-sm);
  color: var(--hm-ink-600);
  font-weight: var(--hm-fw-medium);
}
.stat-icon {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 11px;
  background: rgba(255, 255, 255, 0.78);
  color: var(--hm-ink-700);
  font-size: var(--hm-font-sm);
  font-weight: var(--hm-fw-bold);
  box-shadow: var(--hm-shadow-sm);
}
.stat-card--product .stat-icon,
.stat-card--sales .stat-icon {
  color: var(--hm-primary);
}
.stat-card--order .stat-icon {
  color: var(--hm-success);
}
.stat-card--user .stat-icon {
  color: var(--hm-info);
}

/* 卡片里的"指标数字" */
.stat-value {
  font-size: 30px;
  font-weight: var(--hm-fw-bold);
  color: var(--hm-ink-900);
  line-height: var(--hm-lh-tight);
  font-variant-numeric: tabular-nums; /* 数字等宽，避免刷新时跳动 */
}
.stat-foot {
  margin-top: var(--hm-space-3);
  color: var(--hm-ink-500);
  font-size: var(--hm-font-xs);
}

/* 交易额用红色突出（商城习惯） */
.stat-money {
  color: var(--hm-accent);
}
.order-panel {
  border-radius: var(--hm-radius-lg);
}
.order-panel :deep(.el-card__header) {
  padding: var(--hm-space-4) var(--hm-space-5);
  border-bottom-color: var(--hm-ink-100);
}
.order-panel :deep(.el-card__body) {
  padding: 0;
}
.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--hm-space-3);
}
.panel-head h2 {
  margin: 0;
  color: var(--hm-ink-900);
  font-size: var(--hm-font-md);
}
.panel-head p {
  margin: 4px 0 0;
  color: var(--hm-ink-500);
  font-size: var(--hm-font-xs);
}
.panel-badge {
  padding: 4px 10px;
  border-radius: var(--hm-radius-pill);
  background: var(--hm-primary-soft);
  color: var(--hm-primary);
  font-size: var(--hm-font-xs);
}

/* 表格容器：窄屏可横向滚动 */
.table-scroll {
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}
.table-scroll :deep(.el-table) {
  min-width: 640px;
}
.table-scroll :deep(.el-table__inner-wrapper::before) {
  display: none;
}
.table-scroll :deep(.el-table th.el-table__cell) {
  padding: 12px 0;
}
.table-scroll :deep(.el-table td.el-table__cell) {
  padding: 15px 0;
}
.order-no {
  color: var(--hm-ink-800);
  font-weight: var(--hm-fw-medium);
  font-variant-numeric: tabular-nums;
}

@media (max-width: 768px) {
  .dashboard-page .hm-page-header {
    margin-bottom: var(--hm-space-4);
  }
  .stat-value {
    font-size: var(--hm-font-xl);
  }
}

@media (max-width: 480px) {
  .dashboard-date {
    display: none;
  }
  .stat-card :deep(.el-card__body) {
    padding: var(--hm-space-4);
  }
}

/* ==================== Codex 深色仪表盘收口 ==================== */
.dashboard-page {
  animation: hm-reveal 0.56s cubic-bezier(0.22, 1, 0.36, 1) both;
}
.stat-card,
.data-card,
.filter-card {
  border-color: var(--hm-ink-100);
  background: var(--hm-bg-surface);
  box-shadow: none;
}
.stat-card--product,
.stat-card--order,
.stat-card--user,
.stat-card--sales {
  border-color: var(--hm-ink-100);
  background:
    linear-gradient(135deg, rgba(139, 233, 176, 0.06), transparent 48%),
    var(--hm-bg-surface);
}
.stat-icon,
.panel-badge {
  border: 1px solid var(--hm-primary-border);
  background: var(--hm-primary-soft);
  color: var(--hm-primary);
}
.stat-value,
.stat-money,
.order-no,
.panel-head h2 {
  color: var(--hm-ink-900);
}
.stat-foot,
.panel-head p {
  color: var(--hm-ink-500);
}
.dashboard-date {
  border-color: var(--hm-ink-100);
  background: var(--hm-ink-050);
}
.data-card :deep(.el-card__header),
.filter-card :deep(.el-card__body) {
  background: var(--hm-bg-subtle);
  border-color: var(--hm-ink-100);
}
/* ==================== 经典灰白仪表盘 ==================== */
.stat-card,
.data-card,
.filter-card {
  border-color: var(--hm-ink-100);
  background: var(--hm-bg-surface);
  box-shadow: none;
}
.stat-card--product,
.stat-card--order,
.stat-card--user,
.stat-card--sales {
  border-color: var(--hm-ink-100);
  background: var(--hm-bg-surface);
}
.stat-icon,
.panel-badge {
  border: 1px solid var(--hm-primary-border);
  background: var(--hm-primary-soft);
  color: var(--hm-primary);
}
.stat-value,
.stat-money,
.order-no,
.panel-head h2 {
  color: var(--hm-ink-900);
}
.stat-foot,
.panel-head p {
  color: var(--hm-ink-500);
}
.dashboard-date {
  border-color: var(--hm-ink-100);
  background: var(--hm-bg-subtle);
}
.data-card :deep(.el-card__header),
.filter-card :deep(.el-card__body) {
  background: var(--hm-bg-subtle);
  border-color: var(--hm-ink-100);
}
</style>
