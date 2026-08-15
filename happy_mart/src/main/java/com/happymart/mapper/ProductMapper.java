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
     * AI 购物助手关键词召回（v1.11，阶段四，见开发文档 §14）
     * <p>
     * 按商品名/描述 LIKE 检索【上架】商品（is_deleted=0 AND status=0），按销量降序取前 topK 条。
     * 用于 RAG 混合检索的关键词路召回（配合向量召回），SQL 见 ProductMapper.xml。
     *
     * @param keyword 用户问题原文（直接 LIKE 匹配，不做分词）
     * @param topK    最多返回条数（yml kb.keyword-top-k）
     * @return 命中的上架商品列表
     */
    List<Product> findForKnowledgeBase(@Param("keyword") String keyword, @Param("topK") int topK);
}
