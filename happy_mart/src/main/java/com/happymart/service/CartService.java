package com.happymart.service;

import com.happymart.vo.CartVO;

import java.util.List;

/**
 * 购物车服务接口
 * <p>
 * Service 接口定义了购物车模块对外提供的所有功能。
 * 实现类 CartServiceImpl 里写具体的业务逻辑。
 * <p>
 * 为什么要写接口？
 * 1. 面向接口编程，Controller 只依赖接口，不依赖实现
 * 2. 以后想换实现方式（比如从 MySQL 换成 Redis）不用改 Controller
 * 3. 方便写单元测试（可以 Mock 接口）
 * 4. 和项目里其他 Service 保持一致的风格
 * <p>
 * 购物车 5 个功能：
 * - 获取列表 → 查当前用户购物车所有商品
 * - 添加商品 → 有则加数量，无则新增
 * - 更新数量 → 修改某个商品的数量
 * - 删除商品 → 移除某个商品
 * - 清空购物车 → 全部移除
 */
public interface CartService {

    /**
     * 获取当前用户的购物车列表
     * <p>
     * 联表查询 cart + product，返回带商品信息的 VO 列表。
     * 按加入时间倒序排列（最新的在最前面）。
     *
     * @param userId 当前登录用户的 ID（从 token 解析）
     * @return 购物车 VO 列表（可能为空列表，不会为 null）
     */
    List<CartVO> getCartList(Long userId);

    /**
     * 添加商品到购物车
     * <p>
     * 如果该用户购物车中已经有这个商品了，就把数量加上去。
     * 如果没有，就新增一条记录。
     * <p>
     * 比如：
     * - 购物车里已经有"华为手机 × 2"，再加 1 个 → 变成"华为手机 × 3"
     * - 购物车里没有"华为手机" → 新增"华为手机 × 1"
     *
     * @param userId    当前登录用户的 ID
     * @param productId 要添加的商品 ID
     * @param quantity  添加的数量（前端传来的是 1，但也可以传其他值）
     */
    void addCart(Long userId, Long productId, Integer quantity);

    /**
     * 更新购物车中某个商品的数量
     * <p>
     * 前端点了加减按钮或者直接输入数字后调用。
     * 把某个商品的数量设成新值（不是"加多少"，而是"变成多少"）。
     * <p>
     * 比如当前数量是 2，前端传 quantity=5 → 变成 5。
     *
     * @param userId    当前登录用户的 ID
     * @param productId 商品 ID
     * @param quantity  新的数量（>= 1）
     */
    void updateQuantity(Long userId, Long productId, Integer quantity);

    /**
     * 从购物车删除某个商品
     * <p>
     * 根据 userId + productId 删除。
     * 物理删除（直接 DELETE FROM cart），不是逻辑删除。
     *
     * @param userId    当前登录用户的 ID
     * @param productId 要删除的商品 ID
     */
    void removeCart(Long userId, Long productId);

    /**
     * 清空当前用户的购物车
     * <p>
     * 删除该用户的所有购物车记录。
     * 物理删除。
     *
     * @param userId 当前登录用户的 ID
     */
    void clearCart(Long userId);
}