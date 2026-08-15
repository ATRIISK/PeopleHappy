package com.happymart.config;

import com.happymart.mapper.ProductMapper;
import com.happymart.service.impl.KnowledgeBaseService;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 商品知识库配置类（v1.11，阶段四，见开发文档 §14）
 * <p>
 * 职责：
 * 1. 注册向量存储 SimpleVectorStore（内存向量库，用 DashScope text-embedding-v2 做 embedding）
 * 2. 注册知识库服务 KnowledgeBaseService（RAG 混合检索核心逻辑）
 * 3. 注册 ApplicationRunner：应用启动后自动调用 buildIndex() 构建/加载商品向量索引
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
     * 内存向量存储：SimpleVectorStore 是 Spring AI 内置的简易向量库，
     * 支持 add（写入）/ delete（删除）/ similaritySearch（相似度检索）/ save（持久化到文件）/ load（从文件加载）。
     * 向量化调用 DashScope embedding 模型（text-embedding-v2）。
     */
    @Bean
    public SimpleVectorStore simpleVectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }

    /**
     * 知识库服务：把商品数据源（ProductMapper）+ 向量库 + kb.* 参数组装成服务 Bean
     */
    @Bean
    public KnowledgeBaseService knowledgeBaseService(ProductMapper productMapper,
                                                     EmbeddingModel embeddingModel,
                                                     SimpleVectorStore vectorStore) {
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
