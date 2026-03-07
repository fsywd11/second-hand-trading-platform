<script setup lang="js">
import { ref, onMounted} from 'vue'
import { ElMessage, ElMessageBox } from "element-plus";
import { useRoute, useRouter } from 'vue-router';
import {
  getOrderListService,
  getOrderDetailService,
  cancelOrderService,
  confirmReceiveOrderService,
  updateOrderStatusService
} from "@/api/order.js";
import useUserInfoStore from '@/stores/userInfo.js';
import {userInfoServices} from '@/api/user.js';

const route = useRoute();
const router = useRouter();
const userInfoStore = useUserInfoStore();

// 获取用户信息
const getUserInfo = async () => {
  try {
    let result = await userInfoServices();
    userInfoStore.setInfo(result.data);
  } catch (error) {
    ElMessage.error('获取用户信息失败');
  }
};

const orderStatus = ref('');

const orderList = ref([]);
const loading = ref(false);

const pageNum = ref(1);
const total = ref(0);
const pageSize = ref(10);

// 状态配置
const orderStatusOptions = [
  { label: '全部', value: '' },
  { label: '待付款', value: '1' },
  { label: '待发货', value: '2' },
  { label: '待收货', value: '3' },
  { label: '待评价', value: '4' },
  { label: '退款中', value: '5' }
];

// 状态标签样式映射
const statusTagType = (status) => {
  const map = {
    1: 'warning',
    2: 'primary',
    3: 'info',
    4: 'success',
    5: 'danger'
  };
  return map[status] || 'info';
};

// 订单卡片状态文字色值映射
const getStatusClass = (status) => {
  const map = {
    1: 'status-pending-pay',
    2: 'status-pending-send',
    3: 'status-pending-receive',
    4: 'status-success',
    5: 'status-closed'
  };
  return map[status] || 'status-default';
};

// 获取订单列表
const getOrderList = async () => {
  loading.value = true;
  try {
    const currentUserId = userInfoStore.info.id;
    if (!currentUserId) {
      ElMessage.error('请先登录');
      await router.push('/login');
      return;
    }

    let params = {
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      buyerId: currentUserId
    };

    // 只有当 orderStatus 不为空时才添加到参数中
    if (orderStatus.value !== '') {
      params.orderStatus = Number(orderStatus.value);
    }

    const result = await getOrderListService(params);
    orderList.value = result.data?.items || [];
    total.value = result.data?.total || 0;
  } catch (error) {
    ElMessage.error('获取订单列表失败');
  } finally {
    loading.value = false;
  }
};

const onSizeChange = (size) => {
  pageSize.value = size;
  getOrderList();
};

const onCurrentChange = (num) => {
  pageNum.value = num;
  getOrderList();
};

// 订单详情相关
const dialogVisible = ref(false);
const orderDetail = ref({});
const detailLoading = ref(false);

const showOrderDetail = async (row) => {
  dialogVisible.value = true;
  detailLoading.value = true;
  try {
    const result = await getOrderDetailService(row.id);
    orderDetail.value = result.data || {};
  } catch (error) {
    ElMessage.error('获取订单详情失败');
    dialogVisible.value = false;
  } finally {
    detailLoading.value = false;
  }
};

// 核心操作
const handlePay = async (row) => {
  try {
    await ElMessageBox.confirm(
        `确认支付订单 ${row.orderNo} 吗？\n支付金额：¥${row.totalAmount}`,
        '确认支付',
        {
          confirmButtonText: '确认支付',
          cancelButtonText: '取消',
          type: 'info'
        }
    );

    await updateOrderStatusService(row.id, 2);
    ElMessage.success('支付成功，等待卖家发货');
    await getOrderList();
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('支付失败');
    }
  }
};

const handleCancel = async (row) => {
  try {
    await ElMessageBox.confirm(
        `确认取消订单 ${row.orderNo} 吗？`,
        '取消订单',
        {
          confirmButtonText: '确认取消',
          cancelButtonText: '返回',
          type: 'warning'
        }
    );

    await cancelOrderService(row.id);
    ElMessage.success('订单已取消');
    await getOrderList();
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('取消订单失败');
    }
  }
};

const handleConfirmReceive = async (row) => {
  try {
    await ElMessageBox.confirm(
        `确认已收到商品吗？\n订单号：${row.orderNo}`,
        '确认收货',
        {
          confirmButtonText: '确认收货',
          cancelButtonText: '取消',
          type: 'success'
        }
    );

    await confirmReceiveOrderService(row.id);
    ElMessage.success('确认收货成功');
    await getOrderList();
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('确认收货失败');
    }
  }
};

// 页面初始化
onMounted(async () => {
  await getUserInfo();

  const statusFromQuery = route.query.status;
  if (statusFromQuery !== undefined && statusFromQuery !== null && statusFromQuery !== '') {
    const parsedStatus = Number(statusFromQuery);
    if (!isNaN(parsedStatus) && parsedStatus >= 1 && parsedStatus <= 5) {
      orderStatus.value = String(parsedStatus);
    }
  }
  await getOrderList();
});
</script>

<template>
  <div class="order-page-wrapper">
    <!-- 页面头部标题 -->
    <div class="page-header">
      <span class="page-title">我买到的</span>
    </div>

    <!-- 顶部状态Tab栏 -->
    <el-tabs v-model="orderStatus" class="status-tabs" @tab-click="getOrderList">
      <el-tab-pane
          v-for="item in orderStatusOptions"
          :key="item.value"
          :label="item.label"
          :name="item.value"
      />
    </el-tabs>

    <!-- 订单列表卡片区域 - 新增内容容器 -->
    <div class="content-wrapper">
      <div class="order-list" v-loading="loading">
        <!-- 空状态 -->
        <el-empty v-if="orderList.length === 0 && !loading" description="暂无订单记录" />

        <!-- 订单卡片循环 -->
        <div class="order-card" v-for="order in orderList" :key="order.id">
          <!-- 卡片头部：卖家信息 + 订单状态 -->
          <div class="order-card-header">
            <div class="seller-info">
              <el-avatar :size="24" :src="order.sellerAvatar" v-if="order.sellerAvatar" />
              <el-avatar :size="24" v-else>{{ order.sellerNickname?.charAt(0) || '用' }}</el-avatar>
              <span class="seller-name">{{ order.sellerNickname }}</span>
            </div>
            <span class="order-status-text" :class="getStatusClass(order.orderStatus)">
              {{ order.orderStatusName }}
            </span>
          </div>

          <!-- 商品信息区域 -->
          <div class="goods-content" @click="showOrderDetail(order)">
            <img :src="order.goodsPic" class="goods-cover" alt="商品图片" v-if="order.goodsPic" />
            <div class="goods-info">
              <p class="goods-name">{{ order.goodsName }}</p>
              <p class="goods-price">¥{{ order.totalAmount }}</p>
            </div>
          </div>

          <!-- 操作按钮区域 -->
          <div class="order-action-bar">
            <!-- 联系卖家按钮 - 空置无功能 -->
            <el-button size="small" class="action-btn">联系卖家</el-button>

            <el-button size="small" class="action-btn" v-if="order.orderStatus === 5">删除订单</el-button>

            <!-- 原有核心操作按钮 -->
            <el-button size="small" class="action-btn" @click="handlePay(order)" v-if="order.orderStatus === 1">付款</el-button>
            <el-button size="small" class="action-btn" @click="handleCancel(order)" v-if="order.orderStatus === 1">取消订单</el-button>
            <el-button size="small" class="action-btn" @click="handleConfirmReceive(order)" v-if="order.orderStatus === 3">确认收货</el-button>

            <el-button size="small" class="action-btn primary" @click="showOrderDetail(order)">详情</el-button>
            <el-button size="small" class="action-btn primary">再次购买</el-button>
            <el-button size="small" class="action-btn primary" v-if="order.orderStatus === 4">去评价</el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- 分页区域 -->
    <div class="pagination-wrapper">
      <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :page-sizes="[5, 10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          :total="total"
          @size-change="onSizeChange"
          @current-change="onCurrentChange"
      />
    </div>

    <!-- 订单详情弹窗 -->
    <el-dialog v-model="dialogVisible" title="订单详情" width="700px" destroy-on-close>
      <div v-loading="detailLoading" class="order-detail-content">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="订单编号" :span="2">
            {{ orderDetail.orderNo }}
          </el-descriptions-item>
          <el-descriptions-item label="订单状态">
            <el-tag :type="statusTagType(orderDetail.orderStatus)">
              {{ orderDetail.orderStatusName }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="订单金额">
            <span class="amount">¥{{ orderDetail.totalAmount }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="卖家">
            {{ orderDetail.sellerNickname || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="创建时间" :span="2">
            {{ orderDetail.createTime ? orderDetail.createTime.replace('T', ' ').substring(0, 19) : '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="支付时间" :span="2">
            {{ orderDetail.payTime ? orderDetail.payTime.replace('T', ' ').substring(0, 19) : '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="发货时间" :span="2">
            {{ orderDetail.deliveryTime ? orderDetail.deliveryTime.replace('T', ' ').substring(0, 19) : '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="收货时间" :span="2">
            {{ orderDetail.receiveTime ? orderDetail.receiveTime.replace('T', ' ').substring(0, 19) : '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="取消时间" :span="2">
            {{ orderDetail.cancelTime ? orderDetail.cancelTime.replace('T', ' ').substring(0, 19) : '-' }}
          </el-descriptions-item>
        </el-descriptions>

        <div class="section-title">商品信息</div>
        <div class="goods-detail-card">
          <img v-if="orderDetail.goodsPic" :src="orderDetail.goodsPic" class="goods-detail-img" alt=""/>
          <div class="goods-detail-info">
            <div class="goods-detail-name">{{ orderDetail.goodsName }}</div>
            <div class="goods-detail-price">单价：¥{{ orderDetail.goodsPrice }}</div>
            <div class="goods-detail-num">数量：{{ orderDetail.goodsNum }}</div>
          </div>
        </div>

        <div class="section-title">收货信息</div>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="收货地址">
            {{ orderDetail.address || '-' }}
          </el-descriptions-item>
        </el-descriptions>

        <div v-if="orderDetail.remark" class="section-title">备注信息</div>
        <div v-if="orderDetail.remark" class="remark-content">
          {{ orderDetail.remark }}
        </div>
      </div>
      <template #footer>
        <el-button @click="dialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.order-page-wrapper {
  width: 100%;
  min-height: 100vh;
  background-color: #f5f5f5;
  box-sizing: border-box;
  // 核心修改：设置flex垂直布局
  display: flex;
  flex-direction: column;
  // 移除底部padding，避免分页区域间距问题
}

// 新增内容容器样式
.content-wrapper {
  flex: 1; // 自动填充剩余空间
  overflow-y: auto; // 内容超出时显示滚动条
  padding: 0 12px 12px; // 保留左右内边距，底部内边距避免内容贴分页
  margin-bottom: 8px; // 与分页区域保持间距
}

// 页面标题
.page-header {
  padding: 16px 20px;
  background: #fff;
  .page-title {
    font-size: 18px;
    font-weight: 600;
    color: #333;
  }
}

// 状态Tab栏
.status-tabs {
  background: #fff;
  margin-bottom: 12px;
  :deep(.el-tabs__header) {
    margin: 0;
  }
  :deep(.el-tabs__nav-wrap) {
    padding: 0 12px;
  }
  :deep(.el-tabs__item) {
    font-size: 15px;
    color: #666;
    padding: 0 16px;
  }
  :deep(.el-tabs__item.is-active) {
    color: #333;
    font-weight: 600;
  }
  :deep(.el-tabs__active-bar) {
    background-color: #ff6700;
    height: 3px;
  }
}

// 订单列表容器
.order-list {
  // 移除原有padding，移到content-wrapper中
}

// 订单卡片
.order-card {
  background: #fff;
  border-radius: 8px;
  margin-bottom: 12px;
  overflow: hidden;
}

// 卡片头部：卖家+状态
.order-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #f5f5f5;

  .seller-info {
    display: flex;
    align-items: center;
    gap: 8px;
    .seller-name {
      font-size: 14px;
      color: #333;
      font-weight: 500;
    }
  }

  .order-status-text {
    font-size: 14px;
    font-weight: 500;
    &.status-success {
      color: #ff6700;
    }
    &.status-closed {
      color: #999;
    }
    &.status-pending-pay {
      color: #e6a23c;
    }
    &.status-pending-send {
      color: #409eff;
    }
    &.status-pending-receive {
      color: #909399;
    }
    &.status-default {
      color: #666;
    }
  }
}

// 商品内容区域
.goods-content {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 16px;
  cursor: pointer;
  transition: background-color 0.2s;
  &:hover {
    background-color: #fafafa;
  }

  .goods-cover {
    width: 80px;
    height: 80px;
    border-radius: 6px;
    object-fit: cover;
    flex-shrink: 0;
  }

  .goods-info {
    flex: 1;
    display: flex;
    flex-direction: column;
    justify-content: space-between;
    min-height: 80px;

    .goods-name {
      font-size: 14px;
      color: #333;
      line-height: 1.4;
      margin: 0;
      display: -webkit-box;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .goods-price {
      font-size: 18px;
      font-weight: 600;
      color: #ff4400;
      margin: 0;
      text-align: right;
    }
  }
}

// 操作按钮栏
.order-action-bar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  padding: 12px 16px;
  border-top: 1px solid #f5f5f5;

  .action-btn {
    border-radius: 20px;
    padding: 4px 16px;
    font-size: 13px;
    border-color: #ddd;
    color: #333;
    &.primary {
      background: #ffd700;
      border-color: #ffd700;
      color: #333;
      &:hover {
        background: #ffe033;
        border-color: #ffe033;
      }
    }
  }
}

// 分页容器（修改样式，固定在底部）
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  padding: 12px 20px;
  background: #fff;
  border-radius: 8px;
  // 确保在flex布局中靠底
  margin-top: auto;
}

// 详情页样式
.amount {
  color: #ff4400;
  font-weight: bold;
  font-size: 15px;
}

.order-detail-content {
  .section-title {
    font-size: 16px;
    font-weight: bold;
    color: #303133;
    margin: 20px 0 12px 0;
    padding-left: 10px;
    border-left: 3px solid #409eff;
  }

  .goods-detail-card {
    display: flex;
    align-items: center;
    gap: 16px;
    padding: 16px;
    background-color: #fafafa;
    border-radius: 8px;
    margin-bottom: 10px;

    .goods-detail-img {
      width: 100px;
      height: 100px;
      border-radius: 8px;
      object-fit: cover;
      flex-shrink: 0;
    }

    .goods-detail-info {
      flex: 1;

      .goods-detail-name {
        font-size: 16px;
        font-weight: 500;
        color: #333;
        margin-bottom: 8px;
      }

      .goods-detail-price {
        font-size: 14px;
        color: #666;
        margin-bottom: 4px;
      }

      .goods-detail-num {
        font-size: 14px;
        color: #999;
      }
    }
  }

  .remark-content {
    padding: 12px;
    background-color: #fffbe6;
    border: 1px solid #ffe58f;
    border-radius: 4px;
    color: #d48806;
  }
}

:deep(.el-descriptions__label) {
  font-weight: 500;
  width: 100px;
}
</style>