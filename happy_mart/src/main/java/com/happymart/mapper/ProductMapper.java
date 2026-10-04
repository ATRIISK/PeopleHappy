package com.happymart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.happymart.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 商品 Mapper 接口
 */
@Mapper
public interface ProductMapper extends BaseMapper<Product> {

    /**
     * AI 购物助手关键词召回（v1.11 阶段四；2026-10-05 A 档修复 P0）
     * <p>
     * 按商品名 / 描述 / 分类名 LIKE 检索【上架】商品（is_deleted=0 AND status=0），
     * 多个关键词之间是【OR】关系（任一词命中即召回），按销量降序粗排后截断。
     * 用于 RAG 混合检索的关键词路召回（配合向量召回），SQL 见 ProductMapper.xml。
     * <p>
     * <b>2026-10-05 修复</b>：原实现接收单个 keyword（传的是完整用户问句），
     * SQL 用 {@code name LIKE '%整句%'} 匹配 —— 商品名不可能包含整句，
     * 导致召回恒 0 命中、{@code kb.keyword-top-k} 形同虚设，"混合检索"实际只有向量路生效。
     * 改为接收分词后的词组列表后，型号类专有名词（"Mate 70"）与品类词（"运动"）都能命中。
     * <p>
     * 精排（字段权重 + 命中词数）不在 SQL 里做，放在 KnowledgeBaseService.scoreKeywordHits()，
     * 便于规则调优与单元测试，也避免 SQL 中写死 keywords[n] 下标。
     *
     * @param keywords 分词后的关键词列表（已由调用方做 LIKE 通配符转义与长度过滤）；为空时不调用本方法
     * @param topK     最多返回条数（yml kb.keyword-top-k，建议传过量值由 Java 侧精排后截断）
     * @return 命中的上架商品列表
     */
    List<Product> findForKnowledgeBase(@Param("keywords") List<String> keywords, @Param("topK") int topK);
}
