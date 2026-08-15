<script setup>
/**
 * 管理后台：商品管理页
 *
 * 功能：搜索 / 分页查看全部商品（上架+下架）、新增商品、编辑商品、上架/下架、删除
 * 数据来源：GET /api/admin/product/list 等（见 @/api/admin.js）
 * 项目首次引入 el-table 的后台页面（Element Plus 全量引入，无需额外配置）
 */
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAdminProducts, saveAdminProduct, deleteAdminProduct, updateAdminProductStatus } from '@/api/admin'
import { getCategoryTree } from '@/api/category'

// ==================== 响应式状态 ====================

/** 商品列表数据 */
const productList = ref([])

/** 加载状态 */
const loading = ref(false)

/** 搜索关键词 */
const keyword = ref('')

/** 分页参数 */
const currentPage = ref(1)   // 当前页码
const pageSize = ref(10)     // 每页条数
const total = ref(0)         // 总条数

/** 分类下拉选项（一二级拍平成扁平列表） */
const categoryOptions = ref([])

// ==================== 新增/编辑弹窗状态 ====================

/** 弹窗是否显示 */
const dialogVisible = ref(false)

/** 弹窗标题（新增商品 / 编辑商品） */
const dialogTitle = ref('')

/** 弹窗提交 loading */
const submitting = ref(false)

/** 表单引用（用于校验） */
const formRef = ref(null)

/** 商品表单数据 */
const form = reactive({
  id: null,            // null=新增，有值=修改
  name: '',            // 商品名称
  categoryId: null,    // 分类ID
  price: 0,            // 现价
  originalPrice: null, // 原价（可选）
  stock: 0,            // 库存
  status: 0,           // 0=上架，1=下架
  rating: 0,           // 评分（表单里不提供输入，编辑时保留原值，新增默认 0）
  image: '',           // 主图 URL
  images: '',          // 轮播图 JSON 字符串（如 ["url1","url2"]）
  description: ''      // 描述
})

/** 表单校验规则 */
const rules = {
  name: [{ required: true, message: '请输入商品名称', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }],
  price: [{ required: true, message: '请输入价格', trigger: 'blur' }],
  stock: [{ required: true, message: '请输入库存', trigger: 'blur' }]
}

// ==================== 图片本地上传（v1.10） ====================

/**
 * 上传请求头：el-upload 走原生 XHR，不经过 axios 拦截器，
 * 必须手动把 Bearer token 放进 headers（后端 @Auth(requireAdmin=true) 才能通过）
 * 用 ref 而不是常量（code-review 修复）：SPA 登出再登录后 token 会变，
 * 常量在组件加载时快照旧 token 会导致上传一直 401；打开弹窗时调 refreshUploadHeaders() 刷新
 */
const uploadHeaders = ref({ Authorization: 'Bearer ' + (localStorage.getItem('token') || '') })

/** 打开新增/编辑弹窗时刷新上传请求头（登出重登后 token 已更新，避免旧 token 快照） */
function refreshUploadHeaders() {
  uploadHeaders.value.Authorization = 'Bearer ' + (localStorage.getItem('token') || '')
}

/** 主图是否上传中（防重复点击） */
const mainUploading = ref(false)

/** 轮播图 URL 数组（和 form.images 的 JSON 字符串保持同步） */
const carouselUrls = ref([])

/** 允许的图片扩展名（和后端 AdminUploadServiceImpl 白名单一致） */
const ALLOWED_IMG_EXT = ['jpg', 'jpeg', 'png', 'gif', 'webp']

/** 单张图片最大 5MB（和后端 spring.servlet.multipart.max-file-size 一致） */
const MAX_IMG_SIZE = 5 * 1024 * 1024

/**
 * 上传前校验（体验层，后端才是最终防线）：类型白名单 + 大小
 * 返回 false 会中止本次上传
 */
function beforeImageUpload(file) {
  const ext = (file.name.split('.').pop() || '').toLowerCase()
  if (!ALLOWED_IMG_EXT.includes(ext)) {
    ElMessage.error('仅支持 jpg/jpeg/png/gif/webp 格式')
    return false
  }
  if (file.size > MAX_IMG_SIZE) {
    ElMessage.error('图片大小不能超过 5MB')
    return false
  }
  return true
}

/**
 * 主图专用上传前处理：先走公共校验，通过后标记主图上传中（防重复点击）
 * 轮播图共用 beforeImageUpload（不设 mainUploading，避免传轮播图时主图按钮误变 loading）
 */
function beforeMainUpload(file) {
  const ok = beforeImageUpload(file)
  if (ok) {
    mainUploading.value = true
  }
  return ok
}

/**
 * 主图上传成功回调：response 是后端 Result JSON（原生 XHR 已自动 JSON.parse）
 * code===200 时 data 是相对 URL，写回 form.image
 */
function handleMainUploadSuccess(response) {
  if (response && response.code === 200) {
    form.image = response.data
    ElMessage.success('主图上传成功')
  } else {
    ElMessage.error(response?.message || '主图上传失败')
  }
  mainUploading.value = false   // 复位上传中状态（code-review 修复）
}

/**
 * 轮播图上传成功回调：把返回的 URL 追加进数组，并同步 form.images（JSON 字符串）
 */
function handleCarouselUploadSuccess(response) {
  if (response && response.code === 200) {
    carouselUrls.value.push(response.data)
    syncCarousel()
  } else {
    ElMessage.error(response?.message || '轮播图上传失败')
  }
}

/**
 * 移除某张轮播图
 */
function removeCarousel(idx) {
  carouselUrls.value.splice(idx, 1)
  syncCarousel()
}

/**
 * 轮播图数组 → form.images（JSON 字符串），保持提交格式不变
 * （后端 ProductSaveDTO.images 存 JSON 数组字符串）
 */
function syncCarousel() {
  form.images = carouselUrls.value.length ? JSON.stringify(carouselUrls.value) : ''
}

/**
 * 上传失败回调（网络错误/401/403/500）
 * el-upload 不经过 axios 响应拦截器，需要手动处理这些状态
 */
function handleUploadError(err) {
  const status = err?.status
  if (status === 401) {
    // 与 request.js 的 401 策略保持一致（code-review 修复）：清 token/userInfo + 跳登录，
    // 否则死 token 残留，上传永远失败
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
    ElMessage.error('登录已过期，请重新登录')
    window.location.href = '/login'
  } else if (status === 403) {
    ElMessage.error('无权限访问')
  } else if (status === 413) {
    // nginx client_max_body_size 拦截（code-review 修复）：明确提示，而不是误导性的"网络错误"
    ElMessage.error('图片大小不能超过 5MB')
  } else {
    ElMessage.error('上传失败：' + (err?.message || '网络错误'))
  }
  mainUploading.value = false   // 复位上传中状态
}

// ==================== 数据加载 ====================

/**
 * 加载商品列表（分页 + 可选搜索）
 */
async function loadData() {
  loading.value = true
  try {
    const res = await getAdminProducts({
      keyword: keyword.value || undefined,
      page: currentPage.value,
      size: pageSize.value
    })
    productList.value = res.records || []
    total.value = res.total || 0
    // 删除最后一页的最后一条后，当前页可能超出总页数 → 回退到最后一页重新加载（code-review 修复）
    if (productList.value.length === 0 && currentPage.value > 1) {
      currentPage.value--
      await loadData()
      return
    }
  } catch (err) {
    console.error('获取商品列表失败:', err)
  } finally {
    loading.value = false
  }
}

/**
 * 加载分类下拉选项
 * getCategoryTree() 返回分类树 [{ id, name, children:[...] }]
 * 拍平成扁平列表：一级分类和二级分类都放进去，label 用"父分类 / 子分类"方便辨认
 */
async function loadCategories() {
  const tree = await getCategoryTree()
  const options = []
  for (const parent of tree) {
    options.push({ id: parent.id, name: parent.name })
    if (parent.children) {
      for (const child of parent.children) {
        options.push({ id: child.id, name: `${parent.name} / ${child.name}` })
      }
    }
  }
  categoryOptions.value = options
}

/**
 * 搜索（点击搜索按钮 / 回车）
 */
function handleSearch() {
  currentPage.value = 1   // 搜索时回到第一页
  loadData()
}

/**
 * 每页条数变化时：回到第一页重新加载
 */
function handleSizeChange() {
  currentPage.value = 1
  loadData()
}

// ==================== 新增/编辑 ====================

/**
 * 打开"新增商品"弹窗
 * 清空表单（id=null）
 */
function handleAdd() {
  dialogTitle.value = '新增商品'
  Object.assign(form, {
    id: null, name: '', categoryId: null, price: 0, originalPrice: null,
    stock: 0, status: 0, rating: 0, image: '', images: '', description: ''
  })
  carouselUrls.value = []   // 清空轮播图上传列表
  refreshUploadHeaders()    // 刷新上传 token（防登出重登后旧 token 快照）
  dialogVisible.value = true
}

/**
 * 打开"编辑商品"弹窗
 * 把行数据回显到表单
 * 注意：后端返回的 images 是数组，表单里要存 JSON 字符串，所以要 JSON.stringify
 */
function handleEdit(row) {
  dialogTitle.value = '编辑商品'
  Object.assign(form, {
    id: row.id,
    name: row.name,
    categoryId: row.categoryId,
    price: row.price,
    originalPrice: row.originalPrice,
    stock: row.stock,
    status: row.status,
    rating: row.rating || 0,   // 保留原评分（编辑不能把评分重置成 0）
    image: row.image || '',
    description: row.description || ''
  })
  // 轮播图回显：后端返回的是数组，直接喂给 uploader 展示缩略图；
  // form.images 的 JSON 字符串由 syncCarousel 统一生成
  carouselUrls.value = (row.images && row.images.length) ? [...row.images] : []
  syncCarousel()
  refreshUploadHeaders()    // 刷新上传 token（防登出重登后旧 token 快照）
  dialogVisible.value = true
}

/**
 * 提交保存（新增或修改共用）
 * 校验通过后组装 payload 调 saveAdminProduct，成功后刷新列表
 */
async function handleSubmit() {
  if (!formRef.value) return
  // 表单校验：validate() 校验失败会 reject，用 catch 拿到 false 后 return 阻止提交
  // （code-review 修复：原来 .catch(() => false) 后继续执行，校验失败也会发出请求）
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    // 组装提交数据（和后端 ProductSaveDTO 字段对应）
    const payload = {
      id: form.id,                     // null=新增
      name: form.name,
      description: form.description,
      price: form.price,
      originalPrice: form.originalPrice || null,
      image: form.image,
      images: form.images,             // JSON 字符串（后端直接存）
      categoryId: form.categoryId,
      stock: form.stock,
      rating: form.rating,             // 新增默认 0，编辑保留原评分
      status: form.status
    }
    await saveAdminProduct(payload)
    ElMessage.success(form.id ? '商品修改成功' : '商品新增成功')
    dialogVisible.value = false
    await loadData()
  } catch (err) {
    // 接口错误由响应拦截器统一弹提示，这里只需记录日志
    console.error('保存商品失败:', err)
  } finally {
    submitting.value = false
  }
}

// ==================== 上下架 / 删除 ====================

/**
 * 上架/下架商品
 * 根据当前状态切换：上架(0)→下架(1)，下架(1)→上架(0)
 */
async function handleToggleStatus(row) {
  const newStatus = row.status === 0 ? 1 : 0
  try {
    await updateAdminProductStatus(row.id, newStatus)
    ElMessage.success(newStatus === 0 ? '已上架' : '已下架')
    await loadData()
  } catch (err) {
    console.error('修改商品状态失败:', err)
  }
}

/**
 * 删除商品（二次确认）
 * 后端：逻辑删除 + 级联清购物车 + 清缓存
 */
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确定要删除商品「${row.name}」吗？删除后前台不再显示。`,
      '确认删除',
      {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    await deleteAdminProduct(row.id)
    ElMessage.success('商品已删除')
    await loadData()
  } catch {
    // 用户取消，不处理
  }
}

// ==================== 生命周期 ====================

onMounted(() => {
  loadData()
  loadCategories()
})
</script>

<template>
  <div>
    <!-- ==================== 页面标题 + 工具栏 ==================== -->
    <div class="toolbar">
      <h1 class="page-title">商品管理</h1>
      <div class="toolbar-right">
        <!-- 搜索框 -->
        <el-input
          v-model="keyword"
          placeholder="搜索商品名称"
          clearable
          style="width: 220px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-button type="primary" @click="handleSearch">搜索</el-button>
        <el-button type="success" @click="handleAdd">新增商品</el-button>
      </div>
    </div>

    <!-- ==================== 商品表格 ==================== -->
    <el-card shadow="never">
      <el-table :data="productList" v-loading="loading" empty-text="暂无商品" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column label="主图" width="80">
          <!-- 商品缩略图 -->
          <template #default="{ row }">
            <el-image :src="row.image" fit="cover" style="width: 48px; height: 48px; border-radius: 4px" />
          </template>
        </el-table-column>
        <el-table-column prop="name" label="商品名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="categoryName" label="分类" width="120" />
        <el-table-column label="价格" width="100">
          <template #default="{ row }">¥{{ Number(row.price).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="stock" label="库存" width="80" />
        <el-table-column prop="sales" label="销量" width="80" />
        <el-table-column label="状态" width="80">
          <!-- 上架/下架标签 -->
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'success' : 'info'" size="small">
              {{ row.status === 0 ? '上架' : '下架' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <!-- 编辑 -->
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <!-- 上架/下架（文案随当前状态切换） -->
            <el-button link :type="row.status === 0 ? 'warning' : 'success'" @click="handleToggleStatus(row)">
              {{ row.status === 0 ? '下架' : '上架' }}
            </el-button>
            <!-- 删除 -->
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
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

    <!-- ==================== 新增/编辑弹窗 ==================== -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <!-- 商品名称 -->
        <el-form-item label="商品名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入商品名称" />
        </el-form-item>

        <!-- 分类选择 -->
        <el-form-item label="分类" prop="categoryId">
          <el-select v-model="form.categoryId" placeholder="请选择分类" style="width: 100%">
            <el-option
              v-for="cat in categoryOptions"
              :key="cat.id"
              :label="cat.name"
              :value="cat.id"
            />
          </el-select>
        </el-form-item>

        <!-- 现价 + 原价 -->
        <el-form-item label="价格" prop="price">
          <el-input-number v-model="form.price" :min="0" :precision="2" :step="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="原价" prop="originalPrice">
          <el-input-number v-model="form.originalPrice" :min="0" :precision="2" :step="1" style="width: 100%" />
        </el-form-item>

        <!-- 库存 + 状态 -->
        <el-form-item label="库存" prop="stock">
          <el-input-number v-model="form.stock" :min="0" :precision="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :value="0">上架</el-radio>
            <el-radio :value="1">下架</el-radio>
          </el-radio-group>
        </el-form-item>

        <!-- 主图：本地上传 + 预览 + 移除（v1.10）
             form.image 仍是字符串 URL：本地图是 /upload/... 相对地址，外部 URL 商品编辑时也能回显 -->
        <el-form-item label="主图" prop="image">
          <div style="display: flex; align-items: center; gap: 12px;">
            <el-upload
              action="/api/admin/upload/image"
              name="file"
              :headers="uploadHeaders"
              :show-file-list="false"
              :before-upload="beforeMainUpload"
              :on-success="handleMainUploadSuccess"
              :on-error="handleUploadError"
              :disabled="mainUploading"
            >
              <el-button type="primary" :loading="mainUploading">上传主图</el-button>
            </el-upload>
            <el-image
              v-if="form.image"
              :src="form.image"
              fit="cover"
              style="width: 80px; height: 80px; border-radius: 6px; border: 1px solid #eee"
            />
            <el-button v-if="form.image" link type="danger" @click="form.image = ''">移除</el-button>
          </div>
        </el-form-item>

        <!-- 轮播图：多张本地图片上传（v1.10），缩略图 + 移除按钮 -->
        <el-form-item label="轮播图" prop="images">
          <div style="display: flex; flex-wrap: wrap; gap: 12px; align-items: center;">
            <!-- :key 用 idx 而不是 url（code-review 修复）：images 数组可能含重复 URL，
                 用 url 当 key 会产生重复 Vue key 导致移除时误删缩略图 -->
            <div v-for="(url, idx) in carouselUrls" :key="idx" style="text-align: center;">
              <el-image
                :src="url"
                fit="cover"
                style="width: 80px; height: 80px; border-radius: 6px; border: 1px solid #eee"
              />
              <div>
                <el-button link type="danger" @click="removeCarousel(idx)">移除</el-button>
              </div>
            </div>
            <el-upload
              action="/api/admin/upload/image"
              name="file"
              :headers="uploadHeaders"
              :show-file-list="false"
              :before-upload="beforeImageUpload"
              :on-success="handleCarouselUploadSuccess"
              :on-error="handleUploadError"
            >
              <el-button>添加图片</el-button>
            </el-upload>
          </div>
        </el-form-item>

        <!-- 商品描述 -->
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="请输入商品描述" />
        </el-form-item>
      </el-form>

      <!-- 弹窗底部按钮 -->
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
/* 工具栏：标题 + 右侧搜索/新增按钮 */
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

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

/* 分页居中 */
.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
