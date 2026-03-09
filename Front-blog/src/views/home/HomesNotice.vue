<script lang="js" setup>
import { ref, onMounted, onUnmounted, nextTick, computed } from 'vue';
import { ElMessage } from 'element-plus';
import defaultAvater from '@/assets/default.png'
import { useRouter } from 'vue-router'
import {
  getMyChatListService,
  getSessionMsgService,
  sendChatMsgService,
  markChatMsgAsReadService,
} from "@/api/chat.js";
import { useTokenStore } from "@/stores/token.js";
import useUserInfoStore from "@/stores/userInfo.js";

// ========== 状态管理 ==========
const userInfoStore = useUserInfoStore();
const tokenStore = useTokenStore();
const router = useRouter()

// ========== 响应式数据 ==========
const chatList = ref([]);
const msgList = ref([]);
const currentSessionId = ref(null);
const inputContent = ref("");
const userId = computed(() => userInfoStore.info?.id);
const pageNum = ref(1);
const pageSize = ref(20);
let ws = null;

// 新增：当前会话的交易信息（模拟截图中的商品/订单卡片）
const currentSessionTradeInfo = computed(() => {
  const session = chatList.value.find(item => item.id === currentSessionId.value);
  if (!session) return null;
  return {
    title: session.lastMsg,
    previewImg: session.friendAvatar, // 用好友头像作为商品预览图
  };
});

// ========== 方法定义 ==========
const loadChatList = async () => {
  try {
    const res = await getMyChatListService();
    // 计算当前用户的未读数量
    const myId = userId.value;
    chatList.value = res.data.map(session => ({
      ...session,
      unreadCount: session.fromUserId === myId ? session.toUnread : session.fromUnread,
      time: formatTime(session.lastMsgTime) // 格式化时间
    }));
  } catch (error) {
    ElMessage.error("加载聊天列表失败");
    console.error(error);
  }
};

const enterChat = async (sessionId) => {
  currentSessionId.value = sessionId;
  pageNum.value = 1;
  await loadChatMsg();
  // 进入会话后自动标记消息为已读
  await markAsRead(sessionId);
};

const loadChatMsg = async () => {
  if (!currentSessionId.value) return;
  try {
    const res = await getSessionMsgService(
        currentSessionId.value,
        pageNum.value,
        pageSize.value
    );
    msgList.value = res.data.items; // 修正：从PageBean中取items
    await nextTick(() => {
      const msgListEl = document.querySelector(".msg-list");
      if (msgListEl) {
        msgListEl.scrollTop = msgListEl.scrollHeight;
      }
    });
  } catch (error) {
    ElMessage.error("加载消息失败");
    console.error(error);
  }
};

const getReceiverId = () => {
  const currentSession = chatList.value.find(item => item.id === currentSessionId.value);
  if (!currentSession) return null;
  return currentSession.fromUserId === userId.value
      ? currentSession.toUserId
      : currentSession.fromUserId;
};

const sendMsg = async () => {
  if (!inputContent.value.trim()) {
    ElMessage.warning("消息内容不能为空");
    return;
  }

  const receiverId = getReceiverId();
  if (!receiverId) {
    ElMessage.error("获取接收者信息失败");
    return;
  }

  try {
    const res = await sendChatMsgService({
      sessionId: currentSessionId.value,
      receiverId,
      content: inputContent.value.trim()
    });
    console.log("发送消息成功：", res.data);

    msgList.value.push(res.data);
    inputContent.value = "";

    await nextTick(() => {
      const msgListEl = document.querySelector(".msg-list");
      if (msgListEl) {
        msgListEl.scrollTop = msgListEl.scrollHeight;
      }
    });

    await loadChatList();
  } catch (error) {
    ElMessage.error("发送消息失败");
    console.error(error);
  }
};

// 新增：标记会话消息为已读
const markAsRead = async (sessionId) => {
  try {
    await markChatMsgAsReadService(sessionId);
    // 刷新聊天列表，更新未读数量
    await loadChatList();
  } catch (error) {
    console.error("标记已读失败", error);
  }
};

const initWebSocket = () => {
  if (!userId.value) {
    //console.warn("用户ID为空，无法建立WebSocket连接");
    return;
  }

  if (ws) {
    ws.close();
  }

  const wsUrl = `ws://localhost:8080/ws/chat/${userId.value}/${tokenStore.token}`;
  ws = new WebSocket(wsUrl);

  ws.onopen = () => {
    console.log("WebSocket连接成功");
  };

  ws.onmessage = (event) => {
    try {
      const msg = JSON.parse(event.data);
      if (msg.msgType === 0 && msg.content === "ping") {
        return;
      }

      if (msg.sessionId === currentSessionId.value) {
        msgList.value.push(msg);
        nextTick(() => {
          const msgListEl = document.querySelector(".msg-list");
          if (msgListEl) {
            msgListEl.scrollTop = msgListEl.scrollHeight;
          }
        });
      } else {
        loadChatList();
        ElMessage.info("你有新的聊天消息");
      }
    } catch (error) {
      console.error("解析WebSocket消息失败：", error);
    }
  };



  ws.onclose = (event) => {
    // 打印关闭的核心信息：状态码+原因
    console.log(`WebSocket关闭 - 状态码：${event.code}，原因：${event.reason}`);

    // 只有非正常关闭才重连（排除手动关闭/正常认证失败）
    let reconnectTimer;
    if (event.code !== 1000 && event.code !== 1008) { // 1008=策略违反（认证失败）
      reconnectTimer = setTimeout(() => {
        initWebSocket();
      }, 3000);
    } else {
      // 认证失败时，停止重连并提示用户
      if (event.code === 1008) {
        ElMessage.error(`连接失败：${event.reason}，请重新登录`);
      }
      // 清理计时器
      if (reconnectTimer) clearTimeout(reconnectTimer);
    }
  };

  ws.onerror = (error) => {
    console.error("WebSocket错误：", error);
  };
};

const closeWebSocket = () => {
  if (ws) {
    ws.close();
    ws = null;
    console.log("WebSocket连接已手动关闭");
  }
};

// 辅助函数：格式化时间
const formatTime = (timeStr) => {
  if (!timeStr) return "";
  const date = new Date(timeStr);
  return date.toLocaleString();
};

// ========== 生命周期 ==========
onMounted(() => {
  loadChatList();
  initWebSocket();
});

onUnmounted(() => {
  closeWebSocket();
});

const goToSellerDetail=()=>{
  router.push(`/seller/detail/${userId.value}`);
}
</script>

<template>
  <div class="chat-page">
    <div class="chat-container">
      <div class="chat-main">
        <!-- 左侧聊天列表 -->
        <div class="chat-list">
          <div class="chat-list-header">
            <h2>消息</h2>
          </div>
          <div
              v-for="session in chatList"
              :key="session.id"
              class="chat-item"
              @click="enterChat(session.id)"
              :class="{ active: currentSessionId === session.id }"
          >
            <img :src="session.friendAvatar || defaultAvater " class="avatar" alt="avatar" />
            <div class="info">
              <div class="nickname">{{ session.friendNickname || '未知用户' }}</div>
              <div class="last-msg">{{ session.lastMsg || '暂无消息' }}</div>
            </div>
            <div class="meta">
              <span class="time">{{ session.time }}</span>
              <div v-if="session.unreadCount > 0" class="unread-badge">
                {{ session.unreadCount }}
              </div>
            </div>
            <img
                v-if="session.friendAvatar"
                :src="session.friendAvatar"
                class="preview-img"
                alt="商品预览"
            />
          </div>
        </div>

        <!-- 右侧聊天窗口 -->
        <div class="chat-window" :class="{ empty: !currentSessionId }">
          <div v-if="!currentSessionId" class="empty-state">
            <p class="empty-title">尚未选择任何联系人</p>
            <p class="empty-desc">快点左侧列表聊起来吧~</p>
          </div>

          <template v-else>
            <!-- 新增：顶部会话信息栏（参考闲鱼聊天顶部） -->
            <div class="session-header">
              <div class="session-info">
                <span class="session-name">
                  {{ chatList.find(s => s.id === currentSessionId)?.friendNickname || '未知' }}
                </span>
                <div class="session-actions">
                  <button class="action-btn" @click="goToSellerDetail">闲鱼号</button>
                  <button class="action-btn" @click="()=>{ElMessage.success('暂未开发相应功能')}">...</button>
                </div>
              </div>

              <!-- 交易卡片（参考截图） -->
              <div v-if="currentSessionTradeInfo" class="trade-card">
                <img :src="currentSessionTradeInfo.previewImg" class="trade-img" alt="商品图片" />
                <div class="trade-info">
                  <div class="trade-status">{{ currentSessionTradeInfo.status }}</div>
                </div>
              </div>
            </div>

            <!-- 消息列表 -->
            <div class="msg-list">
              <div
                  v-for="msg in msgList"
                  :key="msg.id"
                  :class="['msg-item', msg.senderId === userId ? 'send' : 'receive']"
              >
                <img :src="msg.senderAvatar || '/default-avatar.png'" class="msg-avatar" alt="头像" />
                <div class="msg-bubble">
                  <div class="msg-content">{{ msg.content }}</div>
                  <div class="msg-time">{{ formatTime(msg.createTime) }}</div>
                  <!-- 新增：未读标记（参考截图“未读”角标） -->
                  <div v-if="msg.isRead === 0 && msg.senderId !== userId" class="unread-tag">未读</div>
                </div>
              </div>
            </div>

            <!-- 发送框 -->
            <div class="msg-input-wrapper">
              <div class="msg-input">
                <input
                    v-model="inputContent"
                    type="text"
                    placeholder="请输入消息，按Enter键发送或点击发送按钮发送"
                    @keyup.enter="sendMsg"
                />
                <button @click="sendMsg" class="send-btn">发送</button>
              </div>
            </div>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.chat-page {
  display: flex;
  height: 100vh;
  align-items: center;
  justify-content: center;
  background-color: #f5f5f5;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
}

.chat-container {
  width: 80%;
  height: 80vh;
  max-width: 1400px;
  background-color: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  overflow: hidden;
}

.chat-main {
  display: flex;
  height: 100%;
}

/* 左侧聊天列表 */
.chat-list {
  width: 360px;
  background-color: #ffffff;
  border-right: 1px solid #e5e5e5;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.chat-list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid #f0f0f0;
}

.chat-list-header h2 {
  font-size: 20px;
  font-weight: 600;
  color: #333;
  margin: 0;
}

.header-actions {
  display: flex;
  gap: 16px;
}

.icon-btn {
  font-size: 18px;
  color: #666;
  cursor: pointer;
  transition: color 0.2s;
}

.icon-btn:hover {
  color: #ff6b35;
}

.chat-item {
  display: flex;
  align-items: center;
  padding: 12px 20px;
  cursor: pointer;
  transition: background-color 0.2s;
  position: relative;
}

.chat-item:hover {
  background-color: #f9f9f9;
}

.chat-item.active {
  background-color: #f0f0f0;
}

.avatar {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  object-fit: cover;
  margin-right: 12px;
}

.info {
  flex: 1;
  min-width: 0;
}

.nickname {
  font-size: 15px;
  font-weight: 500;
  color: #333;
  margin-bottom: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.last-msg {
  font-size: 13px;
  color: #999;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.meta {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  margin-left: 12px;
}

.time {
  font-size: 12px;
  color: #bbb;
  margin-bottom: 4px;
}

.unread-badge {
  min-width: 20px;
  height: 20px;
  line-height: 20px;
  padding: 0 6px;
  border-radius: 10px;
  background-color: #ff4d4f;
  color: #fff;
  font-size: 12px;
  font-weight: 500;
  text-align: center;
}

.preview-img {
  width: 48px;
  height: 48px;
  border-radius: 4px;
  object-fit: cover;
  margin-left: 8px;
}

/* 右侧聊天窗口 */
.chat-window {
  flex: 1;
  display: flex;
  flex-direction: column;
  background-color: #ffffff;
  overflow: hidden;
}

.chat-window.empty {
  background-color: transparent;
  box-shadow: none;
}

.empty-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #999;
}

.empty-title {
  font-size: 16px;
  color: #666;
  margin-bottom: 8px;
}

.empty-desc {
  font-size: 14px;
  color: #999;
}

/* 新增：会话顶部信息栏 */
.session-header {
  padding: 12px 20px;
  border-bottom: 1px solid #f0f0f0;
  background-color: #fff;
}

.session-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.session-name {
  font-size: 16px;
  font-weight: 500;
  color: #333;
}

.session-actions {
  display: flex;
  gap: 8px;
}

.action-btn {
  padding: 4px 12px;
  border: 1px solid #e5e5e5;
  border-radius: 4px;
  background-color: #fff;
  color: #333;
  font-size: 13px;
  cursor: pointer;
}

/* 新增：交易卡片 */
.trade-card {
  display: flex;
  align-items: center;
  padding: 12px;
  background-color: #fff;
  border-radius: 8px;
  border: 1px solid #f0f0f0;
}

.trade-img {
  width: 60px;
  height: 60px;
  border-radius: 4px;
  object-fit: cover;
  margin-right: 12px;
}

.trade-info {
  flex: 1;
}

.trade-price {
  font-size: 16px;
  font-weight: 600;
  color: #ff4d4f;
  margin-bottom: 4px;
}

.trade-freight {
  font-size: 12px;
  color: #999;
  margin-bottom: 4px;
}

.trade-status {
  font-size: 12px;
  color: #666;
}

.trade-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.msg-list {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
  background-color: #fafafa;
}

.msg-item {
  display: flex;
  margin-bottom: 16px;
  align-items: flex-end;
}

.msg-item.send {
  flex-direction: row-reverse;
}

.msg-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  object-fit: cover;
}

.msg-bubble {
  max-width: 60%;
  padding: 10px 14px;
  border-radius: 18px;
  font-size: 14px;
  line-height: 1.5;
  position: relative;
}

.msg-item.receive .msg-bubble {
  background-color: #ffffff;
  color: #333;
  margin-left: 10px;
  border-bottom-left-radius: 4px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.08);
}

.msg-item.send .msg-bubble {
  background-color: #ff6b35;
  color: #ffffff;
  margin-right: 10px;
  border-bottom-right-radius: 4px;
}

.msg-time {
  font-size: 11px;
  color: #bbb;
  margin-top: 4px;
  text-align: right;
}

.msg-item.send .msg-time {
  text-align: left;
}

/* 新增：未读标记 */
.unread-tag {
  position: absolute;
  right: -20px;
  bottom: 0;
  font-size: 11px;
  color: #ff4d4f;
}

/* 新增：输入框工具栏（表情、图片等） */
.msg-input-toolbar {
  display: flex;
  gap: 16px;
  padding: 8px 0;
  border-bottom: 1px solid #f0f0f0;
}

.toolbar-icon {
  font-size: 20px;
  color: #666;
  cursor: pointer;
  transition: color 0.2s;
}

.toolbar-icon:hover {
  color: #ff6b35;
}

.msg-input-wrapper {
  padding: 12px 20px;
  background-color: #ffffff;
  border-top: 1px solid #f0f0f0;
}

.msg-input {
  display: flex;
  align-items: center;
  background-color: #f5f5f5;
  border-radius: 24px;
  padding: 8px 16px;
}

.msg-input input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  font-size: 14px;
  color: #333;
  padding: 6px 0;
}

.msg-input input::placeholder {
  color: #bbb;
}

.send-btn {
  margin-left: 12px;
  padding: 8px 16px;
  background-color: #ff6b35;
  color: #ffffff;
  border: none;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background-color 0.2s;
}

.send-btn:hover {
  background-color: #ff5722;
}
</style>