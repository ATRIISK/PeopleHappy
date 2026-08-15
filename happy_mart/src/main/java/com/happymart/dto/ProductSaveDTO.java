package com.happymart.dto;                          // DTO = Data Transfer Object，用于接收前端传过来的请求参数

import jakarta.validation.constraints.NotBlank;     // @NotBlank → 校验字符串不能为 null 也不能是空串
import jakarta.validation.constraints.NotNull;      // @NotNull → 校验不能为 null
import lombok.Data;                                 // @Data → 自动生成 getter/setter/toString

import java.math.BigDecimal;                        // BigDecimal → 金额类型，保证精度不丢

/**
 * 商品新增/修改请求参数（管理后台用）
 * <p>
 * 前端 POST /api/admin/product/save 时传的 JSON 会转成这个对象。
 * <p>
 * 核心设计：
 * - id 为 null → 新增商品；id 有值 → 修改该商品（同一个接口两种用法）
 * - categoryName 不在 DTO 里：分类名称是冗余字段，由后端根据 categoryId 查分类表补填，
 *   避免前端传脏数据（前端只传 categoryId）
 */
@Data
public class ProductSaveDTO {

    /** 商品ID：null=新增商品，有值=修改该商品 */
    private Long id;

    /** 商品名称（必填） */
    @NotBlank(message = "商品名称不能为空")
    private String name;

    /** 商品描述（可选） */
    private String description;

    /** 现价（必填，BigDecimal 保证金额精度） */
    @NotNull(message = "商品价格不能为空")
    private BigDecimal price;

    /** 原价（划线价，可选） */
    private BigDecimal originalPrice;

    /** 商品主图 URL（列表页用） */
    private String image;

    /** 轮播图列表（JSON 数组字符串，如 ["url1","url2"]） */
    private String images;

    /** 所属分类ID（必填） */
    @NotNull(message = "请选择商品分类")
    private Long categoryId;

    /** 库存（必填） */
    @NotNull(message = "库存不能为空")
    private Integer stock;

    /** 评分 0-5（可选，默认 0） */
    private Double rating;

    /** 状态：0=上架，1=下架（可选，默认 0） */
    private Integer status;
}
