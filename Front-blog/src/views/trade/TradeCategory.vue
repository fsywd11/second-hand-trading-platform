<script setup lang="js">
import { Edit, Delete, Plus, Refresh, Search } from '@element-plus/icons-vue'
import { ref, watch, onMounted } from 'vue'
// 1. 替换API导入 - 使用商品分类接口
import {
  shopCategoryAddService,
  shopCategoryListService,
  shopCategoryUpdateService,
  shopCategoryDeleteService
} from '@/api/shopcategory.js'
import { ElMessage, ElMessageBox } from "element-plus"

// --- 数据定义 ---
// 2. 变量命名适配 - 从article改为category（保持语义）
const categorys = ref([])
const filteredCategorys = ref([])
const selectedIds = ref([])
const isAllSelected = ref(false)
const isIndeterminate = ref(false)
const tableKey = ref(0)
const loading = ref(false)
const searchKeyword = ref('')

// 获取列表数据 - 替换接口调用函数
const getShopCategoryList = async () => {
  loading.value = true
  try {
    let result = await shopCategoryListService()
    categorys.value = result.data || []
    selectedIds.value = []
    updateSelectionStatus()
  } catch (error) {
    // 3. 提示文本适配 - 改为商品分类
    ElMessage.error('获取商品分类列表失败')
  } finally {
    loading.value = false
  }
}

// 搜索过滤逻辑 - 保持原有逻辑（字段名如果后端一致则无需修改）
watch([searchKeyword, categorys], () => {
  if (!searchKeyword.value) {
    filteredCategorys.value = categorys.value
  } else {
    const keyword = searchKeyword.value.toLowerCase()
    filteredCategorys.value = categorys.value.filter(item =>
        // 注意：如果商品分类接口返回的字段名不是categoryName/categoryAlias，需要对应修改
        item.categoryName?.toLowerCase().includes(keyword) ||
        item.categoryAlias?.toLowerCase().includes(keyword)
    )
  }
}, { immediate: true })

onMounted(() => {
  getShopCategoryList()
})

// --- 弹窗逻辑 ---
const dialogVisible = ref(false)
const title = ref('')
const categoryFormRef = ref(null)
// 表单模型 - 保持字段名（根据后端实际接收字段调整）
const categoryModel = ref({ categoryName: '', categoryAlias: '' })

// 表单验证规则 - 保持原有规则
const rules = {
  categoryName: [
    { required: true, message: '请输入分类名称', trigger: 'blur' },
    { min: 1, max: 10, message: '1-10个字符', trigger: 'blur' }
  ],
  categoryAlias: [
    { required: true, message: '请输入分类别名', trigger: 'blur' },
    { min: 1, max: 15, message: '1-15个字符', trigger: 'blur' }
  ]
}

const showDialog = (row) => {
  title.value = '修改商品分类'
  dialogVisible.value = true
  categoryModel.value = { ...row }
}

const clearData = () => {
  categoryModel.value = { categoryName: '', categoryAlias: '' }
  if (categoryFormRef.value) categoryFormRef.value.resetFields()
}

// 保存/更新 - 替换接口调用函数
const saveCategory = async () => {
  if (!categoryFormRef.value) return
  await categoryFormRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true
      try {
        const service = title.value === '添加商品分类' ? shopCategoryAddService : shopCategoryUpdateService
        await service(categoryModel.value)
        ElMessage.success(`${title.value}成功`)
        getShopCategoryList()
        dialogVisible.value = false
      } catch (error) {
        // 增加错误提示
        ElMessage.error(`${title.value}失败：${error.message || '服务器错误'}`)
      } finally {
        loading.value = false
      }
    }
  })
}

// --- 删除逻辑 ---
const deleteCategory = (row) => {
  ElMessageBox.confirm(`确定删除「${row.categoryName}」吗？`, '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    loading.value = true
    try {
      await shopCategoryDeleteService(row.id)
      ElMessage.success('删除成功')
      getShopCategoryList()
    } catch (error) {
      ElMessage.error('删除失败：' + (error.message || '服务器错误'))
    } finally {
      loading.value = false
    }
  }).catch(() => {
    ElMessage.info('已取消删除')
  })
}

const batchDeleteCategories = async () => {
  ElMessageBox.confirm(`确认删除选中的 ${selectedIds.value.length} 个商品分类？`, '批量删除', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'danger'
  }).then(async () => {
    loading.value = true
    try {
      // 实际项目中建议后端提供批量删除接口，此处循环调用
      for (const id of selectedIds.value) {
        await shopCategoryDeleteService(id)
      }
      ElMessage.success('批量删除成功')
      getShopCategoryList()
    } catch (error) {
      ElMessage.error('批量删除失败：' + (error.message || '服务器错误'))
    } finally {
      loading.value = false
    }
  }).catch(() => {
    ElMessage.info('已取消批量删除')
  })
}

// --- 自定义多选逻辑 ---
const updateSelectionStatus = () => {
  const total = filteredCategorys.value.length
  const selected = selectedIds.value.length
  if (total === 0) {
    isAllSelected.value = false
    isIndeterminate.value = false
    return
  }
  isAllSelected.value = selected === total
  isIndeterminate.value = selected > 0 && selected < total
}

const handleSelectionChange = (row, checked) => {
  if (checked) {
    if (!selectedIds.value.includes(row.id)) selectedIds.value.push(row.id)
  } else {
    selectedIds.value = selectedIds.value.filter(id => id !== row.id)
  }
  updateSelectionStatus()
}

const handleAllSelectionChange = (checked) => {
  selectedIds.value = checked ? filteredCategorys.value.map(item => item.id) : []
  tableKey.value++ // 强制刷新表格以同步 Checkbox 状态
  updateSelectionStatus()
}
</script>

<template>
  <el-card class="page-container">
    <template #header>
      <div class="header">
        <!-- 4. UI文本替换 - 文章分类 → 商品分类 -->
        <span class="title-text">商品分类管理</span>
        <div class="header-actions">
          <el-input v-model="searchKeyword" placeholder="搜索名称或别名..." :prefix-icon="Search" clearable style="width: 220px" />
          <el-button type="danger" :icon="Delete" @click="batchDeleteCategories" :disabled="selectedIds.length === 0">
            批量删除 ({{ selectedIds.length }})
          </el-button>
          <el-button :icon="Refresh" @click="getShopCategoryList" :loading="loading" circle />
          <el-button type="primary" :icon="Plus" @click="dialogVisible = true; title='添加商品分类'; clearData()">添加商品分类</el-button>
        </div>
      </div>
    </template>

    <div class="table-wrapper">
      <el-table
          :data="filteredCategorys"
          height="100%"
          style="width: 100%"
          :key="tableKey"
          v-loading="loading"
          border
          stripe
      >
        <el-table-column width="55" align="center">
          <template #header>
            <el-checkbox v-model="isAllSelected" :indeterminate="isIndeterminate" @change="handleAllSelectionChange" />
          </template>
          <template #default="{ row }">
            <el-checkbox :checked="selectedIds.includes(row.id)" @change="(val) => handleSelectionChange(row, val)" />
          </template>
        </el-table-column>

        <el-table-column label="序号" width="80" type="index" align="center" />

        <el-table-column label="分类名称" prop="categoryName" min-width="150" align="center" show-overflow-tooltip />

        <el-table-column label="分类别名" prop="categoryAlias" min-width="150" align="center" show-overflow-tooltip />

        <el-table-column label="创建时间" prop="createTime" width="200" align="center" />

        <el-table-column label="操作" width="160" fixed="right" align="center">
          <template #default="{ row }">
            <el-button :icon="Edit" type="primary" link @click="showDialog(row)">编辑</el-button>
            <el-button :icon="Delete" type="danger" link @click="deleteCategory(row)">删除</el-button>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="暂无商品分类数据" />
        </template>
      </el-table>
    </div>

    <div class="table-footer">
      <div class="footer-info">
        <span>共 {{ categorys.length }} 条记录</span>
        <span v-if="searchKeyword" class="filter-tag"> (过滤出 {{ filteredCategorys.length }} 条)</span>
      </div>
    </div>

    <el-dialog v-model="dialogVisible" :title="title" width="420px" destroy-on-close>
      <el-form ref="categoryFormRef" :model="categoryModel" :rules="rules" label-width="80px" style="padding-right: 20px">
        <el-form-item label="分类名称" prop="categoryName">
          <el-input v-model="categoryModel.categoryName" maxlength="10" show-word-limit placeholder="请输入分类名称" />
        </el-form-item>
        <el-form-item label="分类别名" prop="categoryAlias">
          <el-input v-model="categoryModel.categoryAlias" maxlength="15" show-word-limit placeholder="请输入分类别名" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveCategory" :loading="loading">确认</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<style lang="scss" scoped>
/* 容器全屏化适配 */
.page-container {
  height: 100%;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;

  :deep(.el-card__body) {
    display: flex;
    flex-direction: column;
    flex: 1;
    overflow: hidden;
    padding: 16px;
  }

  .header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    .title-text { font-weight: bold; font-size: 16px; color: #303133; }
    .header-actions { display: flex; gap: 12px; align-items: center; }
  }
}

/* 表格包装层：负责内部滚动 */
.table-wrapper {
  flex: 1;
  overflow: hidden;
  margin-bottom: 12px;
}

/* 模拟分页条的底部固定栏 */
.table-footer {
  flex-shrink: 0;
  display: flex;
  justify-content: flex-end;
  padding-top: 12px;
  border-top: 1px solid #f0f2f5;

  .footer-info {
    color: #909399;
    font-size: 13px;
    .filter-tag {
      margin-left: 8px;
      color: #409EFF;
      font-weight: 500;
    }
  }
}

/* 单元格内容垂直居中微调 */
:deep(.el-table__cell) {
  padding: 10px 0 !important;
}

:deep(.el-table__header th) {
  background-color: #fafafa;
  color: #333;
  font-weight: bold;
}
</style>