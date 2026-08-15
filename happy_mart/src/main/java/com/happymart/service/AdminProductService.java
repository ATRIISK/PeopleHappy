package com.happymart.service;                    // 包声明 → Service 接口层

import com.baomidou.mybatisplus.extension.plugins.pagination.Page; // 分页对象
import com.happymart.dto.ProductSaveDTO;         // 商品新增/修改请求参数
import com.happymart.vo.ProductVO;               // 商品视图对象

/**
 * 管理后台：商品管理服务接口
 * <p>
 * 与前台 ProductService 分离，独立一套"管理员专用"的业务逻辑
 * （前台只管查询，管理员要增删改 + 清缓存）。
 */
public interface AdminProductService {

    /**
     * 分页查询全部商品（管理后台用，不过滤上/下架，展示所有商品）
     *
     * @param keyword 搜索关键词（按商品名称模糊匹配，可为 null）
     * @param page    当前页码
     * @param size    每页条数
     * @return 分页结果（含上架 + 下架商品）
     */
    Page<ProductVO> getAdminProductPage(String keyword, Integer page, Integer size);

    /**
     * 新增/修改商品
     *
     * @param dto 商品参数（id 为 null=新增，有值=修改）
     */
    void saveProduct(ProductSaveDTO dto);

    /**
     * 上架/下架商品
     *
     * @param id     商品ID
     * @param status 状态：0=上架，1=下架
     */
    void updateStatus(Long id, Integer status);

    /**
     * 删除商品（逻辑删除 + 级联清购物车 + 清商品详情缓存）
     *
     * @param id 商品ID
     */
    void deleteProduct(Long id);
}
