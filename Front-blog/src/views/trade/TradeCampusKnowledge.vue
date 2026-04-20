<script setup lang="js">
import { ref, onMounted, watch } from 'vue'
// 导入Element Plus图标（修复：确保导入语句正确）
import {
  Edit, Delete, Search, Refresh, Plus, Close, Select, UploadFilled
} from '@element-plus/icons-vue'
import {
  ElMessage, ElMessageBox, ElDialog, ElForm,
  ElFormItem, ElInput, ElButton,
  ElInputNumber
} from 'element-plus'
// 导入知识图谱API
import {
  kgAddService,
  kgListAllService,
  kgSelectService,
  kgUpdateService,
  kgDeleteService,
  kgSyncService
} from '@/api/campusKnowledge.js'

// ========== 响应式数据 ==========
// 知识图谱规则列表
const kgList = ref([])
// 选中的规则ID（批量操作）
const selectedKgIds = ref([])
// 查询条件
const queryForm = ref({
  sceneType: '',        // 场景类型查询
  sceneValue: ''        // 场景值模糊查询
})
// 弹窗相关
const dialogVisible = ref(false)
const dialogTitle = ref('新增知识图谱规则')
// 表单数据（匹配后端CampusKnowledgeGraph实体）
const form = ref({
  id: '',              // 规则ID（编辑时使用）
  sceneType: '',       // 场景类型（必填）
  sceneValue: '',      // 场景值（必填）
  categoryIds: '',     // 关联分类ID（必填，多个用逗号分隔）
  weight: 1.0,         // 权重（默认1.0）
  createTime: '',      // 创建时间（只读）
  updateTime: ''       // 更新时间（只读）
})
// 表单验证规则
const rules = ref({
  sceneType: [
    { required: true, message: '场景类型不能为空', trigger: 'blur' },
    { min: 2, max: 50, message: '场景类型长度在 2 到 50 个字符', trigger: 'blur' }
  ],
  sceneValue: [
    { required: true, message: '场景值不能为空', trigger: 'blur' },
    { min: 2, message: '场景值长度至少 2 个字符', trigger: 'blur' }
  ],
  categoryIds: [
    { required: true, message: '关联分类ID不能为空', trigger: 'blur' }
  ]
})
const formRef = ref(null)

// ========== 方法定义 ==========
// 获取知识图谱规则列表
const getKgList = async () => {
  try {
    const res = await kgListAllService()
    if (res.code === 0) {
      kgList.value = res.data || []
    } else {
      ElMessage.error('获取知识图谱规则列表失败：' + res.message)
    }
  } catch (error) {
    ElMessage.error('获取知识图谱规则列表异常：' + error.message)
  }
}

// 打开新增弹窗
const openAddDialog = () => {
  dialogTitle.value = '新增知识图谱规则'
  // 重置表单
  resetForm()
  dialogVisible.value = true
}

// 打开编辑弹窗
const openEditDialog = async (row) => {
  try {
    dialogTitle.value = '编辑知识图谱规则'
    // 根据ID查询规则详情
    const res = await kgSelectService(row.id)
    if (res.code === 0) {
      // 填充表单数据（匹配后端返回字段）
      form.value = {
        id: res.data.id || '',
        sceneType: res.data.sceneType || '',
        sceneValue: res.data.sceneValue || '',
        categoryIds: res.data.categoryIds || '',
        weight: res.data.weight ? Number(res.data.weight) : 1.0,
        createTime: res.data.createTime || '',
        updateTime: res.data.updateTime || ''
      }
      dialogVisible.value = true
    } else {
      ElMessage.error('获取规则详情失败：' + res.message)
    }
  } catch (error) {
    ElMessage.error('获取规则详情异常：' + error.message)
  }
}

// 保存规则（新增/编辑）
const saveKgRule = async () => {
  try {
    // 表单验证
    await formRef.value.validate()

    // 构造请求参数（匹配后端实体）
    const requestData = {
      id: form.value.id,
      sceneType: form.value.sceneType.trim(),
      sceneValue: form.value.sceneValue.trim(),
      categoryIds: form.value.categoryIds.trim(),
      weight: form.value.weight
    }

    let res
    if (form.value.id) {
      // 编辑模式
      res = await kgUpdateService(requestData)
    } else {
      // 新增模式
      res = await kgAddService(requestData)
    }

    if (res.code === 0) {
      ElMessage.success(form.value.id ? '规则修改成功' : '规则新增成功')
      dialogVisible.value = false
      resetForm()
      await getKgList()
    } else {
      ElMessage.error((form.value.id ? '修改' : '新增') + '失败：' + res.message)
    }
  } catch (error) {
    if (error.name !== 'ValidationError') {
      ElMessage.error('操作失败：' + error.message)
    }
  }
}

// 重置表单
const resetForm = () => {
  form.value = {
    id: '',
    sceneType: '',
    sceneValue: '',
    categoryIds: '',
    weight: 1.0,
    createTime: '',
    updateTime: ''
  }
  if (formRef.value) {
    formRef.value.clearValidate()
  }
}

// 删除单个规则
const kgDelete = (row) => {
  ElMessageBox.confirm(
      '你确认要删除该知识图谱规则吗?',
      '删除确认',
      {
        confirmButtonText: '确认',
        cancelButtonText: '取消',
        type: 'warning'
      }
  ).then(async () => {
    try {
      await kgDeleteService(row.id)
      ElMessage.success('删除成功')
      await getKgList()
    } catch (error) {
      ElMessage.error('删除失败：' + error.message)
    }
  }).catch(() => {
    ElMessage.info('用户取消了删除')
  })
}

// 批量删除规则
const batchDeleteKg = async () => {
  if (selectedKgIds.value.length === 0) {
    ElMessage.warning('请选择要删除的规则')
    return
  }

  ElMessageBox.confirm(
      `确认要删除选中的 ${selectedKgIds.value.length} 个规则吗？`,
      '批量删除确认',
      {
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
        type: 'danger'
      }
  ).then(async () => {
    try {
      // 批量调用删除接口
      for (const id of selectedKgIds.value) {
        await kgDeleteService(id)
      }
      ElMessage.success('批量删除成功')
      selectedKgIds.value = []
      await getKgList()
    } catch (error) {
      ElMessage.error('批量删除失败：' + error.message)
    }
  })
}

// 处理表格选择变化
const handleSelectionChange = (selection) => {
  selectedKgIds.value = selection.map(row => row.id)
}

// 同步所有规则到Milvus
const syncToMilvus = async () => {
  try {
    // 二次确认防止误操作
    await ElMessageBox.confirm(
        '确认要同步所有规则到Milvus吗？该操作将覆盖Milvus中现有规则！',
        '同步规则到Milvus',
        {
          confirmButtonText: '确认同步',
          cancelButtonText: '取消',
          type: 'warning',
          draggable: true
        }
    )

    // 调用同步接口
    const res = await kgSyncService()
    console.log(res)
    if (res.code === 0) {
      ElMessage.success('规则同步到Milvus成功！')
      // 同步完成后刷新列表
      await getKgList()
    } else {
      ElMessage.error('规则同步失败：' + res.message)
    }
  } catch (error) {
    // 如果是用户取消操作，不提示错误
    if (error !== 'cancel') {
      ElMessage.error('规则同步异常：' + (error.message || '操作取消'))
    }
  }
}

// 重置查询条件
const resetSearchCondition = () => {
  queryForm.value = {
    sceneType: '',
    sceneValue: ''
  }
  getKgList()
}

// 搜索规则
const searchKgList = async () => {
  try {
    // 获取全量列表后前端筛选
    const res = await kgListAllService()
    if (res.code === 0) {
      let data = res.data || []
      // 前端筛选
      if (queryForm.value.sceneType) {
        data = data.filter(item =>
            item.sceneType === queryForm.value.sceneType
        )
      }
      if (queryForm.value.sceneValue) {
        data = data.filter(item =>
            item.sceneValue.includes(queryForm.value.sceneValue)
        )
      }
      kgList.value = data
    } else {
      ElMessage.error('搜索规则失败：' + res.message)
    }
  } catch (error) {
    ElMessage.error('搜索规则异常：' + error.message)
  }
}

// 初始化加载
onMounted(async () => {
  await getKgList()
})

// 监听弹窗关闭事件，重置表单
watch(dialogVisible, (newVal) => {
  if (!newVal) {
    resetForm()
  }
})
</script>

<template>
  <el-card class="page-container">
    <template #header>
      <div class="header">
        <span class="title-text">校园知识图谱规则管理</span>
        <el-button
            type="primary"
            :icon="Plus"
            @click="openAddDialog"
        >
          新增规则
        </el-button>
      </div>
    </template>

    <!-- 搜索区域 -->
    <div class="search-area">
      <el-form inline :model="queryForm">
        <el-form-item label="场景类型">
          <el-input
              v-model="queryForm.sceneType"
              placeholder="请输入场景类型"
              clearable
              style="width: 200px"
          ></el-input>
        </el-form-item>

        <el-form-item label="场景值">
          <el-input
              v-model="queryForm.sceneValue"
              placeholder="请输入场景值关键词"
              clearable
              style="width: 200px"
          ></el-input>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :icon="Search" @click="searchKgList">搜索</el-button>
          <el-button :icon="Refresh" @click="resetSearchCondition">重置</el-button>
          <el-button
              type="danger"
              :icon="Delete"
              @click="batchDeleteKg"
              :disabled="selectedKgIds.length === 0"
          >
            批量删除 ({{ selectedKgIds.length }})
          </el-button>
          <el-button
              type="warning"
              @click="syncToMilvus"
              style="margin-left: 10px"
          >
            <!-- 修复：使用图标组件的正确方式，兼容所有版本 -->
            <template #icon>
              <UploadFilled />
            </template>
            同步到Milvus
          </el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 表格区域 -->
    <div class="table-wrapper">
      <el-table
          :data="kgList"
          height="100%"
          style="width: 100%"
          @selection-change="handleSelectionChange"
          border
          stripe
      >
        <el-table-column type="selection" width="55" align="center"></el-table-column>
        <el-table-column label="规则ID" width="80" prop="id" align="center"></el-table-column>
        <el-table-column label="场景类型" prop="sceneType" min-width="120" align="center" show-overflow-tooltip></el-table-column>
        <el-table-column label="场景值" prop="sceneValue" min-width="180" align="center" show-overflow-tooltip></el-table-column>
        <el-table-column label="关联分类ID" prop="categoryIds" min-width="150" align="center" show-overflow-tooltip></el-table-column>
        <el-table-column label="权重" prop="weight" width="100" align="center">
          <template #default="{ row }">
            {{ row.weight || 1.0 }}
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" width="180" align="center"></el-table-column>
        <el-table-column label="更新时间" prop="updateTime" width="180" align="center"></el-table-column>
        <el-table-column label="操作" width="180" fixed="right" align="center">
          <template #default="{ row }">
            <el-button :icon="Edit" type="primary" link @click="openEditDialog(row)">编辑</el-button>
            <el-button :icon="Delete" type="danger" link @click="kgDelete(row)">删除</el-button>
          </template>
        </el-table-column>

        <!-- 空数据提示 -->
        <template #empty>
          <el-empty description="暂无知识图谱规则记录" />
        </template>
      </el-table>
    </div>

    <!-- 新增/编辑规则弹窗 -->
    <el-dialog
        v-model="dialogVisible"
        :title="dialogTitle"
        width="600px"
        draggable
        :close-on-click-modal="false"
    >
      <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-width="100px"
          style="max-width: 500px; margin: 0 auto"
      >
        <el-form-item label="场景类型" prop="sceneType">
          <el-input
              v-model="form.sceneType"
              placeholder="请输入场景类型（如：校园场景、用户标签）"
              maxlength="50"
              show-word-limit
          />
        </el-form-item>
        <el-form-item label="场景值" prop="sceneValue">
          <el-input
              v-model="form.sceneValue"
              placeholder="请输入场景值（如：图书馆、大一新生）"
              maxlength="100"
              show-word-limit
          />
        </el-form-item>
        <el-form-item label="关联分类ID" prop="categoryIds">
          <el-input
              v-model="form.categoryIds"
              placeholder="请输入关联分类ID（多个用逗号分隔，如：1,2,3）"
              maxlength="100"
              show-word-limit
          />
          <div style="font-size: 12px; color: #909399; margin-top: 5px;">
            提示：分类ID为商品分类的ID，多个用英文逗号分隔
          </div>
        </el-form-item>
        <el-form-item label="权重">
          <el-input-number
              v-model="form.weight"
              :min="0.1"
              :max="10.0"
              :step="0.1"
              placeholder="请输入权重（默认1.0）"
              style="width: 100%;"
          />
        </el-form-item>
        <el-form-item label="创建时间" v-if="form.createTime">
          <el-input v-model="form.createTime" disabled />
        </el-form-item>
        <el-form-item label="更新时间" v-if="form.updateTime">
          <el-input v-model="form.updateTime" disabled />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">
          <template #icon>
            <Close />
          </template>
          取消
        </el-button>
        <el-button type="primary" @click="saveKgRule">
          <template #icon>
            <Select />
          </template>
          保存
        </el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<style lang="scss" scoped>
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
    .title-text {
      font-weight: bold;
      font-size: 16px;
      color: #303133;
    }
  }
}

.search-area {
  flex-shrink: 0;
  margin-bottom: 12px;
  padding: 10px;
  background-color: #f8f9fa;
  border-radius: 6px;
}

.table-wrapper {
  flex: 1;
  overflow: auto;
  margin-bottom: 12px;
}

:deep(.el-table__cell) {
  padding: 12px 0 !important;
}

:deep(.el-table__header th) {
  background-color: #fafafa;
  color: #333;
  font-weight: bold;
}

:deep(.el-dialog__body) {
  padding: 20px;
  max-height: 60vh;
  overflow-y: auto;
}

:deep(.el-form-item) {
  margin-bottom: 16px;
}
</style>