package com.happymart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.happymart.entity.Order;
import com.happymart.vo.OrderVO;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

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
     * WHERE id = #{productId} AND stock >= #{quantity} AND status = 0
     *
     * WHERE stock >= #{quantity} 是关键：
     * 如果库存不够，影响行数为 0，Java 代码抛异常回滚
     * AND status = 0（v1.13）：下架商品即使残留在购物车里，下单扣库存也拦截（影响行数 0 → 回滚）
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

    /**
     * 批量查询订单项（含商品名称/图片），供订单列表分页后组装 items 用
     * <p>
     * 为什么单独拆出来（code-review 修复）？
     * "一对多联表分页"（order LEFT JOIN order_item）会导致 MyBatis-Plus 的
     * count/LIMIT 作用在展开后的订单项行上：总数虚高、一页装不下几单、
     * 一个订单的订单项会被分页截断。所以订单列表分页只查订单主表，
     * 本页所有订单的订单项用这一个 IN 查询一次性查出，Service 层按 orderId 分组组装。
     *
     * @param orderIds 订单ID集合（本页所有订单）
     * @return 订单项列表（每项含 orderId，用于分组）
     */
    List<OrderVO.OrderItemVO> selectOrderItemsByOrderIds(@Param("orderIds") List<Long> orderIds);

    /**
     * 分页查询全部订单（管理后台用，可按状态过滤，联表带下单人用户名）
     *
     * @param page   分页对象（MyBatis-Plus 自动处理）
     * @param status 订单状态（null=查全部）
     * @return 分页结果（含 items 和 username）
     */
    IPage<OrderVO> selectOrderVOListAll(Page<?> page, @Param("status") Integer status);

    /**
     * 统计已成交订单总金额（管理后台 Dashboard 看板用）
     * <p>
     * status IN (1,2,3) = 已支付/已发货/已完成（已付款且未退款）
     * 排除：0待支付（没付钱）、4已取消、5已退款（钱退回去了）
     * 单位：元（和 Order.totalAmount 一致，别和 PaymentLog.totalFee 的"分"混用）
     *
     * @return 总金额；没有已成交订单返回 0
     */
    BigDecimal sumPaidAmount();

    /**
     * 条件更新：把订单状态从 from 改成 to（管理后台发货用）
     * <p>
     * UPDATE `order` SET status = #{to} WHERE id = #{id} AND status = #{from}
     * <p>
     * ★ WHERE status = #{from} 防并发（和 cancelPendingOrder/markOrderPaid/refundPaidOrder 同一套路）：
     * 只有当前状态还是 from 才能改成功（影响行数 1）；
     * 状态已被其他操作改了 → 影响行数 0 → 上层抛"订单状态异常"
     *
     * @param id    订单ID
     * @param from  期望的当前状态
     * @param to    目标状态
     * @return 影响行数：1=修改成功，0=状态已变（并发冲突）
     */
    int updateStatusIf(@Param("id") Long id, @Param("from") Integer from, @Param("to") Integer to);
}
