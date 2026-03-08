<script setup lang="js">
import { onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import router from "@/router/index.js";
import { ElMessage, ElAvatar } from 'element-plus';
import useUserInfoStore from "@/stores/userInfo.js";
import { useTokenStore } from "@/stores/token.js";
import { goodsOpenDetailSellerService, goodsOpenListService } from "@/api/goods.js";
// 导入评论接口
import { commentallListService } from "@/api/comment.js";
import Footer from "@/components/footer.vue";
import userlogin from "@/assets/default.png";

const loading = ref(false);
const route = useRoute();
const userInfoStore = useUserInfoStore();
const tokenStore = useTokenStore();
const activeTab = ref('baobei');

// 防抖开关，防止频繁点击
const isTogglingFollow = ref(false);

// 卖家信息（统一字段命名风格）
const sellerInfo = ref({
  sellerId: 0,
  sellerNickname: '',
  sellerAvatar: '',
  publishGoodsCount: 0,
  fanCount: 5,       // 粉丝数
  followCount: 0,    // 关注数
});

// 关注状态
const isFollowed = ref(false);

// 商品数据
const otherGoods = ref([]);

// 评论数据
const commentList = ref([]);

// 监听路由变化，重新加载数据
watch(() => route.params.sellerId, async (newId) => {
  const numericId = Number(newId);
  if (isNaN(numericId) || !numericId) return;
  sellerInfo.value.sellerId = numericId;
  await getSellerInfo();
}, { immediate: true });

// 切换关注状态
const toggleFollow = async () => {
  if (isTogglingFollow.value) return; // 防抖
  isTogglingFollow.value = true;

  try {
    // 未登录拦截
    if (tokenStore.token === '') {
      ElMessage.warning('请先登录后再进行关注操作');
      isTogglingFollow.value = false;
      return;
    }

    if (isFollowed.value) {
      // 取消关注逻辑
      // await unfollowSeller(sellerInfo.value.sellerId);
      isFollowed.value = false;
      sellerInfo.value.fanCount = Math.max(0, sellerInfo.value.fanCount - 1);
      ElMessage.success('已取消关注');
    } else {
      // 关注逻辑
      // await followSeller(sellerInfo.value.sellerId);
      isFollowed.value = true;
      sellerInfo.value.fanCount++;
      ElMessage.success('关注成功');
    }
  } catch (error) {
    console.error('关注操作失败:', error);
    ElMessage.error('操作失败，请稍后重试');
  } finally {
    isTogglingFollow.value = false;
  }
};

// 格式化时间
const formatTime = (time) => {
  if (!time) return '';
  const date = new Date(time);
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  }).replace(/\//g, '-');
};

// 获取评论列表
const loadCommentList = async () => {
  try {
    if (!sellerInfo.value.sellerId) return;
    // 调用评论接口，获取该卖家的全部评论
    const res = await commentallListService(sellerInfo.value.sellerId);
    commentList.value = res.data || [];
  } catch (error) {
    console.error('加载评论列表失败:', error);
    ElMessage.error('加载评论列表失败');
  }
};

// 获取卖家详情
async function getSellerInfo() {
  try {
    loading.value = true;
    const res = await goodsOpenDetailSellerService(sellerInfo.value.sellerId);
    if (res.data) {
      // 合并数据，保留默认值
      sellerInfo.value = {
        ...sellerInfo.value,
        ...res.data,
        fanCount: res.data.fanCount || 5,
        followCount: res.data.followCount || 0,
      };

      // 检查关注状态
      // await checkFollowStatus(sellerInfo.value.sellerId).then(res => {
      //   isFollowed.value = res.data;
      // });

      await loadOtherGoods();
      await loadCommentList(); // 加载评论
    }
  } catch (error) {
    console.error('加载卖家信息失败:', error);
    ElMessage.error('加载卖家信息失败');
  } finally {
    loading.value = false;
  }
}

// 加载卖家商品
const loadOtherGoods = async () => {
  try {
    if (!sellerInfo.value.sellerId) return;
    const queryData = {
      sellerId: sellerInfo.value.sellerId,
      pageNum: 1,
      pageSize: 100
    };
    const res = await goodsOpenListService(queryData);
    otherGoods.value = res.data.items || [];
  } catch (error) {
    console.error('加载其他商品失败:', error);
    ElMessage.error('加载商品列表失败');
  }
};

// 跳转到商品详情
const goToGoodsDetail = (id) => {
  router.push(`/goods/detail/${id}`);
};

onMounted(async () => {
  // 初始化加载
  if (route.params.id) {
    await getSellerInfo();
  }
});
</script>

<template>
  <!-- 新增外层容器包裹，用于实现整体间距和居中 -->
  <div class="page-wrapper">
    <div class="seller-detail-container no-select">
      <!-- 卖家信息 Banner -->
      <section class="user-banner">
        <div class="banner-bg-elements"></div>
        <div class="user-info-box">
          <div class="avatar-area">
            <ElAvatar
                :src="sellerInfo.sellerAvatar || userlogin"
                :size="80"
                class="avatar-large"
            />
          </div>

          <div class="text-area">
            <div class="name-row">
              <h1 class="nickname">{{ sellerInfo.sellerNickname || userInfoStore.info.username || '小黎重度依赖神子' }}</h1>
              <span class="credit-badge">🐼{{ sellerInfo.creditLevel }}</span>
            </div>
            <div class="stats-row">
              <span>{{ sellerInfo.fanCount }}粉丝</span>
              <span class="divider">|</span>
              <span>{{ sellerInfo.followCount }}关注</span>
            </div>
            <div class="bio-row">
              {{ sellerInfo.bio }}
            </div>
          </div>

          <div class="action-area">
            <div v-if="!isFollowed" class="wow-bubble">WOW~</div>
            <button
                :class="['follow-btn', { 'is-followed': isFollowed }]"
                @click="toggleFollow"
                :disabled="isTogglingFollow"
            >
              {{ isFollowed ? '已关注' : '关注' }}
            </button>
          </div>
        </div>
      </section>

      <!-- 内容标签页 + 内容区域 容器 -->
      <div class="content-wrapper">
        <!-- 内容标签页 -->
        <nav class="content-tabs">
          <div
              :class="['tab-item', { active: activeTab === 'baobei' }]"
              @click="activeTab = 'baobei'"
          >
            宝贝 <span class="count">{{ sellerInfo.publishGoodsCount || 0 }}</span>
          </div>
          <div
              :class="['tab-item', { active: activeTab === 'credit' }]"
              @click="activeTab = 'credit'"
          >
            信用及评价 <span class="count">{{ commentList.length || 0 }}</span>
          </div>
        </nav>

        <!-- 商品列表 -->
        <div class="content-grid" v-show="activeTab === 'baobei'">
          <!-- 动态加载的商品 -->
          <div
              v-for="item in otherGoods"
              :key="item.id"
              class="baobei-card"
              @click="goToGoodsDetail(item.id)"
          >
            <div class="card-image">
              <img :src="item.goodsPic || userlogin" :alt="item.goodsName" />
            </div>
            <div class="card-info">
              <div class="title-wrap">
                <span class="tag-shipping" v-if="item.postageFree">包邮</span>
                <span class="title">{{ item.goodsName }}</span>
              </div>
              <div class="price-row">
                <span class="price-symbol">¥</span>
                <span class="price-num">{{ Number(item.sellPrice).toFixed(2) }}</span>
                <span class="want-count">{{ item.collectCount || 0 }}人想要</span>
              </div>
              <div class="user-tag">
                <ElAvatar :src="item.sellerAvatar || sellerInfo.sellerAvatar || userlogin" class="avatar-16px" />
                <span>{{ item.sellerNickname || sellerInfo.sellerNickname }}</span>
              </div>
            </div>
          </div>

          <!-- 无商品提示 -->
          <div class="no-goods" v-if="otherGoods.length === 0 && !loading">
            <div class="empty-placeholder">
              <p>该商家暂无在售商品</p>
            </div>
          </div>
        </div>

        <!-- 信用及评价列表 -->
        <div class="content-grid" v-show="activeTab === 'credit'">
          <div class="comment-list" v-if="commentList.length > 0">
            <div
                v-for="item in commentList"
                :key="item.id"
                class="comment-item"
            >
              <div class="comment-avatar">
                <ElAvatar :src="item.userUrl || userlogin" :size="40" />
              </div>
              <div class="comment-content">
                <div class="comment-header">
                  <span class="comment-nickname">{{ item.nickname || '匿名用户' }}</span>
                </div>
                <div class="comment-badge">
                  <span class="comment-text">{{ item.content }}</span>
                </div>
                <div class="comment-time">
                  {{ formatTime(item.createTime) }}
                </div>
              </div>
            </div>
          </div>
          <div class="no-goods" v-else>
            <div class="empty-placeholder">
              <p>暂无信用及评价信息</p>
            </div>
          </div>
        </div>
      </div>

    </div>
  </div>
  <!-- 底部组件 -->
  <div class="footer-wrapper">
    <Footer />
  </div>
</template>

<style lang="scss" scoped>
// 自定义头像尺寸
.avatar-16px {
  --el-avatar-size: 30px;
}

.avatar-large {
  border: 3px solid #fff;
  box-shadow: 0 2px 10px rgba(0,0,0,0.05);
}

// 新增外层页面容器
.page-wrapper {
  width: 100%;
  min-height: 100vh;
  padding: 100px; // 外层容器设置100px间距
  box-sizing: border-box; // 保证padding不会撑大容器
  display: flex;
  justify-content: center; // 水平居中
  align-items: center; // 垂直居中
  background-color: #f8f8f8; // 可选：添加背景色区分容器
}

.seller-detail-container {
  width: 100%;
  min-height: calc(100vh - 200px); // 减去外层100px*2的上下间距，保证整体高度适配
  height: 100%;
  display: flex;
  flex-direction: column;
  background-color: #ffffff;
  font-size: 14px;
  color: #333;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  border-radius: 15px; // 设置15px圆角
  overflow: hidden; // 保证内部元素不会超出圆角
  box-shadow: 0 4px 20px rgba(0,0,0,0.05); // 可选：添加阴影增强视觉效果

  // 用户Banner
  .user-banner {
    height: 160px;
    background: linear-gradient(to right, #fffbf0 0%, #fff6de 100%);
    position: relative;
    overflow: hidden;
    border-radius: 0;
    display: flex;
    justify-content: center;
    align-items: center;

    .banner-bg-elements {
      position: absolute;
      right: -20px;
      bottom: -20px;
      width: 300px;
      height: 100%;
      background-color: #ffecd2;
      border-radius: 50%;
      opacity: 0.4;
      filter: blur(20px);
      z-index: 0;
    }

    .user-info-box {
      display: flex;
      align-items: flex-start;
      gap: 20px;
      position: relative;
      z-index: 1;
      width: 100%;

      padding: 0 20px; // 内部内容少量内边距，保证不会贴边

      .avatar-area {
        width: 80px;
        height: 80px;
        border-radius: 50%;
        overflow: hidden;
        cursor: pointer;
      }

      .text-area {
        flex: 1;
        display: flex;
        flex-direction: column;
        justify-content: center;
        margin-top: 5px;

        .name-row {
          display: flex;
          align-items: center;
          gap: 10px;
          margin-bottom: 8px;

          .nickname {
            font-size: 20px;
            font-weight: bold;
            margin: 0;
            color: #333;
          }

          .credit-badge {
            background-color: #ffd100;
            color: #333;
            font-size: 11px;
            padding: 2px 8px;
            border-radius: 12px;
            font-weight: bold;
          }
        }

        .stats-row {
          font-size: 12px;
          color: #999;
          display: flex;
          align-items: center;
          gap: 8px;
          margin-bottom: 8px;

          .divider {
            color: #ddd;
            font-size: 10px;
          }
        }

        .bio-row {
          font-size: 12px;
          color: #666;
        }
      }

      .action-area {
        display: flex;
        align-items: center;
        gap: 10px;
        margin-top: 15px;

        .wow-bubble {
          font-size: 14px;
          color: #999;
          font-weight: bold;
          font-style: italic;
          background: #f5f5f5;
          padding: 4px 8px;
          border-radius: 12px;
        }

        .follow-btn {
          background: #ffd100;
          color: #333;
          border: none;
          padding: 10px 24px;
          border-radius: 20px;
          font-size: 14px;
          font-weight: bold;
          cursor: pointer;
          transition: all 0.2s ease;
          display: flex;
          align-items: center;
          gap: 6px;

          &:hover:not(.is-followed) {
            background: #f5c800;
          }

          &:disabled {
            opacity: 0.7;
            cursor: not-allowed;
          }

          &.is-followed {
            background: #f0f0f0;
            color: #999;
            font-weight: normal;
          }
        }
      }
    }
  }

  // 内容容器（标签页+商品列表）
  .content-wrapper {
    flex: 1;
    display: flex;
    flex-direction: column;
    width: 100%;
    max-width: 1500px;
    border-radius: 15px;
    overflow: hidden;
    background: #fff;
    box-shadow: 0 2px 10px rgba(0,0,0,0.03);
    margin-top: 10px;
    margin-bottom: 10px;
  }

  // 标签页
  .content-tabs {
    display: flex;
    background: #fff;
    border-bottom: 1px solid #f5f5f5;
    margin: 0;

    .tab-item {
      padding: 20px 20px 15px 20px;
      font-size: 16px;
      cursor: pointer;
      color: #666;
      position: relative;

      .count {
        font-size: 12px;
        margin-left: 2px;
        color: #999;
      }

      &.active {
        color: #333;
        font-weight: bold;

        .count {
          color: #333;
        }

        &::after {
          content: '';
          position: absolute;
          bottom: 0;
          left: 0;
          width: 100%;
          height: 3px;
          background: #ffd100;
          border-radius: 2px;
        }
      }
    }
  }

  // 商品网格
  .content-grid {
    flex: 1;
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
    gap: 20px;
    padding: 0 20px; // 内部内容少量内边距
    align-content: flex-start;
    min-height: 300px; // 保证空状态有足够高度

    .no-goods {
      grid-column: 1 / -1;
      flex: 1;
      display: flex;
      align-items: center;
      justify-content: center;
      height: 100%;
      min-height: 300px; // 空状态最小高度，保证足够空间

      .empty-placeholder {
        height: 200px;
        width: 100%;
        max-width: 600px;
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

    // 评论列表样式
    .comment-list {
      grid-column: 1 / -1;
      width: 100%;
      padding: 10px 0;
    }

    .comment-item {
      display: flex;
      gap: 12px;
      padding: 16px 0;
      border-bottom: 1px solid #f5f5f5;

      &:last-child {
        border-bottom: none;
      }

      .comment-avatar {
        flex-shrink: 0;
      }

      .comment-content {
        flex: 1;
        display: flex;
        flex-direction: column;
        gap: 8px;

        .comment-header {
          display: flex;
          align-items: center;
          gap: 8px;
          font-size: 14px;
          color: #333;

          .comment-tag {
            font-size: 13px;
            color: #666;

            .tag-seller {
              display: inline-block;
              background-color: #e6f7ff;
              color: #1890ff;
              font-size: 11px;
              padding: 1px 6px;
              border-radius: 4px;
              margin-left: 4px;
            }
          }
        }

        .comment-badge {
          display: flex;
          align-items: center;
          gap: 8px;

          .badge-good {
            background-color: #f6ffed;
            color: #52c41a;
            border: 1px solid #b7eb8f;
            font-size: 12px;
            padding: 2px 8px;
            border-radius: 4px;
          }

          .comment-text {
            font-size: 14px;
            color: #333;
          }
        }

        .comment-time {
          font-size: 12px;
          color: #999;
        }
      }
    }
  }

  // 商品卡片
  .baobei-card {
    background: #fff;
    border-radius: 12px;
    overflow: hidden;
    transition: all 0.3s ease;
    cursor: pointer;

    &:hover {
      transform: translateY(-3px);
      box-shadow: 0 8px 20px rgba(0, 0, 0, 0.08);
    }

    .card-image {
      height: 220px;
      width: 100%;
      border-radius: 12px;
      overflow: hidden;

      img {
        width: 100%;
        height: 100%;
        object-fit: cover;
      }
    }

    .card-info {
      padding: 12px 4px;

      .title-wrap {
        font-size: 14px;
        color: #333;
        line-height: 20px;
        margin-bottom: 8px;
        display: -webkit-box;
        -webkit-line-clamp: 2;
        -webkit-box-orient: vertical;
        overflow: hidden;

        .tag-shipping {
          display: inline-block;
          background-color: #ffd100;
          color: #333;
          font-size: 11px;
          padding: 1px 4px;
          border-radius: 4px;
          margin-right: 4px;
          vertical-align: text-bottom;
          font-weight: bold;
        }

        .title {
          font-weight: 500;
        }
      }

      .price-row {
        display: flex;
        align-items: baseline;
        margin-bottom: 10px;

        .price-symbol {
          color: #ff4d4f;
          font-size: 12px;
          font-weight: bold;
        }

        .price-num {
          color: #ff4d4f;
          font-size: 18px;
          font-weight: bold;
          margin-right: 6px;
        }

        .want-count {
          color: #999;
          font-size: 11px;
        }
      }

      .user-tag {
        display: flex;
        align-items: center;
        gap: 6px;
        font-size: 12px;
        color: #999;
      }
    }
  }

  .footer-wrapper {
    margin-top: auto; // 固定到底部
    width: 100%;
    padding: 10px 20px; // 底部组件少量内边距
    box-sizing: border-box;
  }
}

// 响应式适配
@media screen and (max-width: 900px) {
  .page-wrapper {
    padding: 20px; // 移动端减小间距，提升体验
  }

  .seller-detail-container {
    min-height: calc(100vh - 40px); // 对应移动端padding调整

    .user-banner {
      height: auto;
      padding: 20px 0; // 移除左右padding，保留上下

      .user-info-box {
        flex-direction: column;
        align-items: flex-start;
        gap: 15px;
        padding: 0 15px; // 仅给内容加内边距

        .action-area {
          width: 100%;
          justify-content: flex-end;
        }
      }
    }

    .content-wrapper {
      margin: 10px 15px; // 响应式下给容器加左右margin
    }

    .content-tabs {
      padding: 0;
    }

    .content-grid {
      grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
      padding: 0 10px; // 仅给网格内容加少量内边距
    }

    .footer-wrapper {
      margin-top: auto;
    }
  }
}
</style>