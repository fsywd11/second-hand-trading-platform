<script setup lang="js">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useGoodsStore } from '@/stores/goodsStore.js'
import Footer from "@/components/footer.vue";
// 引入默认图片
import defaultPic from '@/assets/【哲风壁纸】我妻善逸-鬼灭之刃.png'
// 引入推荐商品API
import { goodsRecommendByKeywordService } from '@/api/goods.js'
import useUserInfoStore from '@/stores/userInfo.js'
const router = useRouter()
const goodsStore = useGoodsStore()
const userInfoStore = useUserInfoStore();
// 筛选条件
const queryParams = ref({
  sellerId: userInfoStore.info.id || null,
  pageNum: 1,
  pageSize: 24,
  categoryId: null, // 分类ID
  minPrice: null,   // 最低价格
  maxPrice: null,   // 最高价格
  keyword: ''       // 搜索关键词
})

// 分类列表中显示父id为0的数据
const categories = computed(() => goodsStore.categories.filter(item => item.parentId === 0))
const goodsList = computed(() => goodsStore.goodsList)
const total = computed(() => goodsStore.total)
const isLoading = computed(() => goodsStore.isLoading)
// 新增：当前激活的标签
const activeTab = computed({
  get() {
    // 如果有选中的分类ID，返回对应分类名称，否则返回"猜你喜欢"
    if (queryParams.value.categoryId) {
      const activeCategory = goodsStore.categories.find(item => item.id === queryParams.value.categoryId)
      return activeCategory ? activeCategory.categoryName : '猜你喜欢'
    }
    return '猜你喜欢'
  },
  set(val) {
    // 仅用于模板绑定，实际筛选逻辑在handleCategoryChange中处理
  }
})

// 推荐模块加载状态
const moduleLoading = ref(false)

// 推荐模块数据（改为动态获取）
const moduleData = ref([
  {
    title: '衣橱捡漏',
    subtitle: '时尚美衣低价淘',
    color: 'yellow',
    keyword: '服饰', // 新增：关联的搜索关键词
    items: []
  },
  {
    title: '手机数码',
    subtitle: '热门装备省心入',
    color: 'blue',
    keyword: '手机数码', // 新增：关联的搜索关键词
    items: []
  },
  {
    title: '二次元',
    subtitle: '烫门新品随手入',
    color: 'green',
    keyword: '二次元', // 新增：关联的搜索关键词
    items: []
  },
  {
    title: '省钱卡券',
    subtitle: '吃喝玩乐放心购',
    color: 'pink',
    keyword: '卡券优惠券', // 新增：关联的搜索关键词
    items: []
  }
])

// 获取推荐模块数据
const fetchModuleData = async () => {
  try {
    moduleLoading.value = true
    // 遍历每个推荐模块，获取对应关键词的商品
    for (const module of moduleData.value) {
      const res = await goodsRecommendByKeywordService(module.keyword)
      // 核心修复：正确解析后端返回的GoodsVO数据
      let goodsData = []
      // 后端返回结构：Result<Map> → res.data.data.goodsList
      if (res.data && Array.isArray(res.data.goodsList)) {
        goodsData = res.data.goodsList
      }
      // 转换为前端需要的格式（适配GoodsVO字段）
      module.items = goodsData.map((item, index) => ({
        id: item.id || `module-${module.color}-${index}`,
        // 取第一张图片，适配GoodsVO的imageList字段，无数据时用默认图
        image: item.goodsPic
            ? item.goodsPic
            : defaultPic,
        // 价格取sellPrice，确保是数字类型
        price: item.sellPrice || 0
      }))
    }
  } catch (error) {
    console.error('获取推荐商品失败:', error)
    // 异常时使用默认数据，保证页面正常显示
    moduleData.value.forEach(module => {
      module.items = [
        { id: 1, image: "", price: Math.floor(Math.random() * 100 + 10) },
        { id: 2, image: "", price: Math.floor(Math.random() * 100 + 10) },
        { id: 3, image: "", price: Math.floor(Math.random() * 200 + 50) }
      ]
    })
  } finally {
    moduleLoading.value = false
  }
}

// 初始化加载
onMounted(async () => {
  await goodsStore.fetchCategories()
  await fetchGoods()
  // 加载推荐模块数据
  await fetchModuleData()
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

// 新增：处理标签栏的分类点击（清空分类筛选）
const handleGuessLikeClick = () => {
  queryParams.value.categoryId = null
  queryParams.value.pageNum = 1
  fetchGoods()
}

// 新增：处理左侧细分类目的点击事件
const handleCategoryItemClick = async (keyword) => {
  await router.push({
    path: '/homes/search',
    query: { keyword: keyword } // URL参数：/homes/search?keyword=xxx
  });
}

// 静态左侧分类数据（拆分更细，支持点击）
const staticCategories = ref([
  {
    icon: '📱',
    name: '手机 / 数码 / 电脑',
    subItems: [
      { name: '手机', keyword: '手机' },
      { name: '数码', keyword: '数码' },
      { name: '电脑', keyword: '电脑' }
    ]
  },
  {
    icon: '👕',
    name: '服饰 / 箱包 / 运动',
    subItems: [
      { name: '服饰', keyword: '服饰' },
      { name: '箱包', keyword: '箱包' },
      { name: '运动', keyword: '运动' }
    ]
  },
  {
    icon: '🎮',
    name: '技能 / 卡券 / 潮玩',
    subItems: [
      { name: '技能', keyword: '技能' },
      { name: '卡券', keyword: '卡券' },
      { name: '潮玩', keyword: '潮玩' }
    ]
  },
  {
    icon: '👶',
    name: '母婴 / 美妆 / 个护',
    subItems: [
      { name: '母婴', keyword: '母婴' },
      { name: '美妆', keyword: '美妆' },
      { name: '个护', keyword: '个护' }
    ]
  },
  {
    icon: '🏠',
    name: '家具 / 家电 / 家装',
    subItems: [
      { name: '家具', keyword: '家具' },
      { name: '家电', keyword: '家电' },
      { name: '家装', keyword: '家装' }
    ]
  },
  {
    icon: '💎',
    name: '文玩 / 珠宝 / 礼品',
    subItems: [
      { name: '文玩', keyword: '文玩' },
      { name: '珠宝', keyword: '珠宝' },
      { name: '礼品', keyword: '礼品' }
    ]
  },
  {
    icon: '🍔',
    name: '食品 / 宠物 / 花卉',
    subItems: [
      { name: '食品', keyword: '食品' },
      { name: '宠物', keyword: '宠物' },
      { name: '花卉', keyword: '花卉' }
    ]
  },
  {
    icon: '📚',
    name: '图书 / 游戏 / 音像',
    subItems: [
      { name: '图书', keyword: '图书' },
      { name: '游戏', keyword: '游戏' },
      { name: '音像', keyword: '音像' }
    ]
  },
  {
    icon: '🚗',
    name: '汽车 / 电动车 / 租房',
    subItems: [
      { name: '汽车', keyword: '汽车' },
      { name: '电动车', keyword: '电动车' },
      { name: '租房', keyword: '租房' }
    ]
  },
  {
    icon: '🔧',
    name: '五金 / 设备 / 农牧',
    subItems: [
      { name: '五金', keyword: '五金' },
      { name: '设备', keyword: '设备' },
      { name: '农牧', keyword: '农牧' }
    ]
  }
])
</script>

<template>
  <div class="page-layout">
    <!-- 主内容区 -->
    <main class="main-content">
      <!-- 第一大模块：分类 + 轮播 + 推荐模块 -->
      <div class="top-module">
        <!-- 左侧静态分类菜单（支持细分类目点击） -->
        <aside class="sidebar">
          <ul class="category-menu">
            <li
                v-for="(cat, index) in staticCategories"
                :key="index"
                class="static-category-item"
            >
              <span class="icon">{{ cat.icon }}</span>
              <div class="category-content">
                <div class="sub-items">
                  <span
                      v-for="(subItem, subIndex) in cat.subItems"
                      :key="subIndex"
                      class="main-name"
                      @click.stop="handleCategoryItemClick(subItem.keyword)"
                  >
                    {{ subItem.name }}
                    <span v-if="subIndex !== cat.subItems.length - 1">/</span>
                  </span>
                </div>
              </div>
            </li>
          </ul>
        </aside>

        <!-- 右侧轮播 + 推荐模块 -->
        <div class="top-right-content">
          <!-- 顶部轮播/活动区 -->
          <div class="banner-section">
            <div class="banner-item orange">
              <div class="banner-content">
                <div class="banner-text">
                  <h2>校园抄底好物</h2>
                  <p>超绝性价比 <span class="highlight">1</span> 省到底</p>
                </div>
                <div class="banner-character">

                </div>
                <el-button type="primary" size="small" class="banner-button" @click="handleCategoryItemClick('超绝性价比')">去看看 ></el-button>
              </div>
            </div>
          </div>

          <!-- 推荐模块区 -->
          <div class="module-section">
            <div
                class="module-card"
                :class="module.color"
                v-for="(module, idx) in moduleData"
                :key="idx"
                @click="handleCategoryItemClick(module.title)"
            >
              <div class="module-header" >
                <div class="module-title-wrapper">
                  <h3 class="module-title">{{ module.title }} <span class="arrow">➤</span></h3>
                  <span class="module-subtitle">{{ module.subtitle }}</span>
                </div>
                <div class="module-icon">
                  <span class="icon" v-if="module.color === 'yellow'">👟</span>
                  <span class="icon" v-else-if="module.color === 'blue'">📸</span>
                  <span class="icon" v-else-if="module.color === 'green'">🦆</span>
                  <span class="icon" v-else-if="module.color === 'pink'">👝</span>
                </div>
              </div>
              <!-- 推荐模块加载状态 -->
              <div v-if="moduleLoading" class="module-items skeleton-items">
                <div class="skeleton-item" v-for="i in 3" :key="i">
                  <div class="skeleton-img"></div>
                  <div class="skeleton-price"></div>
                </div>
              </div>
              <!-- 推荐模块商品列表 -->
              <div v-else class="module-items">
                <div class="module-item" v-for="item in module.items" :key="item.id" @click.stop="goToDetail(item.id)">
                  <!-- 核心修改：设置图片默认值，无数据时显示导入的默认图 -->
                  <img
                      :src="item.image || defaultPic"
                      :alt="`商品${item.id}`"
                      class="module-item-img"
                  />
                  <span class="price">¥{{ formatPrice(item.price) }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 第二大模块：猜你喜欢 + 商品列表 -->
      <div class="bottom-module">
        <!-- 猜你喜欢标签栏 - 核心修改部分 -->
        <div class="tab-section">
          <!-- 猜你喜欢标签 -->
          <div
              :class="{ active: activeTab === '猜你喜欢' }"
              class="tab-item"
              @click="handleGuessLikeClick"
          >
            猜你喜欢
          </div>
          <!-- 动态渲染分类标签 -->
          <div
              v-for="cat in categories"
              :key="`tab-${cat.id}`"
              :class="{ active: activeTab === cat.categoryName }"
              class="tab-item"
              @click="handleCategoryChange(cat.id)"
          >
            {{ cat.categoryName }}
          </div>
        </div>

        <!-- 商品列表 -->
        <div class="goods-container">
          <!-- 自定义商品骨架屏 -->
          <div class="goods-grid skeleton-grid" v-show="isLoading">
            <div class="goods-card skeleton-card" v-for="i in 8" :key="i">
              <div class="goods-image-wrapper skeleton-image"></div>
              <div class="goods-info">
                <h3 class="goods-name skeleton-name"></h3>
                <div class="goods-price skeleton-price"></div>
                <div class="goods-meta">
                  <span class="stock skeleton-meta-item"></span>
                  <span class="like-count skeleton-meta-item"></span>
                </div>
                <div class="seller-info">
                  <div class="skeleton-avatar"></div>
                  <span class="seller-name skeleton-seller-name"></span>
                </div>
              </div>
            </div>
          </div>

          <!-- 实际商品列表 -->
          <div class="goods-grid" v-show="!isLoading">
            <div
                class="goods-card"
                v-for="goods in goodsList"
                :key="goods.id"
                @click="goToDetail(goods.id)"
            >
              <div class="goods-image-wrapper">
                <img :src="goods.goodsPic || defaultPic" :alt="goods.goodsName" class="goods-image" />
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
    </main>

    <!-- 底部 -->
    <div class="footer-wrapper">
      <Footer />
    </div>
  </div>
</template>

<style lang="scss" scoped>
/* 全局布局 */
.page-layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  padding-top: 50px;
  background-color: #f5f5f5;
}

/* 顶部导航栏 */
.top-nav {
  background-color: #fff;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  position: sticky;
  top: 0;
  z-index: 100;

  .nav-container {
    max-width: 1400px;
    margin: 0 auto;
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 12px 20px;
  }

  .nav-left {
    display: flex;
    align-items: center;
    gap: 20px;

    .logo {
      font-size: 24px;
      font-weight: bold;
      color: #ff6a00;
      text-shadow: 0 1px 2px rgba(255, 106, 0, 0.2);
    }

    .search-input {
      width: 400px;
      :deep(.el-input__wrapper) {
        border-radius: 24px;
        box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
        transition: all 0.3s ease;
        &:hover {
          box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
        }
      }
      :deep(.el-input__inner) {
        padding-left: 16px;
      }
      :deep(.el-input-group__append) {
        border-radius: 0 24px 24px 0;
        background-color: #ff6a00;
        color: #fff;
        border-color: #ff6a00;
        &:hover {
          background-color: #ff8533;
          border-color: #ff8533;
        }
      }
    }
  }

  .nav-right {
    display: flex;
    align-items: center;
    .el-button {
      border-radius: 20px;
      padding: 8px 18px;
      font-weight: 500;
    }
  }
}

/* 主内容区 */
.main-content {
  flex: 1;
  max-width: 1600px;
  margin: 0 auto;
  width: 100%;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 24px;
}

/* 第一大模块：分类 + 轮播 + 推荐模块 */
.top-module {
  display: flex;
  gap: 16px;
  width: 100%;
  background-color: #ffffff;
  border-radius: 16px;
  padding: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  height: 368px;
  box-sizing: border-box;
  align-items: stretch;
}

/* 左侧分类菜单 */
.sidebar {
  width: 180px;
  background-color: #f6f6f6;
  border-radius: 12px;
  padding: 0;
  height: 100%;
  overflow-y: auto;

  .category-menu {
    list-style: none;
    margin: 0;
    height: 100%;
    box-sizing: border-box;

    .static-category-item {
      padding: 5px 12px;
      font-size: 13px;
      color: #333;
      border-radius: 8px;
      display: flex;
      align-items: flex-start;
      gap: 8px;

      .icon {
        width: 18px;
        text-align: center;
        margin-top: 1px;
        flex-shrink: 0;
      }

      .category-content {
        flex: 1;

        .main-name {
          display: block;
          margin-bottom: 4px;
          cursor: default;
          &:hover {
            color: #f58c14;
            cursor: pointer;
          }
        }

        .sub-items {
          display: flex;
          flex-wrap: wrap;
          gap: 3px;

          .sub-item {
            padding: 1px 4px;
            background-color: #e8e8e8;
            border-radius: 3px;
            font-size: 11px;
            cursor: pointer;
            transition: all 0.2s ease;

            &:hover {
              background-color: #ff6a00;
              color: #fff;
            }
          }
        }
      }
    }
  }
}

/* 右侧轮播 + 推荐模块 */
.top-right-content {
  flex: 1;
  display: flex;
  flex-direction: row;
  gap: 16px;
  height: 100%;
}

/* 顶部轮播/活动区 */
.banner-section {
  width: 280px;
  height: 100%;
  border-radius: 12px;
  overflow: hidden;
  flex-shrink: 0;

  .banner-item {
    width: 100%;
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    font-size: 24px;
    font-weight: bold;
    background-size: cover;
    background: linear-gradient(135deg, #ff9a00, #ff6a00) center;

    &.orange {
      background: linear-gradient(135deg, #ff9a00, #ff6a00);
    }

    .banner-content {
      width: 100%;
      height: 100%;
      display: flex;
      flex-direction: column;
      justify-content: space-between;
      padding: 18px;
      text-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
      box-sizing: border-box;

      .banner-text {
        h2 {
          font-size: 22px;
          margin-bottom: 6px;
          font-weight: 700;
          line-height: 1.2;
        }

        p {
          font-size: 14px;
          margin-bottom: 0;
          opacity: 0.9;

          .highlight {
            display: inline-block;
            background-color: #fff;
            color: #ff6a00;
            width: 24px;
            height: 24px;
            border-radius: 50%;
            text-align: center;
            line-height: 24px;
            font-weight: bold;
            text-shadow: none;
            font-size: 14px;
          }
        }
      }

      .banner-character {
        display: flex;
        justify-content: center;
        align-items: center;
        gap: 12px;
        margin: 10px 0;

        .character-icon {
          width: 60px;
          height: 60px;
          background-color: #fff;
          border-radius: 50%;
          display: flex;
          align-items: center;
          justify-content: center;
          color: #ff6a00;
          font-size: 30px;
        }

        .food-icon {
          width: 45px;
          height: 45px;
          background-color: #fff;
          border-radius: 50%;
          display: flex;
          align-items: center;
          justify-content: center;
          color: #ff6a00;
          font-size: 22px;
        }
      }

      .banner-button {
        border-radius: 20px;
        padding: 8px 18px;
        font-size: 14px;
        font-weight: 600;
        background-color: #fff;
        color: #ff6a00;
        border: none;
        width: 100%;
        &:hover {
          background-color: #fff3e6;
          box-shadow: 0 4px 12px rgba(255, 106, 0, 0.3);
        }
      }
    }
  }
}

/* 推荐模块区 - 核心修改：隐藏横向滚动条，优化内部溢出视觉效果 */
.module-section {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  grid-template-rows: repeat(2, 1fr);
  gap: 10px;
  flex: 1;
  height: 100%;
  overflow: hidden;

  .module-card {
    border-radius: 12px;
    padding: 5px;
    color: #333;
    transition: all 0.3s ease;
    display: flex;
    flex-direction: column;
    height: 100%;
    box-sizing: border-box;
    &:hover {
      transform: translateY(-2px);
      cursor: pointer;
    }

    .module-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      margin-bottom: 6px;

      .module-title-wrapper {
        display: flex;
        flex-direction: column;
        gap: 1px;

        .module-title {
          font-size: 14px;
          font-weight: 700;
          margin: 0;
          display: flex;
          align-items: center;

          .arrow {
            font-size: 9px;
            margin-left: 3px;
            color: #999;
          }
        }

        .module-subtitle {
          font-size: 10px;
          color: #666;
        }
      }

      .module-icon {
        width: 32px;
        height: 32px;
        background-color: #fff;
        border-radius: 10px;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 18px;
      }
    }

    .module-items {
      display: flex;
      gap: 8px;
      overflow-x: auto;
      padding-bottom: 2px;
      flex: 1;
      /* 核心修改1：隐藏所有浏览器的横向滚动条，保留滚动功能 */
      &::-webkit-scrollbar {
        display: none;
      }
      -ms-overflow-style: none;
      scrollbar-width: none;

      .module-item {
        flex-shrink: 0;
        width: 70px;
        text-align: center;
        transition: all 0.2s ease;
        &:hover {
          transform: scale(1.05);
          cursor: pointer;
        }

        /* 核心修改2：默认图片样式适配，保证和原图片尺寸一致 */
        .module-item-img {
          width: 70px;
          height: 70px;
          object-fit: cover;
          border-radius: 6px;
          margin-bottom: 2px;
        }

        .price {
          font-size: 11px;
          color: #ff4d4f;
          font-weight: bold;
        }
      }
    }

    /* 推荐模块骨架屏样式 */
    .skeleton-items {
      display: flex;
      gap: 8px;
      flex: 1;

      .skeleton-item {
        flex-shrink: 0;
        width: 70px;
        text-align: center;

        .skeleton-img {
          width: 70px;
          height: 70px;
          background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
          background-size: 200% 100%;
          animation: skeleton-loading 1.5s infinite;
          border-radius: 6px;
          margin-bottom: 2px;
        }

        .skeleton-price {
          width: 40px;
          height: 12px;
          margin: 0 auto;
          background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
          background-size: 200% 100%;
          animation: skeleton-loading 1.5s infinite;
          border-radius: 4px;
        }
      }
    }

    &.yellow {
      background-color: #fff9e6;
      border: 2px solid #fff2cc;
    }
    &.blue {
      background-color: #e6f7ff;
      border: 2px solid #cceeff;
    }
    &.green {
      background-color: #f0fff4;
      border: 2px solid #d9f7e3;
    }
    &.pink {
      background-color: #fff0f6;
      border: 2px solid #ffd6e6;
    }
  }
}

/* 第二大模块：猜你喜欢 + 商品列表 */
.bottom-module {
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: 100%;
  background-color: #ffffff;
  border-radius: 16px;
  padding: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

/* 猜你喜欢标签栏 */
.tab-section {
  display: flex;
  gap: 12px;
  overflow-x: auto;
  padding-bottom: 8px;
  &::-webkit-scrollbar {
    height: 4px;
  }
  &::-webkit-scrollbar-thumb {
    background-color: rgba(0, 0, 0, 0.1);
    border-radius: 2px;
  }

  .tab-item {
    padding: 8px 18px;
    background-color: #f5f5f5;
    border-radius: 20px;
    font-size: 14px;
    color: #1f1f1f;
    cursor: pointer;
    white-space: nowrap;
    transition: all 0.3s ease;

    &:hover {
      background-color: #ff6a00;
    }

    &.active {
      background-color: #ff6a00;
      color: #fff;
      font-weight: 600;
    }
  }
}

/* 商品列表容器 */
.goods-container {
  background-color: transparent;
  border-radius: 0;
  padding: 0;
  box-shadow: none;

  .goods-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
    gap: 20px;

    .goods-card {
      background: #fff;
      border-radius: 12px;
      overflow: hidden;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
      transition: all 0.3s ease;
      cursor: pointer;

      &:hover {
        transform: translateY(-4px);
        box-shadow: 0 8px 16px rgba(0, 0, 0, 0.1);
      }

      .goods-image-wrapper {
        position: relative;
        width: 100%;
        height: 220px;
        overflow: hidden;

        .goods-image {
          width: 100%;
          height: 100%;
          object-fit: cover;
          transition: transform 0.4s ease;
        }

        &:hover .goods-image {
          transform: scale(1.1);
        }

        .goods-tag {
          position: absolute;
          top: 10px;
          left: 10px;
          background: #ff4d4f;
          color: #fff;
          font-size: 11px;
          padding: 3px 8px;
          border-radius: 12px;
          font-weight: 600;
        }
      }

      .goods-info {
        padding: 14px;

        .goods-name {
          font-size: 15px;
          font-weight: 600;
          margin-bottom: 8px;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
          color: #333;
        }

        .goods-price {
          font-size: 20px;
          color: #ff4d4f;
          font-weight: bold;
          margin-bottom: 10px;
          display: flex;
          align-items: baseline;
          &::before {
            content: '¥';
            font-size: 14px;
            margin-right: 2px;
          }
        }

        .goods-meta {
          display: flex;
          justify-content: space-between;
          font-size: 12px;
          color: #999;
          margin-bottom: 12px;

          .stock, .like-count {
            background: #f5f5f5;
            padding: 3px 8px;
            border-radius: 12px;
            font-size: 11px;
          }
        }

        .seller-info {
          display: flex;
          align-items: center;
          gap: 8px;
          font-size: 12px;
          color: #666;

          .el-avatar {
            border: 1px solid #eee;
          }

          .seller-name {
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
            max-width: 100px;
          }
        }
      }
    }
  }

  /* 自定义骨架屏样式 */
  .skeleton-grid {
    .skeleton-card {
      cursor: default;
      &:hover {
        transform: none;
        box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
      }

      .skeleton-image {
        width: 100%;
        height: 220px;
        background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
        background-size: 200% 100%;
        animation: skeleton-loading 1.5s infinite;
        border-radius: 12px 12px 0 0;
      }

      .skeleton-name {
        width: 80%;
        height: 20px;
        background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
        background-size: 200% 100%;
        animation: skeleton-loading 1.5s infinite;
        border-radius: 4px;
        margin-bottom: 12px;
      }

      .skeleton-price {
        width: 60%;
        height: 24px;
        background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
        background-size: 200% 100%;
        animation: skeleton-loading 1.5s infinite;
        border-radius: 4px;
        margin-bottom: 12px;
      }

      .skeleton-meta-item {
        width: 70px;
        height: 18px;
        background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
        background-size: 200% 100%;
        animation: skeleton-loading 1.5s infinite;
        border-radius: 9px;
        display: inline-block;
      }

      .skeleton-avatar {
        width: 24px;
        height: 24px;
        border-radius: 50%;
        background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
        background-size: 200% 100%;
        animation: skeleton-loading 1.5s infinite;
      }

      .skeleton-seller-name {
        width: 80px;
        height: 16px;
        background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
        background-size: 200% 100%;
        animation: skeleton-loading 1.5s infinite;
        border-radius: 4px;
        display: inline-block;
      }
    }
  }
}

/* 骨架屏动画 */
@keyframes skeleton-loading {
  0% {
    background-position: 200% 0;
  }
  100% {
    background-position: -200% 0;
  }
}

/* 底部 */
.footer-wrapper {
  width: 100%;
  flex-shrink: 0;
  background-color: #fff;
  border-top: 1px solid #e5e5e5;
}

/* 响应式适配 */
@media (max-width: 768px) {
  .main-content {
    padding: 12px;
    gap: 16px;
  }

  .top-module {
    flex-direction: column;
    gap: 16px;
    padding: 12px;
    height: auto;
    min-height: 368px;
  }

  .sidebar {
    width: 100%;
    height: auto;
    .category-menu {
      display: flex;
      overflow-x: auto;
      .static-category-item {
        white-space: nowrap;
        margin: 0 4px;
        min-width: 150px;
      }
    }
  }

  .top-right-content {
    flex-direction: column;
  }

  .banner-section {
    width: 100%;
    height: 200px;
  }

  .module-section {
    grid-template-columns: 1fr;
    gap: 16px;
  }

  .bottom-module {
    padding: 12px;
  }

  .goods-container .goods-grid {
    grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
    gap: 12px;

    .goods-card {
      .goods-image-wrapper {
        height: 140px;
      }

      .goods-info {
        padding: 10px;

        .goods-name {
          font-size: 14px;
        }

        .goods-price {
          font-size: 16px;
        }
      }

      .skeleton-image {
        height: 140px;
      }
    }
  }
}
</style>