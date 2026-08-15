package com.happymart.dto.response;

import java.math.BigDecimal;

/**
 * AI 购物助手检索到的参考商品（v1.11，阶段四，见开发文档 §14）
 * <p>
 * 前端 AI 助手回答下方展示"参考商品卡片"时用：
 * - productId + url → 点击跳转商品详情页
 * - title / price / image → 卡片展示
 * <p>
 * record 是 Java 16+ 的简洁类型：字段即构造参数，自动生成 getter/toString/equals。
 * 序列化成 JSON 就是 {"productId":1,"title":"...","price":999.00,"image":"...","url":"/product/1"}
 */
public record ProductSource(
        Long productId,       // 商品 ID
        String title,         // 商品名称
        BigDecimal price,     // 现价（卡片展示）
        String image,         // 商品主图 URL（卡片展示）
        String url            // 前端跳转地址，如 /product/1
) {
}
