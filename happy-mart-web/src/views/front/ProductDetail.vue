<script setup>
/**
 * 商品详情页组件
 * 展示商品图片、价格、库存等信息，支持加入购物车和立即购买操作
 * 套用 FrontLayout 布局，通过路由参数 id 获取商品数据
 */
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ShoppingCart } from '@element-plus/icons-vue'
import { getProductById } from '@/api/product'
import { useUserStore } from '@/stores/user'
import { useCartStore } from '@/stores/cart'
// 数量规范化公共工具（与购物车页共用，code-review 二轮修复去重）
import { normalizeQuantity as normalizeQty } from '@/utils/quantity'

// 路由
const route = useRoute()
const router = useRouter()

// Pinia 状态
const userStore = useUserStore()
const cartStore = useCartStore()

// ==================== 状态变量 ====================

/** 商品数据对象 */
const product = ref(null)

/** 加载状态 */
const loading = ref(true)

/** 加购/立即购买请求中标记（v1.13 code-review F1 防连点）：
 *  请求进行中把按钮置为 loading + 忽略重复点击，防用户连点并发加购 */
const addingToCart = ref(false)

/** 错误信息（加载失败或商品不存在） */
const error = ref('')

/** 当前选中的图片索引（主图） */
const currentImageIndex = ref(0)

/** 购买数量 */
const quantity = ref(1)

// ==================== 计算属性 ====================

/** 当前主图 URL */
const currentImage = computed(() => {
  if (!product.value) return ''
  // 优先使用商品图片列表，如果没有则使用单张主图
  const images = product.value.images
  if (images && images.length > 0) {
    return images[currentImageIndex.value] || images[0]
  }
  return product.value.image || ''
})

/** 商品图片列表（用于缩略图展示） */
const imageList = computed(() => {
  if (!product.value) return []
  const images = product.value.images
  if (images && images.length > 0) {
    return images
  }
  // 如果没有图片列表，使用单张主图作为唯一缩略图
  return product.value.image ? [product.value.image] : []
})

/** 库存是否紧张（小于 100） */
const isLowStock = computed(() => {
  return product.value && product.value.stock < 100
})

/** 库存是否为零（按钮禁用） */
const isOutOfStock = computed(() => {
  return product.value && product.value.stock <= 0
})

// ==================== 数据加载 ====================

/**
 * 加载商品详情
 * 从路由参数中获取商品 ID，调用 getProductById() 异步加载数据
 */
async function loadProduct() {
  loading.value = true
  error.value = ''

  try {
    // 从路由参数获取商品 ID
    const id = Number(route.params.id)

    // 校验 ID 是否有效
    if (!id || isNaN(id)) {
      throw new Error('商品 ID 无效')
    }

    // 调用后端 API 获取商品详情
    const result = await getProductById(id)

    // 检查商品是否存在
    if (!result) {
      error.value = '商品不存在或已下架'
      product.value = null
      return
    }

    // 更新商品数据
    product.value = result
    // 重置图片索引和数量
    currentImageIndex.value = 0
    quantity.value = 1
  } catch (err) {
    console.error('加载商品详情失败:', err)
    error.value = err.message || '加载商品详情失败，请稍后重试'
    product.value = null
  } finally {
    loading.value = false
  }
}

// ==================== 事件处理 ====================

/**
 * 切换主图
 * @param {number} index - 图片索引
 */
function switchImage(index) {
  currentImageIndex.value = index
}

/**
 * 规范化并校验购买数量
 * <p>
 * 数量选择器支持直接输入数字（el-input-number），用户在输入框里可能
 * 输入 0、负数、空值、小数或超过库存的大数字。这里统一做一次兜底：
 * - 先向下取整（后端 quantity 是 Integer，小数必须转成整数，如 2.5 → 2）
 * - 非法值（0 / 负数 / 空）→ 归为 1
 * - 超过库存（且库存 > 0）→ 钳制为库存上限
 * 保证"加入购物车 / 立即购买"时传给后端的 quantity 永远是合法整数。
 * @returns {number} 合法数量（1 ~ 商品库存，无库存时返回 1）
 */
function normalizeQuantity() {
  // 调用公共工具（utils/quantity.js）：只取整 + 至少 1（刻意不在此钳制库存，
  // "超过库存"由 handleAddToCart / handleBuyNow 判断并提示"库存不足"）
  const qty = normalizeQty(quantity.value)
  quantity.value = qty   // 回写输入框，保证显示与提交一致
  return qty
}

/**
 * 加入购物车
 * 未登录：提示并跳转登录页
 * 已登录：调用购物车接口添加商品
 */
async function handleAddToCart() {
  // ★ 防连点（v1.13 code-review F1）：请求进行中忽略重复点击
  if (addingToCart.value) return

  // 检查登录状态
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录')
    router.push({ name: 'Login', query: { redirect: route.fullPath } })
    return
  }

  // 校验数量：取整 + 至少 1
  const qty = normalizeQuantity()

  // ★ 超过库存 → 提示"库存不足"，不静默钳制成最大库存数
  if (product.value.stock > 0 && qty > product.value.stock) {
    ElMessage.error(`库存不足，仅剩 ${product.value.stock} 件`)
    return
  }

  addingToCart.value = true
  try {
    // 调用购物车 Store 的 addItem 方法（传入合法数量）
    await cartStore.addItem(product.value.id, qty)
    ElMessage.success(`已成功将 ${product.value.name} 加入购物车（${qty} 件）`)
  } catch (err) {
    console.error('加入购物车失败:', err)
    ElMessage.error('加入购物车失败，请稍后重试')
  } finally {
    addingToCart.value = false
  }
}

/**
 * 立即购买
 *
 * 流程：
 * 1. 先把这个商品+数量加入购物车
 * 2. 再跳转到购物车页，用户可继续勾选/结算
 *
 * 未登录：提示并跳转登录页
 */
async function handleBuyNow() {
  // ★ 防连点（v1.13 code-review F1）：请求进行中忽略重复点击
  if (addingToCart.value) return

  // 检查登录状态
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录')
    router.push({ name: 'Login', query: { redirect: route.fullPath } })
    return
  }

  // 校验数量：取整 + 至少 1
  const qty = normalizeQuantity()

  // ★ 超过库存 → 提示"库存不足"，不静默钳制成最大库存数
  if (product.value.stock > 0 && qty > product.value.stock) {
    ElMessage.error(`库存不足，仅剩 ${product.value.stock} 件`)
    return
  }

  addingToCart.value = true
  try {
    // 先把商品加入购物车（带上用户输入/选择的合法数量）
    await cartStore.addItem(product.value.id, qty)
    ElMessage.success(`已加入购物车，共 ${qty} 件`)
    // 跳转到购物车页，用户可继续勾选/结算
    router.push({ name: 'Cart' })
  } catch (err) {
    console.error('加入购物车失败:', err)
    ElMessage.error('操作失败，请稍后重试')
  } finally {
    addingToCart.value = false
  }
}

// ==================== 生命周期 ====================

// 组件挂载时加载商品数据
onMounted(() => {
  loadProduct()
})
</script>

<template>
  <div class="product-detail-page">
    <!-- ==================== 加载中状态 ==================== -->
    <div v-if="loading" class="loading-container">
      <el-icon class="loading-icon" :size="32">
        <Loading />
      </el-icon>
      <span class="loading-text">加载中...</span>
    </div>

    <!-- ==================== 错误/不存在状态 ==================== -->
    <div v-else-if="error" class="error-container">
      <el-empty :description="error" />
      <el-button type="primary" round @click="loadProduct">重新加载</el-button>
    </div>

    <!-- ==================== 商品详情内容 ==================== -->
    <template v-else-if="product">
      <!-- ========== 面包屑导航 ========== -->
      <el-breadcrumb class="breadcrumb" separator="/">
        <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
        <el-breadcrumb-item :to="{ name: 'Products', query: { categoryId: product.categoryId } }">
          {{ product.categoryName }}
        </el-breadcrumb-item>
        <el-breadcrumb-item>{{ product.name }}</el-breadcrumb-item>
      </el-breadcrumb>

      <!-- ========== 商品主体：左右两栏布局 ========== -->
      <div class="product-main">
        <!-- ===== 左侧图片区 ===== -->
        <div class="image-section">
          <div class="main-image-wrapper">
            <span class="image-badge">自营精选</span>
            <el-image
              :src="currentImage"
              :alt="product.name"
              class="main-image"
              fit="cover"
            />
          </div>

          <!-- 缩略图列表 -->
          <div class="thumbnail-list">
            <div
              v-for="(img, index) in imageList"
              :key="index"
              class="thumbnail-item"
              :class="{ active: currentImageIndex === index }"
              @click="switchImage(index)"
            >
              <el-image
                :src="img"
                :alt="`${product.name} - 图 ${index + 1}`"
                class="thumbnail-image"
                fit="cover"
              />
            </div>
          </div>
        </div>

        <!-- ===== 右侧信息区 ===== -->
        <div class="info-section">
          <div class="category-eyebrow">{{ product.categoryName }} · HAPPY MART</div>
          <h1 class="product-name">{{ product.name }}</h1>
          <p class="product-desc">{{ product.description }}</p>

          <!-- 价格区块 -->
          <div class="price-block">
            <div class="price-caption">到手价</div>
            <div class="price-row">
              <span class="current-price"><small>¥</small>{{ product.price.toFixed(2) }}</span>
              <span class="original-price">¥{{ product.originalPrice.toFixed(2) }}</span>
            </div>
          </div>

          <!-- 信息行：销量、评分、库存 -->
          <div class="meta-row">
            <div class="meta-item">
              <span class="meta-label">销量</span>
              <span class="meta-value">{{ product.sales }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">评分</span>
              <el-rate
                :model-value="product.rating"
                disabled
                show-score
                text-color="#d9992b"
                score-template="{value}"
              />
            </div>
            <div class="meta-item">
              <span class="meta-label">库存</span>
              <span
                class="meta-value"
                :class="{ 'low-stock': isLowStock, 'out-of-stock': isOutOfStock }"
              >
                {{ isOutOfStock ? '暂无库存' : product.stock }}
              </span>
            </div>
          </div>

          <!-- 数量选择器：点击 − / + 按钮增减（保留原交互），中间输入框可直接输入数字 -->
          <!-- el-input-number 默认布局就是"− [输入框] +"，不设 controls-position：
               点击左侧 − 减少、点击右侧 + 增加，中间输入框支持键盘直接输入，
               输入值自动钳制到 [1, 库存] 范围 -->
          <div class="quantity-row">
            <span class="quantity-label">数量</span>
            <el-input-number
              v-model="quantity"
              :min="1"
              :disabled="isOutOfStock"
              :precision="0"
              value-on-clear="1"
              style="width: 140px"
            />
            <span class="stock-tip">点击 − / + 增减，也可直接输入数量（库存 {{ product.stock }} 件）</span>
          </div>

          <!-- 操作按钮 -->
          <div class="action-row">
            <el-button
              type="danger"
              size="large"
              class="add-cart-btn"
              :icon="ShoppingCart"
              :disabled="isOutOfStock"
              :loading="addingToCart"
              @click="handleAddToCart"
            >
              加入购物车
            </el-button>
            <el-button
              type="warning"
              size="large"
              class="buy-now-btn"
              :disabled="isOutOfStock"
              :loading="addingToCart"
              @click="handleBuyNow"
            >
              立即购买
            </el-button>
          </div>
        </div>
      </div>

      <!-- ========== 商品详情 Tab ========== -->
      <div class="detail-tabs">
        <el-tabs type="border-card">
          <!-- 商品描述 -->
          <el-tab-pane label="商品描述">
            <div class="tab-content description-content">
              <p>{{ product.description }}</p>
              <div class="detail-images">
                <el-image
                  v-for="(img, index) in imageList"
                  :key="index"
                  :src="img"
                  :alt="`${product.name} 详情图 ${index + 1}`"
                  class="detail-image"
                  fit="cover"
                />
              </div>
            </div>
          </el-tab-pane>

          <!-- 规格参数（占位） -->
          <el-tab-pane label="规格参数">
            <div class="tab-content placeholder-content">
              <el-empty description="规格参数加载中..." />
            </div>
          </el-tab-pane>

          <!-- 用户评价（占位） -->
          <el-tab-pane label="用户评价">
            <div class="tab-content placeholder-content">
              <el-empty description="暂无用户评价" />
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </template>
  </div>
</template>

<style scoped>
.product-detail-page {
  min-width: 0;
}
.loading-container,
.error-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--hm-space-4);
  min-height: 420px;
  border: 1px solid var(--hm-ink-100);
  border-radius: var(--hm-radius-xl);
  background: var(--hm-white);
}
.loading-icon {
  color: var(--hm-primary);
  animation: rotating 1.5s linear infinite;
}
@keyframes rotating {
  to { transform: rotate(360deg); }
}
.loading-text {
  color: var(--hm-ink-400);
  font-size: var(--hm-font-base);
}
.breadcrumb {
  margin-bottom: var(--hm-space-4);
  padding: 0 var(--hm-space-1);
  font-size: var(--hm-font-sm);
}

/* ==================== 商品主体 ==================== */
.product-main {
  display: flex;
  gap: var(--hm-space-10);
  align-items: flex-start;
  margin-bottom: var(--hm-space-6);
  padding: var(--hm-space-6);
  border: 1px solid var(--hm-ink-100);
  border-radius: var(--hm-radius-xl);
  background: var(--hm-white);
  box-shadow: var(--hm-shadow-sm);
}
.image-section {
  width: 46%;
  flex-shrink: 0;
  min-width: 0;
}
.main-image-wrapper {
  position: relative;
  aspect-ratio: 1;
  margin-bottom: var(--hm-space-3);
  overflow: hidden;
  border: 1px solid var(--hm-ink-100);
  border-radius: var(--hm-radius-lg);
  background: var(--hm-ink-050);
}
.main-image {
  width: 100%;
  height: 100%;
  display: block;
}
.image-badge {
  position: absolute;
  z-index: 2;
  top: 14px;
  left: 14px;
  padding: 5px 12px;
  border-radius: var(--hm-radius-pill);
  background: rgba(36, 29, 23, 0.82);
  color: #ffd9a8;
  font-size: var(--hm-font-xs);
  letter-spacing: 1px;
}
.thumbnail-list {
  display: flex;
  gap: var(--hm-space-3);
  overflow-x: auto;
  padding: 2px;
}
.thumbnail-item {
  width: 68px;
  height: 68px;
  flex-shrink: 0;
  overflow: hidden;
  border: 2px solid transparent;
  border-radius: var(--hm-radius-md);
  cursor: pointer;
  transition: all var(--hm-transition);
}
.thumbnail-item:hover,
.thumbnail-item.active {
  border-color: var(--hm-primary);
}
.thumbnail-item.active {
  box-shadow: 0 0 0 3px var(--hm-primary-soft);
}
.thumbnail-image {
  width: 100%;
  height: 100%;
  display: block;
}

/* ==================== 商品信息 ==================== */
.info-section {
  flex: 1;
  min-width: 0;
  padding-top: var(--hm-space-2);
}
.category-eyebrow {
  margin-bottom: var(--hm-space-3);
  color: var(--hm-primary);
  font-size: var(--hm-font-xs);
  font-weight: var(--hm-fw-bold);
  letter-spacing: 1.5px;
}
.product-name {
  margin: 0 0 var(--hm-space-3);
  color: var(--hm-ink-900);
  font-size: 28px;
  font-weight: var(--hm-fw-bold);
  line-height: 1.4;
  letter-spacing: 0.5px;
}
.product-desc {
  margin: 0 0 var(--hm-space-5);
  color: var(--hm-ink-500);
  font-size: var(--hm-font-base);
  line-height: 1.8;
}
.price-block {
  position: relative;
  margin-bottom: var(--hm-space-5);
  padding: var(--hm-space-4) var(--hm-space-5);
  overflow: hidden;
  border: 1px solid #f6ddcd;
  border-radius: var(--hm-radius-lg);
  background: linear-gradient(110deg, #fdeee5, #fdf7f0);
}
.price-block::after {
  content: 'SALE';
  position: absolute;
  right: 16px;
  bottom: -14px;
  color: rgba(232, 80, 30, 0.07);
  font-size: 68px;
  font-weight: 700;
  letter-spacing: 3px;
  pointer-events: none;
}
.price-caption {
  margin-bottom: 2px;
  color: var(--hm-ink-500);
  font-size: var(--hm-font-xs);
}
.price-row {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: baseline;
  gap: var(--hm-space-3);
}
.current-price {
  color: var(--hm-primary);
  font-size: 38px;
  font-weight: 700;
  line-height: 1.25;
  font-variant-numeric: tabular-nums;
}
.current-price small {
  margin-right: 3px;
  font-size: 19px;
  font-weight: var(--hm-fw-medium);
}
.original-price {
  color: var(--hm-ink-400);
  font-size: var(--hm-font-md);
  text-decoration: line-through;
}
.meta-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin-bottom: var(--hm-space-5);
  padding: var(--hm-space-4) 0;
  border-top: 1px solid var(--hm-ink-100);
  border-bottom: 1px solid var(--hm-ink-100);
}
.meta-item {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--hm-space-2);
  min-width: 0;
  border-right: 1px solid var(--hm-ink-100);
}
.meta-item:last-child {
  border-right: none;
}
.meta-label {
  color: var(--hm-ink-400);
  font-size: var(--hm-font-sm);
  white-space: nowrap;
}
.meta-value {
  color: var(--hm-ink-800);
  font-size: var(--hm-font-base);
  font-weight: var(--hm-fw-medium);
}
.meta-value.low-stock,
.meta-value.out-of-stock {
  color: var(--hm-primary);
  font-weight: var(--hm-fw-bold);
}
.quantity-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--hm-space-3);
  margin-bottom: var(--hm-space-6);
}
.quantity-label {
  color: var(--hm-ink-500);
  font-size: var(--hm-font-base);
}
.stock-tip {
  color: var(--hm-ink-400);
  font-size: var(--hm-font-xs);
}
.action-row {
  display: flex;
  gap: var(--hm-space-3);
}
.action-row .el-button {
  height: 46px;
  margin: 0;
  padding: 0 28px;
  border-radius: var(--hm-radius-pill);
  font-size: var(--hm-font-md);
}
.action-row :deep(.add-cart-btn.el-button) {
  border-color: var(--hm-primary);
  background: var(--hm-primary);
  box-shadow: 0 8px 20px rgba(232, 80, 30, 0.2);
}
.action-row :deep(.add-cart-btn.el-button:hover) {
  border-color: var(--hm-primary-hover);
  background: var(--hm-primary-hover);
}
.action-row :deep(.buy-now-btn.el-button) {
  border-color: var(--hm-ink-900);
  background: var(--hm-ink-900);
  color: #fff;
}
.action-row :deep(.buy-now-btn.el-button:hover) {
  border-color: var(--hm-ink-700);
  background: var(--hm-ink-700);
  color: #fff;
}

/* ==================== 详情 Tab ==================== */
.detail-tabs {
  margin-bottom: var(--hm-space-6);
}
.detail-tabs :deep(.el-tabs--border-card) {
  overflow: hidden;
  border: 1px solid var(--hm-ink-100);
  border-radius: var(--hm-radius-lg);
  background: var(--hm-white);
  box-shadow: var(--hm-shadow-sm);
}
.detail-tabs :deep(.el-tabs--border-card > .el-tabs__header) {
  border-bottom-color: var(--hm-ink-100);
  background: var(--hm-bg-subtle);
}
.detail-tabs :deep(.el-tabs--border-card > .el-tabs__header .el-tabs__item) {
  color: var(--hm-ink-500);
  border: none;
}
.detail-tabs :deep(.el-tabs--border-card > .el-tabs__header .el-tabs__item.is-active) {
  background: var(--hm-white);
  color: var(--hm-primary);
  font-weight: var(--hm-fw-bold);
}
.tab-content {
  min-height: 200px;
  padding: var(--hm-space-5);
}
.description-content {
  color: var(--hm-ink-600);
  font-size: var(--hm-font-base);
  line-height: 1.9;
}
.detail-images {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--hm-space-4);
  margin-top: var(--hm-space-5);
}
.detail-image {
  width: 100%;
  max-width: 800px;
  overflow: hidden;
  border: 1px solid var(--hm-ink-100);
  border-radius: var(--hm-radius-lg);
}
.placeholder-content {
  display: flex;
  align-items: center;
  justify-content: center;
}

@media (max-width: 1024px) {
  .product-main {
    gap: var(--hm-space-6);
    padding: var(--hm-space-5);
  }
  .product-name {
    font-size: 24px;
  }
  .meta-item {
    flex-direction: column;
    gap: 2px;
  }
}

@media (max-width: 768px) {
  .product-main {
    flex-direction: column;
  }
  .image-section {
    width: 100%;
  }
  .info-section {
    width: 100%;
  }
  .meta-item {
    flex-direction: row;
  }
}

@media (max-width: 480px) {
  .product-main {
    gap: var(--hm-space-5);
    padding: var(--hm-space-4);
    border-radius: var(--hm-radius-lg);
  }
  .product-name {
    font-size: 22px;
  }
  .current-price {
    font-size: 32px;
  }
  .meta-row {
    gap: 0;
  }
  .meta-item {
    flex-direction: column;
  }
  .meta-item :deep(.el-rate) {
    height: 20px;
  }
  .stock-tip {
    width: 100%;
  }
  .action-row {
    flex-direction: column;
  }
  .action-row .el-button {
    width: 100%;
  }
  .tab-content {
    padding: var(--hm-space-3);
  }
}
</style>
