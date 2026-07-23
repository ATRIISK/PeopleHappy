package com.happymart.controller;

import com.happymart.common.result.Result;
import com.happymart.service.CategoryService;
import com.happymart.vo.CategoryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商品分类 Controller
 * 分类接口通常不需要登录，所以不加 @Auth 注解
 */
@Slf4j
@RestController
@RequestMapping("/api/category")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    /**
     * 获取全部分类树
     * 返回一级分类列表，每个分类中带 children 子分类
     */
    @GetMapping("/tree")
    public Result<List<CategoryVO>> getCategoryTree() {
        log.info("请求全部分类树");
        List<CategoryVO> tree = categoryService.getCategoryTree();
        return Result.success(tree);
    }
}