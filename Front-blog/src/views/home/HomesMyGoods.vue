<script setup lang="js">
import { ref, onMounted, watch, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {Search, Refresh, Plus, Edit, DeleteFilled, Switch} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useTokenStore } from "@/stores/token.js"
// 导入商品相关API
import {
  goodsMyListService,
  goodsDeleteService,
  goodsUpdateStatusService // 替换：导入统一的状态更新接口
} from '@/api/goods.js'
import { shopCategoryListService } from '@/api/shopcategory.js'

const router = useRouter()
const route = useRoute()

// ========== 响应式数据 ==========
const pageNum = ref(1)
const pageSize = ref(12)
const total = ref(0)
const goodsList = ref([])
const categoryList = ref([])
const loading = ref(false)
const tokenStore = useTokenStore();
// 查询条件
const queryForm = ref({
  goodsName: '',
  categoryId: '',
  goodsStatus: '',
  minPrice: '',
  maxPrice: ''
})

// 判断当前是否处于搜索状态
const isSearching = computed(() => {
  return queryForm.value.goodsName !== '' ||
      queryForm.value.categoryId !== '' ||
      queryForm.value.goodsStatus !== '' ||
      queryForm.value.minPrice !== '' ||
      queryForm.value.maxPrice !== ''
})

// ========== 方法定义 ==========

// 跳转发布商品页面
const toPublish = () => {
  router.push('/homes/publish')
}

// 获取商品分类列表
const getCategoryList = async () => {
  try {
    const res = await shopCategoryListService()
    if (res.code === 0) {
      categoryList.value = res.data
    }
  } catch (error) {
    console.error('获取分类列表异常', error)
  }
}

// 获取我的商品列表
const getGoodsList = async () => {
  loading.value = true
  try {
    const queryData = {
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      ...queryForm.value
    }
    if(tokenStore.token!==''){
      const res = await goodsMyListService(queryData)
      if (res.code === 0) {
        goodsList.value = res.data.items || []
        total.value = res.data.total || 0
      } else {
        ElMessage.error('获取列表失败：' + res.message)
      }
    }
  } catch (error) {
    ElMessage.error('网络异常，获取列表失败')
  } finally {
    loading.value = false
  }
}

// 分页处理
const onSizeChange = (size) => {
  pageSize.value = size
  getGoodsList()
}
const onCurrentChange = (num) => {
  pageNum.value = num
  getGoodsList()
}

// 重置查询条件
const resetSearchCondition = () => {
  queryForm.value = { goodsName: '', categoryId: '', goodsStatus: '', minPrice: '', maxPrice: '' }
  pageNum.value = 1
  getGoodsList()
}

// 删除单个商品
const goodsDelete = (row) => {
  ElMessageBox.confirm('确认删除该商品吗?', '提示', {
    confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning'
  }).then(async () => {
    await goodsDeleteService(row.id)
    ElMessage.success('删除成功')
    await getGoodsList()
  }).catch(() => {})
}

// 标记商品售出方法
const goodsSoldOut = (row) => {
  // 防止重复标记为售出
  if (row.goodsStatus === 1) {
    ElMessage.warning('该商品已标记为售出状态')
    return
  }

  // 只有在售/下架商品可以标记为售出
  if (![2, 3].includes(row.goodsStatus)) {
    ElMessage.warning('该商品状态不支持标记为售出')
    return
  }

  ElMessageBox.confirm(
      '确认将该商品标记为售出吗？标记后商品状态将变为已售罄',
      '提示',
      {
        confirmButtonText: '确认',
        cancelButtonText: '取消',
        type: 'warning'
      }
  ).then(async () => {
    try {
      // 修改：使用统一的状态更新接口，传入状态2（已售出）
      const res = await goodsUpdateStatusService(row.id, 1)
      if (res.code === 0) {
        ElMessage.success('商品标记为售出成功')
        await getGoodsList() // 刷新列表
      } else {
        ElMessage.error('标记失败：' + res.message)
      }
    } catch (error) {
      ElMessage.error('网络异常，标记失败')
    }
  }).catch(() => {
    ElMessage.info('已取消标记售出操作')
  })
}

// 下架商品方法
const goodsOffShelf = (row) => {
  // 防止重复下架
  if (row.goodsStatus === 3) {
    ElMessage.warning('该商品已处于下架状态')
    return
  }

  // 只有售出状态的商品可以执行下架操作
  if (row.goodsStatus !== 1) {
    ElMessage.warning('仅售出状态的商品可执行此操作')
    return
  }

  ElMessageBox.confirm(
      '确认将该商品下架吗？下架后商品将不再展示在商品列表中',
      '提示',
      {
        confirmButtonText: '确认',
        cancelButtonText: '取消',
        type: 'warning'
      }
  ).then(async () => {
    try {
      // 修改：使用统一的状态更新接口，传入状态3（下架）
      const res = await goodsUpdateStatusService(row.id, 3)
      if (res.code === 0) {
        ElMessage.success('商品下架成功')
        await getGoodsList() // 刷新列表
      } else {
        ElMessage.error('下架失败：' + res.message)
      }
    } catch (error) {
      ElMessage.error('网络异常，下架失败')
    }
  }).catch(() => {
    ElMessage.info('已取消下架操作')
  })
}

// 动态处理按钮点击事件
const handleStatusToggle = (row) => {
  // 售出状态(1) -> 执行下架操作
  if (row.goodsStatus === 1) {
    goodsOffShelf(row)
  }
  // 非售出状态(2/3) -> 执行标记售出操作
  else {
    goodsSoldOut(row)
  }
}

// 跳转到编辑页面
const toEditGoods = (row) => {
  sessionStorage.setItem('editGoods', JSON.stringify(row))
  router.push({
    path: '/homes/goodsEdit',
    query: { id: row.id }
  })
}

// 路由监听，返回时刷新列表
watch(() => route.fullPath, async () => {
  await getGoodsList()
})

// 跳转到商品详情
const goToDetail = (id) => {
  router.push(`/goods/detail/${id}`)
}

onMounted(async () => {
  await getCategoryList()
  await getGoodsList()
})
</script>

<template>
  <div class="my-goods-container">
    <div class="header-bar">
      <h2>我发布的</h2>
      <el-button type="primary" :icon="Plus" @click="toPublish" class="publish-btn">
        发布新商品
      </el-button>
    </div>

    <div class="empty-state-wrapper" v-if="total === 0 && !isSearching && !loading">
      <el-empty description="暂无商品信息" :image-size="200">
        <el-button type="primary" size="large" @click="toPublish">
          发布我的第一个商品
        </el-button>
      </el-empty>
    </div>

    <div class="content-wrapper" v-else>
      <div class="search-area">
        <el-form inline :model="queryForm" class="filter-form">
          <el-form-item label="名称">
            <el-input v-model="queryForm.goodsName" placeholder="商品名称" clearable style="width: 150px"/>
          </el-form-item>
          <el-form-item label="分类">
            <el-select v-model="queryForm.categoryId" placeholder="全部分类" clearable style="width: 120px" @change="getGoodsList">
              <el-option v-for="c in categoryList" :key="c.id" :label="c.categoryName" :value="c.id"/>
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="queryForm.goodsStatus" placeholder="全部" clearable style="width: 100px" @change="getGoodsList">
              <el-option label="在售" :value="1"></el-option>
              <el-option label="已售罄" :value="2"></el-option>
              <el-option label="下架" :value="3"></el-option>
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="getGoodsList">搜索</el-button>
            <el-button :icon="Refresh" @click="resetSearchCondition">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <div class="goods-grid" v-loading="loading">
        <el-empty v-if="goodsList.length === 0" description="没有找到符合条件的商品" style="grid-column: 1 / -1;"></el-empty>

        <div class="goods-card" v-for="item in goodsList" :key="item.id">
          <div class="card-img-wrapper" @click="goToDetail(item.id)">
            <el-image
                :src="item.goodsPic ? item.goodsPic.split(',')[0] : ''"
                fit="cover"
                class="goods-img"
                lazy
            >
              <template #error>
                <div class="image-slot">无图</div>
              </template>
            </el-image>
            <span class="status-badge" :class="'status-' + item.goodsStatus">
              {{ item.goodsStatus === 1 ? '在售' : (item.goodsStatus === 2 ? '已出' : '下架') }}
            </span>
          </div>

          <div class="card-info">
            <div class="goods-title" :title="item.goodsName">{{ item.goodsName }}</div>

            <div class="price-row">
              <div class="price-text">
                <span class="currency">¥</span>
                <span class="amount">{{ item.sellPrice }}</span>
              </div>
              <div class="want-count">{{ item.likeCount || 0 }}人想要</div>
            </div>

            <div class="action-row">
              <button class="custom-btn edit-btn" @click="toEditGoods(item)"><el-icon><Edit /></el-icon>编辑</button>
              <!-- 动态按钮：根据商品状态切换文本、样式和功能 -->
              <button
                  class="custom-btn"
                  @click="handleStatusToggle(item)"
                  :class="['sold-out-btn']"
              >
                <el-icon><Switch /></el-icon>
                {{ item.goodsStatus === 1 ? '下架' : '标记售出' }}
              </button>
              <button class="custom-btn delete-btn" @click="goodsDelete(item)"><el-icon><DeleteFilled /></el-icon>删除</button>
            </div>
          </div>
        </div>
      </div>

      <div class="pagination-area">
        <el-pagination
            v-model:current-page="pageNum"
            v-model:page-size="pageSize"
            :page-sizes="[12, 24, 36]"
            layout="total, sizes, prev, pager, next"
            background
            :total="total"
            @size-change="onSizeChange"
            @current-change="onCurrentChange"
        />
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.my-goods-container {
  min-height: 100%;
  display: flex;
  flex-direction: column;
  background-color: #ffffff;
  /* 左右边界距离修改为 100px */
  padding: 10px;
  box-sizing: border-box;

  .header-bar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 24px;

    h2 { margin: 0; font-size: 24px; color: #222; font-weight: 600; }
    .publish-btn { background-color: #409eff; border-radius: 4px; padding: 10px 20px; font-size: 14px; }
  }
}

.empty-state-wrapper {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: center;
}

.content-wrapper {
  display: flex;
  flex-direction: column;
  flex: 1;
}

.search-area {
  margin-bottom: 30px;
  padding: 16px 20px;
  background: #fcfcfc;
  border: 1px solid #f0f0f0;
  border-radius: 8px;

  .filter-form {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
    align-items: flex-end;
  }

  :deep(.el-form-item) {
    margin-bottom: 0;
    margin-right: 20px;
  }
}

.goods-grid {
  display: grid;
  /* 根据左右100px的边距，卡片宽度自适应 */
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 24px;
  padding-bottom: 40px;
  flex: 1;
  align-content: flex-start;
}

.goods-card {
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
  transition: all 0.3s ease;
  border: 1px solid #f0f0f0;
  display: flex;
  flex-direction: column;

  &:hover {
    box-shadow: 0 12px 24px rgba(0, 0, 0, 0.1);
    transform: translateY(-4px);
  }

  .card-img-wrapper {
    position: relative;
    width: 100%;
    /* 保持图片比例 */
    aspect-ratio: 1 / 1;
    cursor: pointer;
    background-color: #f7f8fa;

    .goods-img {
      width: 100%;
      height: 100%;
    }

    .image-slot {
      display: flex;
      justify-content: center;
      align-items: center;
      width: 100%;
      height: 100%;
      color: #999;
      font-size: 14px;
    }

    .status-badge {
      position: absolute;
      top: 12px;
      right: 12px;
      padding: 4px 10px;
      border-radius: 6px;
      font-size: 12px;
      color: #fff;
      font-weight: 500;
      box-shadow: 0 2px 4px rgba(0,0,0,0.1);
      &.status-1 { background: #67c23a; }
      &.status-2 { background: #f56c6c; }
      &.status-3 { background: #909399; }
    }
  }

  .card-info {
    padding: 16px;
    display: flex;
    flex-direction: column;

    .goods-title {
      font-size: 16px;
      color: #333;
      font-weight: 500;
      line-height: 1.5;
      margin-bottom: 12px;
      display: -webkit-box;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
      overflow: hidden;
      height: 48px;
    }

    .price-row {
      display: flex;
      justify-content: space-between;
      align-items: baseline;
      margin-bottom: 16px;

      .price-text {
        color: #ff4d4f;
        font-weight: bold;
        .currency { font-size: 14px; }
        .amount { font-size: 22px; }
      }

      .want-count { font-size: 13px; color: #999; }
    }

    .action-row {
      display: flex;
      gap: 12px;

      .custom-btn {
        flex: 1;
        border: none;
        padding: 8px 0;
        border-radius: 6px;
        font-size: 14px;
        cursor: pointer;
        color: #fff;
        font-weight: 500;
        transition: background 0.2s;

        &:active { transform: scale(0.97); }

        // 禁用状态样式（保留，以备后续扩展）
        &.disabled {
          background-color: #ccc !important;
          cursor: not-allowed;
          transform: none !important;
          opacity: 0.7;
        }
      }

      .edit-btn { background-color: #79af53; &:hover { background-color: #6a9a48; } }
      /* 标记售出和下架按钮样式 */
      .sold-out-btn { background-color: #e6a23c; &:hover { background-color: #d4912f; } }
      .delete-btn { background-color: #7d848e; &:hover { background-color: #6c737c; } }
    }
  }
}

.pagination-area {
  padding: 30px 0;
  display: flex;
  justify-content: center;
}

// ========== 移动端适配样式 ==========
// 平板设备 (768px - 1024px)
@media screen and (max-width: 1024px) {
  .my-goods-container {
    padding: 40px 50px;

    .header-bar {
      margin-bottom: 20px;
      h2 { font-size: 20px; }
      .publish-btn { padding: 8px 16px; font-size: 13px; }
    }
  }

  .search-area {
    padding: 14px 16px;
    margin-bottom: 24px;

    :deep(.el-form-item) {
      margin-right: 15px;
    }

    :deep(.el-input) {
      width: 130px !important;
    }

    :deep(.el-select) {
      width: 100px !important;
    }
  }

  .goods-grid {
    grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
    gap: 20px;
  }

  .goods-card {
    .card-info {
      padding: 14px;

      .goods-title {
        font-size: 15px;
        margin-bottom: 10px;
        height: 45px;
      }

      .price-row {
        margin-bottom: 14px;

        .price-text {
          .amount { font-size: 20px; }
        }
      }

      .action-row {
        gap: 10px;

        .custom-btn {
          padding: 7px 0;
          font-size: 13px;
        }
      }
    }
  }
}

// 手机设备 (小于768px)
@media screen and (max-width: 768px) {
  .my-goods-container {
    padding: 20px 15px;
    min-height: 100vh;

    .header-bar {
      margin-bottom: 16px;
      h2 { font-size: 18px; }
      .publish-btn {
        padding: 6px 12px;
        font-size: 12px;
        display: flex;
        align-items: center;
        gap: 4px;
      }
    }
  }

  .empty-state-wrapper {
    :deep(.el-empty) {
      :deep(.el-empty-image) {
        width: 150px !important;
        height: 150px !important;
      }
      :deep(.el-empty-description) {
        font-size: 14px;
      }
      :deep(.el-button) {
        padding: 8px 16px;
        font-size: 14px;
      }
    }
  }

  .search-area {
    padding: 12px 14px;
    margin-bottom: 20px;

    .filter-form {
      gap: 8px;
    }

    :deep(.el-form-item) {
      margin-right: 0;
      margin-bottom: 10px !important;
      width: calc(50% - 4px);
    }

    :deep(.el-form-item:last-child) {
      width: 100%;
      display: flex;
      justify-content: center;
      gap: 10px;
    }

    :deep(.el-input) {
      width: 100% !important;
    }

    :deep(.el-select) {
      width: 100% !important;
    }

    :deep(.el-form-item__label) {
      font-size: 12px;
      padding-right: 5px;
    }
  }

  .goods-grid {
    grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
    gap: 15px;
    padding-bottom: 30px;
  }

  .goods-card {
    .card-img-wrapper {
      .status-badge {
        top: 8px;
        right: 8px;
        padding: 2px 8px;
        font-size: 11px;
      }
    }

    .card-info {
      padding: 12px;

      .goods-title {
        font-size: 14px;
        margin-bottom: 8px;
        height: 40px;
        line-height: 1.4;
      }

      .price-row {
        margin-bottom: 12px;

        .price-text {
          .currency { font-size: 12px; }
          .amount { font-size: 18px; }
        }

        .want-count {
          font-size: 12px;
        }
      }

      .action-row {
        gap: 8px;

        .custom-btn {
          padding: 6px 0;
          font-size: 12px;
          border-radius: 4px;
        }
      }
    }
  }

  .pagination-area {
    padding: 20px 0 10px;

    :deep(.el-pagination) {
      font-size: 12px;

      :deep(.el-pagination__sizes) {
        display: none;
      }

      :deep(.el-pager li) {
        padding: 0 8px;
      }
    }
  }
}

// 小屏手机 (小于480px)
@media screen and (max-width: 480px) {
  .goods-grid {
    grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
    gap: 10px;
  }

  .goods-card {
    .card-info {
      .goods-title {
        font-size: 13px;
        height: 36px;
      }

      .price-row {
        .price-text {
          .amount { font-size: 16px; }
        }
      }
    }
  }
}
</style>