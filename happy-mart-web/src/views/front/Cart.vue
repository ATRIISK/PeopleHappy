<script setup>
/**
 * 购物车页组件
 * 展示购物车商品列表，支持全选/勾选、数量修改、删除、结算操作
 * 套用 FrontLayout 布局，作为路由 /cart 的子页面
 */
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, ShoppingCart } from '@element-plus/icons-vue'
import { useCartStore } from '@/stores/cart'

// 路由实例，用于跳转
const router = useRouter()

// ==================== Pinia 状态 ====================

/** 购物车 store，提供完整的 CRUD 方法 */
const cartStore = useCartStore()

// ==================== 响应式状态 ====================

/** 已勾选商品 ID 集合（存储 productId） */
const checkedIds = ref(new Set())

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
 * 修改商品数量
 * 调用 cartStore.updateQuantity 同步到后端
 * @param {Object} item - 购物车商品对象
 * @param {number} newQuantity - 新数量
 */
async function handleQuantityChange(item, newQuantity) {
  // 数量不能小于 1（el-input-number min=1 已约束，但做二次校验）
  if (newQuantity < 1) return

  try {
    await cartStore.updateQuantity(item.productId, newQuantity)
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
 * 提示演示功能，然后清空已结算商品（已勾选的商品）
 */
async function handleCheckout() {
  if (checkedCount.value === 0) {
    ElMessage.warning('请先选择要结算的商品')
    return
  }

  try {
    await ElMessageBox.alert(
      `当前为演示模式，已为您处理 ${checkedCount.value} 件商品，合计 ¥${checkedTotal.value.toFixed(2)}`,
      '模拟结算',
      {
        confirmButtonText: '知道了',
        type: 'success'
      }
    )

    // 清空已结算的勾选商品
    const removePromises = checkedItems.value.map(item =>
      cartStore.removeItem(item.productId)
    )
    await Promise.all(removePromises)

    // 重置勾选状态
    checkedIds.value = new Set()
    ElMessage.success('结算完成，感谢您的购买！')
  } catch {
    // 用户关闭弹窗，不处理
  }
}

/**
 * 去逛逛：跳转到商品列表页
 */
function goShopping() {
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

              <!-- 数量列 -->
              <td class="col-quantity">
                <el-input-number
                  :model-value="item.quantity"
                  :min="1"
                  :max="item.stock || 9999"
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
</style>
