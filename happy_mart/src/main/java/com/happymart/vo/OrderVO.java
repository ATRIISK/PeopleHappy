package com.happymart.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单返回值 VO
 *
 * 包含订单信息和订单项列表
 * 对应前端展示订单需要的数据
 *
 * OrderItemVO 是内部类，在 XML 中用 $ 引用：
 * com.happymart.vo.OrderVO$OrderItemVO
 */
@Data
public class OrderVO {

    /** 订单ID */
    private Long id;

    /** 订单号 */
    private String orderNo;

    /** 订单总金额 */
    private BigDecimal totalAmount;

    /** 订单状态：0待支付 1已支付 2已发货 3已完成 4已取消 */
    private Integer status;

    /** 收货地址ID */
    private Long addressId;

    /**
     * 收货人姓名
     * 联表 address 表查询，下单时 address 的当前值
     */
    private String addressName;

    /**
     * 收货人手机号
     * 联表 address 表查询
     */
    private String addressPhone;

    /**
     * 收货地址详情
     * 格式：省 + 市 + 区/县 + 详细地址
     * 联表 address 表查询
     */
    private String addressDetail;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 订单项列表 */
    private List<OrderItemVO> items;

    /**
     * 订单项 VO（内部类）
     * 每个项包含商品信息和购买数量
     */
    @Data
    public static class OrderItemVO {

        /** 订单项ID */
        private Long id;

        /** 商品ID */
        private Long productId;

        /** 商品名称（联表 product 表获取） */
        private String productName;

        /** 商品主图（联表 product 表获取） */
        private String productImage;

        /** 下单时价格快照 */
        private BigDecimal price;

        /** 购买数量 */
        private Integer quantity;
    }
}
