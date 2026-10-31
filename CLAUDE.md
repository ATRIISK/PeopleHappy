# 众乐电子商城 · 项目约定

> 本文件是 Claude Code 每次启动**自动加载**的入口。只放硬约束的最小集，完整规范见下方"按需读取"。

## 记忆体系（每次动手前先看）

**权威记忆层**：`C:\Users\LEVON\.claude\projects\E--PeopleHappy\memory\`，共 16 条目 + 3 归档。

1. **先读索引再读文件**：打开 `memory\MEMORY.md`，按摘要挑命中文件，**勿整目录全读**。历史轮次在 `archive/`（不进索引、默认不读）。
2. **改核心机制前必读台账**：`memory\document-code-sync-audit-ledger.md` —— 里面是防超卖口径、缓存延时双删、MQ 改 TTL 须删旧队列、支付宝金额单位与回调格式等现行约定，**不要凭记忆重新推导**。
3. **写完必须跑校验**：`python E:\PeopleHappy\.workbuddy\memory-kit\memcheck.py`，**ERROR 归零才算落盘完成**。

**记忆条目硬约束**：
- frontmatter 必填 `scope` / `type` / `status` / `source` / `updated`；写新条目照抄现有条目的结构。
- 条目超 8000 字要拆三段：主文件（快照，≤1 屏）+ `<topic>-ledger.md`（现行口径台账，只追加不涂改）+ `archive/<topic>-archive.md`（历史原文，进 archive 不进索引）。
- **新结论进台账，不就地涂改旧结论**；旧结论作废则改 `status: revoked`（误判）或 `historical`（被取代）。
- 写权不锁谁写，**锁内容规范**：核准事实再写、落对层、格式合规、写后跑 memcheck、双方不并发改同一处。

**完整规范**（按需读，不必每次读）：`E:\PeopleHappy\.workbuddy\memory-kit\SCHEMA.md`

## 开发红线

- **防超卖机制是「基于 InnoDB 行级排他锁的原子条件更新」（悲观锁），不是乐观锁**。product 表无 `version` 字段。被问到"底层加什么锁"答 X 锁 / 悲观锁。
- **退款顺序不可颠倒**：先校验交易号 → 条件更新 `status` 1→5 → 再调支付宝 refund → 恢复库存。反过来会与后台发货撞出"钱已退单未退"。
- 订单状态跃迁一律用 `WHERE status=xx` 条件更新，靠影响行数判幂等。
- 局部更新用 `LambdaUpdateWrapper` 显式 set 目标字段，**不用整实体 `updateById`**（会覆盖并发扣的库存）。
- 改 MQ 队列参数（TTL 等）必须「删旧队列 → 改代码 → 重启」，队列参数创建后不可变。
- 金额单位：支付宝 `total_amount` 是**元**字符串，`payment_log.total_fee` 是**分**。
- 分层：Controller 只收参 / `@Valid` / 调 Service / 包 `Result`；`@Transactional` 与缓存只在 Service；Entity 不直接返回前端。
- 注释与错误信息用简体中文；JavaDoc 末尾不加中文句号。

## 沟通方式

- 中文交流，代码英文命名。**不汇报进展**，直接给结论和产物。
- 不说教语气（不用"你应该/记住/避坑"这类表达）。
- 大范围 / 多文件改动，先给方案确认后再动手。

## 样式 / 视觉类需求（2026-10-05 教训，务必遵守）

本项目已有 `happy-mart-web/src/styles/design-system.css`（令牌 + `.hm-*` 组件类 + 三断点）与 `StateBlock`/`PageHeader` 两个纯展示组件。

**改样式前先分清两类改动，分别验收：**

| 类型 | 含义 | ATRI 能否感知 |
|---|---|---|
| 基础设施 | 建令牌 / 补规范 / 样式重构 | ❌ 不能 —— 别当成"美化"交付 |
| 视觉替换 | 把页面的 class 与样式真正换掉 | ✅ 能 —— 这才是"美化" |

**硬规则：**

1. **禁等值令牌**：把原色值原样搬进变量（如 `--hm-ink-800: #1a1a2e`）等于没改。改前把新旧值逐一比对。
2. **落到具体元素**：至少把高频页（Home / ProductList / ProductDetail / Cart / OrderList）的卡片、按钮、价格、网格真正替换成 `.hm-card` `.hm-btn` `.hm-price`。
3. **改完自查可感知**：`grep -c "hm-card\|hm-btn\|hm-state" src/views/front/*.vue` 确认目标页真的用上了新类；只有 1-2 处命中说明等于没做。
4. **验收标准**是"ATRI 打开页面一眼看出不同"，不是"构建通过"。
5. 状态展示（加载/空/错误）只在异常态出现，**不能作为"页面变好看了"的证据**。

**当前状态（2026-10-31）**：**经典灰白电商版**为生效主题。`design-system.css`（1894 行）是追加式覆盖结构，令牌层为白卡片 + 浅灰底 `#f5f5f5` + 主操作色 `#d94f2b` + 中性灰阶 `#1f2328` 到 `#f2f3f4`；Element Plus 变量由文件末尾「第 8 段 经典灰白电商覆盖」全量重写（94 个变量，已覆盖前两层），故渲染统一。前台 11 页、后台 4 页、两个布局与 3 个组件均已替换。**注意**：第 3 段与第 6 段的 `:root` 变量已被第 8 段全部覆盖（属死代码），但这两段里的 `.el-input`、`.el-table`、`.el-tag` 等选择器规则、以及第 7 段动效类**仍然生效**，不能整段删除；改样式只认令牌层与第 8 段。详见 `memory/frontend-visual-baseline.md`。

## 上线前必须处理

- `POST /api/pay/simulate/{orderId}` 模拟支付调试口 —— **移除**
- `alipay.private-key` / `spring.ai.dashscope.api-key` 明文在 `application.yml` —— **改环境变量注入**

## 本地启动（踩过的坑）

**启动顺序**：MySQL(3306) / RabbitMQ(5672) → Redis(6379) → 后端(8074) → 前端(3000)

- **Redis 必须用托管后台任务启动**：`D:\Redis\redis-server.exe redis.windows.conf`。
  Git Bash 的 `nohup ... &` 和 PowerShell `Start-Process` **都会被回收**（日志显示启动成功但几秒后连不上）。
  验证：`redis-cli.exe ping` 返 PONG 且 `netstat -ano | findstr :6379` 有 LISTENING。
- **后端启动若报端口冲突**：`Port xxxxx was already in use` 而配置是 8074，说明 Tomcat 抢到了别的端口（ATRI 的 imdoc 后端以随机端口在 IDEA 里跑，**不要杀那个进程**）。
  解法：`./mvnw -o spring-boot:run -Dspring-boot.run.arguments=--server.port=8074`
- 前端 `npm run dev` → 3000，Vite 已代理 `/api` 与 `/upload` 到 8074。

## 面试材料红线

对外材料（简历、讲解放、背诵稿）**一律不得提及 AI 知识库 / AI 购物助手 / RAG / Spring AI**。代码与开发文档保留 AI 模块（属项目功能），仅对外材料剔除。
