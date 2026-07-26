package com.happymart.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.happymart.common.exception.BusinessException;
import com.happymart.common.result.ResultCodeEnum;
import com.happymart.entity.Cart;
import com.happymart.mapper.CartMapper;
import com.happymart.service.CartService;
import com.happymart.vo.CartVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 购物车服务实现类
 * <p>
 * 真正的业务逻辑都写在这里。
 * Controller 调 Service 接口，Service 实现类做具体的事情。
 * <p>
 * 注解说明：
 *
 * @Slf4j                          → Lombok，生成 log 变量，用来打日志
 * @Service                         → Spring 标记，把这个类交给 Spring 管理
 * @RequiredArgsConstructor         → Lombok，为 final 字段生成构造器（替代 @Autowired）
 * @Transactional(rollbackFor = Exception.class) → 所有方法都在事务里，出任何异常都回滚
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class CartServiceImpl implements CartService {

    /**
     * 购物车 Mapper
     * <p>
     * CartMapper 继承了 BaseMapper<Cart>，所以自带 insert/delete/update/select 方法。
     * 另外还自定义了 selectCartVOList / deleteByUserIdAndProductId 等联表操作方法。
     * 通过 @RequiredArgsConstructor 自动注入，不用写 @Autowired。
     */
    private final CartMapper cartMapper;

    // ==================== 获取购物车列表 ====================

    /**
     * 获取购物车列表
     * <p>
     * 联表查询 cart + product，返回每个商品在购物车中的信息。
     * 按加入时间倒序排列（最新的排最前）。
     *
     * @param userId 当前用户 ID
     * @return 购物车 VO 列表（不会返回 null）
     */
    @Override
    public List<CartVO> getCartList(Long userId) {
        log.info("查询购物车列表: userId={}", userId);

        // 调用 CartMapper 的自定义方法，执行联表查询
        // SQL：cart LEFT JOIN product，按 create_time DESC 排序
        List<CartVO> cartList = cartMapper.selectCartVOList(userId);

        log.info("购物车列表查询完成: userId={}, 共 {} 件商品", userId, cartList.size());
        return cartList;
    }

    // ==================== 添加商品到购物车 ====================

    /**
     * 添加商品到购物车
     * <p>
     * 业务逻辑（upsert）：
     * 1. 先查这个用户的购物车里有没有这个商品
     * 2. 如果有 → 把数量加起来（原数量 + 新数量）
     * 3. 如果没有 → 新建一条记录
     *
     * @param userId    当前用户 ID
     * @param productId 商品 ID
     * @param quantity  添加数量
     */
    @Override
    public void addCart(Long userId, Long productId, Integer quantity) {
        log.info("添加购物车: userId={}, productId={}, quantity={}", userId, productId, quantity);

        // ===== 1. 查一下这个用户的购物车里有没有这个商品 =====
        // LambdaQueryWrapper 是 MyBatis-Plus 的条件构造器
        // eq(字段, 值) → 生成 WHERE 字段 = 值
        LambdaQueryWrapper<Cart> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Cart::getUserId, userId)          // WHERE user_id = ?
               .eq(Cart::getProductId, productId);   // AND product_id = ?

        // selectOne → 查一条记录（有 UNIQUE KEY 保证不会有多条）
        Cart existingCart = cartMapper.selectOne(wrapper);

        if (existingCart != null) {
            // ===== 2. 已经有了 → 增加数量 =====
            // 原数量 + 新数量，比如之前有 2 件再加 1 件 → 变成 3 件
            int newQuantity = existingCart.getQuantity() + quantity;
            existingCart.setQuantity(newQuantity);
            cartMapper.updateById(existingCart);
            log.info("购物车已有此商品，更新数量: cartId={}, newQuantity={}", existingCart.getId(), newQuantity);
        } else {
            // ===== 3. 没有 → 新增记录 =====
            Cart newCart = new Cart();
            newCart.setUserId(userId);
            newCart.setProductId(productId);
            newCart.setQuantity(quantity);
            // ⚠️ Cart 不继承 BaseEntity，createTime 不会自动填充，需要手动 set
            newCart.setCreateTime(LocalDateTime.now());
            cartMapper.insert(newCart);
            log.info("购物车新增商品: cartId={}, productId={}", newCart.getId(), productId);
        }
    }

    // ==================== 更新数量 ====================

    /**
     * 更新购物车中某个商品的数量
     * <p>
     * 前端把数量改成了新值，这里直接覆盖（不是"加多少"，是"变成多少"）。
     *
     * @param userId    当前用户 ID
     * @param productId 商品 ID
     * @param quantity  新的数量（>= 1）
     */
    @Override
    public void updateQuantity(Long userId, Long productId, Integer quantity) {
        log.info("更新购物车数量: userId={}, productId={}, quantity={}", userId, productId, quantity);

        // 参数校验：数量不能小于 1
        // 虽然前端 el-input-number 的 min=1，但后端也要校验（后端永远不要相信前端传来的数据）
        if (quantity < 1) {
            log.warn("更新数量失败: 数量不能小于1, quantity={}", quantity);
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "数量不能小于1");
        }

        // 执行更新：UPDATE cart SET quantity = ? WHERE user_id = ? AND product_id = ?
        int updated = cartMapper.updateQuantityByUserIdAndProductId(userId, productId, quantity);

        // updated == 0 → 没找到这条记录，说明购物车里没有这个商品
        if (updated == 0) {
            log.warn("更新数量失败: 购物车中不存在此商品, userId={}, productId={}", userId, productId);
            throw new BusinessException(ResultCodeEnum.NOT_FOUND, "购物车中不存在此商品");
        }

        log.info("更新购物车数量成功: userId={}, productId={}, quantity={}", userId, productId, quantity);
    }

    // ==================== 删除购物车商品 ====================

    /**
     * 删除购物车中的某个商品
     * <p>
     * 根据 userId + productId 物理删除。
     * 带上 userId 是安全考虑：防止传别人的 productId 删别人的购物车。
     *
     * @param userId    当前用户 ID
     * @param productId 商品 ID
     */
    @Override
    public void removeCart(Long userId, Long productId) {
        log.info("删除购物车商品: userId={}, productId={}", userId, productId);

        // 调用 CartMapper 自定义方法：DELETE FROM cart WHERE user_id = ? AND product_id = ?
        int deleted = cartMapper.deleteByUserIdAndProductId(userId, productId);

        if (deleted == 0) {
            log.warn("删除购物车失败: 购物车中不存在此商品, userId={}, productId={}", userId, productId);
            throw new BusinessException(ResultCodeEnum.NOT_FOUND, "购物车中不存在此商品");
        }

        log.info("删除购物车商品成功: userId={}, productId={}", userId, productId);
    }

    // ==================== 清空购物车 ====================

    /**
     * 清空当前用户的所有购物车记录
     * <p>
     * 直接物理删除该用户购物车里的所有商品。
     *
     * @param userId 当前用户 ID
     */
    @Override
    public void clearCart(Long userId) {
        log.info("清空购物车: userId={}", userId);

        // 调用 CartMapper 自定义方法：DELETE FROM cart WHERE user_id = ?
        int deleted = cartMapper.deleteByUserId(userId);

        log.info("清空购物车完成: userId={}, 共删除 {} 条", userId, deleted);
    }
}