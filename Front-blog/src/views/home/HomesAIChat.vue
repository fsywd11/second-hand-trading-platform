<script setup lang="js">
import { ref, nextTick } from 'vue';
import {Loading, Headset, ArrowRight} from '@element-plus/icons-vue';
import { ElMessageBox, ElMessage } from 'element-plus';
import { getAiChatUrl } from '@/utils/serviceUrls.js';

// --- 引入 Markdown 解析与高亮 ---
import markdownit from 'markdown-it';
import hljs from 'highlight.js';
import 'highlight.js/styles/github-dark.css';

const emit = defineEmits(['update:modelValue']);

// 初始化 Markdown 解析器
const md = markdownit({
  html: true,
  linkify: true,
  typographer: true,
  highlight: function (str, lang) {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return '<pre class="hljs"><code>' +
            hljs.highlight(str, { language: lang, ignoreIllegals: true }).value +
            '</code></pre>';
      } catch (__) {}
    }
    return '<pre class="hljs"><code>' + md.utils.escapeHtml(str) + '</code></pre>';
  }
});

const inputMsg = ref('');
const isTyping = ref(false);
const scrollRef = ref(null);
const inputRef = ref(null);
const chatList = ref([
  { role: 'ai', content: 'Hi，我是智能客服吴桐，请问有什么可以帮您~'}
]);

import { useTokenStore } from '@/stores/token.js'
const tokenStore = useTokenStore();
const aiChatUrl = getAiChatUrl();

// --- 逻辑：发送消息 ---
const sendMessage = async () => {
  const text = inputMsg.value.trim();
  if (!text || isTyping.value) return;

  chatList.value.push({ role: 'user', content: text });
  inputMsg.value = '';
  isTyping.value = true;
  await scrollToBottom();

  const aiMsgIndex = chatList.value.push({ role: 'ai', content: '' }) - 1;

  // --- 缓冲区打字机逻辑 ---
  let rawTextBuffer = '';
  let currentDisplayText = '';
  const typingSpeed = 35;

  const typingTimer = setInterval(() => {
    if (currentDisplayText.length < rawTextBuffer.length) {
      currentDisplayText += rawTextBuffer.charAt(currentDisplayText.length);
      chatList.value[aiMsgIndex].content = currentDisplayText;
      scrollToBottom();
    } else if (!isTyping.value) {
      clearInterval(typingTimer);
    }
  }, typingSpeed);

  try {
    if(!tokenStore.token){
      ElMessage.warning('请先登录！');
      return
    }
    const response = await fetch(aiChatUrl, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': tokenStore.token
      },
      body: JSON.stringify(text),
    });

    if (!response.ok) throw new Error('网络响应异常');

    const reader = response.body.getReader();
    const decoder = new TextDecoder();

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      const chunk = decoder.decode(value, { stream: true });
      const lines = chunk.split('\n');

      for (const line of lines) {
        if (line.startsWith('data:')) {
          rawTextBuffer += line.replace(/^data:/, '').trim();
        } else if (line.trim() && !line.includes(':')) {
          rawTextBuffer += line;
        }
      }
    }
  } catch (e) {
    console.error("流传输错误:", e);
    clearInterval(typingTimer);
    chatList.value[aiMsgIndex].content = '服务响应异常，请稍后再试。';
  } finally {
    isTyping.value = false;
    await scrollToBottom();
  }
};

const scrollToBottom = async () => {
  await nextTick();
  if (scrollRef.value) {
    scrollRef.value.scrollTo({ top: scrollRef.value.scrollHeight, behavior: 'smooth' });
  }
};

const clearChat = () => {
  ElMessageBox.confirm('确定清空当前对话记录吗？', '确认操作', { type: 'warning' }).then(() => {
    chatList.value =  [  { role: 'ai', content: 'Hi，我是智能客服吴桐，请问有什么可以帮您~'}];
  });
};

// 处理常见问题点击
const handleFaqClick = (content) => {
  inputMsg.value = content;
  sendMessage();
};
</script>

<template>

  <div class="ai-window-wrapper">
    <div class="ai-window-container">
      <div class="chat-header">

        <span class="chat-title">AI智能客服</span>
        <span class="history-link" @click="clearChat">清空历史咨询记录</span>
      </div>

      <div class="chat-main" ref="scrollRef">
        <div v-for="(msg, index) in chatList" :key="index" :class="['message-row', msg.role]">
          <!-- AI 消息 -->
          <div v-if="msg.role === 'ai'" class="ai-message-wrapper">
            <div class="ai-avatar-box">
              <el-icon><Headset /></el-icon>
              <span class="ai-name">吴桐</span>
            </div>
            <div class="message-bubble ai-bubble markdown-body" v-html="md.render(msg.content)"></div>
          </div>

          <!-- 常见问题 -->

          <div v-else-if="msg.role === 'faq'" class="faq-item" @click="handleFaqClick(msg.content)">
            <span class="faq-text">{{ msg.content }}</span>
            <el-icon class="arrow-icon"><ArrowRight /></el-icon>
          </div>

          <!-- 用户消息 -->
          <div v-else class="user-message-wrapper">
            <div class="message-bubble user-bubble">
              {{ msg.content }}
            </div>
          </div>
        </div>

        <div v-if="isTyping" class="message-row ai">
          <div class="ai-avatar-box"><el-icon><Headset /></el-icon></div>
          <div class="message-bubble typing">
            <el-icon class="is-loading"><Loading /></el-icon> 正在处理...
          </div>
        </div>
      </div>

      <div class="chat-footer">
        <div class="input-wrapper">
          <input
              ref="inputRef"
              v-model="inputMsg"
              placeholder="在这儿输入您的问题试试~"
              @keyup.enter="sendMessage"
          />
          <el-button
              type="primary"
              class="send-btn"
              :disabled="!inputMsg.trim() || isTyping"
              @click="sendMessage"
          >
            发送
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.ai-window-wrapper {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100vh;
  background-image: url('@/assets/【哲风壁纸】兽耳娘-动漫.png');
  background-size: cover;
}
.ai-window-container {
  border-radius: 15px;
  width: 1200px;
  height: 80vh;
  background: #f5f5f5;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: 0 0 10px rgba(0, 0, 0, 0.1);
}

.chat-header {
  padding: 16px 20px;
  background: #fff;
  border-bottom: 1px solid #eee;
  display: flex;
  align-items: center;
  justify-content: space-between;

  .back-icon {
    font-size: 20px;
    color: #333;
    cursor: pointer;
  }

  .chat-title {
    font-size: 16px;
    font-weight: 600;
    color: #333;
  }

  .history-link {
    font-size: 14px;
    color: #007aff;
    cursor: pointer;
  }
}

.chat-main {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
  background: #f5f5f5;
  display: flex;
  flex-direction: column;
  gap: 16px;

  .message-row {
    display: flex;
    flex-direction: column;
    gap: 8px;

    &.ai {
      align-items: flex-start;
    }

    &.user {
      align-items: flex-end;
    }

    .ai-message-wrapper {
      display: flex;
      flex-direction: column;
      gap: 4px;
      max-width: 80%;

      .ai-avatar-box {
        display: flex;
        align-items: center;
        gap: 8px;

        .el-icon {
          width: 36px;
          height: 36px;
          background: #ffd100;
          color: #fff;
          border-radius: 50%;
          display: flex;
          align-items: center;
          justify-content: center;
        }

        .ai-name {
          font-size: 12px;
          color: #999;
        }
      }

      .ai-bubble {
        background: #fff;
        color: #333;
        border-radius: 0 12px 12px 12px;
        box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
      }
    }

    .user-message-wrapper {
      max-width: 80%;

      .user-bubble {
        background: #007aff;
        color: #fff;
        border-radius: 12px 0 12px 12px;
      }
    }

    .faq-item {
      background: #fff;
      color: #333;
      padding: 12px 16px;
      border-radius: 8px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      cursor: pointer;
      transition: background 0.2s;
      //长度
      max-width: 320px;

      &:hover {
        background: #f9f9f9;
      }

      .faq-text {
        font-size: 14px;
      }

      .arrow-icon {
        color: #ccc;
        font-size: 14px;
      }
    }

    .message-bubble {
      padding: 12px 16px;
      font-size: 14px;
      line-height: 1.5;
      word-break: break-word;
    }
  }
}

/* Markdown 内部样式补丁 */
:deep(.markdown-body) {
  p { margin: 0 0 8px 0; &:last-child { margin-bottom: 0; } }
  ul, ol { padding-left: 1.5em; margin: 8px 0; }
  li { margin-bottom: 4px; }
  pre.hljs {
    margin: 10px 0; padding: 12px; border-radius: 8px;
    background: #1e293b; color: #e2e8f0; overflow-x: auto;
    code { font-family: 'Fira Code', monospace; font-size: 13px; padding: 0; background: none; color: inherit; }
  }
  code { background: #f1f5f9; color: #ef4444; padding: 2px 4px; border-radius: 4px; font-size: 0.9em; }
  strong { font-weight: 700; color: #0f172a; }
  h1, h2, h3 { font-size: 1.1em; margin: 12px 0 8px 0; border-bottom: none; }
}

.chat-footer {
  background: #fff;
  border-top: 1px solid #eee;

  .footer-tabs {
    padding: 12px 20px;
    display: flex;
    align-items: center;
    gap: 12px;
    border-bottom: 1px solid #eee;

    .tab-item {
      padding: 6px 12px;
      font-size: 14px;
      color: #666;
      border-radius: 16px;
      cursor: pointer;
      transition: all 0.2s;

      &.active {
        background: #f0f0f0;
        color: #333;
      }
    }

    .arrow-right {
      color: #ccc;
      font-size: 14px;
      margin-left: auto;
      cursor: pointer;
    }
  }

  .input-wrapper {
    padding: 12px 20px;
    display: flex;
    align-items: center;
    gap: 12px;

    .input-tools {
      display: flex;
      gap: 16px;

      .tool-icon {
        font-size: 24px;
        color: #666;
        cursor: pointer;
        transition: color 0.2s;

        &:hover {
          color: #007aff;
        }
      }
    }

    input {
      flex: 1;
      border: none;
      background: #f5f5f5;
      outline: none;
      font-size: 14px;
      color: #333;
      padding: 10px 16px;
      border-radius: 20px;
    }

    .send-btn {
      background: #ffb800;
      border-color: #ffb800;
      color: #fff;
      border-radius: 20px;
      padding: 8px 20px;
      font-size: 14px;

      &:disabled {
        background: #ccc;
        border-color: #ccc;
      }
    }
  }
}

.typing {
  color: #999;
  display: flex;
  align-items: center;
  gap: 8px;
  font-style: italic;
}

.fade-in-up-enter-active, .fade-in-up-leave-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.fade-in-up-enter-from, .fade-in-up-leave-to {
  opacity: 0;
  transform: translateY(20px);
}
</style>
