<script setup lang="js">
import { onMounted, ref, watch, computed } from 'vue';
import { useRoute } from 'vue-router';
import Footer from "@/components/footer.vue";
import { ElMessage, ElAvatar, ElTag } from 'element-plus';
import Comment from "@/components/Comment.vue";
import router from "@/router/index.js";
import { commentList } from "@/api/comment.js";
import { goodsDetailService, goodsOpenListService, goodsOpenDetailSellerService } from "@/api/goods.js";
import {ArrowLeftBold, ArrowRightBold, ChatDotRound, Shop, Star} from "@element-plus/icons-vue";

// 引入真实的收藏 API
import { goodsAddCollect, goodsDeleteCollect, goodsListByCollectId } from "@/api/likeCollect.js";
import {useTokenStore} from "@/stores/token.js";

const loading = ref(false);
const route = useRoute();
const goodsId = ref(Number(route.params.id));
const isTogglingCollect = ref(false); // 防抖开关，防止频繁点击
const tokenStore = useTokenStore();
// 商品详情数据（适配GoodsDetailVO，扩展字段）
const goodsDetail = ref({
  id: 0,
  goodsName: '',
  goodsDesc: '',
  goodsPic: '',
  categoryId: 0,
  categoryName: '',
  originalPrice: 0, // 原价
  sellPrice: 0,     // 售价
  discount: '',     // 折扣标签
  postageFree: true,// 是否包邮
  sellerId: 0,
  sellerNickname: '',
  sellerAvatar: '',
  goodsStatusName: '',
  isNewName: '',     // 成色
  stock: 0,
  createTime: '',
  updateTime: '',
  imageList: [],      // 商品多图
  collectCount: 0,    // 收藏数
  isCollected: false, // 真实收藏状态
});

// 卖家信息
const sellerInfo = ref({
  sellerId: 0,
  sellerNickname: '',
  sellerAvatar: '',
  publishGoodsCount: 0,
});

// 当前选中的主图索引
const currentImageIndex = ref(0);

// 评论相关
const comments = ref([]);
const loadComments = async () => {
  try {
    const id = route.params.id;
    if (!id || isNaN(id) || id <= 0) {
      console.error('商品ID不合法:', id);
      return;
    }
    const result = await commentList(id);
    comments.value = result.data;
  } catch (error) {
    ElMessage.error('加载评论失败');
    console.error('评论加载错误:', error);
  }
};

// 监听路由变化，重新加载数据
watch(() => route.params.id, async (newId) => {
  const numericId = Number(newId);
  if (isNaN(numericId) || !numericId) return;
  goodsId.value = numericId;
  // 加载商品详情
  await getGoodsDetailById();
  // 获取用户是否收藏了该商品
  await checkCollectStatus();
  // 加载卖家信息
  await getSellerInfo(goodsDetail.value.sellerId);
}, { immediate: true });

// 卖家其他商品
const otherGoods = ref([]);
const loadOtherGoods = async () => {
  try {
    if (!sellerInfo.value.sellerId) return;
    const queryData = {
      sellerId: sellerInfo.value.sellerId,
      pageNum: 1,
      pageSize: 100
    };
    const res = await goodsOpenListService(queryData);
    otherGoods.value = res.data.items?.filter(item => item.id !== goodsDetail.value.id) || [];
  } catch (error) {
    console.error('加载其他商品失败:', error);
  }
};

// 跳转到其他商品
const goToGoodsDetail = (id) => {
  router.push(`/goods/detail/${id}`);
};

// 获取商品详情
async function getGoodsDetailById() {
  try {
    loading.value = false;
    const res = await goodsDetailService(route.params.id);
    if (!res.data) {
      ElMessage.warning('没有找到该商品');
      await router.push({ path: '/' });
      return;
    }
    // 时间与字段格式化
    res.data.createTime = res.data.createTime ? res.data.createTime.split(' ')[0] : '';
    res.data.updateTime = res.data.updateTime ? res.data.updateTime.split(' ')[0] : '';
    res.data.collectCount = res.data.CollectCount || res.data.collectCount || 0;
    res.data.sellStatusName = res.data.sellStatusName || '在售';

    // 初始化原价和折扣
    if (!res.data.originalPrice) {
      res.data.originalPrice = Number(res.data.sellPrice) * (1.2 + Math.random() * 0.3);
    }
    res.data.originalPrice = Number(res.data.originalPrice);
    res.data.sellPrice = Number(res.data.sellPrice);

    if (!res.data.discount) {
      if (res.data.originalPrice > res.data.sellPrice) {
        const rate = (res.data.sellPrice / res.data.originalPrice) * 10;
        res.data.discount = `${rate.toFixed(1)}折`;
      } else if (res.data.isNewName) {
        res.data.discount = res.data.isNewName;
      } else {
        res.data.discount = '全新';
      }
    }
    goodsDetail.value = Object.assign({}, goodsDetail.value, res.data);
    loading.value = true;
  } catch (error) {
    ElMessage.error('加载商品详情失败');
    console.error('商品加载错误:', error);
  }
}

// 查询当前用户的真实收藏状态
async function checkCollectStatus() {
  try {
    if (!goodsId.value) return;
    if(tokenStore.token===''){
      return;
    }
    const res = await goodsListByCollectId(goodsId.value);
    goodsDetail.value.isCollected = res.data.length > 0;
  } catch (error) {
    console.error('获取收藏状态失败:', error);
  }
}

// 获取卖家详情
async function getSellerInfo(sellerId) {
  try {
    if (!goodsId.value) return;
    const res = await goodsOpenDetailSellerService(sellerId);
    if (res.data) {
      sellerInfo.value = { ...res.data };
      await loadOtherGoods();
    }
  } catch (error) {
    console.error('加载卖家信息失败:', error);
  }
}

// 切换主图
const switchImage = (index) => {
  currentImageIndex.value = index;
};

// 真实的收藏/取消收藏逻辑
const toggleCollect = async () => {
  if (isTogglingCollect.value) return; // 防止重复点击
  isTogglingCollect.value = true;

  try {
    if (goodsDetail.value.isCollected) {
      // 执行取消收藏
      await goodsDeleteCollect(goodsId.value);
      goodsDetail.value.isCollected = false;
      goodsDetail.value.collectCount = Math.max(0, goodsDetail.value.collectCount - 1);
      ElMessage.success('已取消收藏');
    } else {
      // 执行添加收藏
      await goodsAddCollect(goodsId.value);
      goodsDetail.value.isCollected = true;
      goodsDetail.value.collectCount++;
      ElMessage.success('收藏成功');
    }
  } catch (error) {
    console.error('操作收藏失败:', error);
    // 拦截未登录错误（以 401 为例，依据你的 request.js 拦截器可能略有不同）
    if (error.response?.status === 401 || error.code === '401') {
      ElMessage.warning('请先登录后再进行收藏');
    } else {
      ElMessage.error('操作失败，请稍后重试');
    }
  } finally {
    isTogglingCollect.value = false;
  }
};

// 格式化价格
const formatPrice = computed(() => {
  return Number(goodsDetail.value.sellPrice)?.toFixed(2) || '0.00';
});

const formatOriginalPrice = computed(() => {
  return Number(goodsDetail.value.originalPrice)?.toFixed(2) || '0.00';
});

onMounted(async () => {
  await loadComments();
});

const goToSellerDetail=(sellerId)=>{
  router.push(`/seller/detail/${sellerId}`);
}
</script>

<template>
  <div class="goods-detail-container no-select">
    <div class="seller-top-bar">
      <div class="seller-top-left">
        <!-- 关键修改1：移除 size="32"，添加自定义 class -->
        <ElAvatar :src="sellerInfo.sellerAvatar" class="avatar-32px" />
        <div class="seller-top-info">
          <div class="seller-top-name">
            {{ sellerInfo.sellerNickname }}
          </div>
          <div class="seller-top-meta">
            发布{{ sellerInfo.publishGoodsCount }}件宝贝
          </div>
        </div>
      </div>
      <div class="seller-top-right" @click="goToSellerDetail(sellerInfo.sellerId)"><el-icon><Shop /></el-icon>校园号</div>
    </div>

    <el-container>
      <div class="all-container">
        <div class="goods-main">
          <div class="goods-images">
            <div class="thumbnail-list">
              <div
                  v-for="(img, index) in goodsDetail.imageList"
                  :key="img.id || index"
                  class="thumbnail-item"
                  :class="{ active: currentImageIndex === index }"
                  @click="switchImage(index)"
              >
                <img :src="img.imageUrl || goodsDetail.goodsPic" :alt="`缩略图${index + 1}`" />
              </div>
            </div>

            <div class="main-image">
              <img
                  :src="goodsDetail.imageList[currentImageIndex]?.imageUrl || goodsDetail.goodsPic"
                  :alt="goodsDetail.goodsName"
              />
              <div class="image-nav left" @click="switchImage((currentImageIndex - 1 + goodsDetail.imageList.length) % goodsDetail.imageList.length)">
                <el-icon><ArrowLeftBold /></el-icon>
              </div>
              <div class="image-nav right" @click="switchImage((currentImageIndex + 1) % goodsDetail.imageList.length)">
                <el-icon><ArrowRightBold /></el-icon>
              </div>
            </div>
          </div>

          <div class="goods-info">
            <div class="price-box">
              <span class="price">¥{{ formatPrice }}</span>
              <el-tag v-if="goodsDetail.discount" type="warning" size="small" class="discount-tag">
                {{ goodsDetail.discount }}
              </el-tag>
              <span class="original-price" v-if="formatOriginalPrice > 0">
                原价: <del>¥{{ formatOriginalPrice }}</del>
              </span>
              <span class="view-stats">{{ goodsDetail.collectCount }}人想要 | {{ goodsDetail.collectCount }}人浏览</span>
            </div>

            <h1 class="goods-title">{{ goodsDetail.goodsName }}</h1>

            <div class="goods-tech-info">
              {{goodsDetail.goodsDesc}}
            </div>

            <div class="action-buttons fixed">
              <div class="main-btn-group">
                <div class="btn want-btn" @click="() => ElMessage.info('点击了聊一聊')">
                  <el-icon><ChatDotRound /></el-icon>
                  <span>聊一聊</span>
                </div>
                <div class="btn buy-btn" @click="() => ElMessage.info('点击了立即购买')">
                  <span>立即购买</span>
                </div>
              </div>
              <div class="collect-btn" :class="{ collected: goodsDetail.isCollected }" @click="toggleCollect">
                <el-icon><Star /></el-icon>
                <span>{{ goodsDetail.isCollected ? '取消收藏' : '收藏' }}</span>
              </div>
            </div>
          </div>
        </div>

        <div class="goods-detail-bottom">
          <div class="comment-section">
            <h2 class="section-title">评论</h2>
            <Comment :goodsId="goodsId" v-if="loading" :key="goodsId" />
          </div>

          <div class="other-goods-section">
            <h2 class="section-title">其他商品</h2>
            <div class="other-goods-list" v-if="otherGoods.length > 0">
              <div
                  v-for="item in otherGoods"
                  :key="item.id"
                  class="other-goods-item"
                  @click="goToGoodsDetail(item.id)"
              >
                <div class="goods-card">
                  <div class="card-tag" v-if="item.discount || (item.originalPrice > item.sellPrice)">
                    {{ item.discount || `${((item.sellPrice / item.originalPrice) * 10).toFixed(1)}折` }}
                  </div>
                  <img :src="item.goodsPic" :alt="item.goodsName" class="goods-img" />
                  <div class="card-stats">
                    <span>库存{{ item.stock }}</span>
                    <span>{{ item.collectCount || 0 }}人想要</span>
                  </div>
                  <div class="card-name">{{ item.goodsName }}</div>
                  <div class="card-price">
                    ¥{{ Number(item.sellPrice).toFixed(2) }}
                    <span v-if="item.originalPrice > item.sellPrice" class="card-original-price">
                      <del>¥{{ Number(item.originalPrice).toFixed(2) }}</del>
                    </span>
                  </div>
                  <div class="card-seller">
                    <!-- 关键修改2：移除 :size="16"，添加自定义 class -->
                    <ElAvatar class="avatar-16px" :src="item.sellerAvatar || sellerInfo.sellerAvatar" />
                    <span>{{ item.sellerNickname || sellerInfo.sellerNickname }}</span>
                  </div>
                </div>
              </div>
            </div>
            <div class="no-other-goods" v-else>
              <div class="empty-placeholder">
                <p>该商家暂无其他商品</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </el-container>
    <div class="footer-wrapper">
      <Footer />
    </div>
  </div>
</template>

<style scoped lang="scss">
// 新增：自定义头像尺寸样式
.avatar-32px {
  --el-avatar-size: 32px; /* 使用 Element Plus 内置 CSS 变量 */
}

.avatar-16px {
  --el-avatar-size: 16px; /* 使用 Element Plus 内置 CSS 变量 */
}

.goods-detail-container {
  width: 100%;
  min-height: 100vh;
  background-color: #f5f7f9;
  font-size: 14px;
  color: #333;
  padding-top: 70px;

  .seller-top-bar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 12px 20px;
    background: #fff;
    position: sticky;
    top: 0;
    z-index: 99;
    width: calc(100% - 20px);
    max-width: 1580px;
    margin: 15px auto;
    border-radius: 15px;
    box-shadow: 0 2px 12px rgba(0,0,0,0.05);
    border: 1px solid #eee;
    border-bottom: none;

    .seller-top-left {
      display: flex;
      align-items: center;
      gap: 10px;

      .seller-top-info {
        display: flex;
        flex-direction: column;
        gap: 4px;

        .seller-top-name {
          display: flex;
          align-items: center;
          gap: 6px;
          font-weight: 500;

          :deep(.el-tag) {
            background: #52c41a !important;
            border: none;
          }
        }

        .seller-top-meta {
          font-size: 12px;
          color: #666;
          display: flex;
          align-items: center;
          gap: 6px;
        }
      }
    }

    .seller-top-right {
      font-size: 12px;
      color: #666;
      padding: 5px 18px;
      border: 1px solid #eee;
      border-radius: 10px;
      cursor: pointer;
    }
  }

  .el-container {
    width: calc(100% - 20px);
    max-width: 1580px;
    margin: 0 auto;
    background: #fff;
    padding: 20px;
    border-radius: 15px;
    box-shadow: 0 2px 12px rgba(0,0,0,0.05);
    border: 1px solid #eee;
    border-top: none;
  }

  .all-container {
    display: flex;
    flex-direction: column;
    gap: 30px;
    padding: 10px 0;
  }

  .goods-main {
    display: flex;
    gap: 0;
    align-items: flex-start;
    position: relative;
    margin-bottom: 20px;
    width: 100%;
  }

  .goods-images {
    width: 50%;
    display: flex;
    gap: 15px;

    .thumbnail-list {
      width: 80px;
      height: 600px;
      overflow-y: auto;
      &::-webkit-scrollbar {
        display: none;
      }
      -ms-overflow-style: none;
      scrollbar-width: none;
      display: flex;
      flex-direction: column;
      gap: 10px;

      .thumbnail-item {
        width: 80px;
        height: 80px;
        border: 2px solid transparent;
        border-radius: 4px;
        cursor: pointer;
        overflow: hidden;
        transition: border-color 0.2s;

        &.active {
          border-color: #409eff;
        }

        img {
          width: 100%;
          height: 100%;
          object-fit: cover;
        }
      }
    }

    .main-image {
      flex: 1;
      height: 600px;
      border: 1px solid #eee;
      border-radius: 8px;
      overflow: hidden;
      position: relative;

      img {
        width: 100%;
        height: 100%;
        object-fit: cover;
      }

      .image-nav {
        position: absolute;
        top: 50%;
        transform: translateY(-50%);
        width: 40px;
        height: 40px;
        background-color: rgba(0, 0, 0, 0.3);
        color: #fff;
        border-radius: 50%;
        display: flex;
        align-items: center;
        justify-content: center;
        cursor: pointer;
        transition: background-color 0.2s;

        &:hover {
          background-color: rgba(0, 0, 0, 0.5);
        }

        &.left {
          left: 15px;
        }

        &.right {
          right: 15px;
        }
      }
    }
  }

  .goods-info {
    width: 50%;
    display: flex;
    flex-direction: column;
    gap: 16px;
    height: 600px;
    padding: 15px 20px;
    box-sizing: border-box;
    background: #fff;
    border-radius: 8px;
    box-shadow: 0 2px 8px rgba(0,0,0,0.03);

    .price-box {
      display: flex;
      align-items: baseline;
      gap: 10px;
      margin-bottom: 10px;
      flex-wrap: wrap;

      .price {
        font-size: 28px;
        font-weight: bold;
        color: #fe4100;
      }

      .discount-tag {
        background-color: #ffd100 !important;
        color: #333 !important;
        border: none !important;
      }

      .postage-free {
        font-size: 14px;
        color: #52c41a;
        background: #f6ffed;
        padding: 2px 8px;
        border-radius: 4px;
      }

      .original-price {
        font-size: 14px;
        color: #999;
        margin-left: 8px;

        del {
          margin-left: 4px;
        }
      }

      .view-stats {
        font-size: 14px;
        color: #666;
        margin-left: auto;
      }
    }

    .goods-title {
      font-size: 20px;
      font-weight: 500;
      line-height: 1.6;
      margin: 0;
      color: #333;
      padding-bottom: 8px;
      border-bottom: 1px solid #f5f5f5;
    }

    .goods-tech-info{
      font-size: 14px;
      color: #666;
      line-height: 1.8;
      margin: 8px 0;
      padding: 10px;
      background: #fafafa;
      border-radius: 4px;
    }

    .price-method {
      font-size: 14px;
      color: #666;
      margin-top: auto;
    }

    .action-buttons.fixed {
      display: flex;
      gap: 15px;
      width: 100%;
      margin-top: auto;

      .main-btn-group {
        display: flex;
        border-radius: 28px;
        overflow: hidden;
        flex: 1;

        .btn {
          display: flex;
          align-items: center;
          justify-content: center;
          font-size: 16px;
          font-weight: 500;
          cursor: pointer;
          padding: 16px 0;
          width: 50%;
          transition: all 0.2s;
        }

        .want-btn {
          background-color: #ffd100;
          color: #333;
          border-right: 1px solid #e6c000;

          &:hover {
            background-color: #ffc800;
          }
        }

        .buy-btn {
          background-color: #333;
          color: #fff;

          &:hover {
            background-color: #222;
          }
        }
      }

      .collect-btn {
        display: flex;
        align-items: center;
        justify-content: center;
        gap: 6px;
        background: #f5f5f5;
        color: #666;
        border-radius: 28px;
        padding: 0 24px;
        font-size: 14px;
        cursor: pointer;
        white-space: nowrap;
        transition: all 0.2s;

        &.collected {
          background: #ffeded;
          color: #f56c6c;
        }

        &:hover {
          opacity: 0.9;
        }
      }
    }
  }

  .goods-detail-bottom {
    display: flex;
    flex-direction: column;
    gap: 30px;
    width: 100%;

    .section-title {
      font-size: 18px;
      font-weight: bold;
      color: #333;
      margin-bottom: 20px;
      padding-bottom: 12px;
      border-bottom: 2px solid #f5f5f5;
    }

    .comment-section {
      :deep(.comment-wrapper) {
        margin: 0;
        max-width: 100%;
        padding: 10px;
      }
    }

    .other-goods-section {
      .other-goods-list {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
        gap: 25px;

        .other-goods-item {
          cursor: pointer;
          transition: transform 0.2s;

          &:hover {
            transform: translateY(-5px);
          }

          .goods-card {
            border: 1px solid #eee;
            border-radius: 8px;
            overflow: hidden;
            background: #fff;
            position: relative;
            box-shadow: 0 2px 8px rgba(0,0,0,0.05);

            .card-tag {
              position: absolute;
              top: 8px;
              left: 8px;
              background: #ffd100;
              color: #333;
              font-size: 12px;
              padding: 2px 6px;
              border-radius: 4px;
              z-index: 1;
            }

            .goods-img {
              width: 100%;
              height: 200px;
              object-fit: cover;
            }

            .card-stats {
              display: flex;
              justify-content: space-between;
              padding: 10px 12px;
              font-size: 12px;
              color: #666;
              border-bottom: 1px solid #f5f5f5;
            }

            .card-name {
              padding: 10px 12px;
              font-size: 14px;
              color: #333;
              overflow: hidden;
              text-overflow: ellipsis;
              white-space: nowrap;
            }

            .card-price {
              padding: 0 12px 10px;
              font-size: 18px;
              font-weight: bold;
              color: #fe4100;

              .card-original-price {
                font-size: 12px;
                color: #999;
                margin-left: 8px;
                font-weight: normal;

                del {
                  font-size: 12px;
                }
              }
            }

            .card-seller {
              display: flex;
              align-items: center;
              gap: 6px;
              padding: 10px 12px;
              font-size: 12px;
              color: #666;
              border-top: 1px solid #eee;
            }
          }
        }
      }

      .no-other-goods {
        .empty-placeholder {
          height: 200px;
          border: 1px dashed #e0e0e0;
          border-radius: 8px;
          display: flex;
          align-items: center;
          justify-content: center;
          background: #fafafa;

          p {
            font-size: 16px;
            color: #999;
            margin: 0;
          }
        }
      }
    }
  }

  .footer-wrapper {
    margin-top: 40px;
  }
}

@media screen and (max-width: 900px) {
  .goods-detail-container {
    .seller-top-bar {
      width: calc(100% - 20px);
      padding: 10px 12px;

      .seller-top-meta {
        font-size: 10px;
        flex-wrap: wrap;
      }
    }

    .el-container {
      width: calc(100% - 20px);
      padding: 10px;
    }

    .goods-main {
      flex-direction: column;
      gap: 20px;
    }

    .goods-images {
      width: 100%;
      flex-direction: column;

      .thumbnail-list {
        width: 100%;
        height: 80px;
        flex-direction: row;
        overflow-x: auto;
        overflow-y: hidden;
        padding-bottom: 10px;
      }

      .main-image {
        width: 100%;
        height: auto;
        aspect-ratio: 1/1;
      }
    }

    .goods-info {
      height: auto;
      width: 100%;
      padding: 15px;

      .price-box {
        .view-stats {
          margin-left: 0;
          width: 100%;
          text-align: right;
        }
      }

      .action-buttons.fixed {
        flex-direction: column;
        gap: 10px;

        .main-btn-group {
          width: 100%;
        }

        .collect-btn {
          width: 100%;
          padding: 16px 0;
        }
      }
    }

    .goods-detail-bottom {
      flex-direction: column;
      gap: 20px;
    }

    .other-goods-section .other-goods-list {
      grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
    }

    .footer-wrapper {
      margin-top: 20px;
    }
  }
}
</style>