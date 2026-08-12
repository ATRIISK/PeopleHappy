package com.happymart.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.happymart.common.exception.BusinessException;
import com.happymart.common.result.ResultCodeEnum;
import com.happymart.entity.Category;
import com.happymart.entity.Product;
import com.happymart.mapper.CategoryMapper;
import com.happymart.mapper.ProductMapper;
import com.happymart.service.ProductService;
import com.happymart.vo.ProductVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
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
     * 分类 Mapper → 查询分类的子分类
     * 分类筛选时用：点击一级分类要把它下面的所有子分类商品一起查出来
     */
    private final CategoryMapper categoryMapper;

    /**
     * Jackson 的 ObjectMapper → 用来把 JSON 字符串和 Java 对象互相转换
     * 这里主要用于把 product 表的 images 字段（数据库里存的是 JSON 字符串）转成 List<String>
     * 比如数据库存的是 ["a.jpg", "b.jpg"] → 转成 Java 的 List，里面有两个元素
     */
    private final ObjectMapper objectMapper;

    /**
     * Spring Boot 自动配置的 StringRedisTemplate
     * → 专门用来"手写" Redis 操作（这里用来读写热门榜 ZSet，开发文档 §8 场景二）
     * <p>
     * 为什么热门榜 ZSet 不用 RedisConfig 里自定义的 RedisTemplate&lt;String, Object&gt;？
     *   1. RedisConfig 那个是 JSON 序列化：value 会变成 JSON 文本，还带 @class 类型信息。
     *      而 ZSet 的 member（我们存的是商品ID），JSON 序列化后数字反序列化会有坑——
     *      比如存了 Long 123，读回来可能是 Integer，强转 Long 就报错。
     *   2. StringRedisTemplate 的 key 和 member 都是"纯字符串"，类型干净、零歧义，
     *      存 "123" 读回来就是 String "123"，最不容易出问题。
     *   3. StringRedisTemplate 是 Spring Boot 自动配置好的 Bean，不用在 RedisConfig 里再写一遍。
     * <p>
     * 结论：商品详情/分类树这种"整个对象缓存"用注解 @Cacheable（JSON 序列化），
     *      热门榜这种"只存 ID + 销量"用 StringRedisTemplate（纯字符串），各取所长。
     */
    private final StringRedisTemplate stringRedisTemplate;

    // ==================== 热门榜缓存常量 ====================

    /**
     * 热门商品榜在 Redis 里的 key（对齐开发文档 §8 场景二的 product:hot）
     */
    private static final String HOT_KEY = "product:hot";

    /**
     * 榜单最多取几个商品（对齐原来"销量前 8"的逻辑）
     */
    private static final int HOT_LIMIT = 8;

    /**
     * 榜单缓存过期时间：1 小时（对齐开发文档 §8 场景二的 TTL）
     * 用 Duration.ofHours(1) 而不是直接写数字，可读性更好，也方便统一改
     */
    private static final Duration HOT_TTL = Duration.ofHours(1);

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

        // ===== 按分类筛选（支持一级分类自动包含其子分类） =====
        // 场景：商品挂在二级分类（如 9=手机）上，点击一级分类（如 1=手机数码）时
        //      前端传的是父分类ID，必须把父分类展开为 [自身 + 全部子分类] 再查，
        //      否则 WHERE category_id=1 查不到任何商品（语义对应前端 mock/products.js 的 getCategoryIds）
        if (categoryId != null) {
            // 1. 查该分类下的所有子分类（parent_id = categoryId；逻辑删除由 MyBatis-Plus 全局配置自动过滤）
            List<Category> children = categoryMapper.selectList(
                    new LambdaQueryWrapper<Category>().eq(Category::getParentId, categoryId));
            // 2. 组装分类ID集合：自身 + 子分类
            //    - 一级分类有子分类（手机数码 1）→ [1, 9, 10] → 查到手机/平板下的商品
            //    - 一级分类无子分类（家电 3）→ [3] → 行为不变
            //    - 二级分类（手机 9）→ [9] → 行为不变
            List<Long> categoryIds = new ArrayList<>();
            categoryIds.add(categoryId);
            children.forEach(child -> categoryIds.add(child.getId()));
            // 3. WHERE category_id IN (...)
            wrapper.in(Product::getCategoryId, categoryIds);
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
     * <p>
     * 缓存说明（开发文档 §8 场景一，String 结构 + 30 分钟 TTL）：
     * {@code @Cacheable(cacheNames = "product:detail", key = "#id")} 的含义：
     * - 第一次调用：执行方法体 → 查数据库 → 把返回的 ProductVO 存进 Redis（30 分钟后过期）
     * - 第二次调用（30 分钟内）：不执行方法体，直接返回 Redis 里的缓存值 → 不再查数据库
     * - cacheNames = "product:detail" → 对应 RedisConfig 里 CacheManager 的默认配置（TTL 30 分钟）
     * - key = "#id" → 用方法参数 id 作为缓存的 key，
     *   实际 Redis 里的 key 是 product:detail::1（Spring Cache 格式 = 缓存名::key，文档写的 product:detail:{id} 是概念写法）
     * <p>
     * ⏳ 预留：将来管理后台"新增/修改/删除商品"时，要在那个保存方法上加
     *   {@code @CacheEvict(cacheNames = "product:detail", allEntries = true)}
     *   清除全部商品详情缓存，否则后台改了商品，前台要等 30 分钟缓存过期才看到新数据。
     *   当前没有管理员接口（管理后台 ⏳ 未开发），所以暂时只加读缓存 @Cacheable。
     * <p>
     * 注意：缓存命中时方法体不执行，所以"查询商品详情"这句日志在命中缓存时不会打印
     *      （可用这个特征判断缓存是否生效）。
     *
     * @param id 商品 ID
     * @return 商品详情 VO（包含轮播图列表）
     */
    @Override
    @Cacheable(cacheNames = "product:detail", key = "#id")
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

    // ==================== 热门商品榜（Redis ZSet 缓存） ====================

    /**
     * 查询热门商品（销量前 8 名）
     * <p>
     * 首页的"热门推荐"区域调用这个接口。
     * <p>
     * 缓存说明（开发文档 §8 场景二，ZSet 结构 + 1 小时定时刷新）：
     * 热门榜为什么不能用 @Cacheable 注解？
     *   ZSet（有序集合）不是 Spring Cache 抽象原生支持的结构，@Cacheable 只能缓存"整个返回值"，
     *   不能表达"member + score 按分数排序"这种 Redis 特性，所以必须**手写 RedisTemplate 操作**。
     * <p>
     * ZSet 里存什么？
     *   member = 商品ID 的字符串（如 "1"、"2"），score = 销量。
     *   按 score 降序取出来，天然就是"销量榜"。
     * <p>
     * 读取流程（本方法）：
     *   1. 先从 Redis ZSet 取前 8 个商品ID（reverseRange 按 score 从高到低）
     *   2. 命中 → 用商品ID批量查数据库 → 组装 VO 返回
     *   3. 缓存为空（首次启动/缓存过期/定时任务还没跑）→ 查数据库 Top8 → 顺手回填缓存 → 返回
     * <p>
     * 为什么命中缓存后"还要查一次数据库"？
     *   ZSet 里只有商品ID 和销量，商品的名称/价格/图片这些完整信息还在 MySQL 里，
     *   所以要用 ID 去 MySQL 查。但这是"主键 IN 批量查询"，比原来"每次全量查 Top8 再转换"快。
     * <p>
     * 兜底设计：
     *   - 整个方法 try-catch 包裹 → Redis 连不上时自动降级查数据库，首页不会挂
     *   - ZSet 空 → 读取时顺手回填（refreshHotCache），不用干等整点定时任务
     *
     * @return 热门商品列表（最多 8 个）
     */
    @Override
    public List<ProductVO> getHotProducts() {
        log.info("查询热门商品 Top {}", HOT_LIMIT);

        try {
            // ===== 1. 先读 Redis ZSet 缓存 =====
            // reverseRange(key, start, end)：按 score 从高到低取一个区间
            // 0 到 HOT_LIMIT-1 → 取销量最高的前 8 个商品ID（score 就是销量）
            Set<String> hotIds = stringRedisTemplate.opsForZSet()
                    .reverseRange(HOT_KEY, 0, HOT_LIMIT - 1);

            // 命中缓存（ZSet 里有数据）
            if (hotIds != null && !hotIds.isEmpty()) {
                log.info("热门榜命中 Redis 缓存，共 {} 个商品", hotIds.size());
                // 用商品ID批量查库，按 ZSet 顺序组装 VO
                List<ProductVO> cachedList = convertToVOByOrder(hotIds);

                // 补齐逻辑（code-review 修复）：命中缓存但过滤掉下架/已删除商品后不足 8 个时，
                // 说明 ZSet 里混入了无效商品（比如商品被下架了但还没到整点刷新）→
                // 回退数据库查全量 Top8 并刷新缓存，否则首页"热门推荐"会显示 4 个甚至 0 个商品，
                // 最长持续到下次整点定时刷新。
                if (cachedList.size() < HOT_LIMIT) {
                    log.info("热门榜缓存命中的有效商品不足 {} 个，回退数据库刷新全量榜单", HOT_LIMIT);
                    List<ProductVO> hotList = queryHotProducts();
                    fillHotCache(hotList);
                    return hotList;
                }
                return cachedList;
            }

            // ===== 2. 缓存为空（首次启动 / 缓存过期 / 定时任务还没跑） =====
            // 先查数据库 Top8，再顺手回填缓存，这样第一次访问就写进 Redis，不用等整点
            log.info("热门榜缓存为空，查询数据库并回填 Redis 缓存");
            List<ProductVO> hotList = queryHotProducts();  // 查数据库拿 Top8（只查这一次）
            fillHotCache(hotList);                          // 用刚查到的数据回填 ZSet（不重复查询）
            return hotList;

        } catch (Exception e) {
            // ===== 3. 读取异常 → 降级回数据库 =====
            // 注意（code-review 修复）：异常可能来自 Redis（连不上/超时），
            //   也可能来自"命中缓存后批量查库"的数据库操作——所以日志措辞用中性的
            //   "热门榜读取异常"，不误导排查的人以为是 Redis 挂了。
            // 降级：直接查数据库返回，保证首页依然能用（功能降级，不阻断业务）
            // 若数据库也异常，queryHotProducts 抛出的异常会继续上抛给全局异常处理器
            log.warn("热门榜读取异常，降级查询数据库: {}", e.getMessage());
            return queryHotProducts();
        }
    }

    /**
     * 按 ZSet 里的商品ID顺序批量查库，组装成 VO 列表
     * <p>
     * 为什么不能用 List 顺序直接对应？
     *   MySQL 的 IN 查询返回顺序**不保证**和传入顺序一致，所以先把商品转成 Map
     *   （key = 商品ID），再按 hotIds 的顺序一个个取出来组装，保证最终顺序 = 销量榜顺序。
     *
     * @param hotIds ZSet 里按销量降序排好的商品ID集合（顺序就是榜单顺序）
     * @return 按榜单顺序排好的热门商品 VO 列表
     */
    private List<ProductVO> convertToVOByOrder(Set<String> hotIds) {

        // 1. 把 String 类型的商品ID 转成 Long 列表（MyBatis-Plus 批量查询需要 Long）
        //    "1" → 1L
        List<Long> idList = hotIds.stream()
                .map(Long::parseLong)
                .collect(Collectors.toList());

        // 2. 批量查库：SELECT * FROM product WHERE id IN (1,2,3,...) AND status = 0
        //    code-review 修复：用 LambdaQueryWrapper 而不是 selectBatchIds，
        //    是为了加 status=0 过滤（只查上架商品），和 DB 路径 queryHotProducts 保持一致，
        //    否则在两次刷新之间被下架的商品会出现在首页热门榜（两条路径结果不一致）
        //    注意：IN 查询返回顺序不保证和传入顺序一致，顺序问题由下面的 Map 索引解决
        List<Product> products = productMapper.selectList(
                new LambdaQueryWrapper<Product>()
                        .in(Product::getId, idList)      // WHERE id IN (...)
                        .eq(Product::getStatus, 0));     // AND status = 0（只查上架商品）

        // 3. 把商品转成 VO，再存进 Map，key = 商品ID
        //    用 Map 的原因：等会要"按 hotIds 的顺序"重组，Map 用 get 一下就能拿到，效率高
        Map<Long, ProductVO> voMap = products.stream()
                .map(this::convertToVO)                      // 每个 Product → ProductVO
                .collect(Collectors.toMap(ProductVO::getId, vo -> vo));

        // 4. 按 hotIds 的顺序逐个取出 VO（保持销量降序）
        //    注意：如果某个商品被删了/下架了，voMap 里取不到，就跳过它，不影响整体顺序
        List<ProductVO> result = new ArrayList<>();
        for (String idStr : hotIds) {
            ProductVO vo = voMap.get(Long.parseLong(idStr));
            if (vo != null) {
                result.add(vo);
            }
        }
        return result;
    }

    /**
     * 从数据库查询热门商品 Top8（原 getHotProducts 的查询逻辑，抽取出来复用）
     * <p>
     * 这个方法被三处复用：
     * 1. getHotProducts() 缓存未命中时 → 查数据库兜底
     * 2. getHotProducts() Redis 挂掉时 → 降级查询
     * 3. refreshHotCache() 定时刷新时 → 重新查库拿最新数据
     *
     * @return 销量前 8 名的商品 VO 列表
     */
    private List<ProductVO> queryHotProducts() {
        log.info("从数据库查询热门商品 Top {}", HOT_LIMIT);

        // 构建查询条件：只查上架商品，按销量降序，取前 8 条
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getStatus, 0)               // 只查上架的商品（status=0）
               .orderByDesc(Product::getSales)          // 按销量从高到低排序（ORDER BY sales DESC）
               .last("LIMIT " + HOT_LIMIT);             // 只取前 8 条（追加到 SQL 末尾）

        // 执行查询
        List<Product> productList = productMapper.selectList(wrapper);

        // Entity 列表 → VO 列表
        return productList.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    /**
     * 定时刷新热门榜缓存（每小时整点自动执行）
     * <p>
     * 对应开发文档 §8：热门商品榜 ZSet，1 小时定时刷新。
     * 为什么要定时刷新？因为销量在不停变化（用户下单、退单都会改 sales），
     * 榜单不能永远停留在某一次查询的结果上，所以要每小时拉一次最新的。
     * <p>
     * {@code @Scheduled(cron = "0 0 * * * ?")} 的含义：
     * - 定时任务注解，让 Spring 每隔一段时间自动调用这个方法
     * - cron 表达式 "0 0 * * * ?" = 每小时整点执行一次（10:00、11:00、12:00 ...）
     * - 总开关是 RedisConfig 里的 @EnableScheduling（已配好，这里是具体任务）
     * <p>
     * 双保险设计：
     * 1. 每小时定时刷新 → 保证榜单跟着销量变化
     * 2. 写入时还设置 1 小时过期时间（expire）→ 万一定时任务出问题停了，
     *    缓存也会在 1 小时后自然消失，下次 getHotProducts() 发现 ZSet 空会自动回填，
     *    不会一直用脏数据
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void refreshHotCache() {

        // 1. 查数据库拿最新的 Top8
        List<ProductVO> hotList = queryHotProducts();

        // 2. 写入 ZSet（删旧 key → 逐个 ZADD → 设置 1 小时过期，逻辑全在 fillHotCache 里）
        fillHotCache(hotList);

        log.info("热门榜缓存刷新完成，共写入 {} 个商品", hotList.size());
    }

    /**
     * 把热门商品写入 ZSet 缓存（公共方法，供两处复用）
     * <p>
     * code-review 修复：原来"冷缓存回填"和"定时刷新"各自调用 queryHotProducts 查一次库，
     * 导致一次冷请求重复查询数据库。抽出本方法后，调用方自己查好数据传进来，
     * 本方法只负责"写缓存"，不做查询。
     * <p>
     * 被两处复用：
     * 1. getHotProducts() 缓存为空时 → 传刚查好的 hotList，顺手回填（不等整点）
     * 2. refreshHotCache() 定时刷新时 → 传新查的 hotList，重建榜单
     *
     * @param hotList 已按销量降序排好的热门商品 VO 列表
     */
    private void fillHotCache(List<ProductVO> hotList) {

        // 1. 遍历商品，逐个写入 ZSet（member 已存在则更新 score，不删除整个 key）
        //    code-review 修复：原来先 delete 再 ZADD，删 key 到写完之间有毫秒级空窗，
        //    并发读 reverseRange 会看到空的 ZSet → 误判"缓存为空" → 重复查库（刷新边界的惊群）。
        //    改成"只 ZADD 覆盖 + 最后清理多余成员"，key 全程存在，读到空的问题消失。
        ZSetOperations<String, String> zset = stringRedisTemplate.opsForZSet();
        for (ProductVO vo : hotList) {
            // add(key, member, score) 三个参数：
            //   key    = 榜单的 key（product:hot）
            //   member = 商品ID 的字符串（ZSet 里存的元素，后面读取时拿到它去查库）
            //   score  = 销量（排序依据，score 越大排名越靠前；member 已存在则更新 score）
            // 销量理论上数据库有默认值 0，但代码里做个空值保护，最稳妥
            double score = vo.getSales() == null ? 0.0 : vo.getSales().doubleValue();
            zset.add(HOT_KEY, String.valueOf(vo.getId()), score);
        }

        // 2. 清理掉出榜的旧成员（ZADD 不会自动删除"不在新榜里"的旧 member）
        //    场景：上次刷新进榜的商品这次掉出前 8、或已下架/删除，它的 member 还残留在 ZSet 里
        //    做法：只保留 score 最高的 HOT_LIMIT 个 → 删掉"升序排名最靠前"的（即 score 最低的）多余成员
        //    zCard() 查当前成员总数；removeRange(key, start, end) 对应 Redis 的 ZREMRANGEBYRANK，
        //    按 score 升序删除 [start, end] 排名区间
        Long total = zset.zCard(HOT_KEY);
        if (total != null && total > HOT_LIMIT) {
            // 例如 total=10，HOT_LIMIT=8 → 删升序排名 0~1（score 最低的 2 个），保留最高的 8 个
            zset.removeRange(HOT_KEY, 0, total - HOT_LIMIT - 1);
        }

        // 3. 设置 1 小时过期时间（双保险，见 refreshHotCache 注释：定时刷新 + 过期兜底）
        stringRedisTemplate.expire(HOT_KEY, HOT_TTL);
    }

    // ==================== 商品详情缓存管理（经典延时双删） ====================

    /**
     * 延时双删专用的延迟线程池
     *
     * 为什么需要独立线程池，而不是在方法里直接 Thread.sleep(500)？
     *   第二次删除要"延时 500ms"再执行。如果直接 sleep，会阻塞下单请求的线程
     *   （用户下单要多等 0.5 秒才能拿到响应），体验差。
     *   所以用线程池异步执行：方法立即返回，500ms 后由线程池里的线程去删缓存。
     *
     * 为什么用守护线程（daemon）？
     *   守护线程不会阻止 JVM 退出。应用关闭时即使还有待执行的删除任务，
     *   JVM 也能正常退出（最多那次删除没执行——缓存 TTL 30 分钟后自然过期，无影响）。
     *   newScheduledThreadPool 第一个参数 = 池子里常驻的线程数，删除任务很轻量，1 个够用。
     */
    private static final ScheduledExecutorService DELAY_DELETE_POOL =
            Executors.newScheduledThreadPool(1, r -> {
                Thread t = new Thread(r, "cache-delay-delete");
                t.setDaemon(true);   // 守护线程：应用退出时不阻塞
                return t;
            });

    /**
     * 清除指定商品的详情缓存（经典延时双删）
     * <p>
     * 背景（对应开发文档 §8，code-review 补充）：
     * 商品详情的缓存是 @Cacheable 注解缓存的，30 分钟才过期。但商品库存会被订单流程修改：
     * - 用户下单 → 扣库存（OrderServiceImpl.createOrder → orderMapper.updateStock）
     * - 订单取消 / 退单 → 恢复库存（OrderServiceImpl.restoreStockByOrderId → orderMapper.restoreStock）
     * 如果不清缓存，用户买完东西后，商品详情页还会显示旧库存最多 30 分钟
     * （出现"详情页有库存，下单却提示库存不足"的 bug）。
     * <p>
     * 为什么简单删一次还不够，要"延时双删"？
     * 库存更新（DB 变更）和第一次删缓存之间有一个并发窗口，会产生"删了又回填旧值"：
     * <pre>
     *   ① A 下单扣库存（DB: stock 10→5，但事务还没提交）
     *   ② A 第一次删缓存 product:detail::1
     *   ③ 此刻并发读 R 查详情 → 缓存已删 → 查 DB → 读到旧值 10（A 事务未提交）
     *   ④ R 把旧值 10 回填进缓存
     *   ⑤ A 事务提交（DB 正式变成 5）
     *   → 缓存里残留旧值 10，和数据库不一致，要等 30 分钟过期才修复
     * </pre>
     * 延时双删就是专门治这个问题的：
     * <pre>
     *   ① 第一次删除：@CacheEvict 注解（本方法正常返回后自动删）→ 删掉下单前的旧缓存
     *   ② 延时 500ms：给"步骤③④并发读回填旧值"留出发生的时间
     *   ③ 第二次删除：500ms 后线程池再删一次 → 把被回填的旧值删掉
     *   → 即使有并发读回填了旧值，也会在第二次删除时被清掉，缓存最终一致
     * </pre>
     * 为什么延时 500ms？
     *   经验值，覆盖绝大多数"读缓存 miss → 查库 → 回填"的耗时。
     *   太短（如 50ms）可能覆盖不完整；太长（如 5s）会让缓存的空窗期变长。
     * <p>
     * 注意：@CacheEvict 的 key 用 "#productId"（本方法的参数名），
     * 和 getProductById 里 @Cacheable 的 key "#id" 参数名不同，但两个 key 的值都是商品ID，
     * 所以生成的 Redis key 都是 product:detail::{商品ID}，能正确匹配并删除。
     *
     * @param productId 商品 ID
     */
    @Override
    @CacheEvict(cacheNames = "product:detail", key = "#productId")
    public void clearProductDetailCache(Long productId) {

        // ===== 第一次删除 =====
        // 由 @CacheEvict 注解负责：本方法正常返回后，自动删除 product:detail::{productId}
        // 这一步删掉的是"下单前"的旧缓存。
        // 注意：这次删除发生在订单事务提交之前（ProductService 被 OrderServiceImpl 事务内调用），
        //       删的是旧值，无妨——这正是需要第二次延时删除的原因。

        // ===== 第二次延时删除（关键：必须绑定在"事务提交之后"） =====
        // 经典延时双删要覆盖的窗口是"删缓存后、DB 提交前，并发读回填旧值"。
        // 如果第二次删除从"本方法调用时刻"算 500ms，而订单事务因为别的原因
        // （例如 createOrder 末尾给 RabbitMQ 发延迟消息，spring.rabbitmq.template.retry
        //  配置了 3 次重试、初始间隔 1s → 最坏能拖 ~3s）迟迟不提交，
        // 第二次删除就会在事务提交**前**执行——之后并发读照样回填旧值，窗口没闭上。
        // 所以正确做法：等事务真正提交后，再过 500ms 才删第二次。
        // 实现：用 Spring 的事务同步 TransactionSynchronization.afterCommit，
        //       事务提交成功后会回调 afterCommit，在那里再安排延时删除。
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // 当前在事务中（正常路径：被 OrderServiceImpl 下单/退单事务内调用）
            // → 注册一个"事务提交后"的回调，提交后再安排第二次延时删除
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    scheduleDelayedDelete(productId);
                }
            });
        } else {
            // 不在事务中（比如将来其他模块直接调用了本方法）→ 直接安排延时删除
            scheduleDelayedDelete(productId);
        }
    }

    /**
     * 安排第二次延时删除（500ms 后由守护线程池执行）
     * <p>
     * 从 clearProductDetailCache 抽出，供"事务提交后"和"无事务"两条路径复用。
     * 为什么不用 @Async 注解？同类内方法自调用不走 Spring 代理，@Async 会失效；
     * 用独立线程池最直接可靠。
     *
     * @param productId 商品 ID
     */
    private void scheduleDelayedDelete(Long productId) {
        // 500ms 后由线程池执行第二次删除（延时双删的"延时"）
        DELAY_DELETE_POOL.schedule(() -> {
            try {
                // 直接删 Redis key：product:detail::{productId}
                // 注意 key 格式要和 @Cacheable 生成的完全一致（缓存名::key，之前验证过是 product:detail::1）
                String cacheKey = "product:detail::" + productId;
                Boolean deleted = stringRedisTemplate.delete(cacheKey);
                log.info("延时双删完成（第二次删除）: key={}, deleted={}", cacheKey, deleted);
            } catch (Exception e) {
                // 第二次删除失败也无所谓：
                // 最坏情况是缓存里旧值多留一会儿，TTL 30 分钟后自然过期，不会出大问题
                // 打日志方便排查，但不往上抛（不能影响下单主流程）
                log.warn("延时双删（第二次删除）失败: productId={}, error={}",
                        productId, e.getMessage());
            }
        }, 500, TimeUnit.MILLISECONDS);   // 500ms 后执行（延时双删的"延时"）
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
