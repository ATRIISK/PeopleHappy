package com.happymart.service.impl;                // 包声明 → Service 实现类放在 impl 子包下

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper; // MyBatis-Plus 条件查询构造器
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper; // MyBatis-Plus 条件更新构造器
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;         // 分页对象
import com.fasterxml.jackson.core.type.TypeReference;                      // Jackson 泛型类型引用
import com.fasterxml.jackson.databind.ObjectMapper;                        // Jackson JSON 转换器
import com.happymart.common.exception.BusinessException;                    // 业务异常
import com.happymart.common.result.ResultCodeEnum;                          // 错误码枚举
import com.happymart.dto.ProductSaveDTO;                                   // 商品新增/修改请求参数
import com.happymart.entity.Category;                                      // 分类实体
import com.happymart.entity.Product;                                       // 商品实体
import com.happymart.mapper.CartMapper;                                    // 购物车 Mapper（级联清购物车）
import com.happymart.mapper.CategoryMapper;                                // 分类 Mapper（查分类名补填）
import com.happymart.mapper.ProductMapper;                                 // 商品 Mapper
import com.happymart.service.AdminProductService;                          // 本类实现的接口
import com.happymart.service.ProductService;                               // 前台商品服务（复用清缓存）
import com.happymart.vo.ProductVO;                                         // 商品视图对象
import lombok.RequiredArgsConstructor;                                      // @RequiredArgsConstructor → 构造器注入
import lombok.extern.slf4j.Slf4j;                                           // @Slf4j → 日志
import org.springframework.stereotype.Service;                              // @Service → 标记 Service 类
import org.springframework.transaction.annotation.Transactional;            // @Transactional → 事务
import org.springframework.util.StringUtils;                                // StringUtils → 字符串判断

import java.util.List;                                                      // List → 列表
import java.util.stream.Collectors;                                         // Collectors → 流收集

/**
 * 管理后台：商品管理服务实现类
 * <p>
 * 职责：
 * - 分页查询全部商品（上架 + 下架都展示）
 * - 新增/修改商品（分类名由后端补填，保证一致性）
 * - 上架/下架
 * - 删除商品（逻辑删除 + 级联清购物车 + 清缓存）
 * <p>
 * 缓存一致性：商品被改/删后，前台"商品详情"有 30 分钟缓存，
 * 必须调用 ProductService.clearProductDetailCache（延时双删）清掉，否则前台看到旧数据。
 */
@Slf4j                                               // Lombok → 自动生成 log 变量
@Service                                              // 标记为 Service 层，Spring 自动管理
@RequiredArgsConstructor                              // Lombok → 为 final 字段自动生成构造器（构造器注入）
public class AdminProductServiceImpl implements AdminProductService {

    // ↓↓↓ 以下字段都是 final，@RequiredArgsConstructor 自动生成构造器注入它们 ↓↓↓

    /** 商品 Mapper → 操作 product 表 */
    private final ProductMapper productMapper;

    /** 购物车 Mapper → 删除商品时级联清理购物车记录 */
    private final CartMapper cartMapper;

    /** 分类 Mapper → 按 categoryId 查分类名（冗余字段补填） */
    private final CategoryMapper categoryMapper;

    /** Jackson ObjectMapper → 把 images JSON 字符串转成 List */
    private final ObjectMapper objectMapper;

    /** 前台商品服务 → 复用 clearProductDetailCache（延时双删清详情缓存） */
    private final ProductService productService;

    /**
     * 分页查询全部商品（管理后台用，不过滤上/下架）
     * <p>
     * 和前台 getProductPage 的区别：前台只查上架（status=0），
     * 管理后台要看所有商品（包括下架的，方便重新上架）。
     */
    @Override
    public Page<ProductVO> getAdminProductPage(String keyword, Integer page, Integer size) {

        // ===== 1. 构建查询条件 =====
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();

        // 按商品名称模糊搜索（可选）
        // StringUtils.hasText() 检查字符串不为 null 且不为空字符串
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Product::getName, keyword);   // WHERE name LIKE '%keyword%'
        }

        // 最新创建的商品排前面
        wrapper.orderByDesc(Product::getCreateTime);

        // ===== 2. 分页查询 =====
        Page<Product> productPage = productMapper.selectPage(
                new Page<>(page, size),  // 第几页，每页几条
                wrapper                  // 查询条件 + 排序
        );

        // ===== 3. Entity 转 VO =====
        // images 从 JSON 字符串转成 List（和前台 ProductServiceImpl.convertToVO 一致）
        Page<ProductVO> voPage = new Page<>(
                productPage.getCurrent(),  // 当前页码
                productPage.getSize(),     // 每页条数
                productPage.getTotal()     // 总记录数
        );
        voPage.setRecords(productPage.getRecords().stream()
                .map(productService::convertToVO)              // 每个 Product → ProductVO（复用前台商品服务的转换，单一来源）
                .collect(Collectors.toList()));                // 收集成 List

        return voPage;
    }

    /**
     * 新增/修改商品
     * <p>
     * id 为 null → 新增；id 有值 → 修改。
     * 分类名称 categoryName 是冗余字段，这里由后端根据 categoryId 查分类表补填，
     * 保证数据库里存的分类名永远和分类表一致（不信任前端传值）。
     */
    @Override
    public void saveProduct(ProductSaveDTO dto) {

        // ===== 1. 校验分类存在，并取出分类名补填 =====
        Category category = categoryMapper.selectById(dto.getCategoryId());
        if (category == null) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "所选分类不存在");
        }

        // ===== 1.5 校验轮播图 JSON 格式（code-review 修复） =====
        // images 数据库里存 JSON 数组字符串，如果管理员粘贴非法 JSON（如 [abc），
        // 存进库后前台详情页解析失败只会静默兜底成单图，问题不明显。
        // 所以在保存时就校验格式，非法直接提示，不污染数据。
        if (StringUtils.hasText(dto.getImages())) {
            try {
                objectMapper.readValue(dto.getImages(), new TypeReference<List<String>>() {});
            } catch (Exception e) {
                throw new BusinessException(ResultCodeEnum.PARAM_ERROR,
                        "轮播图必须是 JSON 数组格式，如 [\"url1\",\"url2\"]");
            }
        }

        // ===== 2. 组装商品实体 =====
        Product product = new Product();
        if (dto.getId() != null) {
            // 修改场景：先确认商品存在（不存在直接 404）
            Product exist = productMapper.selectById(dto.getId());
            if (exist == null) {
                throw new BusinessException(ResultCodeEnum.NOT_FOUND);
            }
            product.setId(dto.getId());
        }

        // 公共字段（新增和修改都要填）
        product.setName(dto.getName());                              // 商品名称
        product.setDescription(dto.getDescription());                // 描述
        product.setPrice(dto.getPrice());                            // 现价
        product.setOriginalPrice(dto.getOriginalPrice());            // 原价
        product.setImage(dto.getImage());                            // 主图
        product.setImages(dto.getImages());                          // 轮播图 JSON 字符串
        product.setCategoryId(dto.getCategoryId());                  // 分类ID
        product.setCategoryName(category.getName());                 // 分类名（后端补填）
        // 可空字段给默认值，防止存 null（数据库有 DEFAULT，这里双保险）
        product.setStock(dto.getStock() == null ? 0 : dto.getStock());         // 库存
        product.setRating(dto.getRating() == null ? 0.0 : dto.getRating());    // 评分
        product.setStatus(dto.getStatus() == null ? 0 : dto.getStatus());      // 状态默认上架

        // ===== 3. 新增 or 修改 =====
        if (dto.getId() == null) {
            // 新增：销量从 0 开始（销量只能靠下单累计，不能编辑改）
            product.setSales(0);
            // insert 后 MyBatis-Plus 会把自增主键回填到 product.id
            productMapper.insert(product);
            log.info("管理后台新增商品: id={}, name={}", product.getId(), dto.getName());
        } else {
            // 修改：updateById 默认策略是"null 字段不更新"，管理员把某字段清空（如原价）
            // 时 null 会被跳过、库里残留旧值。所以用 LambdaUpdateWrapper 对每个保存字段显式 set
            //（包括 null → SET col = NULL，支持"清空原价/轮播图"）。
            // 销量不在 DTO 里，不 set → 保留原值，不会被编辑覆盖。
            LambdaUpdateWrapper<Product> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(Product::getId, dto.getId())
                    .set(Product::getName, product.getName())
                    .set(Product::getDescription, product.getDescription())
                    .set(Product::getPrice, product.getPrice())
                    .set(Product::getImage, product.getImage())
                    .set(Product::getCategoryId, product.getCategoryId())
                    .set(Product::getCategoryName, product.getCategoryName())
                    .set(Product::getStock, product.getStock());
            // originalPrice / images 支持"清空"（传 null 也 SET NULL，否则清不掉）
            updateWrapper.set(Product::getOriginalPrice, product.getOriginalPrice());
            updateWrapper.set(Product::getImages, product.getImages());
            // status / rating 是可选字段：修改时没传（null）表示"保留原值"，
            // 不能当默认值写（否则部分更新的请求会把下架商品静默上架、把评分清零）——code-review 修复
            if (dto.getStatus() != null) {
                updateWrapper.set(Product::getStatus, dto.getStatus());
            }
            if (dto.getRating() != null) {
                updateWrapper.set(Product::getRating, dto.getRating());
            }
            productMapper.update(null, updateWrapper);
            // 修改后清掉该商品详情缓存，否则前台 30 分钟内还看到旧数据
            productService.clearProductDetailCache(dto.getId());
            log.info("管理后台修改商品: id={}, name={}", dto.getId(), dto.getName());
        }
    }

    /**
     * 上架/下架商品
     * <p>
     * status 只允许 0（上架）/ 1（下架）。
     * 下架后前台商品列表（只查 status=0）和详情缓存都会被清掉/过滤，商品从用户端消失。
     */
    @Override
    public void updateStatus(Long id, Integer status) {
        // 状态值必须合法：0=上架，1=下架（防止前端传非法值把状态改坏）
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "商品状态只能为 0（上架）或 1（下架）");
        }
        // 查商品是否存在
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException(ResultCodeEnum.NOT_FOUND);
        }
        // ★ 只更新 status 字段（code-review 修复）：
        // 不能"读整个实体再 updateById"——并发下用户订单的原子扣库存
        // （UPDATE stock = stock - qty）如果落在 read 和 write 之间，会被整实体写回
        // 的旧 stock 覆盖（丢失更新，库存虚高 → 可能超卖）。用 LambdaUpdateWrapper 只 set status，
        // 不碰 stock/sales/price 等字段。
        LambdaUpdateWrapper<Product> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Product::getId, id).set(Product::getStatus, status);
        productMapper.update(null, wrapper);
        // 上下架影响详情缓存（下架后清掉，避免用户还能看到下架商品的详情页）
        productService.clearProductDetailCache(id);
        log.info("管理后台上下架商品: id={}, status={}", id, status);
    }

    /**
     * 删除商品（逻辑删除 + 级联清购物车 + 清缓存，同一事务）
     * <p>
     * 为什么是逻辑删除？Product 继承 BaseEntity，MyBatis-Plus 的 deleteById
     * 会自动转成 UPDATE is_deleted=1（数据还在库，只是查不到）。
     * <p>
     * 为什么级联删购物车？
     * 购物车联表查询是手写 LEFT JOIN product，不会自动过滤逻辑删除的商品，
     * 所以商品被删后还残留在购物车 → 用户下单查不到该商品会报错。
     * 这里把购物车记录一起删掉，避免"无效购物车项"。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)   // 三步操作同一事务，任一步失败全部回滚
    public void deleteProduct(Long id) {
        // 1. 确认商品存在
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException(ResultCodeEnum.NOT_FOUND);
        }

        // 2. 逻辑删除商品（MyBatis-Plus 转成 UPDATE is_deleted=1）
        productMapper.deleteById(id);

        // 3. 级联删除购物车里该商品的记录
        cartMapper.deleteByProductId(id);

        // 4. 清商品详情缓存（前台首页/详情不再显示）
        productService.clearProductDetailCache(id);

        log.info("管理后台删除商品: id={}, name={}", id, product.getName());
    }

}
