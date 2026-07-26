package com.happymart.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单项实体类
 * 映射数据库 order_item 表
 *
 * 一个订单包含多个订单项，每个订单项记录：
 * - 买了哪个商品（productId）
 * - 买了几个（quantity）
 * - 当时什么价格（price，价格快照）
 *
 * 为什么要记录价格快照？
 * 因为商品价格可能会变，但订单里应该保留下单时的价格作为凭证
 */
@Data
@TableName("order_item")
public class OrderItem {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单ID（关联 order 表） */
    private Long orderId;

    /** 商品ID（关联 product 表） */
    private Long productId;

    /** 购买数量 */
    private Integer quantity;

    /** 下单时的价格快照（不是当前价格，是下单那一刻的价格） */
    private BigDecimal price;
}
