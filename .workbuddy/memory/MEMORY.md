# 项目长期记忆

> **本文件是派生层（L3）**，只放指针 + 本侧执行必备，**不复制内容**。
> 权威记忆层（L2）在 `C:\Users\LEVON\.claude\projects\E--PeopleHappy\memory\`，共 16 条目 + 3 归档。
> **先读 Claude 侧 `memory/MEMORY.md` 索引**，按摘要挑命中文件，勿整目录全读。
> 规范与校验：`.workbuddy/memory-kit/SCHEMA.md`、`python .workbuddy/memory-kit/memcheck.py`（ERROR 归零才算落盘完成）。

## 技术事实（必须遵守，勿再犯错）

### 防超卖机制的正确叫法（2026-08-25 第 23 轮纠偏）
- `updateStock`（`UPDATE product SET stock=stock-? WHERE id=? AND stock>=?`）**不是乐观锁**。
- 判定铁律：冲突时"阻塞"还是"重试"。本项目 InnoDB 执行 UPDATE 时对匹配行加排他行锁（X 锁），并发事务**阻塞排队**，拿到锁后重判条件 → 属**悲观锁机制**。
- 正确叫法：**基于 InnoDB 行级排他锁的"原子条件更新"**（悲观锁 + WHERE 条件校验）。
- product 表**无 version 字段**，不具备乐观锁结构。
- 面试答"底层加什么锁"：答 **InnoDB 排他行锁（X 锁）/ 悲观锁**。
- 现行口径全文见 Claude 侧 `document-code-sync-audit-ledger.md` 第二节；**不要在本文件堆细节**。

### 面试材料红线（2026-08-27）
- 所有面试材料（简历、讲解放、背诵稿）**一律不得提及 AI 知识库 / AI 购物助手 / RAG / Spring AI**。
- 已清理 6 份材料（`Desktop\B2C项目讲解.txt` + `D:\项目解答` 下 4 个 txt + MQ与Redis详解本身无 AI 内容）。
- 今后产出的任何面试准备材料默认剔除 AI 相关内容。

## 项目背景
- 众乐电子商城（happy_mart 后端 + happy-mart-web 前端 + nginx + docker-compose），Spring Boot 3.5.14 + Redis + RabbitMQ + 支付宝沙箱 + AI RAG，位于 `E:\PeopleHappy`，后端端口 8074。
- 另一项目：高并发铁路抢票系统（Redis Lua 预减 + RocketMQ 事务消息）——该项目的预减才是"先扣 Redis 再异步落库"的高并发方案。

## 本侧执行记录（2026-10-05）
- 完成记忆层改造：建 kit（SCHEMA + memcheck + migrate_fm）、15 条目补齐 frontmatter、超长文件三段式拆分（audit/progress 进 archive）、消解"乐观锁"矛盾、重建表格化索引。
- 同轮审计定位 RAG 索引 4 项 P0/P1 缺陷（关键词召回恒 0 命中 / 快照过期 / 全量重写 4.2MB / 语义同质），方案见 Claude 侧 `happy-mart-memory-plan.md`，A 档已实施。

