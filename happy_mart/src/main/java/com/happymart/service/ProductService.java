package com.happymart.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.happymart.vo.ProductVO;

import java.util.List;

/**
 * 商品服务接口
 * <p>
 * 这个接口定义了"商品"相关的所有业务方法。
 * Service 接口的作用是：告诉调用方（Controller）"我能做什么"，
 * 具体怎么做在 ProductServiceImpl 里实现。
 * <p>
 * 为什么要有接口？
 * 1. 面向接口编程，Controller 只依赖接口，不依赖实现类
 * 2. 以后想换实现方式（比如加缓存），直接加一个新实现类就行
 */
public interface ProductService {

    /**
     * 分页查询商品列表
     * <p>
     * 这是商品列表页的核心接口。
     * 支持按分类筛选、关键词搜索、多种排序、分页。
     *
     * @param categoryId 分类ID（可选，传 null 就是查全部分类）
     * @param keyword    搜索关键词（可选，按商品名称模糊匹配）
     * @param sortBy     排序方式（可选，不传就是默认综合排序）
     *                   可选值：sales(销量) / price_asc(价格从低到高) /
     *                   price_desc(价格从高到低) / newest(最新上架) / rating(评分)
     * @param page       当前页码（从 1 开始，默认 1）
     * @param size       每页显示多少条（默认 12）
     * @return 分页对象，里面包含 records（当前页数据）和 total（总条数）
     */
    Page<ProductVO> getProductPage(Long categoryId, String keyword, String sortBy, Integer page, Integer size);

    /**
     * 根据商品 ID 查询商品详情
     * <p>
     * 商品详情页调用这个接口。
     * 会返回商品的完整信息，包括多张轮播图。
     *
     * @param id 商品 ID（从 URL 路径里取）
     * @return 商品详情 VO（不含敏感信息）
     * @throws BusinessException 如果商品不存在，抛 NOT_FOUND 异常
     */
    ProductVO getProductById(Long id);

    /**
     * 查询热门商品榜（销量前 8 名）
     * <p>
     * 首页的"热门推荐"区域调用这个接口。
     * 只返回上架的商品，按销量从高到低排。
     *
     * @return 热门商品列表（最多 8 个）
     */
    List<ProductVO> getHotProducts();
}
