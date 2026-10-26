<script setup>
/**
 * 首页组件
 * 包含 Banner 轮播、分类快捷入口、热门商品推荐三个区域
 * 套用 FrontLayout 布局，作为路由的默认首页
 */
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getHotProducts } from '@/api/product'
import { getCategoryTree } from '@/api/category'
import StateBlock from '@/components/StateBlock.vue'   // 统一加载/空/错误状态展示

// 路由实例，用于页面跳转
const router = useRouter()

/**
 * 分类图标映射表
 * 将分类名称映射到 Element Plus 图标组件名
 * 所有图标已在 main.js 中全局注册
 */
const categoryIconMap = {
  '手机数码': 'Cellphone',    // Element Plus 图标名是 Cellphone 不是 Smartphone
  '电脑办公': 'Monitor',
  '家用电器': 'Refresh',
  '服装鞋帽': 'Tickets',
  '食品生鲜': 'Apple',
  '图书教育': 'Reading',
  '母婴玩具': 'Present',
  '运动户外': 'TrendCharts'
}

/**
 * Banner 轮播数据
 * 3 张促销横幅，使用 picsum.photos 占位图
 */
const banners = [
  { id: 1, image: 'https://picsum.photos/seed/homebanner1/1400/400', title: '618 年中大促' },
  { id: 2, image: 'https://picsum.photos/seed/homebanner2/1400/400', title: '新品首发' },
  { id: 3, image: 'https://picsum.photos/seed/homebanner3/1400/400', title: '品牌特卖' }
]

/**
 * 分类列表
 * 从后端 API 获取全部分类，取前 8 个一级分类用于快捷入口展示
 */
const categoryList = ref([])

/**
 * 热门商品列表
 * 从后端 API 获取销量 Top 8 商品，用于热门推荐展示
 */
const hotProducts = ref([])

/**
 * 热门推荐区加载状态（仅用于展示骨架屏，数据请求逻辑未变）
 */
const hotLoading = ref(true)

/**
 * 热门推荐区加载失败标记（仅用于展示错误提示，可点击重试）
 */
const hotError = ref(false)

/**
 * 跳转到分类商品列表
 * @param {number} categoryId - 分类ID
 */
function goToCategory(categoryId) {
  router.push({ name: 'Products', query: { categoryId } })
}

/**
 * 跳转到商品详情页
 * @param {number} id - 商品ID
 */
function goToProduct(id) {
  router.push({ name: 'ProductDetail', params: { id } })
}

/**
 * 组件挂载时加载分类和热门商品数据
 */
onMounted(async () => {
  try {
    // 获取分类树，取前 8 个一级分类作为快捷入口
    const tree = await getCategoryTree()
    categoryList.value = tree.slice(0, 8)
  } catch (err) {
    console.error('加载分类数据失败:', err)
    categoryList.value = []
  }

  // 加载热门商品 Top 8（loading/error 仅用于展示，接口调用与参数保持原样）
  hotLoading.value = true
  hotError.value = false
  try {
    hotProducts.value = await getHotProducts()
  } catch (err) {
    console.error('加载热门商品失败:', err)
    hotProducts.value = []
    hotError.value = true
  } finally {
    hotLoading.value = false
  }
})

/**
 * 重新加载热门推荐（供 StateBlock 的 retry 事件调用）
 * 复用同一接口，不新增请求参数
 */
async function reloadHotProducts() {
  hotLoading.value = true
  hotError.value = false
  try {
    hotProducts.value = await getHotProducts()
  } catch (err) {
    console.error('重新加载热门商品失败:', err)
    hotProducts.value = []
    hotError.value = true
  } finally {
    hotLoading.value = false
  }
}
</script>

<template>
  <div class="home-page">
    <!-- ==================== Banner 轮播区域 ==================== -->
    <section class="banner-section">
      <el-carousel
        height="360px"
        indicator-position="outside"
        arrow="always"
        :interval="4000"
      >
        <el-carousel-item v-for="banner in banners" :key="banner.id">
          <div class="banner-item">
            <div class="hero-copy">
              <span class="hero-kicker">HAPPY MART · 全品类自营</span>
              <h1>{{ banner.title }}</h1>
              <p>把日常所需，一次买齐。<br>手机数码到食品生鲜，热门好物安心选。</p>
              <router-link to="/products" class="hero-cta">
                去逛逛
                <span aria-hidden="true">→</span>
              </router-link>
            </div>
            <div v-if="hotProducts[banner.id - 1]" class="hero-visual">
              <div class="hero-image-wrap">
                <img
                  :src="hotProducts[banner.id - 1].image"
                  :alt="hotProducts[banner.id - 1].name"
                  class="hero-image"
                >
              </div>
              <div class="hero-price-tag">
                <span>热门爆款</span>
                <strong>¥{{ hotProducts[banner.id - 1].price.toFixed(2) }}</strong>
              </div>
            </div>
            <div v-else class="hero-placeholder">
              <span>众</span>
            </div>
          </div>
        </el-carousel-item>
      </el-carousel>
    </section>

    <!-- ==================== 分类快捷入口区域 ==================== -->
    <section class="category-section">
      <div class="hm-section-head">
        <h2 class="hm-section-title">分类快捷入口</h2>
        <router-link to="/products" class="hm-section-head__more">全部分类 →</router-link>
      </div>
      <div class="category-grid">
        <div
          v-for="cat in categoryList"
          :key="cat.id"
          class="category-item"
          @click="goToCategory(cat.id)"
        >
          <div class="category-icon">
            <el-icon :size="28">
              <component :is="categoryIconMap[cat.name]" />
            </el-icon>
          </div>
          <span class="category-name">{{ cat.name }}</span>
        </div>
      </div>
    </section>

    <!-- ==================== 热门商品推荐区域 ==================== -->
    <section class="hot-section">
      <div class="hm-section-head">
        <h2 class="hm-section-title">热门推荐</h2>
        <router-link to="/products" class="hm-section-head__more">查看全部 →</router-link>
      </div>

      <!-- 加载中：骨架屏占位，避免布局跳动 -->
      <StateBlock
        v-if="hotLoading"
        type="loading"
        compact
        :rows="4"
        message="正在加载热门推荐…"
      />

      <!-- 加载失败：提示并可重试（复用同一接口） -->
      <StateBlock
        v-else-if="hotError"
        type="error"
        compact
        message="热门推荐加载失败"
        @retry="reloadHotProducts"
      />

      <!-- 无数据：空状态提示 -->
      <StateBlock
        v-else-if="hotProducts.length === 0"
        type="empty"
        compact
        message="暂无热门推荐"
        description="稍后再来看看"
      />

      <!-- 有数据：4 列商品卡片网格 -->
      <div v-else class="hm-product-grid">
        <div
          v-for="product in hotProducts"
          :key="product.id"
          class="hm-product-card"
          @click="goToProduct(product.id)"
        >
          <div class="hm-product-card__thumb">
            <span class="hm-product-card__flag">热卖</span>
            <el-image
              :src="product.image"
              :alt="product.name"
              fit="cover"
            />
          </div>
          <div class="hm-product-card__info">
            <div class="hm-product-card__name" :title="product.name">{{ product.name }}</div>
            <div class="hm-product-card__price-row">
              <span class="hm-product-card__price">¥{{ product.price.toFixed(2) }}</span>
              <span class="hm-product-card__orig">¥{{ product.originalPrice.toFixed(2) }}</span>
            </div>
            <div class="hm-product-card__meta">
              <el-rate
                :model-value="product.rating"
                disabled
                show-score
                text-color="#d9992b"
                score-template="{value}"
              />
              <span>已售 {{ product.sales }}</span>
            </div>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.home-page {
  min-width: 0;
}

/* ==================== 主视觉轮播 ==================== */
.banner-section {
  margin-bottom: var(--hm-space-2);
}
.banner-item {
  position: relative;
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  gap: var(--hm-space-10);
  padding: 34px 64px 40px;
  overflow: hidden;
  border: 1px solid #f3d9c5;
  border-radius: 24px;
  background: linear-gradient(115deg, #fdeedd 0%, #f9e2cd 58%, #f6e9dc 100%);
  box-sizing: border-box;
}
.banner-item::before,
.banner-item::after {
  content: '';
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
}
.banner-item::before {
  width: 300px;
  height: 300px;
  right: -100px;
  top: -150px;
  border: 55px solid rgba(255, 255, 255, 0.28);
}
.banner-item::after {
  width: 140px;
  height: 140px;
  left: 46%;
  bottom: -100px;
  background: rgba(255, 255, 255, 0.2);
}
.hero-copy {
  position: relative;
  z-index: 1;
  flex: 1.1;
  min-width: 0;
}
.hero-kicker {
  display: inline-block;
  margin-bottom: var(--hm-space-3);
  color: var(--hm-primary);
  font-size: var(--hm-font-xs);
  font-weight: var(--hm-fw-bold);
  letter-spacing: 2px;
}
.hero-copy h1 {
  margin: 0;
  color: var(--hm-ink-900);
  font-size: 42px;
  line-height: 1.2;
  letter-spacing: 3px;
}
.hero-copy p {
  margin: var(--hm-space-4) 0 0;
  color: var(--hm-ink-500);
  font-size: var(--hm-font-md);
  line-height: 1.9;
}
.hero-cta {
  display: inline-flex;
  align-items: center;
  gap: var(--hm-space-3);
  height: 44px;
  margin-top: var(--hm-space-5);
  padding: 0 26px;
  border-radius: var(--hm-radius-pill);
  background: var(--hm-primary);
  color: #fff;
  font-size: var(--hm-font-md);
  font-weight: var(--hm-fw-bold);
  box-shadow: 0 8px 20px rgba(232, 80, 30, 0.24);
  transition: all var(--hm-transition);
}
.hero-cta:hover {
  background: var(--hm-primary-hover);
  transform: translateY(-2px);
  box-shadow: 0 12px 24px rgba(232, 80, 30, 0.3);
}
.hero-visual {
  position: relative;
  z-index: 1;
  flex: 1;
  height: 250px;
  min-width: 0;
}
.hero-image-wrap {
  position: absolute;
  top: 0;
  right: 34px;
  width: 250px;
  height: 250px;
  padding: 8px;
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.75);
  box-shadow: 0 22px 44px rgba(90, 50, 20, 0.16);
  transform: rotate(2deg);
}
.hero-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 21px;
}
.hero-price-tag {
  position: absolute;
  right: 0;
  bottom: -8px;
  min-width: 118px;
  padding: 10px 16px;
  border: 1px solid rgba(255, 255, 255, 0.8);
  border-radius: var(--hm-radius-lg);
  background: rgba(255, 255, 255, 0.94);
  box-shadow: var(--hm-shadow-md);
}
.hero-price-tag span {
  display: block;
  color: var(--hm-ink-500);
  font-size: var(--hm-font-xs);
}
.hero-price-tag strong {
  display: block;
  margin-top: 2px;
  color: var(--hm-primary);
  font-size: var(--hm-font-lg);
  font-variant-numeric: tabular-nums;
}
.hero-placeholder {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}
.hero-placeholder span {
  display: grid;
  place-items: center;
  width: 180px;
  height: 180px;
  border-radius: 40px;
  background: rgba(255, 255, 255, 0.55);
  color: var(--hm-primary);
  font-size: 72px;
  font-weight: 700;
  transform: rotate(4deg);
}
.banner-section :deep(.el-carousel__indicators) {
  margin-top: 14px;
}
.banner-section :deep(.el-carousel__button) {
  width: 18px;
  height: 5px;
  background: var(--hm-ink-300);
  opacity: 1;
}
.banner-section :deep(.el-carousel__indicator.is-active .el-carousel__button) {
  width: 28px;
  background: var(--hm-primary);
}
.banner-section :deep(.el-carousel__arrow) {
  background: rgba(255, 255, 255, 0.85);
  color: var(--hm-ink-800);
  box-shadow: var(--hm-shadow-sm);
}
.banner-section :deep(.el-carousel__arrow:hover) {
  background: var(--hm-primary);
  color: #fff;
}

/* ==================== 分类快捷入口 ==================== */
.category-section,
.hot-section {
  margin-bottom: var(--hm-space-6);
}
.category-grid {
  display: grid;
  grid-template-columns: repeat(8, minmax(0, 1fr));
  gap: var(--hm-space-3);
}
.category-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  min-height: 104px;
  padding: var(--hm-space-4) var(--hm-space-2);
  border: 1px solid var(--hm-ink-100);
  border-radius: var(--hm-radius-lg);
  background: var(--hm-white);
  box-shadow: var(--hm-shadow-sm);
  cursor: pointer;
  transition: all var(--hm-transition);
}
.category-item:hover {
  border-color: var(--hm-primary-border);
  background: var(--hm-primary-soft);
  transform: translateY(-4px);
  box-shadow: var(--hm-shadow-md);
}
.category-icon {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  border-radius: 15px;
  background: var(--hm-primary-soft);
  color: var(--hm-primary);
  transition: all var(--hm-transition);
}
.category-item:hover .category-icon {
  background: var(--hm-primary);
  color: #fff;
  transform: rotate(-4deg);
}
.category-name {
  color: var(--hm-ink-700);
  font-size: var(--hm-font-sm);
  white-space: nowrap;
}

/* ==================== 响应式 ==================== */
@media (max-width: 1024px) {
  .banner-item {
    padding: 30px 44px 38px;
  }
  .hero-copy h1 {
    font-size: 36px;
  }
  .hero-image-wrap {
    right: 10px;
    width: 220px;
    height: 220px;
  }
  .hero-visual {
    height: 230px;
  }
  .category-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .banner-item {
    padding: 28px 28px 34px;
  }
  .hero-copy h1 {
    font-size: 32px;
  }
  .hero-copy p {
    font-size: var(--hm-font-base);
  }
  .hero-visual,
  .hero-placeholder {
    display: none;
  }
  .banner-section :deep(.el-carousel__arrow) {
    display: none;
  }
}

@media (max-width: 480px) {
  .banner-section :deep(.el-carousel__container) {
    height: 310px !important;
  }
  .banner-item {
    padding: 26px 22px 30px;
    border-radius: var(--hm-radius-xl);
  }
  .hero-copy h1 {
    font-size: 28px;
    letter-spacing: 2px;
  }
  .hero-copy p {
    font-size: var(--hm-font-sm);
  }
  .hero-kicker {
    font-size: 10px;
    letter-spacing: 1px;
  }
  .hero-cta {
    height: 40px;
    padding: 0 22px;
    font-size: var(--hm-font-base);
  }
  .category-grid {
    gap: var(--hm-space-2);
  }
  .category-item {
    min-height: 88px;
    padding: var(--hm-space-3) 2px;
  }
  .category-icon {
    width: 40px;
    height: 40px;
    border-radius: 12px;
  }
  .category-name {
    font-size: var(--hm-font-xs);
  }
}
</style>
