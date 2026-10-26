<script setup>
</script>

<template>
  <div class="not-found-page">
    <div class="not-found-card">
      <div class="not-found-visual" aria-hidden="true">
        <span class="not-found-orbit not-found-orbit--one"></span>
        <span class="not-found-orbit not-found-orbit--two"></span>
        <h1>404</h1>
      </div>
      <span class="not-found-badge">LOST IN THE MARKET</span>
      <p class="not-found-title">页面未找到</p>
      <p class="not-found-desc">您访问的页面可能已被移动或不存在，回到市集继续发现好物吧。</p>
      <el-button type="primary" size="large" class="not-found-btn" @click="$router.push('/')">返回首页</el-button>
    </div>
  </div>
</template>

<style scoped>
.not-found-page {
  min-height: calc(100vh - 140px);
  box-sizing: border-box;
  padding: 56px 20px;
  background:
    radial-gradient(circle at 18% 20%, rgba(232, 80, 30, 0.1), transparent 30%),
    radial-gradient(circle at 82% 80%, rgba(217, 153, 43, 0.1), transparent 32%);
  display: flex;
  align-items: center;
  justify-content: center;
}

.not-found-card {
  position: relative;
  width: min(620px, 100%);
  box-sizing: border-box;
  padding: 40px 56px 48px;
  border: 1px solid var(--hm-ink-100);
  border-radius: 24px;
  background: var(--hm-white);
  box-shadow: var(--hm-shadow-lg);
  text-align: center;
  overflow: hidden;
}

.not-found-visual {
  position: relative;
  width: 260px;
  max-width: 100%;
  margin: 0 auto 20px;
  padding: 18px 0;
  isolation: isolate;
}

.not-found-visual h1 {
  position: relative;
  z-index: 1;
  margin: 0;
  background: linear-gradient(135deg, #f17945, var(--hm-primary) 58%, var(--hm-primary-active));
  background-clip: text;
  -webkit-background-clip: text;
  color: transparent;
  font-size: 104px;
  font-weight: 800;
  line-height: 1.1;
  letter-spacing: 5px;
}

.not-found-orbit {
  position: absolute;
  border-radius: 50%;
  background: var(--hm-primary-soft);
}

.not-found-orbit--one {
  width: 118px;
  height: 118px;
  top: 24px;
  left: 3px;
}

.not-found-orbit--two {
  width: 72px;
  height: 72px;
  right: 0;
  bottom: 12px;
  background: var(--hm-warning-soft);
}

.not-found-badge {
  display: inline-flex;
  padding: 5px 12px;
  border-radius: var(--hm-radius-pill);
  background: var(--hm-primary-soft);
  color: var(--hm-primary);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 1.6px;
}

.not-found-title {
  margin: 18px 0 10px;
  color: var(--hm-ink-900);
  font-size: 26px;
  font-weight: 700;
  letter-spacing: 1px;
}

.not-found-desc {
  max-width: 400px;
  margin: 0 auto 28px;
  color: var(--hm-ink-500);
  font-size: var(--hm-font-base);
  line-height: 1.8;
}

.not-found-btn {
  min-width: 144px;
  height: 44px;
  border-radius: var(--hm-radius-md);
  box-shadow: 0 8px 18px rgba(232, 80, 30, 0.2);
}

@media (max-width: 1024px) {
  .not-found-page {
    min-height: calc(100vh - 120px);
  }
}

@media (max-width: 768px) {
  .not-found-page {
    min-height: calc(100vh - 100px);
    padding: 36px 16px;
  }

  .not-found-card {
    padding: 34px 36px 40px;
  }
}

@media (max-width: 480px) {
  .not-found-page {
    padding: 24px 12px;
  }

  .not-found-card {
    padding: 28px 22px 32px;
    border-radius: 18px;
  }

  .not-found-visual {
    width: 220px;
    margin-bottom: 16px;
  }

  .not-found-visual h1 {
    font-size: 82px;
  }

  .not-found-orbit--one {
    width: 96px;
    height: 96px;
  }

  .not-found-title {
    font-size: 23px;
  }

  .not-found-btn {
    width: 100%;
  }
}
</style>
