package com.happymart.dto;                          // DTO = Data Transfer Object，用于接收前端传过来的请求参数

import jakarta.validation.constraints.NotNull;      // @NotNull → 校验不能为 null
import lombok.Data;                                 // @Data → 自动生成 getter/setter/toString

/**
 * 修改订单状态请求参数（管理后台用）
 * <p>
 * 前端 PUT /api/admin/order/status 时传的 JSON 会转成这个对象。
 * 目前管理员只允许「发货」操作（状态 1 已支付 → 2 已发货），
 * 后端会用白名单状态机校验，不是任意状态都能改。
 */
@Data
public class OrderStatusDTO {

    /** 订单ID（必填） */
    @NotNull(message = "订单ID不能为空")
    private Long id;

    /** 目标状态（必填）：目前合法值只有 2（发货） */
    @NotNull(message = "订单状态不能为空")
    private Integer status;
}
