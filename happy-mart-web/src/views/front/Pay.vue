<template>
  <!-- 支付宝扫码支付页面 -->
  <div class="pay-page">
    <!-- ===== 顶部导航 ===== -->
    <div class="pay-header">
      <el-button text @click="goBack">
        ← 返回订单列表
      </el-button>
    </div>

    <!-- ===== 状态1：加载中 ===== -->
    <div v-if="pageStatus === 'loading'" class="pay-card">
      <div class="pay-loading">
        <el-skeleton :rows="4" animated />
      </div>
    </div>

    <!-- ===== 状态2：订单状态异常（不是待付款） ===== -->
    <div v-else-if="pageStatus === 'invalid'" class="pay-card">
      <div class="pay-result pay-result-warning">
        <el-icon :size="64" color="#e6a23c"><WarningFilled /></el-icon>
        <h2>该订单无法支付</h2>
        <p class="result-desc">订单状态为「{{ statusText }}」，不是待付款状态</p>
        <el-button type="primary" @click="goToOrderDetail">查看订单</el-button>
      </div>
    </div>

    <!-- ===== 状态3：显示二维码，等待支付 ===== -->
    <div v-else-if="pageStatus === 'paying'" class="pay-card">
      <h2 class="pay-title">扫码支付</h2>

      <!-- 二维码区域 -->
      <div class="qr-wrapper">
        <div v-if="qrImage" class="qr-image-wrapper">
          <img :src="qrImage" alt="支付宝支付二维码" class="qr-image" />
        </div>
        <div v-else class="qr-loading">
          <el-icon :size="48" class="is-loading"><Loading /></el-icon>
          <p>正在生成二维码...</p>
        </div>
      </div>

      <!-- 支付说明 -->
      <p class="pay-hint">请使用<strong>支付宝沙箱钱包App</strong>扫码支付</p>

      <!-- 订单信息 -->
      <div class="order-info">
        <div class="info-row">
          <span class="label">订单号</span>
          <span class="value">{{ orderInfo.orderNo }}</span>
        </div>
        <div class="info-row">
          <span class="label">应付金额</span>
          <span class="value amount">¥{{ orderInfo.totalAmount }}</span>
        </div>
      </div>

      <!-- 支付倒计时 -->
      <div class="countdown-bar" v-if="countdown > 0">
        <el-icon><Clock /></el-icon>
        <span>支付剩余时间：<strong>{{ formatTime(countdown) }}</strong></span>
      </div>
      <div class="countdown-bar expired" v-else>
        <el-icon><WarningFilled /></el-icon>
        <span>二维码已过期，请刷新重试</span>
      </div>

      <!-- 底部按钮 -->
      <div class="pay-actions">
        <el-button @click="refreshQrCode">刷新二维码</el-button>
        <el-button type="primary" @click="goToOrderDetail">查看订单</el-button>
      </div>
    </div>

    <!-- ===== 状态4：支付成功 ===== -->
    <div v-else-if="pageStatus === 'success'" class="pay-card">
      <div class="pay-result pay-result-success">
        <el-icon :size="64" color="#67c23a"><CircleCheckFilled /></el-icon>
        <h2>支付成功</h2>
        <div class="result-info">
          <div class="info-row">
            <span class="label">订单号</span>
            <span class="value">{{ orderInfo.orderNo }}</span>
          </div>
          <div class="info-row">
            <span class="label">支付金额</span>
            <span class="value amount">¥{{ orderInfo.totalAmount }}</span>
          </div>
        </div>
        <p class="auto-redirect">页面将在 {{ redirectCountdown }} 秒后自动跳转...</p>
        <div class="pay-actions">
          <el-button type="primary" @click="goToOrderDetail">查看订单</el-button>
          <el-button @click="goShopping">继续购物</el-button>
        </div>
      </div>
    </div>

    <!-- ===== 状态5：支付失败/错误 ===== -->
    <div v-else-if="pageStatus === 'error'" class="pay-card">
      <div class="pay-result pay-result-error">
        <el-icon :size="64" color="#f56c6c"><CircleCloseFilled /></el-icon>
        <h2>支付异常</h2>
        <p class="result-desc">{{ errorMessage }}</p>
        <div class="pay-actions">
          <el-button type="primary" @click="retryPay">重新支付</el-button>
          <el-button @click="goToOrderDetail">查看订单</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
/**
 * 支付宝扫码支付页面
 *
 * 挂载时调用支付接口获取二维码 → 渲染二维码 → 轮询支付状态
 * 状态流转：loading → paying（轮询中）→ success / error
 *
 * 后端 API：
 *   POST /api/order/pay/{id}   → { codeUrl: "https://qr.alipay.com/..." }
 *   GET  /api/order/status/{id} → 0待付款 1已支付 2已发货 3已完成 4已取消
 */
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { WarningFilled, CircleCheckFilled, CircleCloseFilled, Loading, Clock } from '@element-plus/icons-vue'
import QRCode from 'qrcode'
import { getOrderDetail, payOrder, getOrderStatus } from '@/api/order'

const route = useRoute()
const router = useRouter()

// ===== 订单ID =====
const orderId = computed(() => Number(route.params.id))

// ===== 页面状态 =====
// loading  → 初始化/请求中
// invalid  → 订单状态不是待付款
// paying   → 显示二维码，轮询中
// success  → 支付成功
// error    → 出错
const pageStatus = ref('loading')

const orderInfo = ref({})        // 订单信息（用于展示）
const qrImage = ref('')          // 二维码图片（base64 data URL）
const errorMessage = ref('')     // 错误信息

// ===== 倒计时 =====
/** 支付总倒计时（秒）：30 分钟 */
const TOTAL_COUNTDOWN = 30 * 60
const countdown = ref(TOTAL_COUNTDOWN)

/** 支付成功后跳转倒计时（秒） */
const redirectCountdown = ref(3)

// ===== 轮询定时器 =====
let pollTimer = null
let countdownTimer = null
let redirectTimer = null

// ===== 订单状态映射（和 OrderDetail.vue 保持一致）=====
const STATUS_MAP = {
  0: { text: '待付款', type: 'danger' },
  1: { text: '已支付', type: 'warning' },
  2: { text: '已发货', type: 'primary' },
  3: { text: '已完成', type: 'success' },
  4: { text: '已取消', type: 'info' }
}

/** 当前订单的状态文字 */
const statusText = computed(() => STATUS_MAP[orderInfo.value?.status]?.text || '未知')

// ==================== 生命周期 ====================

onMounted(async () => {
  if (!orderId.value) {
    pageStatus.value = 'error'
    errorMessage.value = '订单ID不存在'
    return
  }
  await initPay()
})

onUnmounted(() => {
  // 组件销毁时清除所有定时器
  clearTimers()
})

// ==================== 核心方法 ====================

/**
 * 初始化支付
 * 1. 获取订单详情（验证状态 + 展示信息）
 * 2. 调用支付接口获取二维码
 */
async function initPay() {
  pageStatus.value = 'loading'
  clearTimers()

  try {
    // 1. 获取订单详情
    const detail = await getOrderDetail(orderId.value)
    orderInfo.value = detail

    // 2. 校验订单状态：必须是待付款（0）
    if (detail.status !== 0) {
      pageStatus.value = 'invalid'
      return
    }

    // 3. 调用支付接口获取二维码
    await fetchQrCode()

    // 4. 切换为支付中状态
    pageStatus.value = 'paying'

    // 5. 开始轮询 + 倒计时
    startPolling()
    startCountdown()
  } catch (err) {
    console.error('初始化支付失败:', err)
    pageStatus.value = 'error'
    errorMessage.value = err.message || '获取支付信息失败，请重试'
  }
}

/**
 * 调后端支付接口，获取二维码内容
 * POST /api/order/pay/{id}
 */
async function fetchQrCode() {
  qrImage.value = '' // 先清空旧二维码
  const payResult = await payOrder(orderId.value)

  if (!payResult || !payResult.codeUrl) {
    throw new Error('支付接口返回异常，缺少二维码信息')
  }

  // 用 qrcode 库将 codeUrl 字符串转为 base64 图片
  try {
    qrImage.value = await QRCode.toDataURL(payResult.codeUrl, {
      width: 280,
      margin: 2,
      color: {
        dark: '#1a1a2e',   // 深色码点
        light: '#ffffff'    // 白色背景
      }
    })
  } catch (err) {
    console.error('生成二维码图片失败:', err)
    throw new Error('生成二维码失败，请刷新重试')
  }
}

/**
 * 刷新二维码（手动点击）
 */
async function refreshQrCode() {
  try {
    await fetchQrCode()
    // 重置倒计时
    countdown.value = TOTAL_COUNTDOWN
    ElMessage.success('二维码已刷新')
  } catch (err) {
    ElMessage.error(err.message || '刷新二维码失败')
  }
}

// ==================== 轮询支付状态 ====================

/**
 * 启动轮询：每 3 秒调一次 GET /api/order/status/{id}
 * 检测到 status 不再是 0（待付款）时视为支付完成
 */
function startPolling() {
  pollTimer = setInterval(async () => {
    try {
      const status = await getOrderStatus(orderId.value)

      // 不是待付款 → 支付完成（已支付/已发货/已完成/已取消）
      if (status !== 0) {
        clearTimers()
        if (status === 1 || status === 2 || status === 3) {
          // 支付成功（已支付/已发货/已完成）
          pageStatus.value = 'success'
          startRedirectCountdown()
        } else {
          // 已取消（用户在其他端取消了订单）
          pageStatus.value = 'error'
          errorMessage.value = '订单已被取消'
        }
      }
    } catch (err) {
      console.error('轮询支付状态失败:', err)
      // 轮询出错不中断，继续下一轮
    }
  }, 3000)
}

/**
 * 支付成功后启动跳转倒计时
 * 3 秒后自动跳转到订单详情页
 */
function startRedirectCountdown() {
  redirectCountdown.value = 3
  redirectTimer = setInterval(() => {
    redirectCountdown.value--
    if (redirectCountdown.value <= 0) {
      clearInterval(redirectTimer)
      redirectTimer = null
      goToOrderDetail()
    }
  }, 1000)
}

// ==================== 倒计时 ====================

/**
 * 支付倒计时（30 分钟）
 * 倒计时归零后二维码过期，提示用户刷新
 */
function startCountdown() {
  countdown.value = TOTAL_COUNTDOWN
  countdownTimer = setInterval(() => {
    countdown.value--
    if (countdown.value <= 0) {
      clearInterval(countdownTimer)
      countdownTimer = null
    }
  }, 1000)
}

// ==================== 工具方法 ====================

/** 格式化秒数为 mm:ss */
function formatTime(seconds) {
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`
}

/** 清除所有定时器 */
function clearTimers() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
  if (countdownTimer) {
    clearInterval(countdownTimer)
    countdownTimer = null
  }
  if (redirectTimer) {
    clearInterval(redirectTimer)
    redirectTimer = null
  }
}

// ==================== 页面跳转 ====================

/** 重新支付 */
function retryPay() {
  initPay()
}

/** 跳转到订单详情 */
function goToOrderDetail() {
  router.push({ name: 'OrderDetail', params: { id: orderId.value } })
}

/** 返回订单列表 */
function goBack() {
  router.push({ name: 'Orders' })
}

/** 继续购物 */
function goShopping() {
  router.push({ name: 'Products' })
}
</script>

<style scoped>
/* ==================== 页面容器 ==================== */
.pay-page {
  max-width: 600px;
  margin: 0 auto;
  padding: 20px;
}

/* ==================== 顶部返回 ==================== */
.pay-header {
  margin-bottom: 16px;
}

/* ==================== 支付卡片 ==================== */
.pay-card {
  background: #fff;
  border-radius: 12px;
  padding: 32px;
  text-align: center;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

.pay-title {
  font-size: 20px;
  font-weight: 600;
  color: #1a1a2e;
  margin: 0 0 24px 0;
}

/* ==================== 加载中 ==================== */
.pay-loading {
  padding: 40px 0;
}

/* ==================== 二维码区域 ==================== */
.qr-wrapper {
  display: flex;
  justify-content: center;
  align-items: center;
  margin-bottom: 16px;
  min-height: 200px;
}

.qr-image-wrapper {
  background: #fff;
  border: 8px solid #fff;
  border-radius: 12px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
  display: inline-block;
}

.qr-image {
  display: block;
  width: 280px;
  height: 280px;
}

.qr-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  color: #999;
  font-size: 14px;
}

/* qrcode 库生成时 Loading 图标的旋转动画 */
.is-loading {
  animation: rotating 1.4s linear infinite;
}

@keyframes rotating {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* ==================== 支付说明 ==================== */
.pay-hint {
  font-size: 14px;
  color: #666;
  margin: 0 0 24px 0;
  line-height: 1.6;
}

.pay-hint strong {
  color: #e4393c;
}

/* ==================== 订单信息 ==================== */
.order-info {
  background: #fafafa;
  border-radius: 8px;
  padding: 16px 20px;
  margin-bottom: 16px;
  text-align: left;
}

.info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 0;
  font-size: 14px;
}

.info-row .label {
  color: #999;
}

.info-row .value {
  color: #333;
  font-weight: 500;
}

.info-row .amount {
  font-size: 20px;
  font-weight: bold;
  color: #e4393c;
}

/* ==================== 倒计时 ==================== */
.countdown-bar {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 10px;
  background: #f0f9ff;
  border-radius: 6px;
  font-size: 13px;
  color: #409eff;
  margin-bottom: 20px;
}

.countdown-bar.expired {
  background: #fff5f5;
  color: #f56c6c;
}

/* ==================== 底部按钮 ==================== */
.pay-actions {
  display: flex;
  justify-content: center;
  gap: 12px;
}

/* ==================== 支付结果页 ==================== */
.pay-result {
  padding: 20px 0;
}

.pay-result h2 {
  font-size: 22px;
  margin: 16px 0 12px 0;
  color: #333;
}

.pay-result .result-desc {
  font-size: 14px;
  color: #999;
  margin: 0 0 20px 0;
  line-height: 1.6;
}

.pay-result .result-info {
  background: #fafafa;
  border-radius: 8px;
  padding: 16px 20px;
  margin-bottom: 16px;
  text-align: left;
  max-width: 360px;
  margin-left: auto;
  margin-right: auto;
}

.auto-redirect {
  font-size: 13px;
  color: #999;
  margin: 0 0 20px 0;
}

/* ==================== 各结果颜色 ==================== */
.pay-result-success h2 {
  color: #67c23a;
}

.pay-result-error h2 {
  color: #f56c6c;
}

.pay-result-warning h2 {
  color: #e6a23c;
}
</style>
