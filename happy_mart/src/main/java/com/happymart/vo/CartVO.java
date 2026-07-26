package com.happymart.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 购物车返回值 VO
 * <p>
 * 这个 VO 不只是 cart 表的字段，还有 product 表的商品信息。
 * 因为前端购物车页面要展示：商品名、图片、单价、库存、数量。
 * 这些信息需要联表查询（cart JOIN product）才能拿到。
 * <p>
 * 为什么不直接返回 Cart 实体？
 * 因为 Cart 只有 user_id / product_id / quantity，没有商品名称和价格，
 * 前端收到没法展示。所以必须要一个 VO 来装"购物车 + 商品"的合并数据。
 * <p>
 * 前端 cart.js store 期望的字段：
 * - productId → 商品 ID（前端用它来标识购物车项）
 * - name      → 商品名称
 * - image     → 商品主图
 * - price     → 商品单价
 * - stock     → 商品库存（限制最大购买数量）
 * - quantity  → 这个商品在购物车中的数量
 * - createTime → 加入购物车的时间
 */
@Data
public class CartVO {
    /**
     * 购物车记录 ID（cart 表的主键）
     * 后端内部用，前端页面不使用这个字段
     */
    private Long id;

    /**
     * 商品 ID
     * 前端用这个字段来标识购物车项（不是用 cartId）
     * 切换勾选、修改数量、删除操作都基于 productId
     */
    private Long productId;

    /**
     * 商品名称
     * 来自 product 表的name字段
     */
    private String name;

    /**
     * 商品主图
     * 来自 product 表的 image 字段
     * 前端在购物车表格里显示缩略图
     */
    private String image;

    /**
     * 商品现价
     * 来自 product 表的 price 字段
     * 前端计算小计：price × quantity
     */
    private BigDecimal price;

    /**
     * 商品库存
     * 来自 product 表的 stock 字段
     * 前端 el-input-number 的 max 属性限制最大可购买数量
     */
    private Integer stock;

    /**
     * 购买数量
     * 来自 cart 表的 quantity 字段
     */
    private Integer quantity;

    /**
     * 创建时间（加入购物车的时间）
     * 来自 cart 表的 create_time 字段
     */
    private LocalDateTime createTime;
}



