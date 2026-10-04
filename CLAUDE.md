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

## 上线前必须处理

- `POST /api/pay/simulate/{orderId}` 模拟支付调试口 —— **移除**
- `alipay.private-key` / `spring.ai.dashscope.api-key` 明文在 `application.yml` —— **改环境变量注入**

## 面试材料红线

对外材料（简历、讲解放、背诵稿）**一律不得提及 AI 知识库 / AI 购物助手 / RAG / Spring AI**。代码与开发文档保留 AI 模块（属项目功能），仅对外材料剔除。
