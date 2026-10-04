#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""migrate_fm.py — 一次性迁移：为 PeopleHappy L2 记忆条目补齐 SCHEMA v1.0 顶层 frontmatter。
保留原 metadata 块（工具自动生成）不动。仅在原 frontmatter 缺少 scope/type/status/source/updated 时插入。
"""
import io
import os
import re

MEM = r"C:\Users\LEVON\.claude\projects\E--PeopleHappy\memory"

# file -> (scope, type, source列表, updated, description, confidence, valid_from, revisit_when)
SPEC = {
    "user-working-style.md": dict(
        scope="process:collab", type="rule", confidence="high",
        updated="2026-10-05",
        description="ATRI 工作风格硬规则（中文交流/代码英文命名/注释要中文/不汇报进展/大范围改动需明确授权/文档步骤不用箭头），跨项目通用",
        source=["用户长期交互习惯汇总", "D:\\项目解答 4 份材料风格落地 2026-08-17"],
        valid_from="2026-07-22", revisit_when="用户明确表达偏好变化时"),
    "user-profile.md": dict(
        scope="process:collab", type="fact", confidence="medium",
        updated="2026-10-05",
        description="ATRI 画像（Java 全栈初级开发者，西安，在上海求职 Java 实习），6 个项目经历按时间顺序，技术栈偏好与开发习惯",
        source=["用户自述", "跨项目经验汇总"],
        valid_from="2026-07-22", revisit_when="求职状态或项目经历变化时"),
    "product-module.md": dict(
        scope="project:happy-mart/backend", type="decision", confidence="high",
        updated="2026-10-05",
        description="商品与分类模块设计决策（status 0上架1下架与文档原始 DDL 相反、category_name 冗余存储、分类树内存组装、一级分类联动子分类）",
        source=["src/main/java/com/happymart/service/impl/ProductServiceImpl.java",
                "happy_mart/src/main/resources/mapper/ProductMapper.xml"],
        valid_from="2026-07-23", revisit_when="商品字段或检索策略变化时"),
    "cart-module.md": dict(
        scope="project:happy-mart/backend", type="decision", confidence="high",
        updated="2026-10-05",
        description="购物车模块决策（upsert 原子累加替代 FOR UPDATE，累计库存校验、下架拦截、MySQL 存储未用 Redis Hash）",
        source=["src/main/java/com/happymart/service/impl/CartServiceImpl.java",
                "happy_mart/src/main/resources/mapper/CartMapper.xml",
                "commit:427e5b9", "commit:12a7b8c"],
        valid_from="2026-07-26", revisit_when="购物车改 Redis Hash 或加秒杀场景时"),
    "order-address-module.md": dict(
        scope="project:happy-mart/backend", type="decision", confidence="high",
        updated="2026-10-05",
        description="订单与地址模块决策（防超卖为 InnoDB 排他行锁的原子条件更新非乐观锁、状态机条件更新幂等、退款顺序不可颠倒、订单号 18 位时间戳+随机）",
        source=["src/main/java/com/happymart/service/impl/OrderServiceImpl.java",
                "happy_mart/src/main/resources/mapper/OrderMapper.xml",
                "commit:a9d3d70"],
        valid_from="2026-07-27", revisit_when="订单状态机或库存策略变化时"),
    "alipay-payment-module.md": dict(
        scope="project:happy-mart/backend", type="decision", confidence="high",
        updated="2026-10-05",
        description="支付选型决策（微信 V3 需企业资质故改支付宝沙箱，precreate+RSA2+refund+query，金额单位元/分差异、回调表单参数返纯文本）",
        source=["src/main/java/com/happymart/service/impl/AlipayServiceImpl.java",
                "docs/design/众乐电子商城-开发文档.md#§7"],
        valid_from="2026-07-28", revisit_when="切换生产环境或改支付渠道时"),
    "redis-usage-scenarios.md": dict(
        scope="project:happy-mart/infra", type="fact", confidence="high",
        updated="2026-10-05",
        description="Redis 三场景已落地（详情/分类树 @Cacheable + 热门榜 ZSet 定时刷新 + 延时双删 + CacheErrorHandler 降级），环境 D:\\Redis 5.0.14.1 需手动启动",
        source=["src/main/java/com/happymart/config/RedisConfig.java",
                "src/main/java/com/happymart/service/impl/ProductServiceImpl.java"],
        valid_from="2026-08-12", revisit_when="新增缓存场景或换 Redis 客户端时"),
    "rabbitmq-usage-scenarios.md": dict(
        scope="project:happy-mart/infra", type="fact", confidence="high",
        updated="2026-10-05",
        description="RabbitMQ 订单超时取消已落地（原生 TTL+DLX 不依赖插件），改 TTL 必须删旧队列→改代码→重启（队列参数不可变）",
        source=["src/main/java/com/happymart/config/RabbitMQConfig.java",
                "src/main/java/com/happymart/mq/OrderTimeoutConsumer.java"],
        valid_from="2026-08-03", revisit_when="改 TTL、换插件或新增 MQ 场景时"),
    "maven-config.md": dict(
        scope="project:happy-mart/infra", type="fact", confidence="medium",
        updated="2026-10-05",
        description="构建环境（IDEA 自带 Maven 3.9.11 + 阿里云镜像已配 C:\\Users\\LEVON\\.m2\\settings.xml；环境变量只在 Git Bash 生效，IDEA 终端需另设）",
        source=["C:\\Users\\LEVON\\.m2\\settings.xml"],
        valid_from="2026-07-22", revisit_when="升级 JDK 或换构建工具时"),
    "project-history.md": dict(
        scope="process:collab", type="fact", confidence="medium",
        updated="2026-10-05",
        description="ATRI 六个项目历程时间线（签到打卡→苍穹外卖→保康安医疗→SPEED 失物招领→AI 博客→众乐商城）与跨项目模式对照表",
        source=["跨项目经验汇总"],
        valid_from="2026-07-22", revisit_when="新增项目或项目下线时"),
    "interview-prep-docs.md": dict(
        scope="process:interview", type="fact", confidence="high",
        updated="2026-10-05",
        description="面试材料目录 D:\\项目解答（4 份 txt 已剔除 AI 内容，接口文档 docx 用户要求不同步）；材料风格要求名词展开说清含义、步骤不用箭头",
        source=["D:\\项目解答"],
        valid_from="2026-08-17", revisit_when="准备新一轮面试材料时"),
}


def migrate(fname, spec):
    path = os.path.join(MEM, fname)
    text = io.open(path, encoding="utf-8").read()
    if not text.startswith("---"):
        print("SKIP (no frontmatter):", fname)
        return
    end = text.find("\n---", 3)
    if end == -1:
        print("SKIP (unterminated):", fname)
        return
    old_fm = text[3:end]
    body = text[end + 4:]

    if "scope:" in old_fm:
        print("SKIP (already migrated):", fname)
        return

    # 拆出 metadata 块原文（保留缩进），其余视为旧 name/description
    m = re.search(r"^metadata:[ \t]*\n((?:[ \t]+.*\n?)*)", old_fm, re.M)
    meta_block = ""
    if m:
        meta_block = m.group(0).rstrip("\n")

    src_lines = "\n".join('  - "%s"' % s for s in spec["source"])
    lines = []
    lines.append("name: %s" % fname[:-3])
    lines.append("description: %s" % spec["description"])
    lines.append("scope: %s" % spec["scope"])
    lines.append("type: %s" % spec["type"])
    lines.append("status: active")
    lines.append("confidence: %s" % spec["confidence"])
    lines.append("source:")
    lines.append(src_lines)
    if spec.get("valid_from"):
        lines.append("valid_from: %s" % spec["valid_from"])
    if spec.get("revisit_when"):
        lines.append('revisit_when: "%s"' % spec["revisit_when"])
    lines.append("updated: %s" % spec["updated"])
    if meta_block:
        lines.append(meta_block)

    new_text = "---\n" + "\n".join(lines) + "\n---\n" + body
    io.open(path, "w", encoding="utf-8").write(new_text)
    print("OK:", fname)


if __name__ == "__main__":
    for f, s in SPEC.items():
        try:
            migrate(f, s)
        except Exception as e:
            print("FAIL:", f, e)
