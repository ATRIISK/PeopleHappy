package com.happymart.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.happymart.common.result.Result;
import com.happymart.service.ProductService;
import com.happymart.vo.ProductVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品 Controller
 * 商品浏览不需要登录，不加 @Auth
 */
@Slf4j
@RestController
@RequestMapping("/api/product")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    /**
     * 商品分页查询
     * GET /api/product/list?categoryId=1&keyword=华为&sortBy=sales&page=1&size=12
     */
    @GetMapping("/list")
    public Result<Page<ProductVO>> getProductPage(
            @RequestParam(required = false) Long categoryId,   // 分类ID，可选
            @RequestParam(required = false) String keyword,     // 搜索关键词，可选
            @RequestParam(required = false) String sortBy,      // 排序方式，可选
            @RequestParam(defaultValue = "1") Integer page,     // 页码，默认第1页
            @RequestParam(defaultValue = "12") Integer size) {  // 每页条数，默认12
        log.info("商品分页请求: categoryId={}, keyword={}, sortBy={}, page={}, size={}",
                categoryId, keyword, sortBy, page, size);
        Page<ProductVO> result = productService.getProductPage(
                categoryId, keyword, sortBy, page, size);
        return Result.success(result);
    }

    /**
     * 查询单个商品详情，后续可加 Redis 缓存
     * GET /api/product/detail/1
     */
    @GetMapping("/detail/{id}")
    public Result<ProductVO> getProductById(@PathVariable Long id) {
        log.info("商品详情请求: id={}", id);
        ProductVO productVO = productService.getProductById(id);
        return Result.success(productVO);
    }
}