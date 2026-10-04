package com.happymart.service.impl;

import com.happymart.dto.response.KbSearchResult;
import com.happymart.entity.Product;
import com.happymart.mapper.ProductMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * KnowledgeBaseService 单元测试（纯 Mockito，不启动 Spring、不连数据库/向量库/embedding 接口）
 *
 * <p>背景（2026-10-05 A 档 P0 修复）：这套测试是为了守住"关键词召回恒 0 命中"这个已修复的缺陷。
 * 修复前 {@code hybridSearch} 把完整用户问句当 keyword 传给 SQL 的 {@code name LIKE '%整句%'}，
 * 商品名不可能包含整句 → 关键词路恒 0 命中，"混合检索"实际只有向量路生效。
 * 修复后改为「先分词 → 按词组 OR 召回 → 相关度打分 → 与向量 RRF 分融合排序」。
 *
 * <p>覆盖的规则：
 * <ul>
 *   <li>分词：停用词过滤、长度过滤、LIKE 通配符转义、去重与数量上限</li>
 *   <li>关键词召回：传词组列表而非整句；分词后确实能命中（回归 P0）</li>
 *   <li>融合排序：商品名命中权重 &gt; 分类名 &gt; 描述；向量 RRF 分参与排序</li>
 *   <li>上下文组装：不再重复拼价格；两路都空时返回空结果不 NPE</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("KnowledgeBaseService · RAG 混合检索（2026-10-05 A 档 P0 修复）")
class KnowledgeBaseServiceTest {

    @Mock
    private ProductMapper productMapper;
    @Mock
    private EmbeddingModel embeddingModel;
    @Mock
    private SimpleVectorStore vectorStore;

    // keywordTopK=5, vectorTopK=5, contextTopN=5, docMaxChars=500（与 application.yml 一致）
    private KnowledgeBaseService kb;

    @BeforeEach
    void setUp() {
        kb = new KnowledgeBaseService(productMapper, embeddingModel, vectorStore,
                "./data/test_kb_index.json", 5, 5, 5, 500);
    }

    // ---------- 工具方法 ----------

    /** 造一个上架商品 */
    private Product product(Long id, String name, String desc, String cat, String price) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setDescription(desc);
        p.setCategoryName(cat);
        p.setPrice(new BigDecimal(price));
        p.setImage("/upload/pic.jpg");
        p.setStatus(0);
        p.setSales(100);
        return p;
    }

    /** 造一个向量命中文档 */
    private Document doc(Long productId) {
        return new Document("product:" + productId, "文本", Map.of("productId", productId));
    }

    // ---------- 分词规则 ----------

    @Test
    @DisplayName("分词：整句被切成词组，且不再原样传整句（回归 P0）")
    void keywordRecallShouldPassTokenizedList() {
        // 关键词路召回"运动耳机"，向量路空
        when(productMapper.findForKnowledgeBase(anyList(), anyInt()))
                .thenReturn(List.of(product(1L, "运动耳机", "适合跑步", "运动户外", "99")));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        when(productMapper.selectBatchIds(anyList())).thenReturn(List.of(product(1L, "运动耳机", "适合跑步", "运动户外", "99")));

        kb.hybridSearch("推荐适合运动的耳机");

        // 捕获传给 Mapper 的关键词列表
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(productMapper).findForKnowledgeBase(captor.capture(), anyInt());
        List<String> kws = captor.getValue();

        assertNotNull(kws);
        assertFalse(kws.isEmpty(), "分词后不应为空");
        // 关键断言：绝不能是完整问句
        assertFalse(kws.contains("推荐适合运动的耳机"), "不能把整句当关键词传下去（这是 P0 根因）");
        // 中文串按 bigram 切，应能切出「运动」「耳机」这两个可匹配商品名的词
        assertTrue(kws.contains("运动"), "应切出『运动』，实得: " + kws);
        assertTrue(kws.contains("耳机"), "应切出『耳机』，实得: " + kws);
        // 含停用字的 bigram 噪声应被过滤（"荐一""的耳"之类）
        assertFalse(kws.contains("的耳"), "含停用字『的』的片段应被过滤");
    }

    @Test
    @DisplayName("分词：英文型号串不切分，原样保留")
    void shouldKeepAlphanumericTokenIntact() {
        when(productMapper.findForKnowledgeBase(anyList(), anyInt()))
                .thenReturn(List.of(product(3L, "华为 Mate 70", "旗舰手机", "手机通讯", "6999")));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        when(productMapper.selectBatchIds(anyList()))
                .thenReturn(List.of(product(3L, "华为 Mate 70", "旗舰手机", "手机通讯", "6999")));

        kb.hybridSearch("有没有华为 mate70 的手机");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(productMapper).findForKnowledgeBase(captor.capture(), anyInt());
        // mate70 是型号，不能被切开
        assertTrue(captor.getValue().contains("mate70"), "型号应原样保留，实得: " + captor.getValue());
    }

    @Test
    @DisplayName("分词：LIKE 通配符被转义（防 100%棉 匹配所有商品）")
    void keywordsShouldEscapeLikeWildcards() {
        when(productMapper.findForKnowledgeBase(anyList(), anyInt()))
                .thenReturn(List.of(product(2L, "纯棉T恤", "100%纯棉", "服饰内衣", "59")));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        when(productMapper.selectBatchIds(anyList())).thenReturn(List.of(product(2L, "纯棉T恤", "100%纯棉", "服饰内衣", "59")));

        kb.hybridSearch("100%纯棉的T恤");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(productMapper).findForKnowledgeBase(captor.capture(), anyInt());
        for (String kw : captor.getValue()) {
            assertFalse(kw.contains("%"), "% 必须被转义，实得: " + kw);
            assertFalse(kw.contains("_"), "_ 必须被转义，实得: " + kw);
        }
    }

    @Test
    @DisplayName("分词：纯停用词问句跳过关键词路，不白跑 SQL")
    void shouldSkipKeywordRecallWhenAllStopWords() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        // 每个片段都含停用字或本身就是停用词 → 关键词列表为空
        KbSearchResult r = kb.hybridSearch("我 的 了 呢");

        // 全是停用词 → 关键词列表为空 → 不应调 Mapper 的关键词召回
        verify(productMapper, never()).findForKnowledgeBase(anyList(), anyInt());
        assertEquals("", r.context());
        assertTrue(r.sources().isEmpty());
    }

    // ---------- 融合排序 ----------

    @Test
    @DisplayName("融合排序：商品名命中排在描述命中之前（关键词权重生效）")
    void keywordScoreShouldPreferNameMatch() {
        // id=1 只有描述命中；id=2 商品名命中
        Product descOnly = product(1L, "蓝牙音箱", "适合运动时听歌", "数码电器", "199");
        Product nameHit = product(2L, "运动耳机", "蓝牙无线", "数码电器", "299");
        when(productMapper.findForKnowledgeBase(anyList(), anyInt())).thenReturn(List.of(descOnly, nameHit));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        // 回表顺序故意打乱，验证排序不依赖回表顺序
        when(productMapper.selectBatchIds(anyList())).thenReturn(List.of(descOnly, nameHit));

        KbSearchResult r = kb.hybridSearch("运动耳机");

        assertEquals(2, r.sources().size());
        // [1] 应是商品名命中的 id=2
        assertEquals(2L, r.sources().get(0).productId(), "商品名命中应排在前");
        assertTrue(r.context().contains("[1]"), "context 应带序号");
    }

    @Test
    @DisplayName("融合排序：向量 RRF 分参与排序（不再是插入序决定）")
    void vectorRrfScoreShouldAffectOrder() {
        // 关键词路只命中 id=10；向量路把 id=10 排在第 2 位
        Product kwHit = product(10L, "运动耳机", "运动耳机", "数码电器", "299");
        when(productMapper.findForKnowledgeBase(anyList(), anyInt())).thenReturn(List.of(kwHit));
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(doc(99L), doc(10L)));  // id=10 排第 2
        when(productMapper.selectBatchIds(anyList())).thenReturn(List.of(kwHit));

        KbSearchResult r = kb.hybridSearch("运动耳机");

        // id=10 两路都命中 → 应保留并返回（不被过滤掉）
        assertEquals(1, r.sources().size());
        assertEquals(10L, r.sources().get(0).productId());
    }

    @Test
    @DisplayName("融合排序：多路命中同一商品只出现一次（去重）")
    void shouldDeduplicateAcrossTwoChannels() {
        Product p = product(7L, "运动耳机", "适合跑步", "运动户外", "299");
        when(productMapper.findForKnowledgeBase(anyList(), anyInt())).thenReturn(List.of(p));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc(7L)));
        when(productMapper.selectBatchIds(anyList())).thenReturn(List.of(p));

        KbSearchResult r = kb.hybridSearch("运动耳机");

        assertEquals(1, r.sources().size(), "同一商品被两路召回时只应出现一次");
    }

    // ---------- 上下文组装 ----------

    @Test
    @DisplayName("上下文：价格只出现一次（修复重复拼字段省 token）")
    void contextShouldNotDuplicatePrice() {
        Product p = product(1L, "运动耳机", "适合跑步", "运动户外", "299");
        when(productMapper.findForKnowledgeBase(anyList(), anyInt())).thenReturn(List.of(p));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        when(productMapper.selectBatchIds(anyList())).thenReturn(List.of(p));

        KbSearchResult r = kb.hybridSearch("运动耳机");

        String ctx = r.context();
        int count = ctx.split("299", -1).length - 1;
        // buildProductText 里"价格: ¥299"出现 1 次；修复前外面还拼一次"价格 ¥299" → 共 2 次
        assertEquals(1, count, "价格应在 context 中只出现一次，实际出现 " + count + " 次；context=" + ctx);
    }

    @Test
    @DisplayName("上下文：两路都空时返回空结果且不抛异常")
    void shouldReturnEmptyWhenNoHit() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        // 关键词路可能命中（如"运动"），所以让它返回空
        when(productMapper.findForKnowledgeBase(anyList(), anyInt())).thenReturn(List.of());

        KbSearchResult r = kb.hybridSearch("完全不存在的东西xyz");

        assertEquals("", r.context());
        assertTrue(r.sources().isEmpty());
    }

    @Test
    @DisplayName("健壮性：向量召回抛异常时降级，不影响关键词路结果")
    void shouldDegradeWhenVectorStoreFails() {
        Product p = product(1L, "运动耳机", "适合跑步", "运动户外", "299");
        when(productMapper.findForKnowledgeBase(anyList(), anyInt())).thenReturn(List.of(p));
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenThrow(new RuntimeException("embedding 接口不可用"));
        when(productMapper.selectBatchIds(anyList())).thenReturn(List.of(p));

        KbSearchResult r = kb.hybridSearch("运动耳机");

        // 关键词路仍应返回结果（这正是修复 P0 的价值：不再单点依赖向量路）
        assertEquals(1, r.sources().size());
    }

    @Test
    @DisplayName("健壮性：关键词路抛异常时降级，不影响向量路结果")
    void shouldDegradeWhenKeywordSqlFails() {
        when(productMapper.findForKnowledgeBase(anyList(), anyInt()))
                .thenThrow(new RuntimeException("SQL 执行失败"));
        Product v = product(5L, "向量命中商品", "描述", "分类", "88");
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc(5L)));
        when(productMapper.selectBatchIds(anyList())).thenReturn(List.of(v));

        KbSearchResult r = kb.hybridSearch("某个问题");

        assertEquals(1, r.sources().size());
        assertEquals(5L, r.sources().get(0).productId());
    }
}
