<script setup>
/**
 * AI 购物助手悬浮组件（v1.11，阶段四，见开发文档 §14）
 *
 * 挂在 FrontLayout 上，全前台页面右下角都有悬浮按钮：
 * - 点击展开对话面板，游客也能问（后端 /api/ai/** 已放行）
 * - 后端做 RAG 检索（商品知识库）→ 通义千问回答 → 返回 {response, sources[]}
 * - AI 回答下方展示"参考商品卡片"（缩略图 + 名称 + 价格），点击跳商品详情
 *
 * 代码改写自 AI 博客项目 D:\ai_blog_show 的 AiAssistant.vue（TypeScript → JavaScript）。
 * 与原版差异：
 *   1. 去掉"未登录拦截"——商城 AI 接口对游客开放；
 *   2. sources 由"文字链接"改为"商品卡片"（图片+名称+价格，点击跳详情）；
 *   3. 样式从参考项目的 CSS 变量改为商城自己的色值（深色主题 #1a1a2e）。
 *
 * 会话记忆说明：聊天历史由本组件（前端）自持，每次请求是"无状态"的，
 * 后端不做跨会话记忆——因为接口是公开的，全局内存记忆会让不同游客互相串对话上下文。
 */
import { ref, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ChatDotRound, Close, Promotion } from '@element-plus/icons-vue'
import { sendChat } from '@/api/ai'

const router = useRouter() // 跳商品详情用

// 面板开关
const isOpen = ref(false)
// 输入框内容
const input = ref('')
// 请求中标记（显示打字动画）
const loading = ref(false)
// 界面消息列表：{ role: 'user' | 'assistant', content: 文本, sources: 参考商品数组 }
// 初始给一条 AI 欢迎语
const messages = ref([
  {
    role: 'assistant',
    content: '你好！我是众乐商城 AI 购物助手 🤖\n可以帮你推荐商品、解答选购问题，比如："推荐适合运动的耳机"或"有哪些 2000 元左右的手机"。',
    sources: []
  }
])
// 消息列表 DOM 引用 → 滚动到底部
const listRef = ref(null)

/** 滚动消息列表到底部（等 DOM 更新后） */
const scrollToBottom = async () => {
  await nextTick()
  if (listRef.value) {
    listRef.value.scrollTop = listRef.value.scrollHeight
  }
}

/** 点击参考商品卡片 → 跳商品详情页 */
function goProduct(productId) {
  router.push(`/product/${productId}`)
}

/** 发送消息：调后端 AI 接口，拿到回答 + 参考商品 */
const handleSend = async () => {
  const text = input.value.trim()
  if (!text || loading.value) return

  // 先把用户消息上屏
  messages.value.push({ role: 'user', content: text, sources: [] })
  input.value = ''
  loading.value = true
  await scrollToBottom()

  try {
    const res = await sendChat(text)
    if (res.code === 200) {
      // 成功：回答 + 参考商品卡片
      messages.value.push({
        role: 'assistant',
        content: res.data.response,
        sources: res.data.sources || []
      })
    } else {
      // 后端业务错误（如 AI 服务未配置）
      messages.value.push({
        role: 'assistant',
        content: res.message || '抱歉，AI 服务暂时不可用，请稍后再试。',
        sources: []
      })
    }
  } catch {
    // 网络/超时等异常
    messages.value.push({
      role: 'assistant',
      content: '抱歉，AI 服务暂时不可用，请稍后再试。',
      sources: []
    })
  } finally {
    loading.value = false
    await scrollToBottom()
  }
}

/** Enter 发送 / Shift+Enter 换行 */
const handleKeydown = (e) => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleSend()
  }
}
</script>

<template>
  <div class="ai-assistant">
    <!-- 右下角悬浮按钮 -->
    <button class="fab" :class="{ active: isOpen }" @click="isOpen = !isOpen" aria-label="AI 购物助手">
      <ChatDotRound class="fab-icon" />
    </button>

    <!-- 对话面板（Transition 做展开/收起动画） -->
    <Transition name="panel">
      <div v-if="isOpen" class="panel">
        <!-- 面板头部：标题 + 关闭 -->
        <div class="panel-header">
          <div class="panel-title">
            <span class="dot"></span>
            <span>AI 购物助手</span>
          </div>
          <button class="close-btn" @click="isOpen = false" aria-label="关闭">
            <Close class="close-icon" />
          </button>
        </div>

        <!-- 消息列表 -->
        <div ref="listRef" class="message-list">
          <div v-for="(msg, idx) in messages" :key="idx" class="message" :class="msg.role">
            <div class="avatar">
              <ChatDotRound v-if="msg.role === 'assistant'" class="avatar-icon" />
              <span v-else class="user-emoji">🙋</span>
            </div>
            <div class="msg-body">
              <!-- 气泡文本 -->
              <div class="bubble">{{ msg.content }}</div>
              <!-- 参考商品卡片（AI 回答下方展示，点击跳详情） -->
              <div v-if="msg.role === 'assistant' && msg.sources && msg.sources.length" class="sources">
                <div class="sources-label">参考商品</div>
                <div v-for="s in msg.sources" :key="s.productId" class="source-card" @click="goProduct(s.productId)">
                  <img v-if="s.image" :src="s.image" class="source-img" alt="" />
                  <div class="source-info">
                    <div class="source-title">{{ s.title }}</div>
                    <div class="source-price" v-if="s.price != null">¥{{ s.price }}</div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- 打字中动画 -->
          <div v-if="loading" class="message assistant">
            <div class="avatar">
              <ChatDotRound class="avatar-icon" />
            </div>
            <div class="bubble typing">
              <span class="typing-dot"></span>
              <span class="typing-dot"></span>
              <span class="typing-dot"></span>
            </div>
          </div>
        </div>

        <!-- 底部输入区 -->
        <div class="panel-footer">
          <input
            v-model="input"
            type="text"
            placeholder="问问商品推荐..."
            class="message-input"
            @keydown="handleKeydown"
          />
          <button class="send-btn" :disabled="!input.trim() || loading" @click="handleSend" aria-label="发送">
            <Promotion class="send-icon" />
          </button>
        </div>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
/* ==================== 悬浮容器 ==================== */
.ai-assistant {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 2000; /* 盖过所有页面内容（顶栏 sticky z-index 1000） */
}

/* ==================== 悬浮按钮 ==================== */
.fab {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  border: none;
  background: #1a1a2e; /* 与商城深色顶栏同色 */
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 16px rgba(26, 26, 46, 0.35);
  transition: transform 0.25s, box-shadow 0.25s;
}

.fab:hover {
  transform: scale(1.05);
  box-shadow: 0 6px 24px rgba(26, 26, 46, 0.45);
}

.fab.active {
  transform: scale(0.92);
}

.fab-icon {
  width: 24px;
  height: 24px;
}

/* ==================== 对话面板 ==================== */
.panel {
  position: absolute;
  right: 0;
  bottom: 72px;
  width: 360px;
  max-height: 520px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.18);
  border: 1px solid #e5e5e5;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  transform-origin: bottom right;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  background: #f7f7f9;
  border-bottom: 1px solid #eee;
}

.panel-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 15px;
  font-weight: 600;
  color: #1a1a2e;
}

.panel-title .dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #67c23a; /* 绿色"在线"点 */
  box-shadow: 0 0 0 3px rgba(103, 194, 58, 0.2);
}

.close-btn {
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: #999;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}

.close-btn:hover {
  background: #e8e8ea;
  color: #333;
}

.close-icon {
  width: 16px;
  height: 16px;
}

/* ==================== 消息列表 ==================== */
.message-list {
  flex: 1;
  padding: 16px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 14px;
  background: #fafafa;
}

.message {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}

.message.user {
  flex-direction: row-reverse;
}

/* 头像 */
.avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: #eee;
  color: #666;
}

.message.assistant .avatar {
  background: #1a1a2e;
  color: #fff;
}

.user-emoji {
  font-size: 16px;
}

.avatar-icon {
  width: 16px;
  height: 16px;
}

.msg-body {
  max-width: 240px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

/* 气泡 */
.bubble {
  padding: 10px 14px;
  border-radius: 16px;
  font-size: 14px;
  line-height: 1.55;
  color: #333;
  background: #fff;
  border: 1px solid #eee;
  word-break: break-word;
  white-space: pre-wrap; /* 保留 AI 回答里的换行 */
}

.message.user .bubble {
  background: #1a1a2e;
  color: #fff;
  border: none;
  border-bottom-right-radius: 4px;
}

.message.assistant .bubble {
  border-bottom-left-radius: 4px;
}

/* ==================== 参考商品卡片 ==================== */
.sources {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.sources-label {
  font-size: 11px;
  color: #999;
  margin-left: 2px;
}

.source-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border: 1px solid #eee;
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
  transition: all 0.2s;
}

.source-card:hover {
  border-color: #1a1a2e;
  box-shadow: 0 2px 8px rgba(26, 26, 46, 0.1);
}

.source-img {
  width: 44px;
  height: 44px;
  border-radius: 6px;
  object-fit: cover;
  flex-shrink: 0;
  background: #f5f5f5;
}

.source-info {
  min-width: 0; /* 让长标题能省略号截断 */
}

.source-title {
  font-size: 13px;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.source-price {
  font-size: 14px;
  color: #e64340; /* 电商价格红 */
  font-weight: 600;
  margin-top: 2px;
}

/* ==================== 输入区 ==================== */
.panel-footer {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  border-top: 1px solid #eee;
  background: #fff;
}

.message-input {
  flex: 1;
  height: 40px;
  padding: 0 14px;
  border: 1px solid #d9d9d9;
  border-radius: 20px;
  background: #fff;
  color: #333;
  font-family: inherit;
  font-size: 14px;
  outline: none;
  transition: border-color 0.2s;
}

.message-input::placeholder {
  color: #bbb;
}

.message-input:focus {
  border-color: #1a1a2e;
}

.send-btn {
  width: 40px;
  height: 40px;
  border: none;
  border-radius: 50%;
  background: #1a1a2e;
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
  flex-shrink: 0;
}

.send-btn:hover:not(:disabled) {
  background: #2a2a4a;
  transform: scale(1.05);
}

.send-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.send-icon {
  width: 18px;
  height: 18px;
}

/* ==================== 打字动画 ==================== */
.typing {
  display: flex;
  align-items: center;
  gap: 4px;
}

.typing-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #aaa;
  animation: typingBounce 1.2s ease-in-out infinite;
}

.typing-dot:nth-child(2) { animation-delay: 0.15s; }
.typing-dot:nth-child(3) { animation-delay: 0.3s; }

@keyframes typingBounce {
  0%, 80%, 100% { transform: translateY(0); }
  40% { transform: translateY(-4px); }
}

/* ==================== 面板展开/收起动画 ==================== */
.panel-enter-active,
.panel-leave-active {
  transition: transform 0.3s, opacity 0.25s;
}

.panel-enter-from,
.panel-leave-to {
  opacity: 0;
  transform: scale(0.9) translateY(10px);
}

/* ==================== 移动端适配 ==================== */
@media (max-width: 480px) {
  .ai-assistant {
    right: 16px;
    bottom: 16px;
  }

  .panel {
    width: calc(100vw - 32px);
    right: 0;
    bottom: 68px;
    max-height: 70vh;
  }
}

/* ==================== 暖橙市集视觉 ==================== */
.fab {
  width: 58px;
  height: 58px;
  background: var(--hm-primary);
  box-shadow: 0 10px 26px rgba(232, 80, 30, 0.32);
}
.fab::after {
  content: '';
  position: absolute;
  inset: -5px;
  border: 1px solid var(--hm-primary-border);
  border-radius: 50%;
  pointer-events: none;
}
.fab:hover {
  background: var(--hm-primary-hover);
  box-shadow: 0 14px 30px rgba(232, 80, 30, 0.38);
}
.panel {
  bottom: 76px;
  width: 380px;
  max-height: 560px;
  border: 1px solid var(--hm-ink-200);
  border-radius: var(--hm-radius-xl);
  background: var(--hm-white);
  box-shadow: 0 24px 60px rgba(67, 40, 20, 0.2);
}
.panel-header {
  padding: 16px 18px;
  border-bottom: 1px solid var(--hm-ink-100);
  background: linear-gradient(110deg, #fdeee5, #fff9f3);
}
.panel-title {
  color: var(--hm-ink-900);
  font-size: var(--hm-font-md);
  letter-spacing: 0.5px;
}
.panel-title .dot {
  background: var(--hm-success);
  box-shadow: 0 0 0 3px var(--hm-success-soft);
}
.close-btn {
  color: var(--hm-ink-400);
}
.close-btn:hover {
  background: var(--hm-primary-soft);
  color: var(--hm-primary);
}
.message-list {
  padding: 18px 16px;
  background: var(--hm-bg-page);
}
.avatar {
  border-radius: 10px;
  background: var(--hm-white);
  color: var(--hm-ink-500);
}
.message.assistant .avatar {
  background: var(--hm-primary);
  color: #fff;
  box-shadow: 0 4px 10px rgba(232, 80, 30, 0.18);
}
.msg-body {
  max-width: 258px;
}
.bubble {
  border: 1px solid var(--hm-ink-100);
  border-radius: 14px;
  background: var(--hm-white);
  color: var(--hm-ink-700);
  box-shadow: 0 2px 5px rgba(90, 50, 20, 0.03);
}
.message.user .bubble {
  border-bottom-right-radius: 4px;
  background: var(--hm-ink-900);
  color: #fff;
}
.message.assistant .bubble {
  border-bottom-left-radius: 4px;
}
.sources-label {
  color: var(--hm-ink-400);
  letter-spacing: 1px;
}
.source-card {
  border: 1px solid var(--hm-ink-100);
  border-radius: var(--hm-radius-md);
  background: var(--hm-white);
}
.source-card:hover {
  border-color: var(--hm-primary-border);
  background: var(--hm-primary-soft);
  box-shadow: var(--hm-shadow-sm);
}
.source-img {
  border-radius: 9px;
  background: var(--hm-ink-050);
}
.source-title {
  color: var(--hm-ink-800);
}
.source-price {
  color: var(--hm-primary);
  font-variant-numeric: tabular-nums;
}
.panel-footer {
  padding: 12px 14px;
  border-top: 1px solid var(--hm-ink-100);
  background: var(--hm-white);
}
.message-input {
  border: 1px solid var(--hm-ink-200);
  background: var(--hm-ink-050);
  color: var(--hm-ink-800);
}
.message-input::placeholder {
  color: var(--hm-ink-400);
}
.message-input:focus {
  border-color: var(--hm-primary);
  background: var(--hm-white);
  box-shadow: 0 0 0 3px var(--hm-primary-soft);
}
.send-btn {
  background: var(--hm-primary);
}
.send-btn:hover:not(:disabled) {
  background: var(--hm-primary-hover);
}
.typing-dot {
  background: var(--hm-primary-border);
}

@media (max-width: 480px) {
  .fab {
    width: 54px;
    height: 54px;
  }
  .panel {
    width: calc(100vw - 32px);
    bottom: 70px;
    max-height: 70vh;
  }
  .msg-body {
    max-width: calc(100vw - 130px);
  }
}

/* ==================== Codex 深色助手收口 ==================== */
.fab {
  background: var(--hm-primary);
  color: var(--hm-on-primary);
  box-shadow: 0 10px 30px rgba(139, 233, 176, 0.24);
}
.fab::after {
  border-color: var(--hm-primary-border);
  box-shadow: 0 0 24px rgba(139, 233, 176, 0.12);
}
.fab:hover {
  background: var(--hm-primary-hover);
  box-shadow: 0 14px 36px rgba(139, 233, 176, 0.3);
}
.panel {
  background: rgba(17, 19, 24, 0.97);
  border-color: var(--hm-ink-100);
  box-shadow: 0 26px 72px rgba(0, 0, 0, 0.58);
  backdrop-filter: blur(20px);
}
.panel-header {
  background:
    radial-gradient(circle at 100% 0, rgba(139, 233, 176, 0.1), transparent 42%),
    var(--hm-bg-subtle);
  border-bottom-color: var(--hm-ink-100);
}
.panel-title {
  font-family: 'Cascadia Code', 'SFMono-Regular', Consolas, 'Microsoft YaHei', monospace;
  letter-spacing: 0.8px;
}
.close-btn:hover {
  background: var(--hm-primary-soft);
  color: var(--hm-primary);
}
.message-list {
  background:
    linear-gradient(rgba(255, 255, 255, 0.02) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.02) 1px, transparent 1px),
    var(--hm-bg-page);
  background-size: 24px 24px;
}
.avatar {
  background: var(--hm-ink-050);
  color: var(--hm-ink-500);
  border: 1px solid var(--hm-ink-100);
}
.message.assistant .avatar {
  background: var(--hm-primary);
  color: var(--hm-on-primary);
  box-shadow: 0 4px 14px rgba(139, 233, 176, 0.18);
}
.bubble {
  background: var(--hm-bg-surface);
  border-color: var(--hm-ink-100);
  color: var(--hm-ink-700);
  box-shadow: none;
}
.message.user .bubble {
  background: var(--hm-primary);
  color: var(--hm-on-primary);
}
.source-card {
  background: var(--hm-bg-subtle);
  border-color: var(--hm-ink-100);
}
.source-card:hover {
  background: var(--hm-primary-soft);
}
.source-price {
  color: var(--hm-accent);
}
.panel-footer {
  background: var(--hm-bg-subtle);
  border-top-color: var(--hm-ink-100);
}
.message-input {
  background: var(--hm-ink-050);
  border-color: var(--hm-ink-100);
  color: var(--hm-ink-900);
}
.message-input:focus {
  background: var(--hm-ink-050);
  border-color: var(--hm-primary);
  box-shadow: 0 0 0 3px var(--hm-primary-soft);
}
.send-btn {
  background: var(--hm-primary);
  color: var(--hm-on-primary);
}
.send-btn:hover:not(:disabled) {
  background: var(--hm-primary-hover);
}
/* ==================== 经典灰白助手 ==================== */
.fab {
  background: var(--hm-primary);
  color: var(--hm-on-primary);
  box-shadow: 0 8px 24px rgba(217, 79, 43, 0.24);
}
.fab::after {
  border-color: var(--hm-primary-border);
  box-shadow: none;
}
.fab:hover {
  background: var(--hm-primary-hover);
  box-shadow: 0 12px 28px rgba(217, 79, 43, 0.28);
}
.panel {
  background: rgba(255, 255, 255, 0.98);
  border-color: var(--hm-ink-100);
  box-shadow: 0 20px 48px rgba(31, 35, 40, 0.16);
  backdrop-filter: blur(14px);
}
.panel-header {
  background: var(--hm-bg-subtle);
  border-bottom-color: var(--hm-ink-100);
}
.panel-title {
  color: var(--hm-ink-900);
}
.close-btn:hover {
  background: var(--hm-bg-subtle);
  color: var(--hm-ink-900);
}
.message-list {
  background: var(--hm-bg-subtle);
  background-image: none;
}
.avatar {
  background: var(--hm-bg-surface);
  color: var(--hm-ink-500);
  border-color: var(--hm-ink-100);
}
.message.assistant .avatar {
  background: var(--hm-primary);
  color: var(--hm-on-primary);
  box-shadow: none;
}
.bubble {
  background: var(--hm-bg-surface);
  border-color: var(--hm-ink-100);
  color: var(--hm-ink-700);
  box-shadow: none;
}
.message.user .bubble {
  background: var(--hm-primary);
  color: var(--hm-on-primary);
}
.source-card {
  background: var(--hm-bg-surface);
  border-color: var(--hm-ink-100);
}
.source-card:hover {
  background: var(--hm-primary-soft);
  border-color: var(--hm-primary-border);
}
.source-price {
  color: var(--hm-primary);
}
.panel-footer {
  background: var(--hm-bg-surface);
  border-top-color: var(--hm-ink-100);
}
.message-input {
  background: var(--hm-bg-subtle);
  border-color: var(--hm-ink-100);
  color: var(--hm-ink-900);
}
.message-input:focus {
  background: var(--hm-bg-surface);
  border-color: var(--hm-primary);
  box-shadow: 0 0 0 3px var(--hm-primary-soft);
}
.send-btn {
  background: var(--hm-primary);
  color: var(--hm-on-primary);
}
.send-btn:hover:not(:disabled) {
  background: var(--hm-primary-hover);
}
</style>
