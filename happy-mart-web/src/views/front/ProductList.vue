<script setup>
/**
 * 商品列表页组件
 * 左侧分类筛选（el-tree）+ 右侧商品网格
 * 支持分类筛选、关键词搜索（来自路由 query）、排序、分页
 */
import { ref, watch, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getProducts } from '@/api/product'
import { getCategoryTree } from '@/api/category'

// 路由
const route = useRoute()
const router = useRouter()

// ==================== 状态变量 ====================

/** 当前选中的分类ID（用于 el-tree 高亮，'all' 表示全部分类） */
const currentCategoryId = ref('all')

/** 商品列表数据 */
const products = ref([])

/** 总记录数 */
const total = ref(0)

/** 当前页码 */
const currentPage = ref(1)

/** 每页条数 */
const pageSize = ref(12)

/** 排序方式 */
const sortBy = ref('')

/** 加载状态 */
const loading = ref(false)

/** 错误信息 */
const error = ref('')

// ==================== 分类数据 ====================

/** 分类树数据（从后端 API 获取） */
const categories = ref([])

/** 分类树加载状态 */
const categoriesLoading = ref(true)

/**
 * 将分类数据转换为 el-tree 需要的格式
 * 外层套一个"全部分类"根节点，将 name 字段映射为 label 字段
 */
const treeData = computed(() => {
  return [
    {
      id: 'all',
      label: '全部分类',
      children: categories.value.map(cat => ({
        id: cat.id,
        label: cat.name,
        children: (cat.children || []).map(child => ({
          id: child.id,
          label: child.name
        }))
      }))
    }
  ]
})

// ==================== 排序选项 ====================

/** 排序下拉选项列表 */
const sortOptions = [
  { value: '', label: '综合排序' },
  { value: 'sales', label: '销量优先' },
  { value: 'price_asc', label: '价格从低到高' },
  { value: 'price_desc', label: '价格从高到低' },
  { value: 'newest', label: '最新上架' }
]

// ==================== 数据加载 ====================

/**
 * 加载商品列表
 * 从路由 query 中获取 categoryId 和 keyword，调用 getProducts() 获取数据
 * getProducts() 是异步函数，返回 Promise，需要 await
 */
async function loadProducts() {
  loading.value = true
  error.value = ''

  try {
    // 构建查询参数
    const params = {
      categoryId: route.query.categoryId || undefined,
      keyword: route.query.keyword || undefined,
      sortBy: sortBy.value || undefined,
      page: currentPage.value,
      size: pageSize.value
    }

    // 调用后端 API 获取商品数据
    const result = await getProducts(params)

    // 更新商品列表和总数（后端返回 records/total/current/size）
    products.value = result.records || []
    total.value = result.total || 0
  } catch (err) {
    // 加载失败时显示错误信息
    console.error('加载商品列表失败:', err)
    error.value = '加载商品列表失败，请稍后重试'
    products.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

// ==================== 事件处理 ====================

/**
 * 点击分类节点：更新路由 query 中的 categoryId
 * @param {Object} data - el-tree 节点数据对象
 */
function handleNodeClick(data) {
  // 如果点击的是"全部分类"（id 为 'all'），移除 categoryId 参数
  if (data.id === 'all') {
    router.push({ query: { ...route.query, categoryId: undefined } })
  } else {
    // 否则设置对应的分类ID
    router.push({ query: { ...route.query, categoryId: data.id } })
  }
}

/**
 * 排序方式变更时重新加载数据
 * 切换到第一页
 */
function handleSortChange() {
  currentPage.value = 1
  loadProducts()
}

/**
 * 页码切换时重新加载数据
 * @param {number} page - 新页码
 */
function handlePageChange(page) {
  currentPage.value = page
  loadProducts()
}

/**
 * 每页条数变更时重置到第一页并重新加载
 * @param {number} size - 新每页条数
 */
function handleSizeChange(size) {
  pageSize.value = size
  currentPage.value = 1
  loadProducts()
}

/**
 * 跳转到商品详情页
 * @param {number} id - 商品ID
 */
function goToProduct(id) {
  router.push({ name: 'ProductDetail', params: { id } })
}

// ==================== 加载分类数据 ====================

/** 组件挂载时从后端获取分类树数据 */
onMounted(async () => {
  try {
    categories.value = await getCategoryTree()
  } catch (err) {
    console.error('加载分类树失败:', err)
    categories.value = []
  } finally {
    categoriesLoading.value = false
  }
})

// ==================== 监听路由变化 ====================

/**
 * 监听路由 query 参数变化（categoryId / keyword）
 * 如果用户通过搜索框或顶部导航栏切换了分类/关键词，自动重新加载商品数据
 * 设置 immediate: true 确保组件创建时立即加载一次数据
 */
watch(
  () => route.query,
  (newQuery) => {
    // 更新树节点的选中状态
    // 如果 query 中没有 categoryId，选中"全部分类"根节点
    currentCategoryId.value = newQuery.categoryId ? Number(newQuery.categoryId) : 'all'
    // 重置到第一页
    currentPage.value = 1
    // 重新加载商品数据
    loadProducts()
  },
  { immediate: true }
)
</script>

<template>
  <div class="product-list-page">
    <!-- ==================== 左侧分类筛选侧边栏 ==================== -->
    <aside class="sidebar">
      <div class="sidebar-title">商品分类</div>
      <el-tree
        :data="treeData"
        :props="{ label: 'label', children: 'children' }"
        node-key="id"
        :highlight-current="true"
        :default-expand-all="true"
        :current-node-key="currentCategoryId"
        @node-click="handleNodeClick"
      />
    </aside>

    <!-- ==================== 右侧商品主区域 ==================== -->
    <div class="main-area">

      <!-- 顶部工具栏：显示商品总数 + 排序下拉 -->
      <div class="toolbar">
        <span class="total-info">共 {{ total }} 件商品</span>
        <el-select
          v-model="sortBy"
          placeholder="排序方式"
          size="small"
          style="width: 150px"
          @change="handleSortChange"
        >
          <el-option
            v-for="option in sortOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </div>

      <!-- 加载中状态：居中显示旋转的 Loading 图标 -->
      <div v-if="loading" class="loading-container">
        <el-icon class="loading-icon" :size="32" color="#409eff">
          <Loading />
        </el-icon>
        <span class="loading-text">加载中...</span>
      </div>

      <!-- 错误状态：显示错误提示和重新加载按钮 -->
      <div v-else-if="error" class="error-container">
        <el-empty :description="error" />
        <el-button type="primary" @click="loadProducts">重新加载</el-button>
      </div>

      <!-- 空状态：无匹配商品时显示 -->
      <div v-else-if="products.length === 0" class="empty-container">
        <el-empty description="暂无相关商品" />
      </div>

      <!-- 有数据时显示商品网格和分页 -->
      <template v-else>
        <!-- 4 列商品卡片网格 -->
        <div class="product-grid">
          <div
            v-for="product in products"
            :key="product.id"
            class="product-card"
            @click="goToProduct(product.id)"
          >
            <!-- 商品图片（1:1 比例） -->
            <div class="product-image-wrapper">
              <el-image
                :src="product.image"
                :alt="product.name"
                class="product-image"
                fit="cover"
              />
            </div>
            <!-- 商品信息区域 -->
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

        <!-- 分页组件 -->
        <div class="pagination-wrapper">
          <el-pagination
            v-model:current-page="currentPage"
            v-model:page-size="pageSize"
            :page-sizes="[12, 24, 36, 48]"
            :total="total"
            layout="total, sizes, prev, pager, next, jumper"
            background
            @current-change="handlePageChange"
            @size-change="handleSizeChange"
          />
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
/* ==================== 页面布局：侧边栏 + 主区域 ==================== */
.product-list-page {
  display: flex;
  gap: 20px;
  align-items: flex-start;
}

/* ==================== 左侧分类筛选侧边栏 ==================== */
.sidebar {
  width: 200px;
  flex-shrink: 0;
  background: #fff;
  border: 1px solid #eee;
  border-radius: 8px;
  padding: 16px;
  /* 粘性定位，滚动时侧边栏保持在可视区域 */
  position: sticky;
  top: 20px;
}

/* 侧边栏标题 */
.sidebar-title {
  font-size: 16px;
  font-weight: bold;
  color: #1a1a2e;
  margin-bottom: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid #eee;
}

/* ==================== 右侧商品主区域 ==================== */
.main-area {
  flex: 1;
  min-width: 0;
}

/* ==================== 顶部工具栏 ==================== */
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding: 12px 16px;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #eee;
}

/* 商品总数文字 */
.total-info {
  font-size: 14px;
  color: #666;
}

/* ==================== 加载中状态 ==================== */
.loading-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 0;
  gap: 12px;
}

/* 旋转的 Loading 图标动画 */
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

/* 加载文字 */
.loading-text {
  font-size: 14px;
  color: #999;
}

/* ==================== 错误状态 ==================== */
.error-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 60px 0;
  gap: 16px;
}

/* ==================== 空状态 ==================== */
.empty-container {
  display: flex;
  justify-content: center;
  padding: 60px 0;
}

/* ==================== 商品网格 ==================== */
.product-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

/* ==================== 商品卡片 ==================== */
.product-card {
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.3s ease;
}

/* 卡片 hover 效果：上移 3px，阴影加深 */
.product-card:hover {
  transform: translateY(-3px);
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

/* 商品信息区域 */
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

/* 现价：红色加粗大号显示 */
.product-price {
  font-size: 20px;
  font-weight: bold;
  color: #e4393c;
}

/* 原价：灰色划线小字 */
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

/* ==================== 分页 ==================== */
.pagination-wrapper {
  display: flex;
  justify-content: center;
  padding: 20px 0;
}
</style>
