#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
memcheck.py — 众乐电子商城 记忆体系一致性校验（只读）

用法:
    python memcheck.py                 # 扫描并写出 MEMORY-REPORT.md
    python memcheck.py --report X.md   # 指定报告输出路径

只读保证: 本脚本不写入任何记忆或文档文件, 仅读取 + 生成独立报告。
设计基准: E:\\imdoc-work\\memory-kit\\memcheck.py（同构，适配本项目 scope/type 与数字模式）。
"""
import os
import re
import sys
import datetime
from collections import defaultdict

CLAUDE_MEM = r"C:\Users\LEVON\.claude\projects\E--PeopleHappy\memory"
WB_MEM = r"E:\PeopleHappy\.workbuddy\memory"
DOC_DIRS = [r"E:\PeopleHappy\docs\design"]
APP_YML = r"E:\PeopleHappy\happy_mart\src\main\resources\application.yml"
KIT_DIR = os.path.dirname(os.path.abspath(__file__))
REPORT = os.path.join(KIT_DIR, "MEMORY-REPORT.md")

LONG_LIMIT = 8000
STALE_DAYS = 30
NEW_FIELDS = ["scope", "type", "status", "source", "updated"]
STATUS_VALUES = {"active", "historical", "revoked"}
TYPE_VALUES = {"fact", "decision", "progress", "rule", "procedure"}
SCOPE_VALUES = {
    "project:happy-mart/backend",
    "project:happy-mart/frontend",
    "project:happy-mart/infra",
    "project:happy-mart/ai",
    "process:interview",
    "process:collab",
}

findings = {"ERROR": [], "WARN": [], "INFO": []}
def add(level, section, text):
    findings[level].append((section, text))

def read(p):
    with open(p, encoding="utf-8", errors="replace") as f:
        return f.read()

def parse_fm(text):
    """解析 frontmatter：顶层键 + 缩进子键（metadata 等嵌套块）分开。"""
    if not text.startswith("---"):
        return {}, text
    end = text.find("\n---", 3)
    if end == -1:
        return {}, text
    fm = {}
    cur = None
    for line in text[3:end].splitlines():
        if not line.strip() or line.strip().startswith("#"):
            continue
        indent = len(line) - len(line.lstrip())
        stripped = line.strip()
        if cur and stripped.startswith("- "):
            if not isinstance(fm.get(cur), list):
                fm[cur] = []
            fm[cur].append(stripped[2:].strip().strip('"').strip("'"))
            continue
        if ":" not in line:
            continue
        k, v = line.split(":", 1)
        k, v = k.strip(), v.strip().strip('"').strip("'")
        if indent == 0:
            cur = k if not v else None
            fm[k] = v if v else {}
        elif cur and isinstance(fm.get(cur), dict):
            fm[cur][k] = v
    return fm, text[end + 4:]

def collect_md(d):
    out = []
    if not os.path.isdir(d):
        return out
    for name in sorted(os.listdir(d)):
        if name.endswith(".md"):
            out.append(os.path.join(d, name))
    return out

def collect_archives():
    return collect_md(os.path.join(CLAUDE_MEM, "archive"))

def collect_docs():
    out = []
    for d in DOC_DIRS:
        if os.path.isdir(d):
            for name in sorted(os.listdir(d)):
                if name.endswith(".md"):
                    out.append(os.path.join(d, name))
    for name in ("README.md",):
        p = os.path.join(r"E:\PeopleHappy", name)
        if os.path.exists(p):
            out.append(p)
    return out

def rel(p):
    for base in [CLAUDE_MEM, WB_MEM] + DOC_DIRS:
        if p.startswith(base):
            return os.path.join(os.path.basename(base), os.path.basename(p))
    return p

def is_daily_log(name):
    return bool(re.match(r"\d{4}-\d{2}-\d{2}\.md$", name))

# ---------- 1. 规模与超长 ----------
def check_size(files):
    rows = []
    for p in files:
        t = read(p)
        n = len(t)
        fm, _ = parse_fm(t)
        status = fm.get("status", "")
        if n > LONG_LIMIT and p.startswith(CLAUDE_MEM) and status != "historical":
            add("WARN", "超长", f"{rel(p)} = {n} 字 > {LONG_LIMIT}（L2 条目，需按 SCHEMA 拆 snapshot/ledger/archive）")
        elif n > LONG_LIMIT and not p.startswith(CLAUDE_MEM):
            add("INFO", "超长", f"{rel(p)} = {n} 字（非 L2 条目：L1 交付文档 / 执行日志，不建议拆；可考虑文首加按节导航）")
        rows.append((rel(p), n))
    return rows

# ---------- 2. 索引对齐 ----------
def is_index_example(line):
    return line.lstrip().startswith(">")

def parse_index_lines(text):
    out = []
    for line in text.splitlines():
        if is_index_example(line):
            continue
        m = re.match(r"- \[(.+?)\]\((.+?\.md)\)(.*)$", line)
        if m:
            out.append((m.group(1), m.group(2), m.group(3)))
    return out

def check_index():
    index = os.path.join(CLAUDE_MEM, "MEMORY.md")
    if not os.path.exists(index):
        add("ERROR", "索引对齐", "Claude 侧索引 MEMORY.md 不存在")
        return []
    text = read(index)
    rows = parse_index_lines(text)
    links = [(t, f) for t, f, _ in rows]
    linked = {os.path.basename(f) for _, f in links}
    actual = {os.path.basename(p) for p in collect_md(CLAUDE_MEM) if os.path.basename(p) != "MEMORY.md"}
    for f in sorted(actual - linked):
        add("ERROR", "索引对齐", f"条目文件未被索引引用（孤条目）: {f}")
    for f in sorted(linked - actual):
        add("ERROR", "索引对齐", f"索引指向的文件不存在（死链）: {f}")
    for title, _, tail in rows:
        if not re.search(r"(有效|历史|已撤销)", tail):
            add("WARN", "索引对齐", f"索引行缺状态标记: {title}")
        if not re.search(r"\d{2}-\d{2}", tail):
            add("WARN", "索引对齐", f"索引行缺更新日期: {title}")
    return links

# ---------- 3. 字段完备性 ----------
def check_schema(files):
    for p in files:
        base = os.path.basename(p)
        if base == "MEMORY.md" or is_daily_log(base):
            continue
        fm, _ = parse_fm(read(p))
        missing = [k for k in NEW_FIELDS if k not in fm or fm.get(k) == {}]
        if missing:
            add("WARN", "字段完备", f"{base}: 缺 {', '.join(missing)}")
        st = fm.get("status")
        if st and st not in STATUS_VALUES:
            add("ERROR", "字段完备", f"{base}: status='{st}' 非法（应为 active/historical/revoked）")
        ty = fm.get("type")
        if ty and ty not in TYPE_VALUES:
            add("ERROR", "字段完备", f"{base}: type='{ty}' 非 v1 枚举")
        sc = fm.get("scope")
        if sc and sc not in SCOPE_VALUES:
            add("ERROR", "字段完备", f"{base}: scope='{sc}' 不在本项目枚举内")
        nm = fm.get("name")
        if nm and nm != base[:-3]:
            add("WARN", "字段完备", f"{base}: name='{nm}' 与文件名不一致")

def check_archives(archives):
    """归档文件必须是 historical，且不得被索引引用。"""
    index = os.path.join(CLAUDE_MEM, "MEMORY.md")
    idx_text = read(index) if os.path.exists(index) else ""
    for p in archives:
        base = os.path.basename(p)
        fm, _ = parse_fm(read(p))
        if fm.get("status") != "historical":
            add("WARN", "归档规范", f"archive/{base}: status 应为 historical，实为 '{fm.get('status')}'")
        if base in idx_text:
            add("WARN", "归档规范", f"archive/{base}: 被索引引用（归档不应出现在索引里）")

# ---------- 4. 关键数字一致性 ----------
NUM_PATTERNS = [
    ("单元测试用例数", r"(\d{2})\s*(?:个)?(?:用例|测试)"),
    ("项目模块数", r"(\d{1,2})\s*(?:个)?核心模块"),
    ("数据库表数", r"(\d)\s*张表"),
    ("商品演示数据量", r"(\d{3})\s*个演示商品"),
    ("审计轮次", r"第\s*(\d{1,2})\s*轮"),
    ("订单状态档数", r"(\d)\s*档"),
]

def check_numbers(files):
    """每个文件取首次出现的取值：本体系约定'现状置顶'，首次即最新。归档文件为冻结历史，不参与比对。"""
    table = defaultdict(dict)
    for p in files:
        base = os.path.basename(p)
        if is_daily_log(base) or os.path.basename(os.path.dirname(p)) == "archive":
            continue
        if base == "MEMORY.md":
            continue
        t = read(p)
        for label, pat in NUM_PATTERNS:
            for m in re.finditer(pat, t):
                groups = [g for g in m.groups() if g]
                if groups:
                    table[label][rel(p)] = groups[0]
                    break
    for label, per_file in sorted(table.items()):
        vals = set(per_file.values())
        if len(vals) > 1:
            detail = "; ".join(f"{f}={v}" for f, v in sorted(per_file.items()))
            add("WARN", "数字一致", f"{label} 存在多个取值 -> {detail}")
    return table

# ---------- 5. 重复主题（跨副本） ----------
TOPICS = {
    "防超卖机制口径（乐观锁 vs 原子条件更新）": ["乐观锁", "排他锁", "原子条件更新"],
    "AI 助手 / RAG 提及": ["RAG", "知识库", "AI 购物助手"],
    "支付与超时取消方案": ["超时取消", "死信", "DLX"],
}

def check_topic_overlap(files):
    hits = defaultdict(list)
    for p in files:
        t = read(p)
        for topic, kws in TOPICS.items():
            if any(k in t for k in kws):
                hits[topic].append(rel(p))
    for topic, where in sorted(hits.items()):
        if len(where) >= 2:
            add("INFO", "重复主题", f"{topic} 出现在 {len(where)} 处: {', '.join(where)}")

# ---------- 6. 矛盾检测：乐观锁口径 ----------
# 规则说明：只把「把库存扣减机制断言为乐观锁」判为矛盾。
# 以下情形不算矛盾，须排除：
#   ① frontmatter description 里的概括表述（可能是「非乐观锁」这种否定式）
#   ② 含否定/纠偏/对比标记的行（不是 / 非 / 不满足 / 纠正 / 误 / 悲观锁 / 并非 / 原子条件更新）
#   ③ 讨论面试口径或历史对比本身的行（面试 / 历史 / 曾 / 对比 / 辨析 / 精确定位）
NEG_MARKERS = ("不是", "非", "不满足", "纠正", "误", "悲观锁", "并非", "原子条件更新",
               "无 version", "不具备", "曾出现", "精确定位", "对比", "辨析", "纠偏")

def check_conflict_terms(files):
    """专项：防超卖机制口径。悲观锁口径已定，把库存扣减断言为'乐观锁'的 active 条目是矛盾。"""
    for p in files:
        base = os.path.basename(p)
        if base == "MEMORY.md" or is_daily_log(base):
            continue
        fm, body = parse_fm(read(p))
        if fm.get("status") != "active":
            continue
        for i, line in enumerate(body.splitlines(), 1):
            if "乐观锁" not in line:
                continue
            if any(k in line for k in NEG_MARKERS):
                continue
            add("ERROR", "口径矛盾", f"{rel(p)}:{i} 把库存扣减表述为'乐观锁'，与第 23 轮纠偏结论矛盾（应称原子条件更新/悲观锁）")

# ---------- 7. 过期与复核缺口 ----------
def check_staleness(files):
    today = datetime.date.today()
    for p in files:
        base = os.path.basename(p)
        if base == "MEMORY.md":
            continue
        fm, body = parse_fm(read(p))
        if fm.get("status") == "historical":
            continue
        d = None
        for key in ("updated", "valid_from"):
            v = fm.get(key, "")
            if isinstance(v, dict):
                v = ""
            m = re.search(r"(\d{4})-(\d{2})-(\d{2})", str(v))
            if m:
                d = datetime.date(int(m.group(1)), int(m.group(2)), int(m.group(3)))
                break
        if d and (today - d).days > STALE_DAYS:
            add("WARN", "过期复核", f"{base}: updated {d}，已 {(today - d).days} 天未复核（若仍有效请改 updated；否则改 status: historical）")
        if re.search(r"(待办|遗留|未完成|⏳|❌)", body) and "revisit_when" not in fm:
            add("WARN", "过期复核", f"{base}: 含待办/挂起事项但无 revisit_when 字段（无法自动提醒复核）")

# ---------- 8. 敏感值线索（脱敏输出） ----------
def known_secrets():
    vals = set()
    if os.path.exists(APP_YML):
        for line in read(APP_YML).splitlines():
            m = re.search(r"^\s*(?:password|api-key|private-key|alipay-public-key)\s*:\s*[\"']?([^\"'\s#]{6,})", line)
            if m:
                vals.add(m.group(1))
    return {v for v in vals if v and not v.startswith("@") and "dummy" not in v}

def mask(v):
    return v[:2] + "*" * max(3, len(v) - 2)

def placeholder_like(tok):
    if any(c in tok for c in "<>*…"):
        return True
    if any("\u4e00" <= c <= "\u9fff" for c in tok):
        return True
    return tok.lower().startswith(("your", "xxx", "pwd", "password", "***"))

def check_secrets(files):
    secs = known_secrets()
    patterns = [
        (r"password\s*[:=]\s*(\S{6,})", "password 赋值"),
        (r"api-key\s*:\s*(\S{6,})", "api-key 明文"),
        (r"private-key\s*:\s*(\S{20,})", "私钥明文"),
    ]
    n = 0
    for p in files:
        for i, line in enumerate(read(p).splitlines(), 1):
            for pat, why in patterns:
                for v in re.findall(pat, line):
                    v = v.strip("\"'")
                    if placeholder_like(v):
                        continue
                    if v in secs or re.fullmatch(r"[A-Za-z0-9!@#$%^&*_\-\.]{8,}", v):
                        add("ERROR", "敏感值", f"{rel(p)}:{i} {why} 明文（值 {mask(v)}，建议改环境变量注入）")
                        n += 1
    if n == 0:
        add("INFO", "敏感值", "未发现新增明文口令（已排除 application.yml 已知值与占位符）")

# ---------- 9. 派生层指针有效性 ----------
def check_pointer():
    p = os.path.join(WB_MEM, "MEMORY.md")
    if not os.path.exists(p):
        add("ERROR", "派生层", "WorkBuddy 侧 MEMORY.md 不存在")
        return
    t = read(p)
    if "claude" not in t.lower() or "MEMORY.md" not in t:
        add("ERROR", "派生层", "WorkBuddy MEMORY.md 未包含 Claude 索引指针")
    if len(t) > 2000:
        add("WARN", "派生层", f"WorkBuddy MEMORY.md = {len(t)} 字，超出'只放指针'的预期（建议 < 2000）")

# ---------- 报告 ----------
def build_report(size_rows, links, num_table):
    now = datetime.datetime.now().strftime("%Y-%m-%d %H:%M")
    warn_over = {txt.split(" ")[0] for sec, txt in findings["WARN"] if sec == "超长"}
    L = []
    L.append("# 众乐电子商城 记忆体系一致性报告（memcheck）")
    L.append("")
    L.append(f"> 生成时间：{now}　模式：**只读扫描**（本报告不改动任何记忆或文档）")
    L.append("> 判定：ERROR 必须修；WARN 建议修；INFO 仅记录。本报告只驱动 L1/L2 修正，派生层永不反向写入。")
    L.append("")
    L.append("## 0. 总览")
    L.append("")
    L.append("| 级别 | 数量 |")
    L.append("|---|---|")
    for lv in ("ERROR", "WARN", "INFO"):
        L.append(f"| {lv} | {len(findings[lv])} |")
    L.append("")
    L.append(f"- 扫描文件：Claude 记忆 {len(collect_md(CLAUDE_MEM))} 个、归档 {len(collect_archives())} 个、WorkBuddy 记忆 {len(collect_md(WB_MEM))} 个、L1 文档 {len(collect_docs())} 个")
    L.append(f"- 索引条目：{len(links)} 条")
    L.append("")
    L.append("## 1. 规模（按字数降序，前 8）")
    L.append("")
    L.append("| 文件 | 字数 |")
    L.append("|---|---|")
    for name, n in sorted(size_rows, key=lambda x: -x[1])[:8]:
        flag = " ⚠️超长" if name in warn_over else ""
        L.append(f"| {name} | {n}{flag} |")
    L.append("")
    L.append("## 2. 关键数字一致性（应一致却出现多值的会在这里暴露）")
    L.append("")
    if num_table:
        for label, per_file in sorted(num_table.items()):
            vals = sorted(set(per_file.values()))
            mark = "❌ 不一致" if len(vals) > 1 else "✅"
            L.append(f"**{label}** {mark}：{', '.join(vals)}")
            for f, v in sorted(per_file.items()):
                L.append(f"  - {f} → {v}")
            L.append("")
    else:
        L.append("（未匹配到可比较的数字）")
        L.append("")
    for lv in ("ERROR", "WARN", "INFO"):
        L.append(f"## {'3' if lv == 'ERROR' else '4' if lv == 'WARN' else '5'}. {lv} 明细（{len(findings[lv])}）")
        L.append("")
        if not findings[lv]:
            L.append("（无）")
            L.append("")
            continue
        grouped = defaultdict(list)
        for sec, txt in findings[lv]:
            grouped[sec].append(txt)
        for sec, items in sorted(grouped.items()):
            L.append(f"### {sec}")
            L.append("")
            for it in items:
                L.append(f"- {it}")
            L.append("")
    L.append("## 6. 建议动作")
    L.append("")
    L.append("1. 修 ERROR：索引对齐、口径矛盾、敏感值明文")
    L.append("2. 补 frontmatter：scope / type / status / source / updated")
    L.append("3. 统一数字：以 L1 权威源为准，改记忆里的旧值")
    L.append("4. 拆超长文件：按 SCHEMA 拆 snapshot / ledger / archive")
    L.append("5. 复查过期项：加 revisit_when 或归档")
    return "\n".join(L) + "\n"

def main():
    out = REPORT
    if "--report" in sys.argv:
        out = sys.argv[sys.argv.index("--report") + 1]
    claude_files = collect_md(CLAUDE_MEM)
    archives = collect_archives()
    wb_files = collect_md(WB_MEM)
    docs = collect_docs()
    all_files = claude_files + archives + wb_files + docs

    size_rows = check_size(all_files)
    links = check_index()
    check_schema(claude_files)
    check_archives(archives)
    num_table = check_numbers(all_files)
    check_topic_overlap(all_files)
    check_conflict_terms(claude_files)
    check_staleness(claude_files)
    check_secrets(all_files)
    check_pointer()

    with open(out, "w", encoding="utf-8") as f:
        f.write(build_report(size_rows, links, num_table))
    print("ERROR=%d WARN=%d INFO=%d -> %s" % (len(findings["ERROR"]), len(findings["WARN"]), len(findings["INFO"]), out))
    return 1 if findings["ERROR"] else 0

if __name__ == "__main__":
    sys.exit(main())
