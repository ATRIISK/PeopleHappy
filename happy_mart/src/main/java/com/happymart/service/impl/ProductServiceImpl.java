package com.happymart.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.happymart.common.exception.BusinessException;
import com.happymart.common.result.ResultCodeEnum;
import com.happymart.entity.Product;
import com.happymart.mapper.ProductMapper;
import com.happymart.service.ProductService;
import com.happymart.vo.ProductVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 商品服务实现类
 * <p>
 * 这个类实现了 ProductService 接口中定义的所有方法。
 * 真正的业务逻辑都写在这里：
 * - getProductPage() → 商品列表分页查询
 * - getProductById() → 商品详情查询
 * - getHotProducts() → 热门商品榜
 * - convertToVO() → Entity 转 VO 的公共方法
 * <p>
 * 注解说明：
 * @Service           → 标记这是一个 Service 类，Spring 会自动管理
 * @RequiredArgsConstructor → Lombok，自动生成构造器（不用手写 new）
 * @Transactional     → 类里所有方法都在事务里运行，出错了自动回滚
 * @Slf4j             → 自动生成 log 变量，用来打日志
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)  // 任何异常都回滚，不光是运行时异常
public class ProductServiceImpl implements ProductService{

    // ==================== 注入依赖 ====================

    /**
     * 商品 Mapper → 操作数据库 product 表
     * MyBatis-Plus 的 BaseMapper 自带 insert / deleteById / updateById / selectById / selectList 等方法
     */
    private final ProductMapper productMapper;

    /**
     * Jackson 的 ObjectMapper → 用来把 JSON 字符串和 Java 对象互相转换
     * 这里主要用于把 product 表的 images 字段（数据库里存的是 JSON 字符串）转成 List<String>
     * 比如数据库存的是 ["a.jpg", "b.jpg"] → 转成 Java 的 List，里面有两个元素
     */
    private final ObjectMapper objectMapper;

    // ==================== 商品列表分页查询 ====================

    /**
     * 分页查询商品列表
     * <p>
     * 这是商品列表页调用的接口，支持的功能：
     * 1. 按分类筛选 → 传 categoryId 参数
     * 2. 按关键词搜索 → 传 keyword 参数，模糊匹配商品名称
     * 3. 排序 → 传 sortBy 参数，支持销量/价格/最新/评分
     * 4. 分页 → 传 page 和 size 参数
     * <p>
     * 数据流向：
     * 前端传参 → Controller → Service（这里）→ Mapper → 数据库
     * 数据库返回 Page<Product> → 转成 Page<ProductVO> → 返回给 Controller → 返回给前端
     *
     * @param categoryId 分类ID（可选）
     * @param keyword    搜索关键词（可选）
     * @param sortBy     排序方式（可选）
     * @param page       当前页码
     * @param size       每页条数
     * @return 分页结果（包含商品列表 + 总条数）
     */
    @Override
    public Page<ProductVO> getProductPage(Long categoryId, String keyword, String sortBy, Integer page, Integer size) {

        log.info("商品分类查询: categoryId={}, keyword={}, sortBy={}, page={}, size={}",
                categoryId, keyword, sortBy, page, size);

        // ===== 1. 构建查询条件 =====
        // LambdaQueryWrapper 是 MyBatis-Plus 的条件构造器
        // 它帮我们生成 WHERE 语句，不用手写 SQL
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();

        // 按分类筛选
        // 前端传的是二级分类ID（比如 9=手机），直接匹配 category_id 字段
        if (categoryId != null) {
            wrapper.eq(Product::getCategoryId, categoryId);  // WHERE category_id = ?
        }

        // 按关键词模糊搜索商品名称
        // StringUtils.hasText() 检查字符串不为 null 且不为空字符串
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Product::getName, keyword);  // WHERE name LIKE '%keyword%'
        }

        // 只查上架的商品（status = 0 表示上架）
        // 下架的商品（status = 1）不会出现在商品列表里
        wrapper.eq(Product::getStatus, 0);  // WHERE status = 0

        // ===== 2. 排序 =====
        // 根据前端传的 sortBy 参数决定排序方式
        if (StringUtils.hasText(sortBy)) {
            switch (sortBy) {
                case "sales" -> wrapper.orderByDesc(Product::getSales);      // 销量从高到低
                case "price_asc" -> wrapper.orderByAsc(Product::getPrice);   // 价格从低到高
                case "price_desc" -> wrapper.orderByDesc(Product::getPrice); // 价格从高到低
                case "newest" -> wrapper.orderByDesc(Product::getCreateTime); // 最新上架
                case "rating" -> wrapper.orderByDesc(Product::getRating);    // 评分从高到低
            }
        } else {
            // 没传排序参数，默认按销量排（卖得好的放前面）
            wrapper.orderByDesc(Product::getSales);
        }

        // ===== 3. 执行分页查询 =====
        // MyBatis-Plus 的 Page 对象会自动帮我们计算 limit 和 offset
        // selectPage 会自动执行 SELECT COUNT(*) 查总数 + SELECT ... LIMIT ? OFFSET ? 查当前页数据
        Page<Product> productPage = productMapper.selectPage(
                new Page<>(page, size),   // 第几页，每页几条
                wrapper                   // 查询条件 + 排序
        );

        // ===== 4. Entity 转 VO =====
        // Product（实体类）→ ProductVO（返回给前端的数据）
        // 为什么要转？Entity 里可能有敏感字段，VO 只返回前端需要的数据
        Page<ProductVO> voPage = new Page<>(
                productPage.getCurrent(),  // 当前页码
                productPage.getSize(),     // 每页条数
                productPage.getTotal()     // 总记录数
        );

        // 把 Product 列表转成 ProductVO 列表
        List<ProductVO> voList = productPage.getRecords().stream()
                .map(this::convertToVO)        // 每个 Product 调用 convertToVO 方法转成 ProductVO
                .collect(Collectors.toList()); // 收集成 List
        voPage.setRecords(voList);             // 把转换后的列表放到分页对象里

        log.info("商品分页查询完成: 总数={}, 当前页={}", voPage.getTotal(), voPage.getCurrent());
        return voPage;
    }

    // ==================== 商品详情查询 ====================

    /**
     * 根据 ID 查询单个商品的详细信息
     * <p>
     * 商品详情页调用这个接口。
     * 如果商品不存在，会抛 BusinessException，全局异常处理器会返回 404 给前端。
     *
     * @param id 商品 ID
     * @return 商品详情 VO（包含轮播图列表）
     */
    @Override
    public ProductVO getProductById(Long id) {
        log.info("查询商品详情: id={}", id);

        // selectById 是 BaseMapper 自带的方法，根据主键查一条记录
        Product product = productMapper.selectById(id);

        // 如果查不到，说明商品不存在或者被删了
        if (product == null) {
            log.warn("商品不存在: id={}", id);

            // 抛业务异常 → 全局异常处理器 GlobalExceptionHandler 会拦截
            // 返回给前端：{ code: 404, message: "资源不存在" }
            throw new BusinessException(ResultCodeEnum.NOT_FOUND);
        }

        // 把 Entity 转成 VO 再返回（去掉不必要的字段）
        return convertToVO(product);
    }

    // ==================== 热门商品榜 ====================

    /**
     * 查询热门商品（销量前 8 名）
     * <p>
     * 首页的"热门推荐"区域调用这个接口。
     * 逻辑很简单：
     * 1. 只查上架的商品（status = 0）
     * 2. 按销量从高到低排
     * 3. 只取前 8 条
     * <p>
     * 后续优化：可以加 Redis 缓存，1 小时刷新一次，不用每次都查数据库。
     *
     * @return 热门商品列表（最多 8 个）
     */
    @Override
    public List<ProductVO> getHotProducts() {
        log.info("查询热门商品 Top 8");

        // 构建查询条件
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();

        // .eq(字段, 值) → WHERE 字段 = 值
        // Product::getStatus 是方法引用，相当于 "status" 字段
        wrapper.eq(Product::getStatus, 0)              // 只查上架的商品（status=0）
               .orderByDesc(Product::getSales)          // 按销量从高到低排序（ORDER BY sales DESC）
               .last("LIMIT 8");                        // 只取前 8 条（追加到 SQL 末尾）

        // 执行查询，返回 List<Product>
        // 如果不加 LIMIT，可能会查出几百个商品，但我们只需要前 8 个
        List<Product> productList = productMapper.selectList(wrapper);

        // 把 Entity 列表转成 VO 列表
        // stream() → 把 List 转成流，方便做批量操作
        // map() → 对每个元素执行 convertToVO 转换
        // collect() → 把流转回 List
        List<ProductVO> voList = productList.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        log.info("热门商品查询完成，共 {} 条", voList.size());
        return voList;
    }

    // ==================== 公共转换方法 ====================

    /**
     * Product（数据库实体） → ProductVO（前端视图对象）
     * <p>
     * 为什么要转？
     * 1. Product 里的字段和数据库一一对应，但前端不需要所有字段
     * 2. images 字段在数据库里存的是 JSON 字符串，前端需要的是 List<String>
     * 3. 有些敏感字段（目前没有）不应该返回给前端
     * <p>
     * BeanUtils.copyProperties() 做了什么？
     * 自动把 Product 和 ProductVO 中"字段名相同"的属性复制过去。
     * 比如 Product 有 name、price，ProductVO 也有 name、price，就自动复制了。
     * Product 的 images 是 String（JSON），ProductVO 的 images 是 List<String>，
     * 字段名相同但类型不同，所以需要手动转换。
     *
     * @param product 数据库查出来的商品实体
     * @return 给前端用的商品 VO
     */
    private ProductVO convertToVO(Product product) {
        // 创建一个空的 VO 对象
        ProductVO vo = new ProductVO();

        // 把 Product 中同名属性复制到 VO 中
        // 比如 product.name → vo.name, product.price → vo.price
        BeanUtils.copyProperties(product, vo);

        // 特殊处理 images 字段：
        // 数据库里存的是 JSON 字符串，比如  ["a.jpg", "b.jpg"]
        // 前端要的是数组，所以要用 ObjectMapper 把 JSON 字符串转成 List
        if (product.getImages() != null && !product.getImages().isEmpty()) {
            try {
                // ObjectMapper.readValue() → JSON 字符串 → Java 对象
                // new TypeReference<List<String>>() {} → 告诉 Jackson 要转成 List<String> 类型
                List<String> imageList = objectMapper.readValue(
                        product.getImages(),
                        new TypeReference<List<String>>() {});
                vo.setImages(imageList);
            } catch (Exception e) {
                // JSON 解析失败的处理：不怕，用单张主图兜底
                // 一般是数据问题，比如手写的 JSON 格式不对
                log.warn("商品 images 字段 JSON 解析失败: id={}, images={}",
                        product.getId(), product.getImages());
                vo.setImages(List.of(product.getImage()));  // 只用主图，不展示多图
            }
        }

        return vo;
    }
}
