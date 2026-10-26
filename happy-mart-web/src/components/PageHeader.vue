<script setup>
/**
 * PageHeader —— 统一页头组件
 *
 * 纯布局组件：标题 + 描述 + 右侧操作插槽。
 * 不含任何业务逻辑，插槽内容由父组件决定。
 *
 * 用法：
 *   <PageHeader title="商品管理" desc="共 200 件商品">
 *     <template #actions>
 *       <el-button type="primary">新增商品</el-button>
 *     </template>
 *   </PageHeader>
 */
defineProps({
  /** 页面主标题 */
  title: {
    type: String,
    required: true
  },
  /** 副标题/说明 */
  desc: {
    type: String,
    default: ''
  }
})
</script>

<template>
  <header class="hm-page-header">
    <div class="hm-page-header__text">
      <h1 class="hm-page-header__title">{{ title }}</h1>
      <p v-if="desc" class="hm-page-header__desc">{{ desc }}</p>
    </div>
    <div v-if="$slots.actions" class="hm-page-header__actions">
      <slot name="actions" />
    </div>
  </header>
</template>
