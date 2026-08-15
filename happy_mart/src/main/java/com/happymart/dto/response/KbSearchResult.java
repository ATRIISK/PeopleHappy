package com.happymart.dto.response;

import java.util.List;

/**
 * 商品知识库检索结果（v1.11，阶段四，见开发文档 §14）
 * <p>
 * RAG 混合检索（关键词 LIKE + 向量召回）的输出：
 * - context：拼好序号的商品信息文本，直接塞进大模型的 system prompt 作为参考资料
 *            （格式如 "[1] 《商品名》 价格 ¥xxx \n 商品名\n 描述..."，AI 回答时标注 [1] 引用）
 * - sources：参考商品列表，前端渲染成可点击的商品卡片
 * <p>
 * record 类型，字段即构造参数。
 */
public record KbSearchResult(
        String context,               // 拼进 prompt 的上下文文本（带 [n] 序号）
        List<ProductSource> sources   // 参考商品列表（前端展示）
) {
}
