<script setup lang="js">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox, ElInput } from "element-plus";
import { useRoute, useRouter } from 'vue-router';
import {
  getOrderListService,
  getOrderDetailService,
  handleRefundService, sendOrderService // 导入退款处理接口
} from "@/api/order.js";
import useUserInfoStore from '@/stores/userInfo.js';
import { userInfoServices } from '@/api/user.js';
// 新增：导入创建会话API
import { createChatSessionService } from "@/api/chat.js";

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

// 状态配置（严格对齐后端OrderStatusEnum）
const orderStatusOptions = [
  { label: '全部', value: '' },
  { label: '待付款', value: '1' }, // PENDING_PAY
  { label: '待发货', value: '2' }, // PENDING_DELIVERY
  { label: '待收货', value: '3' }, // PENDING_RECEIVE
  { label: '已完成', value: '4' }, // COMPLETED
  { label: '已取消', value: '5' }  // CANCELED
  // 注：退款中不是订单状态，是退款状态，无需在订单状态筛选中显示
];

// 状态标签样式映射（对齐后端OrderStatusEnum）
const statusTagType = (status) => {
  const map = {
    1: 'warning',  // 待付款
    2: 'primary',  // 待发货
    3: 'info',     // 待收货
    4: 'success',  // 已完成
    5: 'danger'    // 已取消
  };
  return map[status] || 'info';
};

// 订单卡片状态文字色值映射（对齐后端OrderStatusEnum）
const getStatusClass = (status) => {
  const map = {
    1: 'status-pending-pay',   // 待付款
    2: 'status-pending-send',  // 待发货
    3: 'status-pending-receive',// 待收货
    4: 'status-success',       // 已完成
    5: 'status-closed'         // 已取消
  };
  return map[status] || 'status-default';
};

// 退款状态标签样式映射（对齐后端RefundStatusEnum）
const getRefundTagType = (refundStatus) => {
  const map = {
    0: 'info',     // 无退款
    1: 'danger',   // 退款中
    2: 'success',  // 退款成功
    3: 'warning'   // 退款失败
  };
  return map[refundStatus] || 'info';
};

// 获取订单列表
const getOrderList = async () => {
  loading.value = true;
  try {
    if (!userInfoStore.info || !userInfoStore.info.id) {
      ElMessage.error('请先登录');
      await router.push('/login');
      return;
    }
    const currentUserId = userInfoStore.info.id;

    let params = {
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      sellerId: currentUserId, // 卖家视角筛选
      orderStatus: orderStatus.value !== '' ? Number(orderStatus.value) : undefined
      // 退款状态筛选可扩展：refundStatus: xxx
    };

    const result = await getOrderListService(params);
    console.log(result);
    // 后端返回的OrderVO已包含refundStatusName，直接使用
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

// 退款处理弹窗相关（严格对齐后端RefundHandleDTO）
const refundHandleDialogVisible = ref(false);
const currentRefundOrder = ref(null);
const refundHandleForm = ref({
  orderId: null,          // 订单ID（必填）
  handleResult: 2,        // 处理结果：2-退款成功(REFUND_SUCCESS) 3-退款失败(REFUND_FAILED)
  refundRemark: ''        // 处理备注（驳回理由/同意说明）
});

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

// 打开退款处理弹窗
const openRefundHandleDialog = (row) => {
  currentRefundOrder.value = row;
  // 初始化退款处理表单（严格对齐后端RefundHandleDTO）
  refundHandleForm.value = {
    orderId: row.id,
    handleResult: 2, // 默认同意退款（REFUND_SUCCESS）
    refundRemark: ''
  };
  refundHandleDialogVisible.value = true;
};

// 处理退款（严格对齐后端handleRefund接口）
const handleRefund = async () => {
  if (!refundHandleForm.value.orderId) {
    ElMessage.error('订单ID不能为空');
    return;
  }

  // 退款失败（驳回）时必须填写备注（后端校验要求）
  if (refundHandleForm.value.handleResult === 3 && !refundHandleForm.value.refundRemark.trim()) {
    ElMessage.error('驳回退款必须填写处理备注');
    return;
  }

  try {
    const handleType = refundHandleForm.value.handleResult === 2 ? '同意' : '驳回';
    await ElMessageBox.confirm(
        `确认${handleType}该订单的退款申请吗？\n订单号：${currentRefundOrder.value.orderNo}`,
        `${handleType}退款`,
        {
          confirmButtonText: `确认${handleType}`,
          cancelButtonText: '取消',
          type: refundHandleForm.value.handleResult === 2 ? 'info' : 'warning'
        }
    );

    // 构造后端要求的RefundHandleDTO参数
    const refundHandleDTO = {
      orderId: refundHandleForm.value.orderId,
      handleResult: refundHandleForm.value.handleResult,
      refundRemark: refundHandleForm.value.refundRemark.trim()
    };

    // 调用退款处理接口
    await handleRefundService(refundHandleDTO);

    ElMessage.success(`${handleType}退款成功`);
    refundHandleDialogVisible.value = false;
    await getOrderList(); // 刷新订单列表
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(`${refundHandleForm.value.handleResult === 2 ? '同意' : '驳回'}退款失败`);
      console.error('退款处理失败：', error);
    }
  }
};

// 处理Tab切换事件
const handleTabClick = () => {
  pageNum.value = 1;
  getOrderList();
};

// 发货功能（增加退款状态校验：退款中不能发货，对齐后端逻辑）
const handleShip = async (row) => {
  // 校验：退款中（refundStatus=1）的订单禁止发货
  if (row.refundStatus === 1) {
    ElMessage.error('该订单正在退款中，无法发货');
    return;
  }

  try {
    await ElMessageBox.confirm(
        `确认发货吗？\n订单号：${row.orderNo}\n商品：${row.goodsName}`,
        '确认发货',
        {
          confirmButtonText: '确认发货',
          cancelButtonText: '取消',
          type: 'info'
        }
    );

    await sendOrderService(row.id); // 3-待收货（PENDING_RECEIVE）
    ElMessage.success('发货成功，等待买家确认收货');
    await getOrderList();
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('发货失败');
    }
  }
};

// 页面初始化
onMounted(async () => {
  await getUserInfo();

  const statusFromQuery = route.query.status;
  if (statusFromQuery !== undefined && statusFromQuery !== null && statusFromQuery !== '') {
    const parsedStatus = Number(statusFromQuery);
    if (!isNaN(parsedStatus) && parsedStatus >= 1 && parsedStatus <= 5) { // 仅包含订单状态枚举值
      orderStatus.value = String(parsedStatus);
    }
  }
  await getOrderList();
});

// 跳转到商品详情
const goToDetail = (id) => {
  const targetUrl = `${window.location.origin}/goods/detail/${id}`;
  window.open(targetUrl, '_blank');
}


// 发起聊天（聊一聊按钮点击事件）
const startChat = async (sellerId) => {

  // 2. 获取卖家ID
  const receiverId = sellerId;
  if (!receiverId) {
    ElMessage.error('获取卖家信息失败，无法发起聊天');
    return;
  }

  try {
    // 3. 调用创建会话接口（无则创建，有则返回已有会话）
    const res = await createChatSessionService(receiverId);
    const sessionId = res.data.id;

    // 4. 关闭loading并跳转到聊天页面
    ElMessage.closeAll();
    await router.push({
      path: '/homes/notice',
      query: { sessionId } // 携带会话ID
    });
  } catch (error) {
    ElMessage.closeAll();
    console.error('创建聊天会话失败:', error);
    ElMessage.error('发起聊天失败，请稍后重试');
  }
};
</script>

<template>
  <div class="order-page-wrapper">
    <!-- 页面头部标题 -->
    <div class="page-header">
      <span class="page-title">我卖出的</span>
    </div>

    <!-- 顶部状态Tab栏（仅显示订单状态，退款状态是附加属性） -->
    <el-tabs v-model="orderStatus" class="status-tabs" @tab-click="handleTabClick">
      <el-tab-pane
          v-for="item in orderStatusOptions"
          :key="item.value"
          :label="item.label"
          :name="item.value"
      />
    </el-tabs>

    <!-- 订单列表卡片区域 -->
    <div class="content-wrapper">
      <div class="order-list" v-loading="loading">
        <!-- 空状态 -->
        <el-empty v-if="orderList.length === 0 && !loading" description="暂无订单记录" />

        <!-- 订单卡片循环（使用后端返回的OrderVO字段） -->
        <div class="order-card" v-for="order in orderList" :key="order.id">
          <!-- 卡片头部：买家信息 + 订单状态 + 退款状态 -->
          <div class="order-card-header">
            <div class="buyer-info">
              <el-avatar :size="24" :src="order.buyerPic" v-if="order.buyerPic" />
              <el-avatar :size="24" v-else>{{ order.buyerNickname?.charAt(0) || '买' }}</el-avatar>
              <span class="buyer-name">{{ order.buyerNickname }}</span>
            </div>
            <div class="status-group">
              <!-- 订单状态：直接使用后端返回的orderStatusName -->
              <span class="order-status-text" :class="getStatusClass(order.orderStatus)">
                {{ order.orderStatusName }}
              </span>
              <!-- 退款状态：直接使用后端返回的refundStatusName，仅非无退款时显示 -->
              <el-tag
                  v-if="order.refundStatus !== undefined && order.refundStatus !== 0"
                  size="small"
                  :type="getRefundTagType(order.refundStatus)"
                  class="refund-status-tag"
              >
                {{ order.refundStatusName }}
              </el-tag>
            </div>
          </div>

          <!-- 商品信息区域（使用OrderVO的商品字段） -->
          <div class="goods-content" @click="showOrderDetail(order)">
            <img :src="order.goodsPic" class="goods-cover" alt="商品图片" v-if="order.goodsPic" />
            <div class="goods-info">
              <p class="goods-name">{{ order.goodsName }}</p>
              <p class="goods-price">¥{{ order.totalAmount }}</p>
            </div>
          </div>

          <!-- 操作按钮区域 -->
          <div class="order-action-bar">
            <!-- 联系买家按钮 -->
            <el-button size="small" class="action-btn" @click="startChat(order.buyerId)">联系买家</el-button>

            <!-- 核心操作按钮 -->
            <el-button size="small" class="action-btn primary" @click="showOrderDetail(order)">详情</el-button>
            <!-- 发货按钮：仅待发货且无退款时显示 -->
            <el-button
                size="small"
                class="action-btn primary"
                @click="handleShip(order)"
                v-if="order.orderStatus === 2 && (order.refundStatus === undefined || order.refundStatus !== 2)"
            >
              发货
            </el-button>

            <!-- 退款处理按钮：仅退款中（refundStatus=1）时显示 -->
            <el-button
                size="small"
                class="action-btn primary"
                type="danger"
                @click="openRefundHandleDialog(order)"
                v-if="order.refundStatus === 1"
            >
              处理退款
            </el-button>

            <!-- 已处理退款状态显示：直接使用后端返回的refundStatusName -->
            <el-button
                size="small"
                class="action-btn"
                disabled
                :type="getRefundTagType(order.refundStatus)"
                v-else-if="order.refundStatus !== undefined && order.refundStatus !== 0 && order.refundStatus !== 1"
            >
              {{ order.refundStatusName }}
            </el-button>
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

    <!-- 订单详情弹窗（展示完整的订单信息） -->
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
          <!-- 退款状态：使用后端返回的名称 -->
          <el-descriptions-item label="退款状态" v-if="orderDetail.refundStatus !== undefined">
            <el-tag :type="getRefundTagType(orderDetail.refundStatus)">
              {{ orderDetail.refundStatusName }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="订单金额">
            <span class="amount">¥{{ orderDetail.totalAmount }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="支付方式">
            {{ orderDetail.payTypeName || '未支付' }}
          </el-descriptions-item>
          <el-descriptions-item label="买家">
            {{ orderDetail.buyerNickname || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="买家电话">
            {{ orderDetail.buyerPhone || '-' }}
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
          <!-- 退款相关字段 -->
          <el-descriptions-item label="退款金额" :span="2" v-if="orderDetail.refundAmount">
            ¥{{ orderDetail.refundAmount || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="退款原因" :span="2" v-if="orderDetail.refundReason">
            {{ orderDetail.refundReason || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="退款申请时间" :span="2" v-if="orderDetail.refundApplyTime">
            {{ orderDetail.refundApplyTime ? orderDetail.refundApplyTime.replace('T', ' ').substring(0, 19) : '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="退款处理时间" :span="2" v-if="orderDetail.refundHandleTime">
            {{ orderDetail.refundHandleTime ? orderDetail.refundHandleTime.replace('T', ' ').substring(0, 19) : '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="处理备注" :span="2" v-if="orderDetail.refundRemark">
            {{ orderDetail.refundRemark || '-' }}
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

        <div v-if="orderDetail.remark" class="section-title">订单备注</div>
        <div v-if="orderDetail.remark" class="remark-content">
          {{ orderDetail.remark }}
        </div>
      </div>
      <template #footer>
        <el-button @click="dialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 退款处理弹窗（严格对齐后端RefundHandleDTO） -->
    <el-dialog
        v-model="refundHandleDialogVisible"
        title="处理退款申请"
        width="500px"
        :close-on-click-modal="false"
    >
      <div v-if="currentRefundOrder">
        <div class="refund-order-info">
          <p><strong>订单号：</strong>{{ currentRefundOrder.orderNo }}</p>
          <p><strong>商品：</strong>{{ currentRefundOrder.goodsName }}</p>
          <p><strong>退款金额：</strong>¥{{ currentRefundOrder.refundAmount || currentRefundOrder.totalAmount }}</p>
          <p><strong>买家退款原因：</strong>{{ currentRefundOrder.refundReason || '无' }}</p>
        </div>

        <el-radio-group v-model="refundHandleForm.handleResult" class="refund-radio-group">
          <el-radio :label="2">同意退款（退款成功）</el-radio>
          <el-radio :label="3">驳回退款（退款失败）</el-radio>
        </el-radio-group>

        <!-- 处理备注输入框（驳回时必填） -->
        <el-form-item
            label="处理备注"
            :required="refundHandleForm.handleResult === 3"
            class="refund-reason-item"
        >
          <el-input
              v-model="refundHandleForm.refundRemark"
              type="textarea"
              :rows="4"
              :placeholder="refundHandleForm.handleResult === 3 ? '请输入驳回退款的理由（必填）' : '请输入处理备注（可选）'"
              maxlength="200"
              show-word-limit
          />
        </el-form-item>
      </div>

      <template #footer>
        <el-button @click="refundHandleDialogVisible = false">取消</el-button>
        <el-button
            type="primary"
            @click="handleRefund"
        >
          {{ refundHandleForm.handleResult === 2 ? '同意退款' : '驳回退款' }}
        </el-button>
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
  display: flex;
  flex-direction: column;
}

.content-wrapper {
  flex: 1;
  overflow-y: auto;
  padding: 0 12px 12px;
  margin-bottom: 8px;
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

// 订单卡片
.order-card {
  background: #fff;
  border-radius: 8px;
  margin-bottom: 12px;
  overflow: hidden;
}

// 卡片头部：买家+状态
.order-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #f5f5f5;

  .buyer-info {
    display: flex;
    align-items: center;
    gap: 8px;

    .buyer-name {
      font-size: 14px;
      color: #333;
      font-weight: 500;
    }
  }

  .status-group {
    display: flex;
    align-items: center;
    gap: 8px;

    .refund-status-tag {
      margin-left: 8px;
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

    &.el-button--danger {
      background: #f56c6c;
      border-color: #f56c6c;
      color: #fff;
      &:hover {
        background: #f78989;
        border-color: #f78989;
      }
    }
  }
}

// 分页容器
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  padding: 12px 20px;
  background: #fff;
  border-radius: 8px;
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

// 退款处理弹窗样式
.refund-order-info {
  margin-bottom: 20px;
  padding: 10px;
  background-color: #f8f9fa;
  border-radius: 4px;

  p {
    margin: 5px 0;
    font-size: 14px;
  }
}

.refund-radio-group {
  margin-bottom: 20px;
  display: flex;
  gap: 20px;

  :deep(.el-radio) {
    font-size: 14px;
  }
}

.refund-reason-item {
  margin-bottom: 0;

  :deep(.el-form-item__label) {
    font-weight: 500;
  }
}

:deep(.el-descriptions__label) {
  font-weight: 500;
  width: 100px;
}
</style>