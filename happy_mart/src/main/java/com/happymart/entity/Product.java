package com.happymart.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 商品实体类
 * 映射数据库 product 表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product")
public class Product extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;                             // 商品名称，如"华为 Mate 70 Pro"

    private String description;                      // 商品描述

    private BigDecimal price;                        // 现价（用 BigDecimal 保证精度，不用 Double）

    private BigDecimal originalPrice;                // 原价，用于展示划线价格

    private String image;                            // 商品主图 URL（列表页用）

    private String images;                           // 商品轮播图列表，JSON 数组格式 ["url1","url2"]

    private Long categoryId;                         // 所属分类ID

    private String categoryName;                     // 分类名称（冗余字段，避免每次联表查询）

    private Integer sales;                           // 销量

    private Integer stock;                           // 库存

    private Double rating;                           // 评分（0-5 分）

    private Integer status;                          // 状态：0=上架，1=下架
}
