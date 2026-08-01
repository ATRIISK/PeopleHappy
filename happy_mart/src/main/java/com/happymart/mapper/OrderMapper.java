package com.happymart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.happymart.entity.Order;
import com.happymart.vo.OrderVO;
import org.apache.ibatis.annotations.Param;

/**
 * 订单 Mapper 接口
 *
 * 继承 BaseMapper<Order>，自带标准 CRUD
 * 自定义方法：
 * - selectOrderVOById → 查单个订单详情（含订单项和商品信息）
 * - selectOrderVOListByUserId → 分页查用户订单列表（含订单项和商品信息）
 * - updateStock → 安全扣减商品库存（带库存检查）
 * - selectByOrderNo → 根据商户订单号查订单（支付宝异步通知只带 out_trade_no，没有订单主键ID）
 */
public interface OrderMapper extends BaseMapper<Order> {

    /**
     * 根据订单ID查询订单详情（含订单项和商品名称/图片）
     * 联表：order LEFT JOIN order_item LEFT JOIN product
     *
     * @param id 订单ID
     * @return 订单 VO（含 items 列表）
     */
    OrderVO selectOrderVOById(@Param("id") Long id);

    /**
     * 分页查询用户的订单列表（含订单项和商品信息）
     *
     * @param page   分页对象（MyBatis-Plus 自动处理）
     * @param userId 用户ID
     * @param status 订单状态（null=查全部）
     * @return 分页结果
     */
    IPage<OrderVO> selectOrderVOListByUserId(
            Page<?> page,
            @Param("userId") Long userId,
            @Param("status") Integer status);

    /**
     * 扣减商品库存（安全扣减）
     *
     * UPDATE product SET stock = stock - #{quantity}
     * WHERE id = #{productId} AND stock >= #{quantity}
     *
     * WHERE stock >= #{quantity} 是关键：
     * 如果库存不够，影响行数为 0，Java 代码抛异常回滚
     *
     * @param productId 商品ID
     * @param quantity  扣减数量
     * @return 影响行数（0=库存不足）
     */
    int updateStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);

    /**
     * 恢复商品库存（取消订单/退单时回滚）
     *
     * UPDATE product SET stock = stock + #{quantity}
     * WHERE id = #{productId}
     *
     * 与 updateStock 对应，取消或退单时把扣掉的库存加回来。
     * 不需要 stock >= 0 检查，因为只是恢复原来的库存，不会超卖。
     *
     * @param productId 商品ID
     * @param quantity  恢复数量
     * @return 影响行数
     */
    int restoreStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);

    /**
     * 根据商户订单号查询订单
     * 支付宝异步通知回调里只带 out_trade_no（对应 order.order_no），不带订单主键ID
     *
     * @param orderNo 商户订单号
     * @return 订单实体
     */
    Order selectByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 条件更新：取消"待支付"订单（防并发竞态）
     *
     * UPDATE `order` SET status = 4 WHERE id = #{id} AND status = 0
     *
     * ★ WHERE status = 0 是关键（条件更新 = 原子操作）：
     * 在 30 分钟超时边界，可能出现"用户正在支付"和"超时取消"同时执行：
     * - 如果用户已经支付成功（status 已变成 1）→ 影响行数 = 0 → 不取消，钱不会白扣
     * - 如果还没支付（status = 0）→ 影响行数 = 1 → 取消成功
     * 避免"已支付的订单被误取消"和"库存被重复恢复"。
     *
     * @param id 订单ID
     * @return 影响行数：1=取消成功，0=状态已变（已支付/已取消）
     */
    int cancelPendingOrder(@Param("id") Long id);

    /**
     * 条件更新：把"待支付"订单标记为"已支付"（支付宝回调/主动查询兜底用）
     *
     * UPDATE `order` SET status = 1, transaction_id = #{transactionId}, pay_time = NOW()
     * WHERE id = #{id} AND status = 0
     *
     * ★ WHERE status = 0 的作用：
     * 防止支付宝重复通知导致重复处理（第一次成功 status=1，重复通知时影响行数=0 自动跳过）。
     * 同时保证超时取消与支付回调并发时，只有一个操作能成功修改状态。
     *
     * @param id            订单ID
     * @param transactionId 支付宝交易号 trade_no
     * @return 影响行数：1=标记成功，0=订单已不是待支付状态
     */
    int markOrderPaid(@Param("id") Long id, @Param("transactionId") String transactionId);

    /**
     * 条件更新：把"已支付"订单标记为"已退款"（退单时用）
     *
     * UPDATE `order` SET status = 5 WHERE id = #{id} AND status = 1
     *
     * ★ WHERE status = 1 的作用：
     * 防止并发退单/重复退单时库存被重复恢复——
     * 只有第一个把状态从 1 改成 5 的请求（影响行数=1）才会恢复库存，
     * 后面的重复请求影响行数=0，直接跳过。
     *
     * @param id 订单ID
     * @return 影响行数：1=退款状态标记成功，0=订单已不是已支付状态
     */
    int refundPaidOrder(@Param("id") Long id);
}
