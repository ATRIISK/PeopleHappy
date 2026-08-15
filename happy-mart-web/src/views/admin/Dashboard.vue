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
    const [statsRes, recentRes] = await Promise.all([
      getDashboardStats(),
      getRecentOrders()
    ])
    stats.value = statsRes
    recentOrders.value = recentRes || []
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
  <div>
    <!-- ==================== 页面标题 ==================== -->
    <h1 class="page-title">数据看板</h1>

    <!-- ==================== 四张统计卡片 ==================== -->
    <el-row :gutter="20" class="stat-row">
      <!-- 商品总数 -->
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-label">商品总数</div>
          <div class="stat-value">{{ stats.productCount }}</div>
        </el-card>
      </el-col>
      <!-- 订单总数 -->
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-label">订单总数</div>
          <div class="stat-value">{{ stats.orderCount }}</div>
        </el-card>
      </el-col>
      <!-- 用户总数 -->
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-label">用户总数</div>
          <div class="stat-value">{{ stats.userCount }}</div>
        </el-card>
      </el-col>
      <!-- 总交易额（红色高亮，单位元） -->
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-label">总交易额</div>
          <div class="stat-value stat-money">¥{{ Number(stats.totalSales).toFixed(2) }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- ==================== 最近订单 ==================== -->
    <el-card shadow="never">
      <template #header>
        <span style="font-weight: bold">最近订单</span>
      </template>
      <el-table :data="recentOrders" v-loading="loading" empty-text="暂无订单">
        <el-table-column prop="orderNo" label="订单号" min-width="190" />
        <el-table-column prop="username" label="下单人" width="120" />
        <el-table-column label="金额" width="120">
          <!-- 金额用模板格式化：保留两位小数 -->
          <template #default="{ row }">¥{{ Number(row.totalAmount).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <!-- 状态用彩色标签展示 -->
          <template #default="{ row }">
            <el-tag :type="statusTypeMap[row.status]" size="small">
              {{ ORDER_STATUS[row.status] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="下单时间" min-width="180" />
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
/* 页面标题（与前台页面风格一致） */
.page-title {
  font-size: 22px;
  font-weight: bold;
  color: #1a1a2e;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 2px solid #f0f0f0;
}

/* 统计卡片行的下边距 */
.stat-row {
  margin-bottom: 20px;
}

/* 卡片里的"指标名称"文字 */
.stat-label {
  font-size: 14px;
  color: #666;
  margin-bottom: 8px;
}

/* 卡片里的"指标数字" */
.stat-value {
  font-size: 28px;
  font-weight: bold;
  color: #1a1a2e;
}

/* 交易额用红色突出（商城习惯） */
.stat-money {
  color: #e4393c;
}
</style>
