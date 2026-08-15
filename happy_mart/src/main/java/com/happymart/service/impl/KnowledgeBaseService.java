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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
     * 混合检索：关键词召回 + 向量召回 → 合并去重 → 回表取完整商品 → 组装 context 与 sources
     * <p>
     * 为什么"关键词 + 向量"双路混合？
     * - 关键词 LIKE：商品名/描述里的专有名词、型号（如"华为 Mate 70"）命中精准；
     * - 向量召回：能理解语义（如"适合运动的耳机"能召回描述里含"运动/跑步"的耳机），
     *   弥补关键词死板匹配的不足。两路结果去重合并，命中更全更准。
     * <p>
     * 返回的 context 是带 [n] 序号的文本（塞进 system prompt），
     * sources 是参考商品列表（前端渲染卡片）。
     */
    public KbSearchResult hybridSearch(String query) {
        // LinkedHashSet：去重 + 保持插入顺序（关键词结果在前）
        LinkedHashSet<Long> ids = new LinkedHashSet<>();

        // 1. 关键词召回：SQL LIKE 商品名/描述（ProductMapper.findForKnowledgeBase，只查上架商品）
        // ★ 转义 LIKE 通配符（code-review F6）：用户消息里的 % / _ 会被 MySQL 当通配符
        //   （如"100%纯棉"的 % 匹配所有商品），先转义成 \% / \_ 只按普通字符匹配，
        //   否则关键词召回会退化成"按销量取前 N"，污染 RAG 上下文。
        try {
            String likeEscaped = query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            List<Product> keywordHits = productMapper.findForKnowledgeBase(likeEscaped, keywordTopK);
            if (keywordHits != null) {
                keywordHits.forEach(p -> ids.add(p.getId()));
            }
        } catch (Exception e) {
            log.warn("商品关键词召回失败: {}", e.getMessage());
        }

        // 2. 向量召回：embedding 相似度检索（Top-K）
        try {
            List<Document> docs = vectorStore.similaritySearch(
                    SearchRequest.builder().query(query).topK(vectorTopK).build());
            if (docs != null) {
                docs.forEach(d -> {
                    Object pid = d.getMetadata().get("productId");
                    if (pid != null) {
                        ids.add(Long.valueOf(pid.toString()));
                    }
                });
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
        // ★ 按相关性重排（code-review F3）：MySQL IN 查询返回顺序【不保证】与传入 id 顺序一致
        //   （代码库 ProductServiceImpl.convertToVOByOrder 已踩过此坑：'IN 查询返回顺序不保证和传入顺序一致'）。
        //   这里按 ids（LinkedHashSet，关键词结果在前、相关性高的在前）重排，
        //   保证后面 limit(contextTopN) 截断和 [n] 编号始终按"相关性"而非"主键顺序"。
        Map<Long, Product> productById = products.stream()
                .collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));
        products = ids.stream()
                .map(productById::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // 4. 组装 context（带 [1][2]... 序号）与 sources 引用列表
        StringBuilder ctx = new StringBuilder();
        List<ProductSource> sources = new ArrayList<>();
        int i = 1;
        for (Product p : products.stream().limit(contextTopN).toList()) {
            // sources：前端参考商品卡片
            sources.add(new ProductSource(p.getId(), p.getName(), p.getPrice(), p.getImage(), "/product/" + p.getId()));
            // context：拼进 prompt 的参考文本
            String text = buildProductText(p);
            ctx.append("[").append(i).append("] 《").append(p.getName()).append("》")
               .append(" 价格 ¥").append(p.getPrice() == null ? "?" : p.getPrice().toPlainString())
               .append("\n").append(text).append("\n\n");
            i++;
        }
        return new KbSearchResult(ctx.toString().trim(), sources);
    }
}
