package com.happymart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.happymart.entity.Cart;
import com.happymart.vo.CartVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;
/**
 * 购物车 Mapper 接口
 * <p>
 * 继承 BaseMapper<Cart> 后，MyBatis-Plus 会自动提供以下方法：
 * - insert(Cart) → 插入记录
 * - deleteById(id) → 根据主键删除
 * - updateById(Cart) → 根据主键更新
 * - selectById(id) → 根据主键查询
 * - selectOne(Wrapper) → 按条件查一条
 * - selectList(Wrapper) → 按条件查列表
 * <p>
 * 但购物车列表需要联表查商品信息（cart + product），
 * BaseMapper 的 selectList 做不到联表，
 * 所以需要自己写一个 selectCartVOList 方法，在 XML 里写 SQL。
 * <p>
 * 另外，根据 userId + productId 删除和更新也需要自定义方法，
 * 因为 BaseMapper 的 deleteById 只能根据主键删，不适合这里。
 * <p>
 * 所有注解说明：
 *
 * @Mapper          → 告诉 MyBatis 这是一个 Mapper 接口（但我们在启动类加了 @MapperScan，所以不用一个个加）
 * @Param("xxx")    → 给 SQL 里的参数起名字，XML 里通过 #{xxx} 引用
 */
public interface CartMapper extends BaseMapper<Cart> {
/**
 * 查询某个用户的购物车列表（含商品信息）
 * <p>
 * 这个方法会在 CartMapper.xml 里写一个联表查询 SQL：
 * SELECT cart.id, cart.product_id, cart.quantity, cart.create_time,
 *        product.name, product.image, product.price, product.stock
 * FROM cart
 * LEFT JOIN product ON cart.product_id = product.id
 * WHERE cart.user_id = #{userId}
 * ORDER BY cart.create_time DESC
 * <p>
 * 为什么要 LEFT JOIN 不是 INNER JOIN？
 * LEFT JOIN 保证即使商品被删了，购物车记录也能查出来（虽然商品信息为 null）。
 * 实际业务中商品很少真删除（逻辑删除居多），但以防万一。
 *
 * @param userId 用户 ID
 * @return 购物车 VO 列表（每个元素包含购物车 + 商品信息）
 */
    List<CartVO> selectCartVOList(@Param("userId") Long userId);
    /**
     * 根据用户 ID 和商品 ID 删除购物车记录
     * <p>
     * 前端删除操作传的是 productId，不是 cartId。
     * 所以要按 userId + productId 两个条件删。
     * 为什么要带 userId？
     * 防止恶意用户传别人的 productId 把别人购物车删了。
     *
     * @param userId    用户 ID（从 token 解析，安全）
     * @param productId 商品 ID（前端传过来的）
     * @return 删了几条（正常是 1）
     */
    int deleteByUserIdAndProductId(@Param("userId") Long userId, @Param("productId") Long productId);

    /**
     * 清空某个用户的所有购物车记录
     * <p>
     * 前端"清空购物车"功能调用。
     * 直接 DELETE FROM cart WHERE user_id = #{userId}
     *
     * @param userId 用户 ID
     * @return 删了几条
     */
    int deleteByUserId(@Param("userId") Long userId);

    /**
     * 根据用户 ID 和商品 ID 更新数量
     * <p>
     * 前端修改数量时调用。
     * UPDATE cart SET quantity = #{quantity} WHERE user_id = #{userId} AND product_id = #{productId}
     *
     * @param userId    用户 ID
     * @param productId 商品 ID
     * @param quantity  新数量（>= 1）
     * @return 改了几条（正常是 1）
     */
    int updateQuantityByUserIdAndProductId(
            @Param("userId") Long userId,
            @Param("productId") Long productId,
            @Param("quantity") Integer quantity);

    /**
     * 悲观锁查询购物车记录（v1.13 code-review F1 修复）
     * <p>
     * SELECT ... FOR UPDATE：在事务里锁定"该用户该商品"的购物车行，
     * 使并发的同商品加购【读-校验-写】串行化——
     * 解决两个并发加购同时读到旧数量、都通过库存校验、然后互相覆盖（丢更新）的问题。
     * <p>
     * 注意：记录【不存在】时锁不住行（空行无法加锁），两个并发 insert 会撞唯一键 uk_user_product，
     * 由 Service 层捕获 DuplicateKeyException 兜底（重新锁行按"已有"累加）。
     *
     * @param userId    用户 ID
     * @param productId 商品 ID
     * @return 购物车记录（可能为 null）
     */
    /**
     * 加购 upsert（v1.13 code-review F1）：INSERT ... ON DUPLICATE KEY UPDATE
     * <p>
     * 该用户该商品购物车记录不存在 → 新增；已存在 → 数据库层面【原子累加】数量。
     * 并发加购同一商品时：不会抛 DuplicateKeyException、不会丢更新（MySQL 原子累加），
     * 比"先查再 insert + catch 唯一键"更稳（catch 唯一键会把 @Transactional 事务标记回滚）。
     * <p>
     * 前提：cart 表有 (user_id, product_id) 唯一键 uk_user_product。
     *
     * @param userId    用户 ID
     * @param productId 商品 ID
     * @param quantity  本次加购数量
     * @return 影响行数
     */
    int upsertCart(@Param("userId") Long userId, @Param("productId") Long productId, @Param("quantity") Integer quantity);

    /**
     * 根据商品ID删除所有购物车记录（管理后台"删除商品"时级联清理用）
     * <p>
     * 为什么删除商品要连带删购物车？
     * 购物车联表查询 selectCartVOList 是手写 LEFT JOIN product，
     * 而 MyBatis-Plus 的逻辑删除只作用于它自动生成的 SQL，手写 XML 不会自动拼 is_deleted=0，
     * 所以商品被逻辑删除后还会残留在购物车里，用户下单时查不到该商品会报错。
     * 删除商品时把购物车里这个商品的记录一起删掉，避免出现"无效购物车项"。
     *
     * @param productId 商品ID
     * @return 删了几条
     */
    int deleteByProductId(@Param("productId") Long productId);

}
