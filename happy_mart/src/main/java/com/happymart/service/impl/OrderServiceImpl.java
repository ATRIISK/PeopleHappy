package com.happymart.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.happymart.common.exception.BusinessException;
import com.happymart.common.result.ResultCodeEnum;
import com.happymart.entity.Address;
import com.happymart.entity.Order;
import com.happymart.entity.OrderItem;
import com.happymart.mapper.AddressMapper;
import com.happymart.mapper.CartMapper;
import com.happymart.mapper.OrderItemMapper;
import com.happymart.mapper.OrderMapper;
import com.happymart.mapper.ProductMapper;
import com.happymart.service.OrderService;
import com.happymart.vo.CartVO;
import com.happymart.vo.OrderVO;
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
    private final ProductMapper productMapper;
    private final AddressMapper addressMapper;

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

        order.setStatus(4);     // 4=已取消
        orderMapper.updateById(order);
        log.info("订单已取消: id={}", orderId);
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

    // ==================== 私有方法 ====================

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
