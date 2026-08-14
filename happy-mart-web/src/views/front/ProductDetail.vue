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

  try {
    // 调用购物车 Store 的 addItem 方法（传入合法数量）
    await cartStore.addItem(product.value.id, qty)
    ElMessage.success(`已成功将 ${product.value.name} 加入购物车（${qty} 件）`)
  } catch (err) {
    console.error('加入购物车失败:', err)
    ElMessage.error('加入购物车失败，请稍后重试')
  }
}

/**
 * 立即购买
 *
 * 流程：
 * 1. 先把这个商品+数量加入购物车
 * 2. 再跳转到购物车页，用户可继续结算
 *
 * 未登录：提示并跳转登录页
 */
async function handleBuyNow() {
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

  try {
    // 先把商品加入购物车（带上用户输入/选择的合法数量）
    await cartStore.addItem(product.value.id, qty)
    ElMessage.success(`已加入购物车，共 ${qty} 件`)
    // 跳转到购物车页，用户可继续勾选/结算
    router.push({ name: 'Cart' })
  } catch (err) {
    console.error('加入购物车失败:', err)
    ElMessage.error('操作失败，请稍后重试')
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
      <el-icon class="loading-icon" :size="32" color="#409eff">
        <Loading />
      </el-icon>
      <span class="loading-text">加载中...</span>
    </div>

    <!-- ==================== 错误/不存在状态 ==================== -->
    <div v-else-if="error" class="error-container">
      <el-empty :description="error" />
      <el-button type="primary" @click="loadProduct">重新加载</el-button>
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
          <!-- 主图大图 -->
          <div class="main-image-wrapper">
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
          <!-- 商品名称 -->
          <h1 class="product-name">{{ product.name }}</h1>

          <!-- 商品描述 -->
          <p class="product-desc">{{ product.description }}</p>

          <!-- 价格区块 -->
          <div class="price-block">
            <div class="price-row">
              <span class="current-price">¥{{ product.price.toFixed(2) }}</span>
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
                text-color="#ff9900"
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
              :icon="ShoppingCart"
              :disabled="isOutOfStock"
              @click="handleAddToCart"
            >
              加入购物车
            </el-button>
            <el-button
              type="warning"
              size="large"
              :disabled="isOutOfStock"
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
              <!-- 商品图片预览（大图） -->
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
/* ==================== 页面容器 ==================== */
.product-detail-page {
  /* 父级 main-content 已有 padding */
}

/* ==================== 加载中状态 ==================== */
.loading-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 120px 0;
  gap: 12px;
}

.loading-icon {
  animation: rotating 1.5s linear infinite;
}

@keyframes rotating {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.loading-text {
  font-size: 14px;
  color: #999;
}

/* ==================== 错误状态 ==================== */
.error-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 80px 0;
  gap: 16px;
}

/* ==================== 面包屑导航 ==================== */
.breadcrumb {
  margin-bottom: 24px;
  font-size: 14px;
}

/* ==================== 商品主体：左右两栏布局 ==================== */
.product-main {
  display: flex;
  gap: 40px;
  margin-bottom: 40px;
  align-items: flex-start;
}

/* ===== 左侧图片区 ===== */
.image-section {
  width: 480px;
  flex-shrink: 0;
}

/* 主图容器 */
.main-image-wrapper {
  aspect-ratio: 1;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #eee;
  margin-bottom: 12px;
}

.main-image {
  width: 100%;
  height: 100%;
  display: block;
}

/* 缩略图列表 */
.thumbnail-list {
  display: flex;
  gap: 8px;
}

/* 单个缩略图项 */
.thumbnail-item {
  width: 72px;
  height: 72px;
  border-radius: 4px;
  overflow: hidden;
  cursor: pointer;
  border: 2px solid transparent;
  transition: border-color 0.2s ease;
  flex-shrink: 0;
}

/* 缩略图 hover 效果 */
.thumbnail-item:hover {
  border-color: #e4393c;
}

/* 选中状态：红色边框 */
.thumbnail-item.active {
  border-color: #e4393c;
}

.thumbnail-image {
  width: 100%;
  height: 100%;
  display: block;
}

/* ===== 右侧信息区 ===== */
.info-section {
  flex: 1;
  min-width: 0;
}

/* 商品名称 */
.product-name {
  font-size: 22px;
  font-weight: bold;
  color: #1a1a2e;
  line-height: 1.4;
  margin-bottom: 8px;
}

/* 商品描述 */
.product-desc {
  font-size: 14px;
  color: #999;
  line-height: 1.6;
  margin-bottom: 20px;
}

/* 价格区块 */
.price-block {
  background: #fff5f5;
  padding: 16px 20px;
  border-radius: 8px;
  margin-bottom: 20px;
}

.price-row {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

/* 现价：36px 红色粗体 */
.current-price {
  font-size: 36px;
  font-weight: bold;
  color: #e4393c;
  line-height: 1;
}

/* 原价：划线灰色 */
.original-price {
  font-size: 16px;
  color: #999;
  text-decoration: line-through;
}

/* 信息行 */
.meta-row {
  display: flex;
  align-items: center;
  gap: 32px;
  margin-bottom: 20px;
  padding: 12px 0;
  border-top: 1px solid #f0f0f0;
  border-bottom: 1px solid #f0f0f0;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 6px;
}

.meta-label {
  font-size: 14px;
  color: #999;
}

.meta-value {
  font-size: 14px;
  color: #333;
  font-weight: 500;
}

/* 库存紧张：红色标记 */
.meta-value.low-stock {
  color: #e4393c;
  font-weight: bold;
}

/* 库存为零 */
.meta-value.out-of-stock {
  color: #e4393c;
  font-weight: bold;
}

/* 数量选择行 */
.quantity-row {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
}

.quantity-label {
  font-size: 14px;
  color: #999;
}

/* 库存提示文字（数量选择器右侧） */
.stock-tip {
  font-size: 13px;
  color: #999;
}

/* 操作按钮行 */
.action-row {
  display: flex;
  gap: 12px;
}

.action-row .el-button {
  padding: 12px 28px;
  font-size: 15px;
}

/* ==================== 商品详情 Tab ==================== */
.detail-tabs {
  margin-bottom: 40px;
}

/* Tab 内容区 */
.tab-content {
  padding: 20px;
  min-height: 200px;
}

/* 商品描述内容 */
.description-content {
  line-height: 1.8;
  color: #555;
  font-size: 14px;
}

/* 商品详情大图列表 */
.detail-images {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-top: 20px;
}

.detail-image {
  width: 100%;
  max-width: 800px;
  border-radius: 8px;
  border: 1px solid #eee;
}

/* 占位内容居中 */
.placeholder-content {
  display: flex;
  justify-content: center;
  align-items: center;
}
</style>
