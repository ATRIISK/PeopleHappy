<script setup>
/**
 * StateBlock —— 统一状态展示组件（loading / empty / error）
 *
 * 纯展示组件：只根据传入的 type/文案渲染对应状态，**不发起任何请求、不改任何数据**。
 * 重试由父组件通过 retry 事件回调触发（父组件决定是否重新加载，本组件只 emit）。
 *
 * 用法：
 *   <StateBlock v-if="error" type="error" message="加载失败" @retry="loadData" />
 *   <StateBlock v-else-if="loading" type="loading" />
 *   <StateBlock v-else-if="!list.length" type="empty" message="暂无商品" />
 */
import { computed } from 'vue'

const props = defineProps({
  /** 状态类型：loading | empty | error */
  type: {
    type: String,
    default: 'empty',
    validator: (v) => ['loading', 'empty', 'error'].includes(v)
  },
  /** 主文案 */
  message: {
    type: String,
    default: ''
  },
  /** 补充说明 */
  description: {
    type: String,
    default: ''
  },
  /** 是否紧凑（用于卡片内、表格上方等小空间） */
  compact: {
    type: Boolean,
    default: false
  },
  /** 紧凑模式下的骨架屏数量 */
  rows: {
    type: Number,
    default: 3
  }
})

const emit = defineEmits(['retry'])

/** 各状态的默认文案 */
const DEFAULTS = {
  loading: '加载中…',
  empty: '暂无数据',
  error: '加载失败'
}

/** 图标字符：用几何/符号字符避免引入字体依赖 */
const ICONS = {
  loading: '',
  empty: '🗂',
  error: '⚠'
}

const title = computed(() => props.message || DEFAULTS[props.type])
const desc = computed(() => {
  if (props.description) return props.description
  if (props.type === 'error') return '请检查网络后重试'
  if (props.type === 'empty') return '换个条件试试，或稍后再来'
  return ''
})
</script>

<template>
  <!-- 加载中：紧凑模式用骨架屏，标准模式用旋转指示器 -->
  <div
    v-if="type === 'loading'"
    class="hm-state"
    :class="{ 'hm-state--compact': compact }"
    role="status"
    aria-live="polite"
  >
    <template v-if="compact">
      <div class="hm-skeleton hm-skeleton--title"></div>
      <div
        v-for="i in rows"
        :key="i"
        class="hm-skeleton hm-skeleton--text"
        :style="{ width: `${100 - i * 12}%` }"
      ></div>
    </template>
    <template v-else>
      <div class="hm-spinner" aria-hidden="true"></div>
      <p class="hm-state__desc">{{ title }}</p>
    </template>
    <span class="hm-sr-only">{{ title }}</span>
  </div>

  <!-- 空数据 -->
  <div
    v-else-if="type === 'empty'"
    class="hm-state hm-state--empty"
    :class="{ 'hm-state--compact': compact }"
  >
    <div class="hm-state__icon" aria-hidden="true">{{ ICONS.empty }}</div>
    <p class="hm-state__title">{{ title }}</p>
    <p v-if="desc" class="hm-state__desc">{{ desc }}</p>
  </div>

  <!-- 错误：带重试按钮 -->
  <div
    v-else
    class="hm-state hm-state--error"
    :class="{ 'hm-state--compact': compact }"
    role="alert"
  >
    <div class="hm-state__icon" aria-hidden="true">{{ ICONS.error }}</div>
    <p class="hm-state__title">{{ title }}</p>
    <p v-if="desc" class="hm-state__desc">{{ desc }}</p>
    <button type="button" class="hm-btn hm-btn--sm" @click="emit('retry')">
      重新加载
    </button>
  </div>
</template>
