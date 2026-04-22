<script setup lang="js">
import { onMounted, ref, watch, computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import Footer from "@/components/footer.vue";
import { ElMessage, ElAvatar, ElTag} from 'element-plus';
import Comment from "@/components/Comment.vue";
import { commentList } from "@/api/comment.js";
import { goodsDetailService, goodsOpenListService, goodsOpenDetailSellerService, goodsTraceService } from "@/api/goods.js";
import {ArrowLeftBold, ArrowRightBold, ChatDotRound, Shop, Star} from "@element-plus/icons-vue";

import { goodsAddCollect, goodsDeleteCollect, goodsListByCollectId } from "@/api/likeCollect.js";
import {useTokenStore} from "@/stores/token.js";

import { createChatSessionService } from "@/api/chat.js";

const loading = ref(false);
const route = useRoute();
const router = useRouter();
const goodsId = ref(Number(route.params.id));
const isTogglingCollect = ref(false); // 防重复点击
const tokenStore = useTokenStore();
const createDefaultTraceInfo = () => ({
  traceId: '',
  latestVersion: 0,
  latestEventType: '',
  latestPayloadHash: '',
  latestBlockHash: '',
  latestTxHash: '',
  currentPayloadHash: '',
  dataVerified: false,
  chainVerified: false,
  verified: false,
  message: '',
  records: []
});
const traceInfo = ref(createDefaultTraceInfo());
const traceLoading = ref(false);
const traceDialogVisible = ref(false);
const traceEventMap = {
  GOODS_PUBLISHED: '商品发布',
  GOODS_UPDATED: '商品信息修改',
  GOODS_STATUS_CHANGED: '商品状态变更',
  GOODS_DELETED: '商品删除前存证',
  GOODS_ORDER_LOCKED: '订单创建后锁定商品',
  GOODS_ORDER_PAID: '订单支付凭证同步',
  GOODS_ORDER_SENT: '订单发货凭证同步',
  GOODS_OWNERSHIP_TRANSFERRED: '交易完成权属变更',
  GOODS_ORDER_REFUND_APPLIED: '退款申请同步',
  GOODS_ORDER_REFUND_HANDLED: '退款处理同步',
  GOODS_ORDER_ADMIN_CHANGED: '后台交易状态同步',
  GOODS_ORDER_DELETED: '订单删除后关联快照更新',
  GOODS_ORDER_RESTOCKED: '订单取消/退款后恢复库存',
  ORDER_CREATED: '订单创建',
  ORDER_PAID: '订单支付',
  ORDER_SENT: '订单发货',
  ORDER_RECEIVED: '订单确认收货',
  ORDER_CANCELED: '订单取消',
  ORDER_REFUND_APPLIED: '订单申请退款',
  ORDER_REFUND_HANDLED: '订单退款处理',
  ORDER_STATUS_ADMIN_CHANGED: '后台修改订单状态',
  ORDER_DELETED: '订单删除前存证'
};
// 商品详情数据
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

// 检查登录状态
const checkLogin = () => {
  if (tokenStore.token === '') {
    ElMessage.warning('请先登录后再发起聊天');
    return false;
  }
  return true;
};

// 发起聊天
const startChat = async () => {
  // 1. 检查登录
  if (!checkLogin()) return;

  // 2. 获取卖家ID
  const receiverId = goodsDetail.value.sellerId;
  if (!receiverId) {
    ElMessage.error('获取卖家信息失败，无法发起聊天');
    return;
  }

  try {
    // 3. 调用创建会话接口
    const res = await createChatSessionService(receiverId);
    const sessionId = res.data.id;

    // 4. 跳转到聊天页面
    ElMessage.closeAll();
    await router.push({
      path: '/homes/notice',
      query: { sessionId }
    });
  } catch (error) {
    ElMessage.closeAll();
    console.error('创建聊天会话失败:', error);
    ElMessage.error('发起聊天失败，请稍后重试');
  }
};

// 当前选中的主图索引
const currentImageIndex = ref(0);

// 评论相关
const comments = ref([]);
const loadComments = async () => {
  try {
    const id = route.params.id;
    if (!id || isNaN(id) || id <= 0) {
      console.error('商品ID不合法', id);
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
  currentImageIndex.value = 0;
  // 加载商品详情
  await getGoodsDetailById();
  // 获取用户是否收藏了该商品
  await checkCollectStatus();
  // 加载卖家信息
  await getSellerInfo(goodsDetail.value.sellerId);
  // 加载链上溯源信息
  await getGoodsTraceById();
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
    console.error('获取收藏状态失败', error);
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

const normalizeTraceInfo = (data = {}) => {
  const normalized = Object.assign(createDefaultTraceInfo(), data);
  normalized.records = Array.isArray(data.records) ? data.records.slice(0, 1) : [];
  return normalized;
};

const getGoodsTraceById = async () => {
  if (!goodsId.value) {
    traceInfo.value = createDefaultTraceInfo();
    return;
  }

  traceLoading.value = true;
  try {
    const res = await goodsTraceService(goodsId.value);
    traceInfo.value = normalizeTraceInfo(res.data);
  } catch (error) {
    console.error('加载商品溯源信息失败:', error);
    traceInfo.value = normalizeTraceInfo({
      message: '商品溯源信息加载失败，请稍后重试'
    });
  } finally {
    traceLoading.value = false;
  }
};

// 收藏/取消收藏逻辑
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
    // 拦截未登录错误
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

const traceStatusType = computed(() => {
  if (!traceInfo.value.traceId) return 'info';
  return traceInfo.value.verified ? 'success' : 'danger';
});

const traceStatusText = computed(() => {
  if (!traceInfo.value.traceId) return '未上链';
  return traceInfo.value.verified ? '验证通过' : '验证异常';
});

const traceAlertType = computed(() => {
  if (!traceInfo.value.traceId) return 'info';
  return traceInfo.value.verified ? 'success' : 'error';
});

const formatTraceEvent = (eventType) => {
  return traceEventMap[eventType] || eventType || '未知事件';
};

const formatTraceTime = (value) => {
  if (!value) return '--';
  const normalized = String(value).replace('T', ' ');
  return normalized.length > 19 ? normalized.slice(0, 19) : normalized;
};

const formatHashPreview = (value) => {
  if (!value) return '--';
  return value.length > 24 ? `${value.slice(0, 12)}...${value.slice(-8)}` : value;
};

const getVerifyResultText = (flag) => {
  return flag ? '通过' : '异常';
};

const getRecordVerifyType = (record) => {
  return record?.blockVerified && record?.previousLinkVerified ? 'success' : 'danger';
};

const getRecordVerifyText = (record) => {
  return record?.blockVerified && record?.previousLinkVerified ? '区块验证通过' : '区块验证异常';
};

const openTraceDialog = async () => {
  traceDialogVisible.value = true;
  if (!traceLoading.value) {
    await getGoodsTraceById();
  }
};

onMounted(async () => {
  await loadComments();
});

const goToSellerDetail=(sellerId)=>{
  router.push(`/seller/detail/${sellerId}`);
}

// 立即购买
const handleBuyNow = () => {
  // 检查是否登录
  if (!tokenStore.token) {
    ElMessage.warning('请先登录后再购买');
    router.push('/homes/login');
    return;
  }

  // 检查商品是否可购买
  const status = Number(goodsDetail.value.goodsStatus);
  if (status !== 1) {
    const statusMap = {
      2: '已售罄',
      3: '已下架',
      4: '审核中',
      5: '违规封禁'
    };
    const statusText = statusMap[status] || '不可购买';
    ElMessage.warning(`该商品${statusText}`);
    return;
  }

  // 跳转到支付页面
  router.push({
    path: '/payment',
    query: { goodsId: goodsId.value }
  });
}

// 处理图片预览列表
const previewImageList = computed(() => {
  // 优先使用多图列表
  if (goodsDetail.value.imageList && goodsDetail.value.imageList.length) {
    return goodsDetail.value.imageList.map(item => item.imageUrl).filter(Boolean);
  }
  // 备用：使用主图
  return goodsDetail.value.goodsPic ? [goodsDetail.value.goodsPic] : [];
});

// 切换图片
const switchImage = (index) => {
  const listLength = goodsDetail.value.imageList?.length || 1;
  if (listLength === 0) return;
  // 确保索引在有效范围内
  currentImageIndex.value = (index + listLength) % listLength;
};
</script>

<template>
  <div class="goods-detail-container">
    <div class="seller-top-bar">
      <div class="seller-top-left">
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
      <div class="seller-top-right" @click="goToSellerDetail(sellerInfo.sellerId)"><el-icon><Shop /></el-icon>店铺主页</div>
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
              <el-image
                  :src="goodsDetail.imageList[currentImageIndex]?.imageUrl || goodsDetail.goodsPic"
                  :alt="goodsDetail.goodsName"
                  :preview-src-list="previewImageList"
                  fit="cover"
                  class="main-image-img"
                  :preview-teleported="true"
              >
                <template #error>
                  <div class="image-slot">加载失败</div>
                </template>
                <template #placeholder>
                  <div class="image-slot">加载中...</div>
                </template>
              </el-image>
              <div class="image-nav left" @click="switchImage((currentImageIndex - 1 + (goodsDetail.imageList.length || 1)) % (goodsDetail.imageList.length || 1))">
                <el-icon><ArrowLeftBold /></el-icon>
              </div>
              <div class="image-nav right" @click="switchImage((currentImageIndex + 1) % (goodsDetail.imageList.length || 1))">
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

            <div class="traceability-card">
              <div class="traceability-head">
                <div>
                  <div class="traceability-eyebrow">链上溯源</div>
                  <div class="traceability-title">商品发布、交易与信息修改均已链上存证</div>
                </div>
                <el-tag :type="traceStatusType" effect="dark">
                  {{ traceStatusText }}
                </el-tag>
              </div>

              <div class="traceability-row">
                <span class="trace-label">溯源编码</span>
                <button class="trace-link" type="button" @click="openTraceDialog">
                  {{ traceLoading ? '加载中...' : (traceInfo.traceId || '暂无链编码，点击查看商品存证信息') }}
                </button>
              </div>

              <div class="traceability-meta">
                <span>
                  链路校验：
                  <strong :class="traceInfo.chainVerified ? 'trace-ok' : 'trace-warn'">
                    {{ traceInfo.traceId ? getVerifyResultText(traceInfo.chainVerified) : '未生成' }}
                  </strong>
                </span>
                <span>
                  数据比对：
                  <strong :class="traceInfo.dataVerified ? 'trace-ok' : 'trace-warn'">
                    {{ traceInfo.traceId ? getVerifyResultText(traceInfo.dataVerified) : '未生成' }}
                  </strong>
                </span>
                <span v-if="traceInfo.latestVersion">最新版本：V{{ traceInfo.latestVersion }}</span>
              </div>

              <p class="traceability-message">
                {{ traceInfo.message || '点击链编码可查看该商品的基本存证信息。' }}
              </p>
            </div>

            <div class="action-buttons fixed">
              <div class="main-btn-group">
                <div class="btn want-btn" @click="startChat">
                  <el-icon><ChatDotRound /></el-icon>
                  <span>聊一聊</span>
                </div>
                <div class="btn buy-btn" @click="handleBuyNow">
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

    <el-dialog
        v-model="traceDialogVisible"
        title="商品链上溯源记录"
        width="90%"
        destroy-on-close
        class="trace-dialog-window"
    >
      <div v-loading="traceLoading" class="trace-dialog">
        <div class="trace-dialog__summary">
          <div class="trace-summary-card">
            <span class="trace-summary-card__label">商品链编码</span>
            <span class="trace-summary-card__value trace-code">{{ traceInfo.traceId || '--' }}</span>
          </div>
          <div class="trace-summary-card">
            <span class="trace-summary-card__label">商品名称</span>
            <span class="trace-summary-card__value">{{ goodsDetail.goodsName || '--' }}</span>
          </div>
          <div class="trace-summary-card">
            <span class="trace-summary-card__label">当前库存</span>
            <span class="trace-summary-card__value">{{ goodsDetail.stock ?? '--' }}</span>
          </div>
          <div class="trace-summary-card">
            <span class="trace-summary-card__label">当前售价</span>
            <span class="trace-summary-card__value">¥{{ formatPrice }}</span>
          </div>
        </div>
        <el-alert
            :title="traceInfo.traceId ? '该链编码可用于核验商品真伪与原始发布信息' : '该商品当前暂无链编码'"
            :type="traceInfo.traceId ? 'success' : 'info'"
            :closable="false"
            show-icon
        />
      </div>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.avatar-32px {
  --el-avatar-size: 32px;
}

.avatar-16px {
  --el-avatar-size: 16px;
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
    width: 100%;
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

      .main-image-img {
        width: 100%;
        height: 100%;
      }

      .image-slot {
        width: 100%;
        height: 100%;
        display: flex;
        align-items: center;
        justify-content: center;
        background-color: #f5f5f5;
        color: #999;
      }

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

    @media screen and (max-width: 900px) {
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

          .main-image-img {
            width: 100%;
            height: 100%;
          }
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

    .traceability-card {
      display: flex;
      flex-direction: column;
      gap: 12px;
      padding: 18px;
      border-radius: 16px;
      background: linear-gradient(135deg, #fff8ec 0%, #ffffff 100%);
      border: 1px solid #f3dfb4;

      .traceability-head {
        display: flex;
        align-items: flex-start;
        justify-content: space-between;
        gap: 12px;
      }

      .traceability-eyebrow {
        font-size: 12px;
        color: #a16a00;
        letter-spacing: 0.08em;
        margin-bottom: 6px;
      }

      .traceability-title {
        font-size: 16px;
        font-weight: 600;
        color: #333;
        line-height: 1.6;
      }

      .traceability-row {
        display: flex;
        align-items: center;
        gap: 12px;
      }

      .trace-label {
        min-width: 72px;
        font-size: 13px;
        color: #7c6b4e;
      }

      .trace-link {
        padding: 0;
        border: none;
        background: transparent;
        color: #b35f00;
        font-size: 14px;
        font-weight: 600;
        text-align: left;
        cursor: pointer;
        word-break: break-all;

        &:hover {
          color: #8f4b00;
        }
      }

      .traceability-meta {
        display: flex;
        flex-wrap: wrap;
        gap: 12px 24px;
        font-size: 13px;
        color: #666;
      }

      .traceability-message {
        margin: 0;
        color: #666;
        line-height: 1.7;
      }
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

  .trace-ok {
    color: #1f8d49;
  }

  .trace-warn {
    color: #d35454;
  }

  .trace-dialog {
    display: flex;
    flex-direction: column;
    gap: 16px;
  }

  .trace-dialog__summary {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
    gap: 12px;
  }

  .trace-summary-card {
    display: flex;
    flex-direction: column;
    gap: 8px;
    padding: 14px;
    border-radius: 14px;
    background: #f8fafc;
    border: 1px solid #e8edf4;
  }

  .trace-summary-card__label {
    font-size: 12px;
    color: #7b8794;
  }

  .trace-summary-card__value {
    font-size: 14px;
    font-weight: 600;
    color: #1f2937;
    line-height: 1.6;
    word-break: break-all;
  }

  .trace-summary-card__sub {
    font-size: 12px;
    color: #94a3b8;
  }

  .trace-records {
    position: relative;
    display: flex;
    flex-direction: column;
    gap: 16px;
    padding-left: 16px;

    &::before {
      content: '';
      position: absolute;
      top: 10px;
      bottom: 10px;
      left: 6px;
      width: 2px;
      background: linear-gradient(180deg, #f1c061 0%, #d7dee8 100%);
    }
  }

  .trace-record {
    position: relative;
    display: flex;
    gap: 14px;
  }

  .trace-record__marker {
    position: relative;
    z-index: 1;
    width: 14px;
    height: 14px;
    margin-top: 10px;
    flex-shrink: 0;
    border-radius: 50%;
    background: #f5b940;
    box-shadow: 0 0 0 4px rgba(245, 185, 64, 0.16);
  }

  .trace-record__content {
    flex: 1;
    padding: 16px;
    border-radius: 18px;
    background: #fff;
    border: 1px solid #e8edf4;
    box-shadow: 0 10px 24px rgba(15, 23, 42, 0.04);
  }

  .trace-record__header {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 12px;
  }

  .trace-record__title {
    display: flex;
    align-items: center;
    gap: 10px;
    font-size: 16px;
    font-weight: 600;
    color: #1f2937;
    line-height: 1.5;
  }

  .trace-version {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 46px;
    padding: 4px 10px;
    border-radius: 999px;
    background: #fff3cd;
    color: #8a4f00;
    font-size: 12px;
    font-weight: 700;
  }

  .trace-record__time {
    margin-top: 6px;
    font-size: 12px;
    color: #94a3b8;
  }

  .trace-record__summary {
    margin: 12px 0 16px;
    color: #475569;
    line-height: 1.8;
  }

  .trace-record__grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
    gap: 12px;
  }

  .trace-item {
    padding: 10px 12px;
    border-radius: 12px;
    background: #f8fafc;
    border: 1px solid #e8edf4;

    label {
      display: block;
      margin-bottom: 6px;
      font-size: 12px;
      color: #94a3b8;
    }

    span {
      display: block;
      font-size: 13px;
      color: #1f2937;
      line-height: 1.7;
      word-break: break-all;
    }
  }

  .trace-record__details {
    margin-top: 14px;
    border: 1px solid #e8edf4;
    border-radius: 12px;
    background: #fbfdff;
    overflow: hidden;

    summary {
      padding: 12px 14px;
      cursor: pointer;
      list-style: none;
      font-size: 14px;
      font-weight: 600;
      color: #334155;

      &::-webkit-details-marker {
        display: none;
      }
    }

    pre {
      margin: 0;
      padding: 0 14px 14px;
      overflow: auto;
      white-space: pre-wrap;
      word-break: break-all;
      font-size: 12px;
      line-height: 1.7;
      color: #334155;
      font-family: "Consolas", "SFMono-Regular", monospace;
    }
  }

  .trace-code {
    font-family: "Consolas", "SFMono-Regular", monospace;
    font-size: 12px;
    line-height: 1.7;
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

      .traceability-card {
        .traceability-head,
        .traceability-row {
          flex-direction: column;
          align-items: flex-start;
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

    .trace-dialog__summary,
    .trace-record__grid {
      grid-template-columns: 1fr;
    }

    .trace-record__header {
      flex-direction: column;
      align-items: flex-start;
    }
  }
}
</style>