package com.happymart.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.happymart.common.exception.BusinessException;
import com.happymart.config.RabbitMQConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import com.happymart.common.result.ResultCodeEnum;
import com.happymart.entity.Address;
import com.happymart.entity.Order;
import com.happymart.entity.OrderItem;
import com.happymart.mapper.AddressMapper;
import com.happymart.mapper.CartMapper;
import com.happymart.mapper.OrderItemMapper;
import com.happymart.mapper.OrderMapper;
import com.happymart.service.AlipayService;
import com.happymart.service.OrderService;
import com.happymart.service.ProductService;
import com.happymart.vo.CartVO;
import com.happymart.vo.OrderVO;
import com.happymart.vo.PayVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 订单服务实现类
 *
 * 核心方法：createOrder
 * 整个方法在一个事务中执行（@Transactional）
 * 任何步骤失败都会回滚全部操作
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final CartMapper cartMapper;
    private final AddressMapper addressMapper;
    private final AlipayService alipayService;
    // ===== RabbitMQ 消息队列：用于下单后发送"订单超时取消"延迟消息 =====
    // 通过 @RequiredArgsConstructor 自动注入（final 字段）
    private final RabbitTemplate rabbitTemplate;

    /**
     * 商品 Service → 用于清除商品详情缓存（code-review 修复）
     * <p>
     * 为什么订单模块要依赖商品模块？
     *   下单扣库存 / 取消退单恢复库存 都会改 product 表的 stock 字段，
     *   而商品详情页有 Redis 缓存（product:detail，30 分钟过期）。
     *   如果库存变了不清缓存，用户会看到旧库存（最多 30 分钟）。
     *   所以库存变化后要调 productService.clearProductDetailCache() 立刻清掉缓存。
     *   这是"缓存一致性"的必要处理，不是多余的模块耦合。
     */
    private final ProductService productService;

    @Override
    public OrderVO createOrder(Long userId, Long addressId, List<Long> productIds) {
        log.info("创建订单: userId={}, addressId={}, productIds={}", userId, addressId, productIds);

        // ===== 1. 获取当前用户的购物车商品（联表查，同时拿到商品信息） =====
        List<CartVO> cartList = cartMapper.selectCartVOList(userId);
        if (cartList == null || cartList.isEmpty()) {
            log.warn("创建订单失败: 购物车为空, userId={}", userId);
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "购物车为空，请先添加商品");
        }

        // ===== 1.5 如果指定了 productIds，只保留被选中的商品 =====
        // 前端勾选了哪几件商品，就把它们的 productId 传过来
        // 没传 productIds（或为空）则默认结算全部购物车
        if (productIds != null && !productIds.isEmpty()) {
            // 用 filter 过滤：只保留 productId 在 productIds 列表中的项
            cartList = cartList.stream()
                    .filter(item -> productIds.contains(item.getProductId()))
                    .collect(Collectors.toList());

            // 过滤后如果为空 → 说明传的商品 ID 都不在购物车里
            if (cartList.isEmpty()) {
                log.warn("创建订单失败: 选中的商品不在购物车中, productIds={}", productIds);
                throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "请选择要结算的商品");
            }

            log.info("已过滤购物车: 选中 {} 件商品", cartList.size());
        }

        // ===== 2. 检查每个商品的库存是否足够 =====
        for (CartVO cartItem : cartList) {
            if (cartItem.getStock() == null || cartItem.getQuantity() > cartItem.getStock()) {
                log.warn("库存不足: productId={}, 库存={}, 需要={}",
                        cartItem.getProductId(), cartItem.getStock(), cartItem.getQuantity());
                throw new BusinessException(ResultCodeEnum.STOCK_NOT_ENOUGH,
                        "商品「" + cartItem.getName() + "」库存不足");
            }
        }

        // ===== 3. 生成订单号 =====
        // 格式：yyyyMMddHHmmss + 4位随机数字（共18位）
        String orderNo = generateOrderNo();

        // ===== 4. 计算总金额（从数据库的价格计算，不是前端传的） =====
        // 防止用户篡改价格：前端显示的金额仅供参考，实际以数据库价格为准
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (CartVO cartItem : cartList) {
            BigDecimal subtotal = cartItem.getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity()));
            totalAmount = totalAmount.add(subtotal);
        }

        // ===== 5. 创建订单记录 =====
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setTotalAmount(totalAmount);
        order.setStatus(0);     // 0=待支付
        order.setAddressId(addressId);
        order.setCreateTime(LocalDateTime.now());

        orderMapper.insert(order);
        log.info("订单记录已创建: id={}, orderNo={}", order.getId(), orderNo);

        // ===== 6. 创建订单项（价格快照） =====
        List<OrderItem> orderItems = cartList.stream().map(cartItem -> {
            OrderItem item = new OrderItem();
            item.setOrderId(order.getId());
            item.setProductId(cartItem.getProductId());
            item.setQuantity(cartItem.getQuantity());
            item.setPrice(cartItem.getPrice());     // ★ 价格快照：商品当前价格
            return item;
        }).collect(Collectors.toList());

        orderItemMapper.insertBatch(orderItems);
        log.info("订单项已创建: 共 {} 条", orderItems.size());

        // ===== 7. 扣减库存（逐个扣减，带库存检查） =====
        for (CartVO cartItem : cartList) {
            int affected = orderMapper.updateStock(cartItem.getProductId(), cartItem.getQuantity());
            if (affected == 0) {
                // 虽然前面检查过库存，但并发情况下可能有其他用户同时下单
                // 导致库存被扣完，这里会抛异常触发事务回滚
                log.error("扣减库存失败（并发冲突）: productId={}, quantity={}",
                        cartItem.getProductId(), cartItem.getQuantity());
                throw new BusinessException(ResultCodeEnum.STOCK_NOT_ENOUGH,
                        "商品「" + cartItem.getName() + "」库存不足，请重新下单");
            }
        }
        // ★ 扣库存成功后，清除这些商品的详情缓存（code-review 修复）
        // 否则用户下单后，商品详情页会显示旧库存最多 30 分钟
        // 清缓存是幂等操作：即使后面某步抛异常事务回滚，清掉的缓存也无害（下次查库会回填正确数据）
        for (CartVO cartItem : cartList) {
            productService.clearProductDetailCache(cartItem.getProductId());
        }
        log.info("库存扣减完成");

        // ===== 8. 清空购物车（只删除已结算的商品） =====
        // 如果指定了 productIds → 只删除被选中的商品（留着未勾选的）
        // 如果没指定 productIds → 全部清空（兼容旧版本行为）
        if (productIds != null && !productIds.isEmpty()) {
            // 遍历已结算的商品列表，逐个从购物车中删除
            // 这样未勾选的商品还会留在购物车里
            for (CartVO cartItem : cartList) {
                cartMapper.deleteByUserIdAndProductId(userId, cartItem.getProductId());
            }
            log.info("购物车已清理: 删除了 {} 件已结算的商品", cartList.size());
        } else {
            // 没有指定 productIds → 默认全部下单 → 全部清空
            cartMapper.deleteByUserId(userId);
            log.info("购物车已清空: userId={}", userId);
        }

        // ===== 9. 查完整订单数据返回 =====
        OrderVO orderVO = orderMapper.selectOrderVOById(order.getId());
        log.info("订单创建成功: orderNo={}, totalAmount={}", orderNo, totalAmount);

        // ===== ★ 发送延迟消息到 RabbitMQ（订单超时取消的兜底机制） =====
        // 开发文档 §7.4 超时取消流程：
        //   订单创建 → RabbitMQ 延迟队列（30分钟）→ 查询订单状态
        //   → 如果 status=0（待支付）→ 更新为 4（已取消）+ 恢复库存
        //
        // 消息流向：进入 order.delay.ttl.queue（TTL=30分钟）
        // - 用户 30 分钟内付了款 → 消息过期，消费者检查状态时会跳过（不重复取消）
        // - 用户 30 分钟还没付款 → 消息进死信队列 → OrderTimeoutConsumer 取消订单
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.ORDER_EXCHANGE,          // 交换机：order.exchange
                    RabbitMQConfig.ORDER_DELAY_ROUTING_KEY, // 路由键：order.delay
                    order.getId()                           // 消息体：订单ID
            );
            log.info("已发送订单超时取消延迟消息: orderId={}", order.getId());
        } catch (Exception e) {
            // RabbitMQ 发送失败不影响主流程
            // 订单已经创建成功了，不能因为 MQ 挂了导致下单失败
            // 打日志告警，后续可通过定时任务补偿
            log.error("发送订单超时取消消息失败: orderId={}, error={}", order.getId(), e.getMessage(), e);
        }

        return orderVO;
    }

    @Override
    public IPage<OrderVO> getOrderList(Long userId, Integer status, Integer page, Integer size) {
        log.info("查询订单列表: userId={}, status={}, page={}, size={}", userId, status, page, size);

        Page<OrderVO> pageParam = new Page<>(page, size);
        IPage<OrderVO> result = orderMapper.selectOrderVOListByUserId(pageParam, userId, status);

        log.info("订单列表查询完成: 共 {} 条, 当前页 {}", result.getTotal(), result.getCurrent());
        return result;
    }

    @Override
    public OrderVO getOrderDetail(Long userId, Long orderId) {
        log.info("查询订单详情: id={}, userId={}", orderId, userId);

        // 先查订单实体，校验所有权
        Order order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            log.warn("订单不存在或无权访问: orderId={}, userId={}", orderId, userId);
            throw new BusinessException(ResultCodeEnum.ORDER_NOT_FOUND);
        }

        // 再查完整数据（含订单项）
        OrderVO orderVO = orderMapper.selectOrderVOById(orderId);
        return orderVO;
    }

    @Override
    public void cancelOrder(Long userId, Long orderId) {
        log.info("取消订单: id={}, userId={}", orderId, userId);

        // 查订单 + 校验所有权
        Order order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException(ResultCodeEnum.ORDER_NOT_FOUND);
        }

        // 只有"待支付"的订单才能取消
        if (order.getStatus() != 0) {
            log.warn("取消订单失败: 当前状态不允许取消, status={}", order.getStatus());
            throw new BusinessException(ResultCodeEnum.ORDER_STATUS_ERROR, "当前订单状态不允许取消");
        }

        // ★ 条件更新：status 0 → 4（原子操作，WHERE status=0 防并发）
        // 30 分钟边界可能出现"用户正在支付"和"取消订单"同时执行：
        // - 用户已支付成功（status 变成 1）→ 影响行数 0 → 不取消，避免钱被白扣
        // - 用户还没支付（status = 0）→ 影响行数 1 → 取消成功
        int affected = orderMapper.cancelPendingOrder(orderId);
        if (affected == 0) {
            log.warn("取消订单失败: 订单状态已被修改（可能已支付）, orderId={}", orderId);
            throw new BusinessException(ResultCodeEnum.ORDER_STATUS_ERROR, "订单状态已变化，无法取消");
        }

        // ★ 取消成功（状态确认变成 4）后才恢复库存
        // 恢复商品库存（下单时扣了库存，取消要加回来）
        // 开发文档 §7.4 超时取消流程也要求"如果有扣库存 → 回滚库存"
        restoreStockByOrderId(orderId);

        log.info("订单已取消: id={}, orderId={}，库存已回滚", orderId, orderId);
    }

    @Override
    public void cancelOrderByTimeout(Long orderId) {
        log.info("系统自动取消超时订单: orderId={}", orderId);

        // 1. 查订单（不需要校验 userId，这是系统自动操作，不是用户请求）
        //    主要目的是确认订单存在 + 拿 orderNo 用于日志
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            log.warn("超时取消失败: 订单不存在, orderId={}", orderId);
            return; // 订单不存在 → 不用处理
        }

        // 2. ★ 条件更新：status 0 → 4（原子操作，先改状态，再恢复库存）
        //    这是防并发竞态的关键（开发文档 §7.4 超时取消流程）：
        //    30 分钟边界可能出现"用户刚好支付成功"：
        //    - 用户已支付（status 已是 1）→ 影响行数 0 → 直接 return，不取消已支付订单
        //    - 用户还没支付（status = 0）→ 影响行数 1 → 取消成功，继续恢复库存
        //    先改状态再恢复库存，保证即使与支付并发，也只有一个操作能恢复库存（不会双倍）
        int affected = orderMapper.cancelPendingOrder(orderId);
        if (affected == 0) {
            // 用户已经付了款（status=1）或者已经取消/退款了
            // 消息虽然过期了但不需要做任何操作
            log.info("超时取消跳过: 订单已处理, orderId={}, status={}", orderId, order.getStatus());
            return;
        }

        // 3. 取消成功（状态确认是 4）后才恢复库存
        //    下单时扣了库存，取消要加回来；与 cancelOrder 复用同一个私有方法
        restoreStockByOrderId(orderId);

        log.info("订单已超时自动取消: orderId={}, orderNo={}，库存已回滚",
                orderId, order.getOrderNo());
    }

    @Override
    public void refundOrder(Long userId, Long orderId) {
        log.info("退单退款: id={}, userId={}", orderId, userId);

        // 查订单 + 校验所有权
        Order order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException(ResultCodeEnum.ORDER_NOT_FOUND);
        }

        // 只有"已支付"未发货的订单才能退单
        // 已发货（2）需要联系客服，已完成（3）不能退
        if (order.getStatus() != 1) {
            log.warn("退单失败: 当前状态不允许退单, status={}", order.getStatus());
            throw new BusinessException(ResultCodeEnum.ORDER_STATUS_ERROR, "当前订单状态不允许退单");
        }

        // ===== ★ 调用支付宝退款接口（先退款再改状态+恢复库存） =====
        // 支付宝交易号在 order.transactionId 中，支付成功时由 handlePaid() 回填
        String tradeNo = order.getTransactionId();
        if (tradeNo == null || tradeNo.isEmpty()) {
            log.error("退单失败: 订单无支付宝交易号, orderId={}", orderId);
            throw new BusinessException(ResultCodeEnum.PAY_FAIL, "该订单无支付宝交易记录，退款失败");
        }
        // out_request_no 传订单号 orderNo 作为幂等键：
        // 同一订单重复调用退款接口，支付宝返回相同结果而不会重复退款，
        // 防止"支付宝退款成功但本地事务回滚"后重试时被支付宝拒绝。
        boolean refundSuccess = alipayService.tradeRefund(
                tradeNo, order.getTotalAmount(), order.getOrderNo(), order.getOrderNo());
        if (!refundSuccess) {
            log.error("退单失败: 支付宝退款接口返回失败, orderId={}, tradeNo={}", orderId, tradeNo);
            throw new BusinessException(ResultCodeEnum.PAY_FAIL, "退款失败，请稍后重试或联系客服");
        }

        // ★ 条件更新：status 1 → 5（原子操作，WHERE status=1 防并发）
        // 防止并发/重复退单导致库存被重复恢复：
        // - 只有第一个把 status 从 1 改成 5 的请求（影响行数 1）才会恢复库存
        // - 重复退单时影响行数 0 → 直接跳过（钱已经退了，不能报错也不能重复恢复库存）
        int affected = orderMapper.refundPaidOrder(orderId);
        if (affected == 0) {
            log.warn("退单跳过: 订单状态已被修改, orderId={}", orderId);
            return;
        }

        // 恢复商品库存（把扣掉的库存加回来）
        restoreStockByOrderId(orderId);

        log.info("退单成功: id={}, orderNo={}, tradeNo={}，库存已回滚，支付宝已退款",
                orderId, order.getOrderNo(), tradeNo);
    }

    @Override
    public void confirmOrder(Long userId, Long orderId) {
        log.info("确认收货: id={}, userId={}", orderId, userId);

        // 查订单 + 校验所有权
        Order order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException(ResultCodeEnum.ORDER_NOT_FOUND);
        }

        // 只有"已发货"的订单才能确认收货
        if (order.getStatus() != 2) {
            log.warn("确认收货失败: 当前状态不允许确认, status={}", order.getStatus());
            throw new BusinessException(ResultCodeEnum.ORDER_STATUS_ERROR, "当前订单状态不允许确认收货");
        }

        order.setStatus(3);     // 3=已完成
        orderMapper.updateById(order);
        log.info("订单已确认收货: id={}", orderId);
    }

    @Override
    public void updateOrderAddress(Long userId, Long orderId, Long newAddressId) {
        log.info("修改订单地址: orderId={}, userId={}, newAddressId={}", orderId, userId, newAddressId);

        // ===== 1. 查订单 + 校验所有权 =====
        // 确保是当前用户的订单，不能改别人的
        Order order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException(ResultCodeEnum.ORDER_NOT_FOUND);
        }

        // ===== 2. 校验订单状态：只有待付款（0）和待发货（1）能改地址 =====
        // 发货后地址已用于物流，不能再修改
        if (order.getStatus() != 0 && order.getStatus() != 1) {
            log.warn("修改地址失败: 当前订单状态不允许修改, status={}", order.getStatus());
            throw new BusinessException(ResultCodeEnum.ORDER_STATUS_ERROR, "当前订单状态不允许修改地址");
        }

        // ===== 3. 校验新地址是否存在且属于当前用户 =====
        // 防止传别人的地址ID
        Address address = addressMapper.selectById(newAddressId);
        if (address == null || !address.getUserId().equals(userId)) {
            log.warn("修改地址失败: 地址不存在或不属于当前用户, addressId={}", newAddressId);
            throw new BusinessException(ResultCodeEnum.ADDRESS_NOT_FOUND);
        }

        // ===== 4. 更新订单的地址ID =====
        order.setAddressId(newAddressId);
        orderMapper.updateById(order);
        log.info("订单地址已修改: orderId={}, newAddressId={}", orderId, newAddressId);
    }

    @Override
    public PayVO pay(Long userId, Long orderId) {
        log.info("发起支付宝扫码支付: orderId={}, userId={}", orderId, userId);

        // 查订单 + 校验所有权
        Order order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException(ResultCodeEnum.ORDER_NOT_FOUND);
        }

        // 只有"待付款"的订单才能发起支付
        if (order.getStatus() != 0) {
            log.warn("发起支付失败: 当前状态不允许支付, status={}", order.getStatus());
            throw new BusinessException(ResultCodeEnum.ORDER_STATUS_ERROR, "当前订单状态不允许支付");
        }

        PayVO payVO = alipayService.createQrOrder(order.getOrderNo(), order.getTotalAmount(), "HappyMart Order Payment");
        log.info("支付宝扫码支付已创建: orderId={}, orderNo={}", orderId, order.getOrderNo());
        return payVO;
    }

    @Override
    public Integer getPayStatus(Long userId, Long orderId) {
        // 查订单 + 校验所有权
        Order order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException(ResultCodeEnum.ORDER_NOT_FOUND);
        }

        // 如果订单还是待付款，主动查支付宝交易状态（兜底方案）
        // 防止异步通知因隧道不稳定丢失后，订单永远卡在"待付款"
        if (order.getStatus() == 0 && order.getOrderNo() != null) {
            AlipayService.TradeQueryResult tradeResult = alipayService.queryTrade(order.getOrderNo());
            if (tradeResult != null
                    && ("TRADE_SUCCESS".equals(tradeResult.getTradeStatus())
                        || "TRADE_FINISHED".equals(tradeResult.getTradeStatus()))) {
                // 支付宝那边已支付 → 同步更新本地订单状态
                log.info("主动查询到支付宝已支付，同步更新订单: orderNo={}, tradeNo={}",
                        order.getOrderNo(), tradeResult.getTradeNo());
                handlePaid(order.getOrderNo(), tradeResult.getTradeNo());
                return 1; // 已支付
            }
        }

        return order.getStatus();
    }

    @Override
    public void handlePaid(String orderNo, String tradeNo) {
        log.info("处理支付宝支付回调: orderNo={}, tradeNo={}", orderNo, tradeNo);

        Order order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            log.error("处理支付回调失败: 订单不存在, orderNo={}", orderNo);
            throw new BusinessException(ResultCodeEnum.ORDER_NOT_FOUND);
        }

        // ★ 条件更新：status 0 → 1（原子操作，WHERE status=0）
        // 有两个作用：
        // 1. 幂等：支付宝可能重复通知，第一次成功后 status=1，重复通知影响行数 0 → 自动跳过
        // 2. 防并发：与超时取消（cancelPendingOrder）并发时，
        //    如果超时先执行把 status 改成 4，这里影响行数 0 → 不会把已取消的订单改回已支付
        int affected = orderMapper.markOrderPaid(order.getId(), tradeNo);
        if (affected == 0) {
            log.info("订单已处理过或状态已变，跳过重复回调: orderNo={}, status={}",
                    orderNo, order.getStatus());
            return;
        }

        log.info("订单支付成功: orderNo={}, tradeNo={}", orderNo, tradeNo);
    }

    // ==================== 私有方法 ====================

    /**
     * 恢复指定订单的所有商品库存
     *
     * 在取消订单/退单时调用，把扣掉的库存加回到 product 表。
     * 遍历 order_item 表拿到每个商品的购买数量，逐个调用 restoreStock。
     *
     * @param orderId 订单 ID
     */
    private void restoreStockByOrderId(Long orderId) {
        // 查订单项（拿到每个商品的购买数量）
        List<OrderItem> items = orderItemMapper.selectByOrderId(orderId);
        for (OrderItem item : items) {
            orderMapper.restoreStock(item.getProductId(), item.getQuantity());

            // ★ 恢复库存后清除该商品的详情缓存（code-review 修复）
            // 否则取消/退单后，详情页库存还是旧的（会显示"库存不足"但实际已恢复）
            // 本方法被 cancelOrder / cancelOrderByTimeout / refundOrder 三处复用，改这一处全覆盖
            productService.clearProductDetailCache(item.getProductId());

            log.debug("库存已恢复: productId={}, quantity={}", item.getProductId(), item.getQuantity());
        }
    }

    /**
     * 生成订单号
     *
     * 格式：yyyyMMddHHmmss + 4位随机数字
     * 例如：202607261530451234
     *
     * 为什么不用雪花算法？
     * 简单够用，18位数字，每秒最多生成10000个订单不会重复
     * 生产环境建议用雪花算法或 Redis 自增序列
     *
     * @return 18位订单号
     */
    private String generateOrderNo() {
        String timePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int randomPart = ThreadLocalRandom.current().nextInt(1000, 10000);
        return timePart + randomPart;
    }
}
