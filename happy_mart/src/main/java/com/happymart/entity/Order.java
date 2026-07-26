package com.happymart.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单实体类
 * 映射数据库 order 表
 *
 * ⚠️ 不继承 BaseEntity
 * order 表没有 update_time 和 is_deleted 字段
 *
 * ⚠️ 表名用反引号包裹
 * order 是 MySQL 关键字，不加反引号会报错
 */
@Data
@TableName("`order`")
public class Order {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单号（唯一，18位：yyyyMMddHHmmss + 4位随机） */
    private String orderNo;

    /** 用户ID */
    private Long userId;

    /** 订单总金额 */
    private BigDecimal totalAmount;

    /** 订单状态：0待支付 1已支付 2已发货 3已完成 4已取消 */
    private Integer status;

    /** 收货地址ID */
    private Long addressId;

    /** 微信支付交易号（支付成功后回填） */
    private String transactionId;

    /** 微信预支付ID（调用统一下单后回填） */
    private String prepayId;

    /** 支付时间 */
    private LocalDateTime payTime;

    /** 创建时间 */
    private LocalDateTime createTime;
}
