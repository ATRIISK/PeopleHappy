package com.happymart.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商品返回值 VO
 * 返回给前端，images 从 JSON 字符串转为 List<String>
 */
@Data
public class ProductVO {

    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private BigDecimal originalPrice;
    private String image;                // 主图
    private List<String> images;         // 轮播图列表（由 JSON 字符串转换而来）
    private Long categoryId;
    private String categoryName;
    private Integer sales;
    private Integer stock;
    private Double rating;
    private Integer status;
}