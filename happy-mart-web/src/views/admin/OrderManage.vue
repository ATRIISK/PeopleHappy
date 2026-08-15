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
  <div>
    <!-- ==================== 页面标题 + 筛选 ==================== -->
    <div class="toolbar">
      <h1 class="page-title">订单管理</h1>
      <!-- 状态筛选 Tab
           v-model 双向绑定：选中值自动写回 activeStatus（code-review 修复：
           原来用 :model-value 单向绑定 + @change 不接收参数，选中状态永远停在"全部"） -->
      <el-radio-group v-model="activeStatus" @change="handleStatusChange">
        <el-radio-button v-for="tab in statusTabs" :key="tab.value" :value="tab.value">
          {{ tab.label }}
        </el-radio-button>
      </el-radio-group>
    </div>

    <!-- ==================== 订单表格 ==================== -->
    <el-card shadow="never">
      <el-table :data="orderList" v-loading="loading" empty-text="暂无订单" stripe>
        <el-table-column prop="orderNo" label="订单号" min-width="190" />
        <el-table-column prop="username" label="下单人" width="110" />
        <el-table-column label="金额" width="110">
          <template #default="{ row }">¥{{ Number(row.totalAmount).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <!-- 状态彩色标签 -->
          <template #default="{ row }">
            <el-tag :type="statusTypeMap[row.status]" size="small">
              {{ ORDER_STATUS[row.status] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="下单时间" min-width="180" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <!-- 只有"已支付"（status=1）的订单才能发货 -->
            <el-button v-if="row.status === 1" link type="primary" @click="handleShip(row)">
              发货
            </el-button>
            <span v-else style="color: #ccc">-</span>
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
  </div>
</template>

<style scoped>
/* 工具栏：标题 + 状态筛选 */
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

/* 分页靠右 */
.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
