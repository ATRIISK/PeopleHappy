package com.happymart.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.happymart.dto.response.KbSearchResult;
import com.happymart.dto.response.ProductSource;
import com.happymart.entity.Product;
import com.happymart.mapper.ProductMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 商品知识库服务（v1.11，阶段四，见开发文档 §14）
 * <p>
 * RAG（检索增强生成）核心：把商品数据变成向量文档存入内存向量库，
 * 用户提问时做"混合检索"（关键词 LIKE + 向量相似度），把命中的商品信息拼进
 * system prompt 交给通义千问，让它基于真实商品数据回答，不靠模型瞎编。
 * <p>
 * 关键方法：
 * - buildIndex()：启动建库（索引文件存在则加载，否则从商品表全量构建）
 * - hybridSearch()：混合检索（关键词召回 + 向量召回 → 去重 → 回表取完整商品 → 组装 context + sources）
 * - upsert() / remove()：商品增删改时增量维护知识库（管理后台调用）
 * <p>
 * 注意：本类由 KnowledgeBaseConfig 以 @Bean 方式创建（不是 @Service），
 * 因为需要把 ProductMapper、EmbeddingModel、SimpleVectorStore、kb.* 配置组装进来。
 * <p>
 * 代码结构复用自 AI 博客项目 D:\ai_blog_show（Posts → 本项目 Product）。
 */
@Slf4j  // Lombok → 自动生成 log 对象（slf4j 日志）
public class KnowledgeBaseService {

    private final ProductMapper productMapper;    // 商品数据源（关键词召回 + 回表取完整商品）
    private final EmbeddingModel embeddingModel;  // 向量模型（text-embedding-v2，理论上由 vectorStore 内部使用）
    private final SimpleVectorStore vectorStore;  // 内存向量库
    private final String indexFilePath;           // 索引文件路径（持久化用）
    private final int keywordTopK;                // 关键词召回条数
    private final int vectorTopK;                 // 向量召回条数
    private final int contextTopN;                // 拼进 prompt 的商品数
    private final int docMaxChars;                // 单商品拼入 prompt 的最大字符数

    public KnowledgeBaseService(ProductMapper productMapper,
                                EmbeddingModel embeddingModel,
                                SimpleVectorStore vectorStore,
                                String indexFilePath,
                                int keywordTopK,
                                int vectorTopK,
                                int contextTopN,
                                int docMaxChars) {
        this.productMapper = productMapper;
        this.embeddingModel = embeddingModel;
        this.vectorStore = vectorStore;
        this.indexFilePath = indexFilePath;
        this.keywordTopK = keywordTopK;
        this.vectorTopK = vectorTopK;
        this.contextTopN = contextTopN;
        this.docMaxChars = docMaxChars;
    }

    /**
     * 构建知识库索引（应用启动时由 ApplicationRunner 调用）
     * <p>
     * 索引文件已存在 → 直接 load（秒级，省一次全量 embedding 调用）
     * 索引文件不存在 → 从商品表全量取【上架商品】（status=0）向量化写入，然后 save 到文件
     */
    public void buildIndex() {
        Path path = Path.of(indexFilePath);
        if (Files.exists(path)) {
            try {
                // 有意取舍（code-review F7）：load 直接加载持久化索引，不与商品表对账。
                // 正常路径下管理后台增删改已通过 upsert/remove 保持索引与 DB 同步；
                // 绕过管理后台直接改 DB（如手工 UPDATE / 重新 seed）会让索引残留"已下架商品"，
                // 但每次启动全量对账要调 N 次付费 embedding，成本太高，故不校验（启动速度优先）。
                vectorStore.load(new File(indexFilePath));
                log.info("商品知识库索引已加载: {}", indexFilePath);
                return;
            } catch (Exception e) {
                log.warn("加载商品知识库索引失败，将重建: {}", e.getMessage());
            }
        }
        // 全量取上架商品（status=0；is_deleted=0 由 MyBatis-Plus 逻辑删除自动过滤）
        List<Product> products = productMapper.selectList(
                new LambdaQueryWrapper<Product>().eq(Product::getStatus, 0));
        if (products == null || products.isEmpty()) {
            log.info("没有上架商品，跳过知识库建库");
            return;
        }
        // 向量化写入：失败（如 API Key 未配置/网络异常）→ 只记 ERROR 不崩，
        // 并且【不保存空索引文件】——否则下次启动 Files.exists() 会把空文件误当
        // "已建库"直接加载并 return，知识库就永远为空了（v1.11 修复）
        try {
            vectorStore.add(products.stream().map(this::toDocument).toList());
            saveIndex();
            log.info("商品知识库索引构建完成，共 {} 件商品", products.size());
        } catch (Exception e) {
            log.error("商品向量化建库失败（embedding 接口不可用或 API Key 未配置）: {}", e.getMessage());
        }
    }

    /**
     * 商品 → 向量文档（一商品一文档，id 格式 "product:{id}"）
     * <p>
     * 向量化的文本内容 = 名称 + 描述 + 价格 + 分类（比只用名称召回更准）。
     * metadata 存商品关键信息，向量召回命中后靠 productId 回表取完整商品。
     */
    private Document toDocument(Product p) {
        String text = buildProductText(p);           // 生成向量化文本（已按 docMaxChars 截断）
        Map<String, Object> meta = new HashMap<>();
        meta.put("productId", p.getId());            // 回表主键
        meta.put("title", p.getName());              // 展示用
        meta.put("price", p.getPrice() == null ? null : p.getPrice().toPlainString()); // 展示用（字符串存，避免 BigDecimal 序列化问题）
        meta.put("image", p.getImage());             // 展示用
        meta.put("url", "/product/" + p.getId());    // 前端跳转
        return new Document("product:" + p.getId(), text, meta);
    }

    /**
     * 组装商品向量化文本：名称 + 描述 + 价格 + 分类，超过 docMaxChars 截断
     */
    private String buildProductText(Product p) {
        StringBuilder sb = new StringBuilder();
        sb.append(p.getName());                      // 商品名称（必填）
        if (p.getDescription() != null && !p.getDescription().isBlank()) {
            sb.append("\n").append(p.getDescription()); // 商品描述
        }
        if (p.getPrice() != null) {
            sb.append("\n价格: ¥").append(p.getPrice().toPlainString()); // 价格
        }
        if (p.getCategoryName() != null && !p.getCategoryName().isBlank()) {
            sb.append("\n分类: ").append(p.getCategoryName()); // 分类名
        }
        String text = sb.toString();
        // 防 token 溢出：超长描述截断（doc-max-chars，yml 配置 500）
        if (text.length() > docMaxChars) {
            text = text.substring(0, docMaxChars);
        }
        return text;
    }

    /**
     * 增量新增/更新：删除旧文档 → 写入新文档 → 存索引
     * <p>
     * 管理后台新增/编辑商品后调用。try-catch 保证 AI 失败不影响管理端业务。
     */
    public void upsert(Product product) {
        if (product == null || product.getId() == null) {
            return;
        }
        try {
            vectorStore.delete(List.of("product:" + product.getId())); // 删旧
            vectorStore.add(List.of(toDocument(product)));             // 加新
            saveIndex();
            log.info("商品知识库增量更新完成: id={}", product.getId());
        } catch (Exception e) {
            log.error("商品知识库增量更新失败（不影响业务）: {}", e.getMessage());
        }
    }

    /**
     * 增量删除：删除该商品的向量文档并存索引
     * <p>
     * 管理后台删除/下架商品后调用。try-catch 保证 AI 失败不影响管理端业务。
     */
    public void remove(Long productId) {
        if (productId == null) {
            return;
        }
        try {
            vectorStore.delete(List.of("product:" + productId));
            saveIndex();
            log.info("商品知识库增量移除完成: id={}", productId);
        } catch (Exception e) {
            log.error("商品知识库增量移除失败（不影响业务）: {}", e.getMessage());
        }
    }

    /** 索引文件写锁（code-review F5）：多管理员并发增删改商品时，防止 vectorStore.save 同时写同一文件导致 JSON 损坏 */
    private final Object saveLock = new Object();

    /**
     * 保存索引到本地文件（目录不存在时先创建）
     * <p>
     * synchronized(saveLock)：upsert/remove 会在管理后台并发触发（如两个管理员同时改不同商品），
     * 不加锁的话两个线程同时 vectorStore.save 写同一文件，可能交错写出损坏的 JSON，
     * 下次启动 load 失败只能全量重建。加锁后同一 JVM 内写文件串行化（本项目单体部署，够用）。
     */
    private void saveIndex() {
        synchronized (saveLock) {
            try {
                File file = new File(indexFilePath);
                File parent = file.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs(); // 目录不存在则创建（如 data/）
                }
                vectorStore.save(file);
            } catch (Exception e) {
                log.error("保存商品知识库索引失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 混合检索：关键词召回 + 向量召回 → 融合排序去重 → 回表取完整商品 → 组装 context 与 sources
     * <p>
     * 为什么"关键词 + 向量"双路混合？
     * - 关键词路：商品名/描述里的专有名词、型号（如"华为 Mate 70"）、品类词（如"运动"）命中精准；
     * - 向量召回：能理解语义（如"适合运动的耳机"能召回描述里含"运动/跑步"的耳机），
     *   弥补关键词死板匹配的不足。两路结果融合后命中更全更准。
     * <p>
     * <b>2026-10-05 A 档修复</b>：原实现有关键词路恒 0 命中的 P0 缺陷 ——
     * 把【完整用户问句】当 keyword 传给 SQL 的 {@code name LIKE '%整句%'}，
     * 商品名不可能包含整句，导致 keyword-top-k 形同虚设、混合检索实际只有向量路生效。
     * 修复为：先把问句切成关键词（extractKeywords），再按词组 OR 召回。
     * 同时把向量 similarity score 真正用起来参与排序（原实现用 LinkedHashSet 插入序，
     * 关键词结果恒在前，score 被完全丢弃），并去掉 context 里重复的价格字段。
     * <p>
     * 返回的 context 是带 [n] 序号的文本（塞进 system prompt），
     * sources 是参考商品列表（前端渲染卡片）。
     */
    public KbSearchResult hybridSearch(String query) {
        // 召回打分表：productId -> 综合得分（RRF 融合，见下）
        Map<Long, Double> rrfScores = new HashMap<>();
        // 关键词命中集合（用于回表后二次校验与去重）
        LinkedHashSet<Long> ids = new LinkedHashSet<>();

        // 1. 关键词召回：先分词，再按词组 OR 匹配（ProductMapper.findForKnowledgeBase）
        // ★ 分词而不是传整句：整句 LIKE 匹配不到商品名（详见方法 javadoc 的 P0 说明）
        // ★ 转义 LIKE 通配符（code-review F6）：用户消息里的 % / _ 会被 MySQL 当通配符
        //   （如"100%纯棉"的 % 匹配所有商品），先转义成 \% / \_ 只按普通字符匹配，
        //   否则关键词召回会退化成"按销量取前 N"，污染 RAG 上下文。
        List<String> keywords = extractKeywords(query);
        if (!keywords.isEmpty()) {
            try {
                // 传 topK * 3 过量召回，把精排留给 Java 侧 scoreKeywordHits()（见 Mapper javadoc）
                List<Product> keywordHits = productMapper.findForKnowledgeBase(keywords, keywordTopK * 3);
                if (keywordHits != null) {
                    // 按相关度打分后排序（商品名命中权重最高），再取 topK 进融合
                    List<Product> scored = scoreKeywordHits(keywordHits, keywords);
                    for (Product p : scored) {
                        ids.add(p.getId());
                    }
                }
            } catch (Exception e) {
                log.warn("商品关键词召回失败: {}", e.getMessage());
            }
        }

        // 2. 向量召回：embedding 相似度检索（Top-K）
        // ★ 修复：原实现丢弃了 similarity score，导致排序完全由 LinkedHashSet 插入顺序决定。
        //   现在取 score 参与 RRF 融合（rank 越靠前贡献越大）。
        try {
            List<Document> docs = vectorStore.similaritySearch(
                    SearchRequest.builder().query(query).topK(vectorTopK).build());
            if (docs != null) {
                // rank 从 1 开始，与 RRF 公式一致
                int rank = 1;
                for (Document d : docs) {
                    Object pid = d.getMetadata().get("productId");
                    if (pid != null) {
                        Long id = Long.valueOf(pid.toString());
                        ids.add(id);
                        // RRF（Reciprocal Rank Fusion）：score += 1 / (K + rank)
                        // K 取 60 是社区常用值，作用是压制头部排名的绝对优势、让多路命中更容易叠加
                        rrfScores.merge(id, 1.0 / (60 + rank), Double::sum);
                        rank++;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("商品向量召回失败: {}", e.getMessage());
        }

        // 两路都没命中 → 返回空（AI 会如实说"暂无相关商品"）
        if (ids.isEmpty()) {
            return new KbSearchResult("", List.of());
        }

        // 3. 按 ID 批量回表取完整商品（保证返回的是真实商品数据，而非向量文档里的旧快照）
        // 注：MyBatis-Plus 3.5.7 的方法名是 selectBatchIds（selectByIds 是 3.5.9+ 的别名）
        List<Product> products;
        try {
            products = productMapper.selectBatchIds(new ArrayList<>(ids));
        } catch (Exception e) {
            log.warn("按 ID 取商品失败: {}", e.getMessage());
            return new KbSearchResult("", List.of());
        }
        Map<Long, Product> productById = products.stream()
                .collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));
        products = ids.stream()
                .map(productById::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // 4. 融合排序（2026-10-05 A 档）：
        //   关键词命中给基础分（按字段权重：名称 > 分类 > 描述），向量命中叠加 RRF 分。
        //   排序不再依赖 LinkedHashSet 插入序——两路分数可比，取综合分降序。
        final Map<Long, Double> keywordScores = buildKeywordScores(products, keywords);
        products.sort(Comparator.comparingDouble(
                (Product p) -> keywordScores.getOrDefault(p.getId(), 0.0)
                        + rrfScores.getOrDefault(p.getId(), 0.0))
                .reversed());

        // 5. 组装 context（带 [1][2]... 序号）与 sources 引用列表
        StringBuilder ctx = new StringBuilder();
        List<ProductSource> sources = new ArrayList<>();
        int i = 1;
        for (Product p : products.stream().limit(contextTopN).toList()) {
            // sources：前端参考商品卡片
            sources.add(new ProductSource(p.getId(), p.getName(), p.getPrice(), p.getImage(), "/product/" + p.getId()));
            // context：拼进 prompt 的参考文本
            // ★ 修复：原来这里额外拼了一次"价格 ¥x"，而 buildProductText 内部已含"价格: ¥x"，
            //   同一信息在 prompt 里出现两遍、白白吃 token。改为只拼 buildProductText 的结果。
            ctx.append("[").append(i).append("] ")
               .append(buildProductText(p))
               .append("\n\n");
            i++;
        }
        return new KbSearchResult(ctx.toString().trim(), sources);
    }

    /**
     * 从用户问句中提取关键词（2026-10-05 A 档新增，P0 修复的核心）
     * <p>
     * 为什么要分词：原实现把整句当 keyword 传 SQL，商品名不可能包含"推荐适合运动的耳机"整句，
     * 召回恒 0 命中。切成词组后，"运动""耳机"能分别命中商品描述与分类名。
     * <p>
     * <b>中文没有空格，必须做二次切分</b>——这是实测踩到的坑：
     * 第一版只按非中英文数字切分，"推荐适合运动的耳机"整串是一个连续 token，
     * 等于没切分，召回照样是 0 命中。所以纯中文串再按【二元组（bigram）】切：
     * "推荐适合运动的耳机" → 推荐/适合/运动/动的/的耳/耳机，能正确切出"运动""耳机"。
     * bigram 是无分词器场景下的经典做法（召回率优先，噪声靠停用词过滤）。
     * <p>
     * 完整规则：
     * <ol>
     *   <li>先按非中英文数字切（覆盖空格与标点）；</li>
     *   <li>英文/数字词直接保留（型号如 "mate70"）；</li>
     *   <li>中文串：长度 2 直接保留；长度 &gt;2 切成 bigram；</li>
     *   <li>丢弃停用词、以及含停用字的 bigram（如"荐一""的耳"）；</li>
     *   <li>做 LIKE 通配符转义（% / _ / \），否则"100%棉"会匹配所有商品；</li>
     *   <li>去重后最多取前 8 个（太多会让 OR 条件过宽、召回噪声变大）。</li>
     * </ol>
     *
     * @param query 用户原始问句
     * @return 关键词列表（可能为空，此时跳过关键词路）
     */
    private List<String> extractKeywords(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        // 1. 按非中英文数字的字符切分（覆盖空白 + 标点）
        String[] tokens = query.split("[^\\u4e00-\\u9fa5a-zA-Z0-9]+");
        Set<String> result = new LinkedHashSet<>();
        for (String token : tokens) {
            if (token == null || token.isBlank()) {
                continue;
            }
            String lower = token.toLowerCase();
            for (String piece : splitToken(lower)) {
                // 2. 停用词过滤（整词）
                if (STOP_WORDS.contains(piece)) {
                    continue;
                }
                // 3. 含停用字的片段直接丢（"荐一""的耳"这类 bigram 噪声）
                if (hasStopChar(piece)) {
                    continue;
                }
                // 4. 长度下限：单字中文噪声大（"跑""鞋"单独召回太宽），但英文/数字型号如 "70" 保留
                if (piece.length() < 2) {
                    continue;
                }
                // 5. LIKE 通配符转义（code-review F6）
                result.add(escapeLike(piece));
                if (result.size() >= 8) {
                    return new ArrayList<>(result);
                }
            }
        }
        return new ArrayList<>(result);
    }

    /**
     * 单个 token 切分：中文串按 bigram 切，英文/数字原样返回（2026-10-05 A 档）
     * <p>
     * 中文没有空格分词，"推荐适合运动的耳机"整串是一个 token，直接当关键词等于没分词。
     * bigam（相邻两字）是无分词器时最实用的切法：能切出"运动""耳机"，
     * 代价是产生"荐一""的耳"等噪声，靠 hasStopChar 过滤。
     */
    private List<String> splitToken(String token) {
        // 纯英文/数字（含型号如 mate70、iphone15）：不切，原样保留
        if (!token.matches("[\\u4e00-\\u9fa5]+")) {
            return List.of(token);
        }
        int len = token.length();
        if (len <= 2) {
            return List.of(token);
        }
        List<String> bigrams = new ArrayList<>(len - 1);
        for (int i = 0; i + 2 <= len; i++) {
            bigrams.add(token.substring(i, i + 2));
        }
        return bigrams;
    }

    /**
     * 判断片段是否含停用字（含则丢弃该片段）
     * <p>
     * 用于过滤 bigram 噪声："一下""的耳""荐一"这类片段本身不是词，
     * 拿去 LIKE 匹配只会引入无关商品。
     */
    private boolean hasStopChar(String piece) {
        for (int i = 0; i < piece.length(); i++) {
            if (STOP_CHARS.indexOf(piece.charAt(i)) >= 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * 关键词召回结果打分与排序（2026-10-05 A 档新增）
     * <p>
     * 权重设计：商品名命中 3 分 > 分类名 2 分 > 描述 1 分。
     * 理由：专有名词与型号基本都出现在商品名里，名字命中最可能是用户要找的那个；
     * 描述文本长、容易蹭到泛化词，权重最低；分类名能兜住只说品类的问句。
     *
     * @param hits     关键词路召回的商品
     * @param keywords 实际使用的关键词列表
     * @return 按分数降序排列的商品列表
     */
    private List<Product> scoreKeywordHits(List<Product> hits, List<String> keywords) {
        Map<Long, Double> scores = new HashMap<>();
        for (Product p : hits) {
            double score = 0;
            String name = lower(p.getName());
            String desc = lower(p.getDescription());
            String cat = lower(p.getCategoryName());
            for (String kw : keywords) {
                if (name.contains(kw)) {
                    score += 3;
                }
                if (cat.contains(kw)) {
                    score += 2;
                }
                if (desc.contains(kw)) {
                    score += 1;
                }
            }
            // 命中词越多越相关；同分时靠销量（SQL 已按销量粗排，这里稳定排序即可）
            scores.put(p.getId(), score);
        }
        List<Product> sorted = new ArrayList<>(hits);
        sorted.sort(Comparator.comparingDouble((Product p) -> scores.getOrDefault(p.getId(), 0.0)).reversed());
        return sorted.stream().limit(keywordTopK).collect(Collectors.toList());
    }

    /**
     * 回表后重新计算关键词得分（用于与向量 RRF 分做融合排序）
     */
    private Map<Long, Double> buildKeywordScores(List<Product> products, List<String> keywords) {
        Map<Long, Double> scores = new HashMap<>();
        if (keywords.isEmpty()) {
            return scores;
        }
        for (Product p : products) {
            double score = 0;
            String name = lower(p.getName());
            String desc = lower(p.getDescription());
            String cat = lower(p.getCategoryName());
            for (String kw : keywords) {
                if (name.contains(kw)) {
                    score += 3;
                }
                if (cat.contains(kw)) {
                    score += 2;
                }
                if (desc.contains(kw)) {
                    score += 1;
                }
            }
            scores.put(p.getId(), score);
        }
        return scores;
    }

    /** null 安全的 lowercase */
    private String lower(String s) {
        return s == null ? "" : s.toLowerCase();
    }

    /**
     * LIKE 通配符转义：把用户输入里的 % 和 _ 转成字面量匹配
     * <p>
     * 不转义的后果："100%纯棉" 的 % 会被 MySQL 当通配符匹配所有行，
     * 关键词召回退化成"按销量取前 N"，把无关商品灌进 RAG 上下文。
     */
    private String escapeLike(String raw) {
        return raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /**
     * 停用词（无检索价值，问句里高频但不该参与 LIKE 匹配）
     */
    private static final Set<String> STOP_WORDS = Set.of(
            "的", "了", "和", "与", "或", "是", "在", "有", "个", "些", "什么", "怎么", "如何",
            "推荐", "有没有", "适合", "我想", "我要", "请问", "帮忙", "一下", "哪些", "哪个",
            "求", "给", "来", "吧", "呢", "吗", "啊", "呀", "哦", "嗯", "这", "那", "都",
            "the", "a", "an", "is", "are", "for", "to", "of", "and", "or", "what", "how",
            "please", "recommend", "me", "i", "want", "need", "any", "some", "can", "you"
    );

    /**
     * 单字停用字符（2026-10-05 A 档新增）
     * <p>
     * 用于过滤 bigram 噪声片段：中文按二元组切会产生"一下""的耳""荐一"这类非词片段，
     * 它们拿去 LIKE 匹配只会引入无关商品。含这些字的片段一律丢弃。
     */
    private static final String STOP_CHARS = "的了和与或在有个些什么怎如求给来吧呢吗啊呀哦嗯这那都我你他她它";
}
