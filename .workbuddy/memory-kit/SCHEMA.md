# 众乐电子商城 记忆条目 schema 约定（v1.0，2026-10-05）

> 适用范围：`C:\Users\LEVON\.claude\projects\E--PeopleHappy\memory\*.md`（L2 记忆层）。
> 设计基准：Claude 官方上下文工程与记忆管理最佳实践（structured note-taking / just-in-time retrieval / compaction），
> 并对齐 `E:\imdoc-work\memory-kit\SCHEMA.md` 的 v1.1 口径，便于两个项目共用同一套工具思路。
> 目的：让"文件式记忆"也能被脚本校验，把一致性从"靠自觉"变成"靠脚本"。

## 1. frontmatter 字段

```yaml
---
name: zhonge-mall-conventions        # 必填，与文件名一致（去 .md）
description: 一句话摘要                # 必填，会进索引，写清"是什么 + 最新状态"
scope: project:happy-mart/backend     # 必填，作用域；取值见下表
type: fact | decision | progress | rule | procedure   # 必填
status: active | historical | revoked # 必填；revoked=结论作废，禁止再引用
confidence: high | medium | low       # 选填，默认 medium
source:                               # 必填，至少一条，用于回溯与纠错
  - "docs/design/众乐电子商城-开发文档.md#§15.4"
  - "commit:a9d3d70"
valid_from: 2026-07-22                # 选填，事实生效时间
revisit_when: "上线部署前"             # 选填，触发复核的条件（比日期更适合本项目）
supersedes: [xxx-v0]                  # 选填，被本条取代的旧条目
updated: 2026-10-05                   # 必填，最后人工确认的日期（非文件 mtime）
---
```

**scope 取值**（本项目）：

| scope | 含义 |
|---|---|
| `project:happy-mart/backend` | 后端（Spring Boot / 8074） |
| `project:happy-mart/frontend` | 前端（Vue 3 / 3000） |
| `project:happy-mart/infra` | 部署与中间件（nginx / docker / Redis / RabbitMQ） |
| `project:happy-mart/ai` | AI 购物助手与知识库（RAG） |
| `process:interview` | 面试材料口径（对外表达红线） |
| `process:collab` | 协作与工作流约定 |

**status 语义**：

| 值 | 含义 | 注入行为 |
|---|---|---|
| active | 当前有效，可依赖 | 默认参与检索与注入 |
| historical | 已被后续轮次覆盖，仅留痕 | 可检索，不主动注入，引用时必须标注 |
| revoked | 结论作废（如误报、错误推断） | 禁止引用；保留原文仅供追溯 |

**type 语义**：`fact`（事实/现状）/ `decision`（决策+理由）/ `progress`（进度快照）/ `rule`（红线/约定）/ `procedure`（操作流程）。

## 2. 三层职责与写者

| 层 | 位置 | 写者 | 是否可重建 |
|---|---|---|---|
| L1 权威源 | `docs/design/众乐电子商城-开发文档.md`、`README.md` | 双方，落盘为文档变更 | 否（唯一真相） |
| L2 记忆层 | Claude `memory/`（索引 + 条目 + 台账 + 归档） | Claude 或 WorkBuddy 均可写（受内容规范约束） | 否（索引在此定义） |
| L3 派生层 | WorkBuddy `.workbuddy/memory/`（指针 + 日志）、校验报告 | WorkBuddy 只写日志 | 是 |

**回写方向单一**：`memcheck` 报告只驱动 L1 / L2 修正，L3 永不反向写入。

## 3. 拆分与命名规则

单个条目文件超过 **8000 字符**即拆为三段（同一 `scope`）：

| 文件 | 内容 | status | 是否进索引 |
|---|---|---|---|
| `<topic>.md`（主文件，保留原名） | 现状快照 + 待办 + 关键技术 + 指针，≤1 屏 | active | 是 |
| `<topic>-ledger.md` | 现行决策与口径台账，**一行一条**，带出处 | active | 是 |
| `archive/<topic>-archive.md` | 历史轮次原文（逐字搬运，不改写） | historical | **否**（放 `archive/`，默认不读） |

台账与归档的指针写回主文件末节。

## 3.1 解析约定

- frontmatter 支持一层嵌套：顶层键不缩进，`metadata:` 等块下的子键缩进 2 空格 —— 因此顶层 `type`（fact/decision/…）与 `metadata.type`（Claude 工具自带 memory/feedback/reference）互不冲突。
- 校验器只认顶层字段；`metadata` 块原样保留，不做改动。
- 旧条目的 `modified` 是工具自动写入，**不作为时效依据**；时效只看顶层 `updated`。

## 4. 每轮落盘后的动作

1. 核准事实（不凭记忆写入）→ 2. 更新 L2（frontmatter 的 status/updated/revisit_when；新结论进台账，不就地涂改）→ 3. 更新索引单行 → 4. 跑 `memcheck.py` → 5. **ERROR 归零才算落盘完成**。

## 5. 使用方式

| 步骤 | 动作 | 命令/落点 |
|---|---|---|
| ① 读索引 | 先读 Claude `memory/MEMORY.md`，按摘要挑命中文件 | 只读命中文件，勿整目录扫 |
| ② 干活 | 开发 / 测试 / 排查；需要现成知识时读台账（`*-ledger.md`） | — |
| ③ 落盘三件套 | L1 文档 + L2（新结论进台账 / 现状快照更新）+ 索引那一行 | 见 §4 |
| ④ 跑校验 | `python E:\PeopleHappy\.workbuddy\memory-kit\memcheck.py` → 看 `MEMORY-REPORT.md`，**ERROR 归零才算完成** | 只读，不改任何记忆 |

## 5.1 写权划分（沿用 imdoc 拍板口径：不锁谁写，锁内容规范）

| 层 | Claude | WorkBuddy | 理由 |
|---|---|---|---|
| L1 文档（开发文档 / README） | 主写 | 可回填执行结果 | 事实唯一，但允许执行侧当场留痕 |
| L2 `memory/` | 可写 | 可写 | 防漂移靠内容规范 + memcheck，不靠单一写者 |
| L3 派生层 | 可读 | 写 | 全部可重建，丢了不心疼 |
| 校验 `memcheck.py` | 跑 | 跑 | 纯文件操作，两边结果一致 |

**L2 写入五关（硬规则，缺一不可）**：

1. **核准事实再写** —— 不凭记忆或推测；
2. **落对层** —— 事实/交付物 → L1，索引/主题条目 → L2，指针 + 执行必备 → L3，不跨层堆放、不重复；
3. **格式合规** —— frontmatter 必填字段置顶层、索引行用 `- [主题](文件.md) — 摘要 ｜ 状态 · MM-DD`、台账只追加不涂改旧结论；
4. **写后跑 `memcheck`，ERROR 必须为 0**；
5. **双方不并发改同一处** —— 后写者以已核准的最新事实为准。

## 5.2 本项目特有约定

### 面试材料红线（`process:interview`）
所有面试相关材料（简历、讲解放、背诵稿）**一律不得提及 AI 知识库 / AI 购物助手 / RAG / Spring AI**。
代码与开发文档中保留 AI 模块（属项目功能），仅对外材料剔除。

### 防超卖机制口径（`project:happy-mart/backend`，2026-08-25 第 23 轮纠偏）
`updateStock`（`UPDATE product SET stock=stock-? WHERE id=? AND stock>=?`）**不是乐观锁**。
正确表述：**基于 InnoDB 行级排他锁的"原子条件更新"**（悲观锁 + WHERE 条件校验）。
判定铁律：冲突时"阻塞"还是"重试"——本项目阻塞排队后重判条件，故为悲观锁。product 表无 `version` 字段。
