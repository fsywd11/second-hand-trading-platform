<script setup lang="js">
import { ref, onMounted} from 'vue'
import { ElMessage, ElMessageBox, ElForm, ElFormItem, ElInput, ElInputNumber, ElDialog } from "element-plus";
import { useRoute, useRouter } from 'vue-router';
import {
  getOrderListService,
  getOrderDetailService,
  cancelOrderService,
  confirmReceiveOrderService,
  updateOrderStatusService,
  applyRefundService // 导入退款申请接口
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

// 退款状态文本映射（根据实际业务调整）
const getRefundStatusText = (refundStatus) => {
  const map = {
    0: '未申请退款',
    1: '退款审核中',
    2: '退款成功',
    3: '退款驳回',
    4: '退款处理中'
  };
  return map[refundStatus] || '退款中';
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

// 退款申请相关逻辑
const refundDialogVisible = ref(false);
const refundLoading = ref(false);
const currentRefundOrder = ref(null);
const refundForm = ref({
  refundAmount: 0,
  refundReason: ''
});
const refundFormRules = {
  refundAmount: [
    { required: true, message: '请输入退款金额', trigger: 'blur' },
    { type: 'number', min: 0.01, message: '退款金额必须大于0', trigger: 'blur' }
  ],
  refundReason: [
    { required: true, message: '请填写退款原因', trigger: 'blur' },
    { min: 5, max: 200, message: '退款原因长度在5-200个字符之间', trigger: 'blur' }
  ]
};

// 声明表单引用（解决TS报错）
const refundFormRef = ref(null);

// 打开退款申请弹窗（增加退款状态校验）
const openRefundDialog = (row) => {
  // 前置校验：如果已申请退款，提示用户并返回
  if (row.refundStatus !== undefined && row.refundStatus !== 0) {
    ElMessage.info(`该订单${getRefundStatusText(row.refundStatus)}，无法重复申请`);
    // 引导用户查看详情
    showOrderDetail(row);
    return;
  }

  currentRefundOrder.value = row;
  refundForm.value = {
    refundAmount: row.totalAmount,
    refundReason: ''
  };
  refundDialogVisible.value = true;
};

// 提交退款申请
const submitRefundApply = async () => {
  // 手动校验表单（解决TS报错）
  if (refundFormRef.value) {
    try {
      await refundFormRef.value.validate();
    } catch (error) {
      ElMessage.error('请填写完整的退款信息');
      return;
    }
  }

  // 基础验证
  if (!refundForm.value.refundAmount || refundForm.value.refundAmount <= 0) {
    ElMessage.error('请输入有效的退款金额');
    return;
  }

  if (refundForm.value.refundAmount > currentRefundOrder.value.totalAmount) {
    ElMessage.error(`退款金额不能超过订单总额 ¥${currentRefundOrder.value.totalAmount}`);
    return;
  }

  if (!refundForm.value.refundReason.trim()) {
    ElMessage.error('请填写退款原因');
    return;
  }

  try {
    // 二次确认
    await ElMessageBox.confirm(
        `确认申请退款吗？\n订单号：${currentRefundOrder.value.orderNo}\n退款金额：¥${refundForm.value.refundAmount}\n退款原因：${refundForm.value.refundReason}`,
        '退款申请确认',
        {
          confirmButtonText: '确认申请',
          cancelButtonText: '取消',
          type: 'warning'
        }
    );

    refundLoading.value = true;

    // 构造退款申请参数
    const refundApplyDTO = {
      orderId: currentRefundOrder.value.id,
      orderNo: currentRefundOrder.value.orderNo,
      buyerId: userInfoStore.info.id,
      refundAmount: refundForm.value.refundAmount,
      refundReason: refundForm.value.refundReason
    };

    // 调用退款申请接口
    await applyRefundService(refundApplyDTO);

    ElMessage.success('退款申请提交成功，等待卖家处理');
    refundDialogVisible.value = false;
    await getOrderList(); // 刷新订单列表
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('退款申请提交失败，请稍后重试');
      console.error('退款申请失败：', error);
    }
  } finally {
    refundLoading.value = false;
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
      >
      </el-tab-pane>
    </el-tabs>

    <!-- 订单列表卡片区域 -->
    <div class="content-wrapper">
      <div class="order-list" v-loading="loading">
        <!-- 空状态 -->
        <el-empty v-if="orderList.length === 0 && !loading" description="暂无订单记录" />

        <!-- 订单卡片循环 -->
        <div class="order-card" v-for="order in orderList" :key="order.id">
          <!-- 卡片头部：卖家信息 + 订单状态 + 退款状态 -->
          <div class="order-card-header">
            <div class="seller-info">
              <el-avatar :size="24" :src="order.sellerAvatar" v-if="order.sellerAvatar" />
              <el-avatar :size="24" v-else>{{ order.sellerNickname?.charAt(0) || '用' }}</el-avatar>
              <span class="seller-name">{{ order.sellerNickname }}</span>
            </div>
            <div class="status-group">
              <span class="order-status-text" :class="getStatusClass(order.orderStatus)">
                {{ order.orderStatusName }}
              </span>
              <!-- 显示退款状态标签 -->
              <el-tag
                  v-if="order.refundStatus !== undefined && order.refundStatus !== 0"
                  size="small"
                  type="danger"
                  class="refund-status-tag"
              >
                {{ getRefundStatusText(order.refundStatus) }}
              </el-tag>
            </div>
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
            <!-- 联系卖家按钮 -->
            <el-button size="small" class="action-btn">联系卖家</el-button>

            <el-button size="small" class="action-btn" v-if="order.orderStatus === 5">删除订单</el-button>

            <!-- 原有核心操作按钮 -->
            <el-button size="small" class="action-btn" @click="handlePay(order)" v-if="order.orderStatus === 1">付款</el-button>
            <el-button size="small" class="action-btn" @click="handleCancel(order)" v-if="order.orderStatus === 1">取消订单</el-button>
            <el-button size="small" class="action-btn" @click="handleConfirmReceive(order)" v-if="order.orderStatus === 3">确认收货</el-button>

            <!-- 退款按钮：仅在待发货/待收货状态且未申请退款时显示 -->
            <el-button
                size="small"
                class="action-btn primary"
                @click="openRefundDialog(order)"
                v-if="[2, 3].includes(order.orderStatus) && (order.refundStatus === undefined || order.refundStatus === 0)"
                type="danger"
            >
              申请退款
            </el-button>

            <!-- 已申请退款提示按钮 -->
            <el-button
                size="small"
                class="action-btn"
                disabled
                v-else-if="order.refundStatus !== undefined && order.refundStatus !== 0"
            >
              {{ getRefundStatusText(order.refundStatus) }}
            </el-button>

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
      >
      </el-pagination>
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
          <!-- 详情页显示退款状态 -->
          <el-descriptions-item label="退款状态" v-if="orderDetail.refundStatus !== undefined">
            <el-tag :type="orderDetail.refundStatus === 0 ? 'info' : 'danger'">
              {{ getRefundStatusText(orderDetail.refundStatus) }}
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
          <!-- 退款相关时间 -->
          <el-descriptions-item label="退款申请时间" :span="2" v-if="orderDetail.refundApplyTime">
            {{ orderDetail.refundApplyTime ? orderDetail.refundApplyTime.replace('T', ' ').substring(0, 19) : '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="退款完成时间" :span="2" v-if="orderDetail.refundCompleteTime">
            {{ orderDetail.refundCompleteTime ? orderDetail.refundCompleteTime.replace('T', ' ').substring(0, 19) : '-' }}
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

    <!-- 退款申请弹窗 -->
    <el-dialog
        v-model="refundDialogVisible"
        title="申请退款"
        width="500px"
        :close-on-click-modal="false"
        :before-close="() => { refundLoading.value = false }"
    >
      <el-form
          :model="refundForm"
          :rules="refundFormRules"
          ref="refundFormRef"
          label-width="100px"
          class="refund-form"
      >
        <el-form-item label="订单编号" prop="orderNo">
          <span>{{ currentRefundOrder?.orderNo || '-' }}</span>
        </el-form-item>
        <el-form-item label="订单金额" prop="totalAmount">
          <span>¥{{ currentRefundOrder?.totalAmount || 0 }}</span>
        </el-form-item>
        <el-form-item label="退款金额" prop="refundAmount">
          <el-input-number
              v-model="refundForm.refundAmount"
              :min="0.01"
              :max="currentRefundOrder?.totalAmount || 0"
              :precision="2"
              style="width: 100%;"
              placeholder="请输入退款金额"
          />
        </el-form-item>
        <el-form-item label="退款原因" prop="refundReason">
          <el-input
              v-model="refundForm.refundReason"
              type="textarea"
              :rows="4"
              placeholder="请详细描述退款原因（5-200字）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="refundDialogVisible = false">取消</el-button>
        <el-button
            type="primary"
            @click="submitRefundApply"
            :loading="refundLoading"
        >
          提交申请
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

// 退款表单样式
.refund-form {
  margin-top: 10px;
  :deep(.el-form-item__content) {
    line-height: 1.5;
  }
}

:deep(.el-descriptions__label) {
  font-weight: 500;
  width: 100px;
}
</style>