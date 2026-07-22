<script setup>
/**
 * 首页组件
 * 包含 Banner 轮播、分类快捷入口、热门商品推荐三个区域
 * 套用 FrontLayout 布局，作为路由的默认首页
 */
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { mockProducts, categories } from '@/mock/products'

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
 * 从 mock 数据中取前 8 个分类，用于快捷入口展示
 */
const categoryList = categories.slice(0, 8)

/**
 * 热门商品列表
 * 从 mock 数据中取前 8 个商品，用于热门推荐展示
 */
const hotProducts = ref([])

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
 * 组件挂载时加载热门商品数据
 * mockProducts 为同步数据，但使用 async 保持与 Mock 函数一致性
 */
onMounted(async () => {
  // 取前 8 个商品作为热门推荐
  hotProducts.value = mockProducts.slice(0, 8)
})
</script>

<template>
  <div class="home-page">
    <!-- ==================== Banner 轮播区域 ==================== -->
    <section class="banner-section">
      <el-carousel
        height="360px"
        indicator-position="dots"
        arrow="always"
        :interval="4000"
      >
        <el-carousel-item v-for="banner in banners" :key="banner.id">
          <!-- 每个 banner 使用背景图展示，标题文字白色带阴影 -->
          <div
            class="banner-item"
            :style="{ backgroundImage: `url(${banner.image})` }"
          >
            <div class="banner-title">{{ banner.title }}</div>
          </div>
        </el-carousel-item>
      </el-carousel>
    </section>

    <!-- ==================== 分类快捷入口区域 ==================== -->
    <section class="category-section">
      <!-- section-title：左侧红色边框线 4px -->
      <div class="section-title">分类快捷入口</div>
      <!-- 8 列网格，2 行 x 4 列 -->
      <div class="category-grid">
        <div
          v-for="cat in categoryList"
          :key="cat.id"
          class="category-item"
          @click="goToCategory(cat.id)"
        >
          <!-- 分类图标，使用 <component :is> 动态渲染 Element Plus 图标 -->
          <div class="category-icon">
            <el-icon :size="32">
              <component :is="categoryIconMap[cat.name]" />
            </el-icon>
          </div>
          <!-- 分类名称 -->
          <span class="category-name">{{ cat.name }}</span>
        </div>
      </div>
    </section>

    <!-- ==================== 热门商品推荐区域 ==================== -->
    <section class="hot-section">
      <div class="section-title">热门推荐</div>
      <!-- 4 列商品卡片网格 -->
      <div class="product-grid">
        <div
          v-for="product in hotProducts"
          :key="product.id"
          class="product-card"
          @click="goToProduct(product.id)"
        >
          <!-- 商品图片，aspect-ratio: 1 保持正方形 -->
          <div class="product-image-wrapper">
            <el-image
              :src="product.image"
              :alt="product.name"
              class="product-image"
              fit="cover"
            />
          </div>
          <!-- 商品信息 -->
          <div class="product-info">
            <!-- 商品名称（单行省略） -->
            <div class="product-name" :title="product.name">{{ product.name }}</div>
            <!-- 价格行：现价 + 原价（划线） -->
            <div class="product-price-row">
              <span class="product-price">¥{{ product.price.toFixed(2) }}</span>
              <span class="product-original-price">¥{{ product.originalPrice.toFixed(2) }}</span>
            </div>
            <!-- 评分和销量 -->
            <div class="product-meta">
              <el-rate
                :model-value="product.rating"
                disabled
                show-score
                text-color="#ff9900"
                score-template="{value}"
              />
              <span class="product-sales">已售 {{ product.sales }}</span>
            </div>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
/* ==================== 首页容器 ==================== */
.home-page {
  /* 父级 main-content 已有 padding，这里不再重复添加 */
}

/* ==================== Banner 轮播区域 ==================== */
.banner-section {
  /* 负边距抵消父级的 padding，使 banner 满宽显示 */
  margin: -20px -20px 30px;
}

/* 每个 Banner 项：背景图居中覆盖 */
.banner-item {
  width: 100%;
  height: 100%;
  background-size: cover;
  background-position: center;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* Banner 标题：白色文字带深色阴影，突出显示 */
.banner-title {
  font-size: 36px;
  font-weight: bold;
  color: #fff;
  text-shadow: 2px 2px 8px rgba(0, 0, 0, 0.6);
  letter-spacing: 4px;
  user-select: none;
}

/* ==================== 公共 Section 标题样式 ==================== */
.section-title {
  font-size: 22px;
  font-weight: bold;
  color: #1a1a2e;
  padding-left: 14px;
  border-left: 4px solid #e4393c;
  margin-bottom: 20px;
  line-height: 1;
}

/* ==================== 分类快捷入口区域 ==================== */
.category-section {
  margin-bottom: 40px;
}

/* 8 列网格布局 */
.category-grid {
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  gap: 12px;
}

/* 每个分类项：垂直居中布局 */
.category-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 24px 10px;
  background: #fff;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s ease;
}

/* 分类项 hover 效果：背景变红，文字变白，上移 2px */
.category-item:hover {
  background: #e4393c;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(228, 57, 60, 0.3);
}

/* hover 时分类图标变白 */
.category-item:hover .category-icon :deep(svg) {
  color: #fff;
}

/* hover 时分类名称变白 */
.category-item:hover .category-name {
  color: #fff;
}

/* 分类图标容器 */
.category-icon {
  margin-bottom: 8px;
}

/* 分类图标默认颜色（红色） */
.category-icon :deep(svg) {
  color: #e4393c;
  transition: color 0.3s ease;
}

/* 分类名称文字 */
.category-name {
  font-size: 14px;
  color: #333;
  transition: color 0.3s ease;
}

/* ==================== 热门商品推荐区域 ==================== */
.hot-section {
  margin-bottom: 40px;
}

/* 4 列网格布局，16px 间距 */
.product-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

/* 商品卡片 */
.product-card {
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.3s ease;
}

/* 卡片 hover 效果：上移 4px，阴影加深 */
.product-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
}

/* 商品图片容器：保持 1:1 宽高比 */
.product-image-wrapper {
  aspect-ratio: 1;
  overflow: hidden;
}

/* 商品图片 */
.product-image {
  width: 100%;
  height: 100%;
  display: block;
}

/* 商品信息区 */
.product-info {
  padding: 12px;
}

/* 商品名称：单行省略号 */
.product-name {
  font-size: 14px;
  color: #333;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-bottom: 8px;
}

/* 价格行：现价和原价并排 */
.product-price-row {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 8px;
}

/* 现价：红色 20px 粗体 */
.product-price {
  font-size: 20px;
  font-weight: bold;
  color: #e4393c;
}

/* 原价：划线灰色小字 */
.product-original-price {
  font-size: 13px;
  color: #999;
  text-decoration: line-through;
}

/* 评分与销量行 */
.product-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

/* 销量文字 */
.product-sales {
  font-size: 12px;
  color: #999;
  white-space: nowrap;
}
</style>
