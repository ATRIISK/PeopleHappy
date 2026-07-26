package com.happymart.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 创建订单请求参数 DTO
 *
 * 前端 POST /api/order/create 时传的 JSON 转成这个对象
 *
 * 必填字段：
 * - addressId → 收货地址
 *
 * 可选字段：
 * - productIds → 要下单的商品 ID 列表
 *   如果传了这个字段，后端只结算购物车中被选中的商品
 *   如果没传（或为空），则结算购物车中全部商品（兼容旧版本）
 *
 * 其他信息（商品数量、单价、总金额）后端从数据库获取
 * 不依赖前端传的数据，防止篡改
 */
@Data
public class OrderCreateDTO {

    /** 收货地址ID（必填） */
    @NotNull(message = "请选择收货地址")
    private Long addressId;

    /**
     * 要下单的商品 ID 列表（可选）
     *
     * 前端勾选了哪几件商品，就把它们的 productId 传过来。
     * 后端收到后只结算这些商品，其他留在购物车中。
     *
     * 为 null 或空列表 → 默认结算全部购物车商品
     */
    private List<Long> productIds;
}
