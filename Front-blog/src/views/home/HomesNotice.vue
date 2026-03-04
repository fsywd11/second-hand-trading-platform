<script lang="js" setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue';
import { ElMessage } from 'element-plus';
import {
  getMyChatListService,
  getSessionMsgService,
  sendChatMsgService,
} from "@/api/chat.js";
import { useTokenStore } from "@/stores/token.js";
import useUserInfoStore from "@/stores/userInfo.js";

// ========== 状态管理 ==========
const userInfoStore = useUserInfoStore();
const tokenStore = useTokenStore();

// ========== 响应式数据 ==========
const chatList = ref([]);
const msgList = ref([]);
const currentSessionId = ref(null);
const inputContent = ref("");
const userId = ref(userInfoStore.info.id);
const pageNum = ref(1);
const pageSize = ref(20);
let ws = null;

// ========== 方法定义 ==========
const loadChatList = async () => {
  try {
    const res = await getMyChatListService();
    chatList.value = res.data.data;
  } catch (error) {
    ElMessage.error("加载聊天列表失败");
    console.error(error);
  }
};

const enterChat = async (sessionId) => {
  currentSessionId.value = sessionId;
  pageNum.value = 1;
  await loadChatMsg();
};

const loadChatMsg = async () => {
  try {
    const res = await getSessionMsgService(
        currentSessionId.value,
        pageNum.value,
        pageSize.value
    );
    msgList.value = res.data.data.items;
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

    msgList.value.push(res.data.data);
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

const initWebSocket = () => {
  if (!userId.value) {
    console.warn("用户ID为空，无法建立WebSocket连接");
    return;
  }

  if (ws) {
    ws.close();
  }

  const wsUrl = `ws://${window.location.host}/ws/chat/${userId.value}`;
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

  ws.onclose = () => {
    console.log("WebSocket连接关闭，正在重连...");
    setTimeout(() => {
      initWebSocket();
    }, 3000);
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

// ========== 生命周期 ==========
onMounted(() => {
  loadChatList();
  initWebSocket();
});

onUnmounted(() => {
  closeWebSocket();
});
</script>

<template>
  <div class="chat-page">
    <!-- 中间容器：占屏幕 80% 并居中 -->
    <div class="chat-container">
      <div class="chat-main">
        <!-- 左侧聊天列表 -->
        <div class="chat-list">
          <div class="chat-list-header">
            <h2>消息</h2>
            <div class="header-actions">
              <span class="icon-btn">➕</span>
              <span class="icon-btn">⚙️</span>
            </div>
          </div>
          <div
              v-for="session in chatList"
              :key="session.id"
              class="chat-item"
              @click="enterChat(session.id)"
              :class="{ active: currentSessionId === session.id }"
          >
            <img :src="session.friendAvatar" class="avatar" alt="" />
            <div class="info">
              <div class="nickname">{{ session.friendNickname }}</div>
              <div class="last-msg">{{ session.lastMsg }}</div>
            </div>
            <div class="meta">
              <span class="time">{{ session.time }}</span>
              <div v-if="session.unreadCount > 0" class="unread-badge">
                {{ session.unreadCount }}
              </div>
            </div>
            <!-- 预览图（如果有） -->
            <img
                v-if="session.previewImg"
                :src="session.previewImg"
                class="preview-img"
                alt=""
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
            <!-- 消息列表 -->
            <div class="msg-list">
              <div
                  v-for="msg in msgList"
                  :key="msg.id"
                  :class="['msg-item', msg.senderId === userId ? 'send' : 'receive']"
              >
                <img :src="msg.senderAvatar" class="msg-avatar" alt="" />
                <div class="msg-bubble">
                  <div class="msg-content">{{ msg.content }}</div>
                  <div class="msg-time">{{ msg.createTime }}</div>
                </div>
              </div>
            </div>

            <!-- 发送框 -->
            <div class="msg-input-wrapper">
              <div class="msg-input">
                <input
                    v-model="inputContent"
                    type="text"
                    placeholder="请输入消息..."
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

/* 中间容器：占屏幕 80% 并居中 */
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