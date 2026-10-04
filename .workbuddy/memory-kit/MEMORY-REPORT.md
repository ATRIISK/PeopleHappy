# 众乐电子商城 记忆体系一致性报告（memcheck）

> 生成时间：2026-10-05 03:08　模式：**只读扫描**（本报告不改动任何记忆或文档）
> 判定：ERROR 必须修；WARN 建议修；INFO 仅记录。本报告只驱动 L1/L2 修正，派生层永不反向写入。

## 0. 总览

| 级别 | 数量 |
|---|---|
| ERROR | 0 |
| WARN | 2 |
| INFO | 7 |

- 扫描文件：Claude 记忆 18 个、归档 3 个、WorkBuddy 记忆 6 个、L1 文档 2 个
- 索引条目：17 条

## 1. 规模（按字数降序，前 8）

| 文件 | 字数 |
|---|---|
| design\众乐电子商城-开发文档.md | 52790 |
| memory\document-code-sync-audit-archive.md | 19770 |
| E:\PeopleHappy\README.md | 11915 |
| memory\2026-10-05.md | 10089 |
| memory\project-progress-archive.md | 9037 |
| memory\document-code-sync-audit-ledger.md | 5489 |
| memory\project-sync-complete-archive.md | 4836 |
| memory\redis-usage-scenarios.md | 4559 |

## 2. 关键数字一致性（应一致却出现多值的会在这里暴露）

**单元测试用例数** ✅：16
  - E:\PeopleHappy\README.md → 16
  - design\众乐电子商城-开发文档.md → 16
  - memory\document-code-sync-audit.md → 16
  - memory\project-progress.md → 16

**商品演示数据量** ✅：200
  - E:\PeopleHappy\README.md → 200
  - memory\document-code-sync-audit-ledger.md → 200
  - memory\project-progress.md → 200

**审计轮次** ❌ 不一致：16, 23
  - memory\document-code-sync-audit-ledger.md → 23
  - memory\document-code-sync-audit.md → 23
  - memory\project-progress.md → 23
  - memory\user-working-style.md → 16

**数据库表数** ❌ 不一致：5, 8
  - E:\PeopleHappy\README.md → 8
  - design\众乐电子商城-开发文档.md → 8
  - memory\project-history.md → 5
  - memory\project-progress.md → 8

## 3. ERROR 明细（0）

（无）

## 4. WARN 明细（2）

### 数字一致

- 审计轮次 存在多个取值 -> memory\document-code-sync-audit-ledger.md=23; memory\document-code-sync-audit.md=23; memory\project-progress.md=23; memory\user-working-style.md=16
- 数据库表数 存在多个取值 -> E:\PeopleHappy\README.md=8; design\众乐电子商城-开发文档.md=8; memory\project-history.md=5; memory\project-progress.md=8

## 5. INFO 明细（7）

### 敏感值

- 未发现新增明文口令（已排除 application.yml 已知值与占位符）

### 超长

- memory\2026-10-05.md = 10089 字（非 L2 条目：L1 交付文档 / 执行日志，不建议拆；可考虑文首加按节导航）
- design\众乐电子商城-开发文档.md = 52790 字（非 L2 条目：L1 交付文档 / 执行日志，不建议拆；可考虑文首加按节导航）
- E:\PeopleHappy\README.md = 11915 字（非 L2 条目：L1 交付文档 / 执行日志，不建议拆；可考虑文首加按节导航）

### 重复主题

- AI 助手 / RAG 提及 出现在 13 处: memory\MEMORY.md, memory\document-code-sync-audit-ledger.md, memory\interview-prep-docs.md, memory\project-progress.md, memory\user-profile.md, memory\zhongle-mall-project.md, memory\document-code-sync-audit-archive.md, memory\project-progress-archive.md, memory\2026-08-20.md, memory\2026-08-27.md, memory\MEMORY.md, design\众乐电子商城-开发文档.md, E:\PeopleHappy\README.md
- 支付与超时取消方案 出现在 15 处: memory\MEMORY.md, memory\document-code-sync-audit-ledger.md, memory\document-code-sync-audit.md, memory\interview-prep-docs.md, memory\project-history.md, memory\project-progress.md, memory\rabbitmq-usage-scenarios.md, memory\zhongle-mall-project.md, memory\document-code-sync-audit-archive.md, memory\project-progress-archive.md, memory\2026-08-20.md, memory\2026-08-26.md, memory\2026-10-05.md, design\众乐电子商城-开发文档.md, E:\PeopleHappy\README.md
- 防超卖机制口径（乐观锁 vs 原子条件更新） 出现在 16 处: memory\MEMORY.md, memory\document-code-sync-audit-ledger.md, memory\happy-mart-memory-plan.md, memory\interview-prep-docs.md, memory\order-address-module.md, memory\project-progress.md, memory\document-code-sync-audit-archive.md, memory\project-progress-archive.md, memory\2026-08-20.md, memory\2026-08-25.md, memory\2026-08-26.md, memory\2026-08-27.md, memory\2026-10-05.md, memory\MEMORY.md, design\众乐电子商城-开发文档.md, E:\PeopleHappy\README.md

## 6. 建议动作

1. 修 ERROR：索引对齐、口径矛盾、敏感值明文
2. 补 frontmatter：scope / type / status / source / updated
3. 统一数字：以 L1 权威源为准，改记忆里的旧值
4. 拆超长文件：按 SCHEMA 拆 snapshot / ledger / archive
5. 复查过期项：加 revisit_when 或归档
