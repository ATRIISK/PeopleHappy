# 项目长期记忆

> **本文件是派生层（L3）**，只放指针 + 本侧执行必备，**不复制内容**。
> 权威记忆层（L2）在 `C:\Users\LEVON\.claude\projects\E--PeopleHappy\memory\`，17 条目 + 3 归档。
> **先读 Claude 侧 `memory/MEMORY.md` 索引**，按摘要挑命中文件，勿整目录全读。
> 规范与校验：`.workbuddy/memory-kit/SCHEMA.md`、`python .workbuddy/memory-kit/memcheck.py`（ERROR 归零才算落盘完成）。

## 必须遵守的技术事实

- **防超卖 = InnoDB 排他行锁的「原子条件更新」（悲观锁），不是乐观锁**（2026-08-25 第 23 轮纠偏）。product 表无 `version` 字段。面试答"底层加什么锁"→ X 锁/悲观锁。全文见 L2 `document-code-sync-audit-ledger.md` 第二节。
- **面试材料一律不提 AI 知识库 / AI 购物助手 / RAG / Spring AI**（2026-08-27 ATRI 定）。代码与开发文档保留 AI 模块。
- **样式类需求禁"等值令牌"**（2026-10-05 教训）：原色值搬进变量=无效改动；须区分「基础设施（不可感知）」与「视觉替换（可感知）」，改完自查目标页是否真用上新类。详见 L2 `frontend-visual-baseline.md` 与 `CLAUDE.md`。
- **本地启动**：Redis 须用托管后台任务起（`nohup`/`Start-Process` 都会被回收）；后端报随机端口冲突是 ATRI 的 imdoc 后端，**不要杀**。详见 `CLAUDE.md`「本地启动」节。

## 项目背景

- 众乐电子商城（`happy_mart` 后端 + `happy-mart-web` 前端 + nginx + docker-compose），Spring Boot 3.5.14 + Redis + RabbitMQ + 支付宝沙箱 + AI RAG，后端端口 8074。
- 另一项目：高并发铁路抢票系统（Redis Lua 预减 + RocketMQ 事务消息）——该项目的预减才是"先扣 Redis 再异步落库"的高并发方案。

## 本侧执行记录（2026-10-05）

- 记忆层改造：建 kit（SCHEMA + memcheck + migrate_fm）、条目补齐 frontmatter、超长文件三段式拆分进 archive、消解"乐观锁"矛盾、重建表格化索引；`memcheck` ERROR 51→0。
- RAG 关键词召回 P0 修复（A 档）+ 新增 12 用例；`mvn test` 28 全绿。
- 新建 `CLAUDE.md`（Claude Code 自动加载入口）+ 索引首行指引，治"新会话不知道规范"。
- 前端视觉改造：只完成设计系统基础设施，前台页面样式未替换，**ATRI 认为无效果**，后续方向待其决定（A 真替换 5 高频页 / B 指认位置 / C 先定配色）。

详细过程见本目录 `2026-10-05.md`。
