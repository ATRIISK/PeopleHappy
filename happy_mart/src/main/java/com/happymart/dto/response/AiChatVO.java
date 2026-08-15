package com.happymart.dto.response;

import java.util.List;

/**
 * AI 购物助手对话接口返回体（v1.11，阶段四，见开发文档 §14）
 * <p>
 * POST /api/ai/chat 的返回 data：
 * {
 *   "response": "通义千问生成的回答文本",
 *   "sources": [ {productId, title, price, image, url}, ... ]  // 参考商品卡片
 * }
 * <p>
 * record 类型，字段即构造参数。
 */
public record AiChatVO(
        String response,              // AI 生成的回答
        List<ProductSource> sources   // 参考商品列表（可能为空）
) {
}
