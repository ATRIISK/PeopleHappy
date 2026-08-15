package com.happymart.controller.admin;            // 包声明 → 管理后台 Controller 统一放 controller/admin 包

import com.baomidou.mybatisplus.extension.plugins.pagination.Page; // 分页对象
import com.happymart.common.annotation.Auth;                    // 自定义 @Auth 注解 → requireAdmin=true 需要管理员权限
import com.happymart.common.result.Result;                      // 统一返回结果
import com.happymart.dto.ProductSaveDTO;                        // 商品新增/修改请求参数
import com.happymart.service.AdminProductService;               // 管理后台商品服务
import com.happymart.vo.ProductVO;                              // 商品视图对象
import jakarta.validation.Valid;                                // @Valid → 开启参数校验（配合 DTO 中的 @NotBlank/@NotNull）
import lombok.RequiredArgsConstructor;                          // @RequiredArgsConstructor → 自动构造器注入
import lombok.extern.slf4j.Slf4j;                               // @Slf4j → 日志
import org.springframework.web.bind.annotation.DeleteMapping;   // @DeleteMapping → DELETE 请求
import org.springframework.web.bind.annotation.GetMapping;      // @GetMapping → GET 请求
import org.springframework.web.bind.annotation.PathVariable;   // @PathVariable → 路径参数
import org.springframework.web.bind.annotation.PostMapping;     // @PostMapping → POST 请求
import org.springframework.web.bind.annotation.PutMapping;      // @PutMapping → PUT 请求
import org.springframework.web.bind.annotation.RequestBody;     // @RequestBody → 把请求体 JSON 转成 Java 对象
import org.springframework.web.bind.annotation.RequestMapping;  // @RequestMapping → 类级别路径前缀
import org.springframework.web.bind.annotation.RequestParam;    // @RequestParam → 查询参数
import org.springframework.web.bind.annotation.RestController;  // @RestController → 返回 JSON

/**
 * 管理后台：商品管理接口
 * <p>
 * 所有接口都加 @Auth(requireAdmin = true)：
 * - 拦截器先校验登录（401）
 * - 再校验角色必须是 ADMIN（403），普通用户调用会被拒绝
 * <p>
 * 接口清单：
 * GET    /api/admin/product/list         → 分页查全部商品（可搜索）
 * POST   /api/admin/product/save         → 新增/修改商品
 * PUT    /api/admin/product/status/{id}  → 上架/下架商品
 * DELETE /api/admin/product/{id}         → 删除商品
 */
@Slf4j                                               // Lombok → 自动生成 log 变量
@RestController                                       // @Controller + @ResponseBody = 返回 JSON
@RequestMapping("/api/admin/product")                  // 所有接口以 /api/admin/product 开头
@RequiredArgsConstructor                              // Lombok → 自动生成构造器注入
public class AdminProductController {

    private final AdminProductService adminProductService;   // 注入管理后台商品服务

    /**
     * 分页查询全部商品（管理后台用，含上架+下架）
     * <p>
     * GET /api/admin/product/list?keyword=手机&page=1&size=10
     */
    @Auth(requireAdmin = true)                          // 必须管理员权限！
    @GetMapping("/list")
    public Result<Page<ProductVO>> getProductPage(
            @RequestParam(required = false) String keyword,  // 搜索关键词（可选）
            @RequestParam(defaultValue = "1") Integer page,  // 页码，默认第 1 页
            @RequestParam(defaultValue = "10") Integer size) // 每页条数，默认 10
    {
        return Result.success(adminProductService.getAdminProductPage(keyword, page, size));
    }

    /**
     * 新增/修改商品（id 为 null=新增，有值=修改）
     * <p>
     * POST /api/admin/product/save
     * body：{ id?, name, description?, price, originalPrice?, image?, images?, categoryId, stock, rating?, status? }
     */
    @Auth(requireAdmin = true)
    @PostMapping("/save")
    public Result<Void> saveProduct(@Valid @RequestBody ProductSaveDTO dto) {
        adminProductService.saveProduct(dto);
        return Result.success();
    }

    /**
     * 上架/下架商品
     * <p>
     * PUT /api/admin/product/status/1?status=1 （status：0=上架，1=下架）
     * 用查询参数传 status，比 body 简单直观
     */
    @Auth(requireAdmin = true)
    @PutMapping("/status/{id}")
    public Result<Void> updateStatus(
            @PathVariable Long id,              // 商品ID（路径参数）
            @RequestParam Integer status) {     // 目标状态（查询参数）：0=上架，1=下架
        adminProductService.updateStatus(id, status);
        return Result.success();
    }

    /**
     * 删除商品（逻辑删除 + 级联清购物车 + 清缓存）
     * <p>
     * DELETE /api/admin/product/1
     */
    @Auth(requireAdmin = true)
    @DeleteMapping("/{id}")
    public Result<Void> deleteProduct(@PathVariable Long id) {
        adminProductService.deleteProduct(id);
        return Result.success();
    }
}
