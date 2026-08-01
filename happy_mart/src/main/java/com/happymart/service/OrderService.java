package com.happymart.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.happymart.vo.OrderVO;
import com.happymart.vo.PayVO;

import java.util.List;

/**
 * 订单服务接口
 *
 * 提供创建订单、查看订单列表、查看订单详情、取消订单、退单退款、确认收货
 * 所有操作都需要 userId（从 token 解析），确保只能操作自己的订单
 *
 * 更新记录：
 * - 2026-07-29 新增 refundOrder（退单退款）
 */
public interface OrderService {

    /**
     * 创建订单（从购物车生成订单）
     *
     * 核心流程：
     * 1. 查购物车列表
     * 2. 按 productIds 过滤（如果传了的话）
     * 3. 检查库存
     * 4. 生成订单号 + 计算总金额
     * 5. 保存订单 + 订单项
     * 6. 扣减库存
     * 7. 清空已结算的购物车商品
     *
     * @param userId     当前用户 ID
     * @param addressId  收货地址 ID
     * @param productIds 要购买的商品 ID 列表（null=全部，非空=只买这些）
     * @return 创建好的订单 VO（含订单项）
     */
    OrderVO createOrder(Long userId, Long addressId, List<Long> productIds);

    /**
     * 分页查询用户订单列表
     *
     * @param userId 当前用户 ID
     * @param status 订单状态（null=查全部）
     * @param page   页码
     * @param size   每页条数
     * @return 分页结果（含订单项）
     */
    IPage<OrderVO> getOrderList(Long userId, Integer status, Integer page, Integer size);

    /**
     * 查询订单详情
     *
     * @param userId  当前用户 ID（校验所有权）
     * @param orderId 订单 ID
     * @return 订单 VO（含订单项）
     */
    OrderVO getOrderDetail(Long userId, Long orderId);

    /**
     * 取消订单（只能取消"待付款"的订单）
     * 取消后恢复商品库存。
     *
     * @param userId  当前用户 ID
     * @param orderId 订单 ID
     */
    void cancelOrder(Long userId, Long orderId);

    /**
     * 退单退款（只能对"已支付"未发货的订单操作）
     *
     * 与 cancelOrder 的区别：
     * - cancelOrder：status=0（待付款）→ 4（已取消），用户没付钱，不用走退款
     * - refundOrder：status=1（已支付）→ 5（已退款），用户已付款，需要调支付宝退款接口 + 恢复库存
     *
     * 退款流程：
     * 1. 校验订单状态（只有 status=1 才能退）
     * 2. 调 AlipayService.tradeRefund() → 支付宝 alipay.trade.refund 接口
     * 3. 恢复商品库存（restoreStockByOrderId）
     * 4. 更新 status=5（已退款）
     *
     * 整个流程在 @Transactional 中执行，退款失败则整体回滚。
     *
     * @param userId  当前用户 ID
     * @param orderId 订单 ID
     */
    void refundOrder(Long userId, Long orderId);

    /**
     * 确认收货（只能操作"已发货"的订单）
     *
     * @param userId  当前用户 ID
     * @param orderId 订单 ID
     */
    void confirmOrder(Long userId, Long orderId);

    /**
     * 修改订单收货地址
     *
     * 只能在"待付款（0）"或"待发货（1）"状态下修改。
     * 发货后不允许再改地址。
     *
     * @param userId        当前用户 ID
     * @param orderId       订单 ID
     * @param newAddressId  新收货地址 ID
     */
    void updateOrderAddress(Long userId, Long orderId, Long newAddressId);

    /**
     * 发起支付宝扫码支付（只能操作"待付款"的订单）
     *
     * @param userId  当前用户 ID
     * @param orderId 订单 ID
     * @return 支付 VO（含 codeUrl，即支付宝 qr_code）
     */
    PayVO pay(Long userId, Long orderId);

    /**
     * 查询订单支付状态（前端下单后轮询用）
     *
     * @param userId  当前用户 ID
     * @param orderId 订单 ID
     * @return 订单状态：0待付款 1已支付 2已发货 3已完成 4已取消 5已退款
     */
    Integer getPayStatus(Long userId, Long orderId);

    /**
     * 处理支付宝异步通知：订单标记为已支付
     * 由 PayNotifyController 验签通过后调用
     *
     * @param orderNo 商户订单号
     * @param tradeNo 支付宝交易号
     */
    void handlePaid(String orderNo, String tradeNo);

    /**
     * 系统自动取消超时订单（RabbitMQ 消费者调用）
     *
     * 和 cancelOrder 的区别：
     * - cancelOrder → 用户主动取消，需要验证 userId（只能取消自己的订单）
     * - cancelOrderByTimeout → 系统自动取消，无需验证 userId（30 分钟未支付自动取消）
     *
     * 逻辑：
     * 1. 查订单
     * 2. 只有 status=0（待支付）才能取消
     * 3. 恢复商品库存（restoreStockByOrderId）
     * 4. 更新 status=4（已取消）
     * 5. 如果已支付（status≠0）→ 跳过，说明用户已经付了
     *
     * @param orderId 订单 ID
     */
    void cancelOrderByTimeout(Long orderId);
}
