package com.happymart.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.happymart.common.exception.BusinessException;
import com.happymart.common.result.ResultCodeEnum;
import com.happymart.entity.Cart;
import com.happymart.entity.Product;            // 商品实体 → 查库存用
import com.happymart.mapper.CartMapper;
import com.happymart.mapper.ProductMapper;       // 商品 Mapper → 加购/改数量时查库存（v1.13）
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

    /**
     * 商品 Mapper（v1.13 新增）
     * <p>
     * 加购 / 改数量时查商品库存，校验"购物车累计数量"不超过库存上限，
     * 修复"库存 9 的商品可以反复加购到购物车 18 个"的 bug。
     */
    private final ProductMapper productMapper;

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

        // 参数校验：数量不能小于 1（与 updateQuantity 保持一致）
        // 前端虽然限制了输入，但后端永远不要相信前端数据（可被绕过/篡改），
        // 否则会插入一条 quantity=0 甚至负数的购物车记录
        if (quantity == null || quantity < 1) {
            log.warn("添加购物车失败: 数量不能小于1, quantity={}", quantity);
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "数量不能小于1");
        }

        // ===== 1. 查商品库存（同时校验商品存在、未被逻辑删除）=====
        Product product = productMapper.selectById(productId);
        if (product == null) {
            log.warn("添加购物车失败: 商品不存在或已删除, productId={}", productId);
            throw new BusinessException(ResultCodeEnum.NOT_FOUND, "商品不存在");
        }
        Integer stock = product.getStock();   // 商品库存（null 视为不限制，防御脏数据）

        // ===== 2. 查一下这个用户的购物车里有没有这个商品 =====
        // LambdaQueryWrapper 是 MyBatis-Plus 的条件构造器
        // eq(字段, 值) → 生成 WHERE 字段 = 值
        LambdaQueryWrapper<Cart> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Cart::getUserId, userId)          // WHERE user_id = ?
               .eq(Cart::getProductId, productId);   // AND product_id = ?

        // selectOne → 查一条记录（有 UNIQUE KEY 保证不会有多条）
        Cart existingCart = cartMapper.selectOne(wrapper);

        // 购物车已有该商品数量（没有则为 0）
        int existingQty = existingCart == null ? 0 : existingCart.getQuantity();

        // ★ 库存累计校验（v1.13，修复用户报告的 bug）：
        // 加购【不扣库存】（下单才扣），但购物车累计数量不能超过库存上限。
        // 场景：库存 9 → 加购 9（购物车 9）→ 返回详情页再加购 9 → 已有 9 + 本次 9 = 18 > 库存 9，
        // 必须拦截，否则购物车数量虚高、到结算下单才报库存不足。
        // 前端只能校验"本次输入 ≤ 库存"（v1.8.1），看不到购物车已有数量，
        // 所以"已有 + 本次"的累计校验必须放后端兜底（前端可被绕过，后端永远要自证）。
        if (stock != null && existingQty + quantity > stock) {
            int maxCanAdd = Math.max(0, stock - existingQty);   // 本次最多还能加的数量
            log.warn("添加购物车失败: 累计数量超库存, productId={}, existingQty={}, quantity={}, stock={}",
                    productId, existingQty, quantity, stock);
            throw new BusinessException(ResultCodeEnum.STOCK_NOT_ENOUGH,
                    "库存不足：该商品库存仅 " + stock + " 件，购物车已有 " + existingQty + " 件，本次最多还能加 " + maxCanAdd + " 件");
        }

        if (existingCart != null) {
            // ===== 3. 已经有了 → 增加数量 =====
            // 原数量 + 新数量，比如之前有 2 件再加 1 件 → 变成 3 件（累计已在上方校验不超库存）
            int newQuantity = existingQty + quantity;
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

        // 参数校验：数量不能为 null 且不能小于 1（与 addCart 保持一致）
        // 虽然前端 el-input-number 的 min=1，但后端也要校验（后端永远不要相信前端传来的数据）
        if (quantity == null || quantity < 1) {
            log.warn("更新数量失败: 数量不能小于1, quantity={}", quantity);
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "数量不能小于1");
        }

        // ★ 库存校验（v1.13）：改后的数量不能超过商品库存，防购物车数量虚高
        // （与 addCart 的累计校验一致——这里 quantity 就是"改后总数"，直接和库存比即可）
        Product product = productMapper.selectById(productId);
        if (product == null) {
            log.warn("更新数量失败: 商品不存在或已删除, productId={}", productId);
            throw new BusinessException(ResultCodeEnum.NOT_FOUND, "商品不存在");
        }
        if (product.getStock() != null && quantity > product.getStock()) {
            log.warn("更新数量失败: 超库存, productId={}, quantity={}, stock={}",
                    productId, quantity, product.getStock());
            throw new BusinessException(ResultCodeEnum.STOCK_NOT_ENOUGH,
                    "库存不足：该商品库存仅 " + product.getStock() + " 件");
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