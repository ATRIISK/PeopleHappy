package com.happymart.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.happymart.vo.ProductVO;

/**
 * 商品服务接口
 */
public interface ProductService {

    /**
     * 分页查询商品
     * @param categoryId 分类ID(可选,传null查全部)
     * @param keyword 关键词(可选,按名称模糊搜索)
     * @param sortBy 排序方式:sales/price_asc/price_desc/newest/rating
     * @param page 页码,从1开始
     * @param size 每页条数
     */
    Page<ProductVO> getProductPage(Long categoryId, String keyword, String sortBy, Integer page, Integer size);

    /**
     * 根据ID查询商品详情
     */
    ProductVO getProductById(Long id);
}
