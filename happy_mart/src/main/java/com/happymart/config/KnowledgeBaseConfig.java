package com.happymart.config;

import com.happymart.mapper.ProductMapper;
import com.happymart.service.impl.KnowledgeBaseService;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 商品知识库配置类（v1.11，阶段四，见开发文档 §14）
 * <p>
 * 职责：
 * 1. 组装知识库服务 KnowledgeBaseService（RAG 检索核心逻辑）
 * 2. 注册 ApplicationRunner：应用启动后自动调用 buildIndex() 构建/加载商品向量索引
 * <p>
 * <b>2026-11-01 适配智谱 GLM</b>：切换到智谱开放平台后，该账号没有 embedding 额度
 * （embedding-2 / embedding-3 均返回「1113 余额不足或无可用资源包」），
 * 因此 application.yml 里关掉了 embedding 自动配置。本类改为用 ObjectProvider 取 EmbeddingModel：
 * 取得到就建 SimpleVectorStore 走混合检索，取不到就把 vectorStore 置空，
 * 知识库退化为纯关键词召回（分词 + LIKE 召回 + 权重排序），AI 对话链路不受影响。
 * 充值后把 embedding.enabled 改回 true，下面这段逻辑自动恢复混合检索，无需改代码。
 * <p>
 * kb.* 五个配置项在 application.yml 里定义：
 * index-file-path 索引文件路径、keyword-top-k 关键词召回数、vector-top-k 向量召回数、
 * context-top-n 拼 prompt 的商品数、doc-max-chars 单商品截断字符数。
 * <p>
 * 代码结构复用自 AI 博客项目 D:\ai_blog_show（知识源 Posts → 本项目 Product）。
 */
@Configuration
public class KnowledgeBaseConfig {

    @Value("${kb.index-file-path}")   private String indexFilePath;  // 向量索引文件路径（SimpleVectorStore 持久化）
    @Value("${kb.keyword-top-k}")     private int keywordTopK;       // 关键词召回条数
    @Value("${kb.vector-top-k}")      private int vectorTopK;        // 向量召回条数
    @Value("${kb.context-top-n}")     private int contextTopN;       // 拼进 prompt 的上下文商品数
    @Value("${kb.doc-max-chars}")     private int docMaxChars;       // 单个商品拼入 prompt 的最大字符数

    /**
     * 知识库服务：把商品数据源（ProductMapper）+ 向量库 + kb.* 参数组装成服务 Bean
     * <p>
     * 向量库在这里按需创建，不再单独注册成 Bean：
     * embedding 可用时才 new SimpleVectorStore，不可用时传 null，
     * KnowledgeBaseService 内部对 null 做了保护（跳过建库/向量召回，只走关键词路）。
     * 这样不依赖 @ConditionalOnBean 在普通配置类里的解析顺序，行为确定。
     */
    @Bean
    public KnowledgeBaseService knowledgeBaseService(ProductMapper productMapper,
                                                     ObjectProvider<EmbeddingModel> embeddingModels) {
        EmbeddingModel embeddingModel = embeddingModels.getIfAvailable();   // 没配 embedding 时为 null
        SimpleVectorStore vectorStore = embeddingModel == null
                ? null
                : SimpleVectorStore.builder(embeddingModel).build();
        return new KnowledgeBaseService(productMapper, embeddingModel, vectorStore,
                indexFilePath, keywordTopK, vectorTopK, contextTopN, docMaxChars);
    }

    /**
     * 启动钩子：应用启动完成后自动构建商品知识库索引。
     * 索引文件已存在则直接加载（秒级），否则从商品表全量构建（首次启动，需要调 embedding 接口）。
     * 若 API Key 未配置导致建库失败，会被 KnowledgeBaseService 内部 try-catch 吞掉，不影响系统启动。
     */
    @Bean
    public ApplicationRunner knowledgeBaseInitializer(KnowledgeBaseService knowledgeBaseService) {
        return args -> knowledgeBaseService.buildIndex();
    }
}
