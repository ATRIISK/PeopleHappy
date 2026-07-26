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

import java.util.List;

/**
 * 商品 Controller
 * <p>
 * Controller 层只做三件事：
 * 1. 接收前端传来的参数
 * 2. 调用 Service 层处理业务
 * 3. 把结果包装成统一格式返回给前端
 * <p>
 * 这些接口都是给用户浏览商品的，不需要登录，所以不加 @Auth 注解。
 * 加了 @Auth 的话，没登录的用户就看不到商品了，显然不合理。
 * <p>
 * 路径前缀：/api/product
 * 比如商品列表就是 GET /api/product/list
 */
@Slf4j  // Lombok → 自动生成 log 变量，代码里直接 log.info() 打日志
@RestController  // 标记这是一个 Controller，所有方法返回 JSON（不返回页面）
@RequestMapping("/api/product")  // 这个类里所有接口都以 /api/product 开头
@RequiredArgsConstructor  // Lombok → 为 final 字段自动生成构造器，不用手写 @Autowired
public class ProductController {

    /**
     * 注入商品 Service
     * final 表示这个字段在对象创建后不能改
     * @RequiredArgsConstructor 会自动生成构造器，Spring 通过构造器把 ProductService 传进来
     */
    private final ProductService productService;

    /**
     * 商品分页查询
     * <p>
     * 请求方式：GET
     * URL 示例：/api/product/list?categoryId=9&keyword=华为&sortBy=sales&page=1&size=12
     * <p>
     * 参数说明（全部可选）：
     * - categoryId：分类ID，不传就查全部分类
     * - keyword：搜索关键词，按商品名称模糊匹配
     * - sortBy：排序方式（sales/price_asc/price_desc/newest/rating）
     * - page：页码，默认第 1 页
     * - size：每页条数，默认 12 条
     * <p>
     * 返回格式：Result<Page<ProductVO>>
     * 前端从 data.records 拿商品列表，从 data.total 拿总数
     */
    @GetMapping("/list")
    public Result<Page<ProductVO>> getProductPage(
            @RequestParam(required = false) Long categoryId,   // 分类ID（?categoryId=9）
            @RequestParam(required = false) String keyword,     // 搜索关键词（?keyword=华为）
            @RequestParam(required = false) String sortBy,      // 排序方式（?sortBy=sales）
            @RequestParam(defaultValue = "1") Integer page,     // 页码（?page=2），默认第1页
            @RequestParam(defaultValue = "12") Integer size) {  // 每页条数（?size=24），默认12条
        log.info("商品分页请求: categoryId={}, keyword={}, sortBy={}, page={}, size={}",
                categoryId, keyword, sortBy, page, size);

        // 调用 Service 层，传入参数获取分页结果
        Page<ProductVO> result = productService.getProductPage(
                categoryId, keyword, sortBy, page, size);

        // 包装成统一格式返回
        // Result.success(data) → { code: 200, message: "成功", data: ... }
        return Result.success(result);
    }

    /**
     * 查询商品详情
     * <p>
     * 请求方式：GET
     * URL 示例：/api/product/detail/1
     * <p>
     * 路径参数：
     * - id：商品ID，从 URL 路径中获取
     * <p>
     * 返回：商品的完整信息（包括多张轮播图、描述、评价等）
     * 如果商品不存在，Service 会抛 BusinessException，全局异常处理器返回 404
     * <p>
     * 后续可以加 Redis 缓存：第一次查完存到 Redis，下次直接从缓存取，不用查数据库
     */
    @GetMapping("/detail/{id}")
    public Result<ProductVO> getProductById(@PathVariable Long id) {
        log.info("商品详情请求: id={}", id);

        // 调用 Service 层查询商品详情
        ProductVO productVO = productService.getProductById(id);

        return Result.success(productVO);
    }

    /**
     * 热门商品榜
     * <p>
     * 请求方式：GET
     * URL：/api/product/hot
     * <p>
     * 返回销量最高的 8 个商品（只包含上架的商品）。
     * 首页的"热门推荐"区域会调用这个接口展示商品。
     * <p>
     * 不需要参数，也不需要登录。
     * <p>
     * 返回格式：Result<List<ProductVO>>
     * 前端从 data 拿商品列表，直接遍历展示
     */
    @GetMapping("/hot")
    public Result<List<ProductVO>> getHotProducts() {
        log.info("热门商品榜请求");

        // 调用 Service 层获取热门商品列表
        List<ProductVO> hotList = productService.getHotProducts();

        return Result.success(hotList);
    }
}
