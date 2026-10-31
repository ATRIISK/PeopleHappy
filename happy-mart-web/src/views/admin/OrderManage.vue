<script setup>
/**
 * 管理后台：订单管理页
 *
 * 功能：按状态筛选 / 分页查看全部订单（含下单人）、发货（已支付 1 → 已发货 2）
 * 数据来源：GET /api/admin/order/list + PUT /api/admin/order/status
 * 取消/退单/确认收货属于用户端操作，管理员只负责"发货"。
 */
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAdminOrders, updateAdminOrderStatus } from '@/api/admin'

// ==================== 响应式状态 ====================

/** 订单列表数据 */
const orderList = ref([])

/** 加载状态 */
const loading = ref(false)

/** 当前选中的状态筛选值（null=全部） */
const activeStatus = ref(null)

/** 分页参数 */
const currentPage = ref(1)   // 当前页码
const pageSize = ref(10)     // 每页条数
const total = ref(0)         // 总条数

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

/** 状态标签颜色 */
const statusTypeMap = {
  0: 'danger',
  1: 'warning',
  2: 'primary',
  3: 'success',
  4: 'info',
  5: 'info'
}

/** 筛选 Tab 列表（全部 + 各状态） */
const statusTabs = [
  { label: '全部', value: null },
  { label: '待付款', value: 0 },
  { label: '已支付', value: 1 },
  { label: '已发货', value: 2 },
  { label: '已完成', value: 3 },
  { label: '已取消', value: 4 },
  { label: '已退款', value: 5 }
]

// ==================== 数据加载 ====================

/**
 * 加载订单列表（分页 + 可选状态筛选）
 */
async function loadData() {
  loading.value = true
  try {
    const res = await getAdminOrders({
      status: activeStatus.value === null ? undefined : activeStatus.value,
      page: currentPage.value,
      size: pageSize.value
    })
    orderList.value = res.records || []
    total.value = res.total || 0
    // 发货/删除最后一页的最后一条后，当前页可能超出总页数 → 回退到最后一页重新加载（code-review 修复）
    if (orderList.value.length === 0 && currentPage.value > 1) {
      currentPage.value--
      await loadData()
      return
    }
  } catch (err) {
    console.error('获取订单列表失败:', err)
  } finally {
    loading.value = false
  }
}

/**
 * 切换状态 Tab 时：回到第一页重新加载
 */
function handleStatusChange() {
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

// ==================== 发货 ====================

/**
 * 发货（订单状态 1 已支付 → 2 已发货）
 * 二次确认后调后端接口；后端用白名单状态机 + 条件更新防并发，
 * 若订单状态已被其他操作改掉，后端会返回"订单状态异常"提示。
 */
async function handleShip(row) {
  try {
    await ElMessageBox.confirm(
      `确定对订单「${row.orderNo}」进行发货操作吗？`,
      '确认发货',
      {
        confirmButtonText: '确定发货',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    await updateAdminOrderStatus({ id: row.id, status: 2 })
    ElMessage.success('发货成功')
    await loadData()
  } catch {
    // 用户取消操作，不处理
  }
}

// ==================== 生命周期 ====================

onMounted(() => {
  loadData()
})
</script>

<template>
  <div class="order-page">
    <!-- ==================== 页面标题 + 筛选 ==================== -->
    <div class="hm-page-header">
      <div>
        <h1 class="hm-page-header__title">订单管理</h1>
        <p class="hm-page-header__desc">筛选交易状态并处理待发货订单</p>
      </div>
      <span class="order-summary">共 {{ total }} 笔订单</span>
    </div>

    <el-card class="filter-card" shadow="never">
      <div class="filter-toolbar">
        <div class="filter-copy">
          <strong>订单状态</strong>
          <span>按当前交易阶段筛选</span>
        </div>
        <!-- 状态筛选 Tab
             v-model 双向绑定：选中值自动写回 activeStatus（code-review 修复：
             原来用 :model-value 单向绑定 + @change 不接收参数，选中状态永远停在"全部"） -->
        <el-radio-group v-model="activeStatus" class="status-filter" @change="handleStatusChange">
          <el-radio-button v-for="tab in statusTabs" :key="tab.value" :value="tab.value">
            {{ tab.label }}
          </el-radio-button>
        </el-radio-group>
      </div>
    </el-card>

    <!-- ==================== 订单表格 ==================== -->
    <el-card class="data-card" shadow="never">
      <template #header>
        <div class="data-card__head">
          <div>
            <h2>订单列表</h2>
            <p>查看订单金额、进度与下单时间</p>
          </div>
          <span class="record-count">{{ total }} 条记录</span>
        </div>
      </template>
      <div class="table-scroll">
      <el-table :data="orderList" v-loading="loading" empty-text="暂无订单">
        <el-table-column prop="orderNo" label="订单号" min-width="200">
          <template #default="{ row }"><span class="order-no">{{ row.orderNo }}</span></template>
        </el-table-column>
        <el-table-column prop="username" label="下单人" width="110" />
        <el-table-column label="金额" width="120">
          <template #default="{ row }"><span class="hm-price">¥{{ Number(row.totalAmount).toFixed(2) }}</span></template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <!-- 状态彩色标签 -->
          <template #default="{ row }">
            <el-tag :type="statusTypeMap[row.status]" size="small" effect="light" round>
              {{ ORDER_STATUS[row.status] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="下单时间" min-width="180" />
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <!-- 只有"已支付"（status=1）的订单才能发货 -->
            <el-button v-if="row.status === 1" type="primary" size="small" plain @click="handleShip(row)">
              发货
            </el-button>
            <span v-else class="empty-action">-</span>
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
  </div>
</template>

<style scoped>
/* ==================== 工具栏（标题 + 右侧操作） ==================== */
.order-page .hm-page-header {
  margin-bottom: var(--hm-space-5);
}
.order-summary,
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
  gap: var(--hm-space-5);
}
.filter-copy {
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
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
.status-filter {
  flex-wrap: wrap;
}
.status-filter :deep(.el-radio-button__inner) {
  border-color: var(--hm-ink-200);
  color: var(--hm-ink-600);
  background: var(--hm-white);
}
.status-filter :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  border-color: var(--hm-primary);
  background: var(--hm-primary);
  box-shadow: -1px 0 0 0 var(--hm-primary);
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
  min-width: 730px; /* 低于此宽度列会挤成一团，宁可横向滚动 */
}
.table-scroll :deep(.el-table__inner-wrapper::before) {
  display: none;
}
.table-scroll :deep(.el-table th.el-table__cell) {
  height: 46px;
}
.table-scroll :deep(.el-table td.el-table__cell) {
  padding: 14px 0;
}
.order-no {
  color: var(--hm-ink-800);
  font-weight: var(--hm-fw-medium);
  font-variant-numeric: tabular-nums;
}
.empty-action {
  display: inline-block;
  width: 48px;
  color: var(--hm-ink-300);
  text-align: center;
}

/* 表格内的次要文字 */
.hm-cell-secondary {
  color: var(--hm-ink-500);
  font-size: var(--hm-font-sm);
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
    flex-direction: column;
    gap: var(--hm-space-3);
  }
}

@media (max-width: 768px) {
  .data-card__head {
    align-items: flex-start;
  }
}

@media (max-width: 480px) {
  .order-summary {
    display: none;
  }
  .filter-card :deep(.el-card__body),
  .data-card :deep(.el-card__header),
  .pagination-wrap {
    padding-left: var(--hm-space-4);
    padding-right: var(--hm-space-4);
  }
  .record-count {
    display: none;
  }
}

/* ==================== Codex 深色订单管理收口 ==================== */
.order-page {
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
.filter-card :deep(.el-card__body),
.data-card :deep(.el-card__header),
.data-card :deep(.el-card__body) {
  background: transparent;
  border-color: var(--hm-ink-100);
}
.data-card__head h2,
.order-no {
  color: var(--hm-ink-900);
}
.data-card__head p,
.hm-cell-secondary {
  color: var(--hm-ink-500);
}
.status-filter :deep(.el-radio-button__inner) {
  background: var(--hm-ink-050);
  border-color: var(--hm-ink-100);
  color: var(--hm-ink-500);
}
.status-filter :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  background: var(--hm-primary-soft);
  border-color: var(--hm-primary-border);
  color: var(--hm-primary);
  box-shadow: none;
}
.pagination-wrap {
  background: var(--hm-bg-subtle);
}
</style>
