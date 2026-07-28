package com.happymart.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 支付流水实体类
 * 映射数据库 payment_log 表
 *
 * ⚠️ 不继承 BaseEntity
 * payment_log 表没有 update_time 和 is_deleted 字段，和 Address/Order 一致
 *
 * 每次收到支付宝异步通知都会插入一条记录（无论验签/处理成功与否），
 * 用于排查支付问题（原始报文都留档）。
 */
@Data
@TableName("payment_log")
public class PaymentLog {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 商户订单号（对应 order.order_no） */
    private String orderNo;

    /** 支付宝交易号（trade_no，支付成功后由支付宝返回） */
    private String transactionId;

    /** 支付方式：'PRECREATE'（支付宝扫码支付，对应 alipay.trade.precreate 接口） */
    private String payType;

    /** 金额，单位：分（和 order.total_amount 的"元"不同，这里统一按分存，便于和历史设计保持一致） */
    private Integer totalFee;

    /** 支付宝交易状态：WAIT_BUYER_PAY / TRADE_SUCCESS / TRADE_FINISHED / TRADE_CLOSED */
    private String tradeState;

    /** 支付宝异步通知原始参数（表单参数拼接后的字符串，便于排查问题） */
    private String notifyRaw;

    /** 创建时间 */
    private LocalDateTime createTime;
}