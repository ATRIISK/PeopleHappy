#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
batch_admin_tidy.py — 后台三页（UserManage / OrderManage / ProductManage）样式令牌化 + 表格响应式。

仅改 <style> 与模板 class 属性，不改任何 script 逻辑、接口调用、数据结构。
"""
import io
import os
import re

ADMIN = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'views', 'admin')

# 文件名 -> (页头标题是否保留 h1 文案, 表格 empty-text)
TARGETS = {
    'UserManage.vue': '用户管理',
    'OrderManage.vue': '订单管理',
    'ProductManage.vue': '商品管理',
}

# 统一的工具栏样式（替换各页零散定义）
TOOLBAR_CSS = """/* ==================== 工具栏（标题 + 右侧操作） ==================== */
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--hm-space-4);
  margin-bottom: var(--hm-space-5);
  flex-wrap: wrap;
}

.page-title {
  font-size: var(--hm-font-xl);
  font-weight: var(--hm-fw-bold);
  color: var(--hm-ink-900);
  margin: 0;
  line-height: var(--hm-lh-tight);
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: var(--hm-space-2);
  flex-wrap: wrap;
}

/* 表格容器：窄屏可横向滚动，避免列被挤压 */
.table-scroll {
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}
.table-scroll :deep(.el-table) {
  min-width: 720px; /* 低于此宽度列会挤成一团，宁可横向滚动 */
}

/* 表格内的次要文字 */
.hm-cell-secondary {
  color: var(--hm-ink-500);
  font-size: var(--hm-font-sm);
}

@media (max-width: 768px) {
  .toolbar {
    align-items: flex-start;
    flex-direction: column;
  }
  .toolbar-right {
    width: 100%;
  }
  .page-title {
    font-size: var(--hm-font-lg);
  }
}
"""


def patch(fname):
    path = os.path.join(ADMIN, fname)
    text = io.open(path, encoding='utf-8').read()
    original = text

    # 1. 表格外包 .table-scroll（仅包住 el-table 开标签 → 表格结束标签）
    if 'class="table-scroll"' not in text:
        text = text.replace(
            '<el-table :data=',
            '<div class="table-scroll">\n      <el-table :data='
        )
        # 关闭：第一个 </el-table> 之后插入 </div>
        text = re.sub(
            r'(\n(\s*)</el-table>)',
            r'\1\n\2</div>',
            text,
            count=1
        )

    # 2. 替换 style 中的 .toolbar / .page-title / .toolbar-right 三段
    def replace_block(src, selector):
        """删除 .selector { ... } 这一整块"""
        pattern = re.compile(r'\n(/\*[^*]*\*/\n)?\.' + re.escape(selector) + r'\s*\{[^}]*\}', re.M)
        return pattern.sub('', src, count=1)

    for sel in ('toolbar', 'page-title', 'toolbar-right'):
        text = replace_block(text, sel)

    # 3. 在 <style scoped> 后插入统一工具栏样式
    text = text.replace('<style scoped>', '<style scoped>\n' + TOOLBAR_CSS, 1)

    if text != original:
        io.open(path, 'w', encoding='utf-8').write(text)
        print('PATCHED:', fname)
    else:
        print('NO CHANGE:', fname)


if __name__ == '__main__':
    for f in TARGETS:
        patch(f)
