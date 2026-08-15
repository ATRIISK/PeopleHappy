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
    images: row.images && row.images.length ? JSON.stringify(row.images) : '',
    description: row.description || ''
  })
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

        <!-- 主图 URL -->
        <el-form-item label="主图 URL" prop="image">
          <el-input v-model="form.image" placeholder="https://example.com/img.jpg" />
        </el-form-item>

        <!-- 轮播图 JSON 字符串 -->
        <el-form-item label="轮播图" prop="images">
          <el-input
            v-model="form.images"
            type="textarea"
            :rows="2"
            placeholder='JSON 数组格式，如 ["https://a.jpg","https://b.jpg"]'
          />
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
