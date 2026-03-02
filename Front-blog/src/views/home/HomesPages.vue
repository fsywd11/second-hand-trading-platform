<script setup lang="js">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useGoodsStore } from '@/stores/goodsStore.js'
import Footer from "@/components/footer.vue";
const router = useRouter()
const goodsStore = useGoodsStore()

// 筛选条件
const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  categoryId: null, // 分类ID
  minPrice: null,   // 最低价格
  maxPrice: null,   // 最高价格
  keyword: ''       // 搜索关键词
})

// 分类列表
const categories = computed(() => goodsStore.categories)
const goodsList = computed(() => goodsStore.goodsList)
const total = computed(() => goodsStore.total)
const isLoading = computed(() => goodsStore.isLoading)

// 初始化加载
onMounted(async () => {
  await goodsStore.fetchCategories()
  await fetchGoods()
})

// 查询商品
const fetchGoods = async (forceRefresh = false) => {
  await goodsStore.fetchGoodsList(queryParams.value, forceRefresh)
}

// 切换分类
const handleCategoryChange = (categoryId) => {
  queryParams.value.categoryId = categoryId
  queryParams.value.pageNum = 1
  fetchGoods()
}

// 页码变化
const onCurrentChange = (pageNum) => {
  queryParams.value.pageNum = pageNum
  fetchGoods()
}

// 跳转到商品详情
const goToDetail = (id) => {
  router.push(`/goods/detail/${id}`)
}

// 格式化价格
const formatPrice = (price) => {
  return Number(price).toFixed(1)
}
</script>

<template>
  <div class="page-layout">

    <div class="main-content">
      <div class="goods-list-page">
        <div class="filter-section">
          <h2 class="filter-title">筛选商品</h2>

          <div class="filter-item">
            <span class="filter-label">商品类别：</span>
            <div class="category-btn-group">
              <el-button
                  :type="queryParams.categoryId === null ? 'primary' : 'default'"
                  size="small"
                  @click="handleCategoryChange(null)"
                  class="category-btn"
              >
                全部
              </el-button>

              <el-button
                  v-for="cat in categories"
                  :key="cat.id"
                  :type="queryParams.categoryId === cat.id ? 'primary' : 'default'"
                  size="small"
                  @click="handleCategoryChange(cat.id)"
                  class="category-btn"
              >
                {{ cat.categoryName }}
              </el-button>
            </div>
          </div>
        </div>

        <div class="goods-container">
          <el-skeleton v-show="isLoading" :rows="3" :items="4" />

          <div class="goods-grid" v-show="!isLoading">
            <div
                class="goods-card"
                v-for="goods in goodsList"
                :key="goods.id"
                @click="goToDetail(goods.id)"
            >
              <div class="goods-image-wrapper">
                <img :src="goods.goodsPic" :alt="goods.goodsName" class="goods-image" />
                <div class="goods-tag" v-if="goods.isNew === 1">新品</div>
              </div>

              <div class="goods-info">
                <h3 class="goods-name">{{ goods.goodsName }}</h3>
                <div class="goods-price">¥{{ formatPrice(goods.sellPrice) }}</div>
                <div class="goods-meta">
                  <span class="stock">库存{{ goods.stock }}</span>
                  <span class="like-count">{{ goods.collectCount }}人想要</span>
                </div>
                <div class="seller-info">
                  <el-avatar :size="24" :src="goods.sellerPic || ''" />
                  <span class="seller-name">{{ goods.sellerNickname }}</span>
                </div>
              </div>
            </div>
          </div>

          <el-pagination
              v-model:current-page="queryParams.pageNum"
              v-model:page-size="queryParams.pageSize"
              :total="total"
              layout="prev, pager, next"
              background
              @current-change="onCurrentChange"
              style="margin-top: 20px; justify-content: center"
          />
        </div>
      </div>
    </div>

    <div class="footer-wrapper">
      <Footer />
    </div>

  </div>
</template>

<style lang="scss" scoped>
/* ================= 新增布局控制 ================= */
.page-layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh; /* 页面最小高度为100%视口高度 */
}

.main-content {
  flex: 1; /* 无数据时自动拉伸，将底部区域挤到下方 */
  display: flex;
  flex-direction: column;
}

.footer-wrapper {
  width: 100%;
  flex-shrink: 0; /* 确保底部不会被挤压变形 */
}
/* ================================================ */

/* 以下为你原本的样式，完全保持原样，不破坏原有效果 */
.goods-list-page {
  max-width: 1200px;
  width: 100%;
  margin: 30px auto;
  padding-top: 70px;
}

.filter-section {
  margin-bottom: 24px;

  .filter-title {
    font-size: 30px;
    font-weight: bold;
    margin-bottom: 16px;
  }

  .filter-item {
    display: flex;
    align-items: center;
    gap: 12px;
    flex-wrap: wrap;

    .filter-label {
      font-size: 14px;
      color: #666;
    }

    .category-btn-group {
      display: flex;
      gap: 8px;
      flex-wrap: wrap;
    }

    .category-btn {
      border-radius: 20px !important;
      padding: 4px 16px !important;
      transition: all 0.2s ease !important;

      &.el-button--primary {
        background-color: #409eff !important;
        border-color: #409eff !important;
        color: #fff !important;

        &:hover {
          background-color: #66b1ff !important;
          border-color: #66b1ff !important;
        }
      }

      &.el-button--default {
        background-color: #f5f7fa !important;
        border-color: #e4e7ed !important;
        color: #606266 !important;

        &:hover {
          background-color: #e4e7ed !important;
          border-color: #dcdfe6 !important;
          color: #303133 !important;
        }
      }
    }
  }
}

.goods-container {
  .goods-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
    gap: 20px;

    .goods-card {
      background: #fff;
      border-radius: 12px;
      overflow: hidden;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
      transition: all 0.3s ease;
      cursor: pointer;

      &:hover {
        transform: translateY(-4px);
        box-shadow: 0 8px 16px rgba(0, 0, 0, 0.12);
      }

      .goods-image-wrapper {
        position: relative;
        width: 100%;
        height: 200px;
        overflow: hidden;

        .goods-image {
          width: 100%;
          height: 100%;
          object-fit: cover;
          transition: transform 0.3s ease;
        }

        &:hover .goods-image {
          transform: scale(1.05);
        }

        .goods-tag {
          position: absolute;
          top: 8px;
          left: 8px;
          background: #ff4d4f;
          color: #fff;
          font-size: 12px;
          padding: 2px 6px;
          border-radius: 4px;
        }
      }

      .goods-info {
        padding: 12px;

        .goods-name {
          font-size: 16px;
          font-weight: 600;
          margin-bottom: 8px;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }

        .goods-price {
          font-size: 18px;
          color: #ff4d4f;
          font-weight: bold;
          margin-bottom: 8px;
        }

        .goods-meta {
          display: flex;
          justify-content: space-between;
          font-size: 12px;
          color: #999;
          margin-bottom: 12px;

          .stock, .like-count {
            background: #f5f5f5;
            padding: 2px 6px;
            border-radius: 4px;
          }
        }

        .seller-info {
          display: flex;
          align-items: center;
          gap: 8px;
          font-size: 12px;
          color: #666;

          .seller-name {
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
          }
        }
      }
    }
  }
}

@media (max-width: 768px) {
  .goods-list-page {
    padding: 12px;
  }

  .filter-section .filter-item {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;

    .category-btn-group {
      width: 100%;
    }

    .category-btn {
      padding: 2px 12px !important;
      font-size: 12px !important;
    }
  }

  .goods-container .goods-grid {
    grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
    gap: 12px;

    .goods-card {
      .goods-image-wrapper {
        height: 140px;
      }

      .goods-info {
        padding: 8px;

        .goods-name {
          font-size: 14px;
        }

        .goods-price {
          font-size: 16px;
        }
      }
    }
  }
}
</style>