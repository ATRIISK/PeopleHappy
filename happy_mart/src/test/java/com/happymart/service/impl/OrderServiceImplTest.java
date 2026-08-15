package com.happymart.service.impl;

import com.happymart.common.exception.BusinessException;
import com.happymart.common.result.ResultCodeEnum;
import com.happymart.entity.Order;
import com.happymart.entity.OrderItem;
import com.happymart.mapper.AddressMapper;
import com.happymart.mapper.CartMapper;
import com.happymart.mapper.OrderItemMapper;
import com.happymart.mapper.OrderMapper;
import com.happymart.service.AlipayService;
import com.happymart.service.ProductService;
import com.happymart.vo.CartVO;
import com.happymart.vo.OrderVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OrderServiceImpl 单元测试（纯 Mockito，不启动 Spring、不连数据库/Redis/RabbitMQ）
 *
 * <p>为什么写这套测试？—— 严格对照开发文档的核心业务规则，验证"最容易被问、最不能出错"的逻辑：
 * <ul>
 *   <li>开发文档 §7.2 下单流程：金额计算（不信任前端）、库存扣减（乐观锁）、清缓存、发超时消息</li>
 *   <li>开发文档 §7.3 支付回调：条件更新幂等（重复通知不重复处理）</li>
 *   <li>开发文档 §7.4 超时取消：条件更新防并发（已支付订单绝不被误取消、库存不重复恢复）</li>
 *   <li>开发文档 §7.5 退单退款：先退款再改状态、幂等键防重复退款、重复退单不重复恢复库存</li>
 * </ul>
 *
 * <p>为什么用 Mockito mock 掉所有 Mapper / Service / RabbitTemplate？
 * 单元测试只测 OrderServiceImpl 自己的业务逻辑，不测数据库 SQL 和第三方接口。
 * 这样测试不依赖本机的 MySQL / Redis / RabbitMQ / 支付宝沙箱环境，任何环境都能一键跑，速度快（毫秒级）。
 * （Mapper 的 SQL 正确性由集成测试覆盖，见项目后续规划。）
 *
 * <p>关于 @Transactional：这里直接 new 出来的 Service 没有 Spring 事务代理，@Transactional 注解被忽略，
 * 方法就是普通方法调用——正好让我们能单独验证"方法内部的逻辑分支"，不必真正提交/回滚事务。
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    /** 被测对象。@InjectMocks 会把下面 7 个 @Mock 通过构造函数注入（OrderServiceImpl 用 @RequiredArgsConstructor） */
    @InjectMocks
    private OrderServiceImpl service;

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private OrderItemMapper orderItemMapper;
    @Mock
    private CartMapper cartMapper;
    @Mock
    private AddressMapper addressMapper;
    @Mock
    private AlipayService alipayService;
    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private ProductService productService;

    // ==================== 一、下单 createOrder（文档 §7.2） ====================

    /**
     * 用例 1：全量结算下单成功（productIds 传 null = 结算购物车全部商品）
     * <p>
     * 验证点（对应文档 §7.2 的 10 个步骤里的关键步骤）：
     * 1. 总金额 = 单价 × 数量（从数据库价格计算，不信任前端）→ 5999 × 2 = 11998
     * 2. 订单号是 18 位（yyyyMMddHHmmss 14 位 + 4 位随机数）
     * 3. 订单初始状态 = 0（待支付）
     * 4. 扣库存、清商品详情缓存、清空购物车、发送超时取消消息
     */
    @Test
    void createOrder_全量结算_成功() {
        Long userId = 1L, addressId = 10L;

        // 购物车里 1 件商品：单价 5999、库存 10、买 2 件
        CartVO item = cartItem(1L, "5999.00", 10, 2);
        when(cartMapper.selectCartVOList(userId)).thenReturn(List.of(item));

        // 模拟 MyBatis insert 后的主键回填（真实环境 BaseMapper.insert 会自动把自增 id 写回实体，mock 要手动做）
        doAnswer(inv -> {
            Order o = inv.getArgument(0, Order.class);
            o.setId(100L);
            return 1;
        }).when(orderMapper).insert(any(Order.class));

        when(orderItemMapper.insertBatch(anyList())).thenReturn(1);   // 批量插入订单项
        when(orderMapper.updateStock(1L, 2)).thenReturn(1);           // 乐观锁扣库存成功
        when(orderMapper.selectOrderVOById(100L)).thenReturn(new OrderVO()); // 查完整订单返回

        // 执行
        OrderVO result = service.createOrder(userId, addressId, null);

        // 断言返回非空
        assertNotNull(result);

        // 捕获 insert 的 Order 实体，验证关键字段
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper).insert(captor.capture());
        Order saved = captor.getValue();
        assertEquals(0, new BigDecimal("11998.00").compareTo(saved.getTotalAmount()), "总金额应=5999×2");
        assertEquals(0, saved.getStatus(), "初始状态应为待支付");
        assertEquals(userId, saved.getUserId());
        assertEquals(addressId, saved.getAddressId());
        assertNotNull(saved.getOrderNo(), "订单号不能为空");
        assertEquals(18, saved.getOrderNo().length(), "订单号应为 18 位（时间戳14位+随机4位）");

        // 验证扣库存、清缓存、清空购物车、发超时消息（消息体=订单ID）
        verify(orderMapper).updateStock(1L, 2);
        verify(productService).clearProductDetailCache(1L);
        verify(cartMapper).deleteByUserId(userId);                  // productIds 为 null → 全部清空
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), eq(100L));
    }

    /**
     * 用例 2：指定 productIds 下单（只结算选中的商品）
     * <p>
     * 验证点（对应文档 §7.2 步骤 1.5 和 8）：
     * 1. 只对选中的商品算金额、扣库存、清缓存
     * 2. 未选中的商品不动（库存不扣、缓存不清）
     * 3. 只删除已结算的商品（deleteByUserIdAndProductId），不清空整个购物车
     */
    @Test
    void createOrder_指定productIds_只结算选中商品() {
        Long userId = 1L, addressId = 10L;

        // 购物车 2 件：商品1（选中，100×2）、商品2（未选中，50×1）
        CartVO selected = cartItem(1L, "100.00", 5, 2);
        CartVO unselected = cartItem(2L, "50.00", 5, 1);
        when(cartMapper.selectCartVOList(userId)).thenReturn(List.of(selected, unselected));

        doAnswer(inv -> {
            Order o = inv.getArgument(0, Order.class);
            o.setId(100L);
            return 1;
        }).when(orderMapper).insert(any(Order.class));
        when(orderItemMapper.insertBatch(anyList())).thenReturn(1);
        when(orderMapper.updateStock(1L, 2)).thenReturn(1);
        when(orderMapper.selectOrderVOById(100L)).thenReturn(new OrderVO());
        when(cartMapper.deleteByUserIdAndProductId(userId, 1L)).thenReturn(1);

        // 只结算商品1
        service.createOrder(userId, addressId, List.of(1L));

        // 金额只含商品1：100×2=200，不包含商品2
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper).insert(captor.capture());
        assertEquals(0, new BigDecimal("200.00").compareTo(captor.getValue().getTotalAmount()));

        // 只扣商品1 的库存，商品2 不扣
        verify(orderMapper).updateStock(1L, 2);
        verify(orderMapper, never()).updateStock(eq(2L), anyInt());
        // 只清商品1 的缓存，商品2 不清
        verify(productService).clearProductDetailCache(1L);
        verify(productService, never()).clearProductDetailCache(2L);
        // 只删已结算的商品1，不清空整个购物车
        verify(cartMapper).deleteByUserIdAndProductId(userId, 1L);
        verify(cartMapper, never()).deleteByUserId(anyLong());
    }

    /**
     * 用例 3：购物车为空 → 抛参数异常（文档 §7.2 步骤 1）
     */
    @Test
    void createOrder_购物车为空_抛参数异常() {
        when(cartMapper.selectCartVOList(1L)).thenReturn(Collections.emptyList());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createOrder(1L, 10L, null));

        assertEquals(ResultCodeEnum.PARAM_ERROR.getCode(), ex.getCode());
        verify(orderMapper, never()).insert(any(Order.class)); // 不该创建订单
    }

    /**
     * 用例 4：库存不足 → 抛库存异常（文档 §7.2 步骤 2）
     */
    @Test
    void createOrder_库存不足_抛库存异常() {
        // 库存 1 件，但要买 2 件
        when(cartMapper.selectCartVOList(1L)).thenReturn(List.of(cartItem(1L, "100.00", 1, 2)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createOrder(1L, 10L, null));

        assertEquals(ResultCodeEnum.STOCK_NOT_ENOUGH.getCode(), ex.getCode());
        verify(orderMapper, never()).insert(any(Order.class));
    }

    /**
     * 用例 5：扣库存并发冲突（乐观锁 WHERE stock >= quantity 影响行数=0）→ 抛库存异常
     * <p>
     * 这是文档 §7.2 步骤 7 强调的并发场景：虽然前置检查库存够，但并发下其他用户可能同时下单把库存抢光，
     * updateStock 返回 0 就要抛异常回滚，不能超卖。
     */
    @Test
    void createOrder_扣库存并发冲突_抛库存异常() {
        when(cartMapper.selectCartVOList(1L)).thenReturn(List.of(cartItem(1L, "100.00", 5, 2)));

        doAnswer(inv -> {
            Order o = inv.getArgument(0, Order.class);
            o.setId(100L);
            return 1;
        }).when(orderMapper).insert(any(Order.class));
        when(orderItemMapper.insertBatch(anyList())).thenReturn(1);
        when(orderMapper.updateStock(1L, 2)).thenReturn(0); // 乐观锁条件不满足，扣库存失败

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createOrder(1L, 10L, null));

        assertEquals(ResultCodeEnum.STOCK_NOT_ENOUGH.getCode(), ex.getCode());
    }

    // ==================== 二、超时取消 cancelOrderByTimeout（文档 §7.4） ====================

    /**
     * 用例 6：待支付订单超时 → 取消 + 恢复库存（文档 §7.4 步骤 5）
     */
    @Test
    void cancelOrderByTimeout_待支付_取消并恢复库存() {
        when(orderMapper.selectById(100L)).thenReturn(order(100L, 1L, 0, "NO1", null, null));
        when(orderMapper.cancelPendingOrder(100L)).thenReturn(1); // 条件更新成功（status 0→4）
        // 订单里有 2 个商品：商品1 买 2 件、商品2 买 1 件
        when(orderItemMapper.selectByOrderId(100L)).thenReturn(List.of(orderItem(1L, 2), orderItem(2L, 1)));
        when(orderMapper.restoreStock(1L, 2)).thenReturn(1);
        when(orderMapper.restoreStock(2L, 1)).thenReturn(1);

        service.cancelOrderByTimeout(100L);

        // 验证：取消状态 + 逐个恢复库存 + 清缓存
        verify(orderMapper).cancelPendingOrder(100L);
        verify(orderMapper).restoreStock(1L, 2);
        verify(orderMapper).restoreStock(2L, 1);
        verify(productService).clearProductDetailCache(1L);
        verify(productService).clearProductDetailCache(2L);
    }

    /**
     * 用例 7：已支付订单超时消息到达 → 跳过，不取消、不恢复库存（文档 §7.4 步骤 5 的核心防并发）
     * <p>
     * 这是超时取消最关键的逻辑：用户在 30 分钟边界刚付完款，
     * cancelPendingOrder 的 WHERE status=0 条件不满足（status 已是 1），影响行数=0 → 直接 return。
     * 绝不能把已付款的订单误取消、更不能恢复库存（否则钱扣了货也没了）。
     */
    @Test
    void cancelOrderByTimeout_已支付_跳过不恢复库存() {
        when(orderMapper.selectById(100L)).thenReturn(order(100L, 1L, 1, "NO1", "TRADE001", "200.00"));
        when(orderMapper.cancelPendingOrder(100L)).thenReturn(0); // 已支付，条件更新失败

        service.cancelOrderByTimeout(100L);

        verify(orderMapper).cancelPendingOrder(100L);
        // 关键：绝不查订单项、绝不恢复库存、绝不清缓存
        verify(orderItemMapper, never()).selectByOrderId(anyLong());
        verify(orderMapper, never()).restoreStock(anyLong(), anyInt());
        verify(productService, never()).clearProductDetailCache(anyLong());
    }

    /**
     * 用例 8：订单不存在 → 静默返回（不抛异常，不影响 MQ 消费者）
     */
    @Test
    void cancelOrderByTimeout_订单不存在_静默返回() {
        when(orderMapper.selectById(999L)).thenReturn(null);

        assertDoesNotThrow(() -> service.cancelOrderByTimeout(999L));
        verify(orderMapper, never()).cancelPendingOrder(anyLong());
    }

    // ==================== 三、退单退款 refundOrder（文档 §7.5） ====================

    /**
     * 用例 9：退单成功 → 先条件更新状态 1→5 → 支付宝退款 → 恢复库存（文档 §7.5，code-review 调整顺序）
     * <p>
     * 验证点：refundPaidOrder 条件更新在前（防 admin 发货并发，见用例 10/12 说明）、
     * 支付宝退款参数正确（tradeNo=交易号、幂等键 out_request_no=订单号）、恢复库存、清缓存。
     */
    @Test
    void refundOrder_成功_退款改状态恢复库存() {
        when(orderMapper.selectById(100L)).thenReturn(order(100L, 1L, 1, "NO1", "TRADE001", "200.00"));
        when(orderMapper.refundPaidOrder(100L)).thenReturn(1); // 条件更新成功（status 1→5）
        // 支付宝退款成功（tradeNo=TRADE001，幂等键 outRequestNo=NO1）
        when(alipayService.tradeRefund(eq("TRADE001"), any(BigDecimal.class), eq("NO1"), eq("NO1"))).thenReturn(true);
        when(orderItemMapper.selectByOrderId(100L)).thenReturn(List.of(orderItem(1L, 2)));
        when(orderMapper.restoreStock(1L, 2)).thenReturn(1);

        service.refundOrder(1L, 100L);

        // 验证退款参数：tradeNo=transactionId、退款金额=订单总额、幂等键=订单号（防止重复退款）
        verify(alipayService).tradeRefund(eq("TRADE001"), any(BigDecimal.class), eq("NO1"), eq("NO1"));
        verify(orderMapper).refundPaidOrder(100L);
        verify(orderMapper).restoreStock(1L, 2);
        verify(productService).clearProductDetailCache(1L);
    }

    /**
     * 用例 10：支付宝退款失败 → 抛支付异常（code-review 调整后顺序：
     * 条件更新 1→5 先执行，支付宝退款失败抛异常，事务回滚使 refundPaidOrder 失效，订单回到已支付）
     */
    @Test
    void refundOrder_支付宝退款失败_抛支付异常() {
        when(orderMapper.selectById(100L)).thenReturn(order(100L, 1L, 1, "NO1", "TRADE001", "200.00"));
        when(orderMapper.refundPaidOrder(100L)).thenReturn(1); // 条件更新先成功
        when(alipayService.tradeRefund(anyString(), any(BigDecimal.class), anyString(), anyString())).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.refundOrder(1L, 100L));

        assertEquals(ResultCodeEnum.PAY_FAIL.getCode(), ex.getCode());
        // 退款失败 → 抛异常 → 事务回滚，绝不能恢复库存
        verify(orderMapper).refundPaidOrder(100L);          // 已执行（新顺序：先条件更新）
        verify(orderMapper, never()).restoreStock(anyLong(), anyInt());
    }

    /**
     * 用例 11：订单没有支付宝交易号 → 抛支付异常（fail-fast 前置校验，不改状态）
     */
    @Test
    void refundOrder_无交易号_抛支付异常() {
        when(orderMapper.selectById(100L)).thenReturn(order(100L, 1L, 1, "NO1", null, "200.00"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.refundOrder(1L, 100L));

        assertEquals(ResultCodeEnum.PAY_FAIL.getCode(), ex.getCode());
        verify(alipayService, never()).tradeRefund(anyString(), any(BigDecimal.class), anyString(), anyString());
        verify(orderMapper, never()).refundPaidOrder(anyLong()); // 无交易号直接拒绝，不改状态
    }

    /**
     * 用例 12：重复退单（并发/重复请求）→ 跳过，不退款不重复恢复库存（文档 §7.5 幂等核心 + code-review 顺序调整）
     * <p>
     * 场景：两个退单请求同时进来 / 或与管理员"发货"并发。refundPaidOrder 是条件更新（WHERE status=1），
     * 只有第一个能成功（影响行数=1）；第二个影响行数=0 → 直接 return——
     * ★ code-review 后绝不打支付宝退款（原来先退款再改状态，并发下钱退了但状态没改成）。
     */
    @Test
    void refundOrder_重复退单_跳过不重复恢复库存() {
        when(orderMapper.selectById(100L)).thenReturn(order(100L, 1L, 1, "NO1", "TRADE001", "200.00"));
        when(orderMapper.refundPaidOrder(100L)).thenReturn(0); // 已被并发发货/退单，条件更新失败

        assertDoesNotThrow(() -> service.refundOrder(1L, 100L)); // 不抛异常，静默返回

        verify(orderMapper).refundPaidOrder(100L);
        verify(alipayService, never()).tradeRefund(anyString(), any(BigDecimal.class), anyString(), anyString()); // 不退款！
        verify(orderMapper, never()).restoreStock(anyLong(), anyInt());
        verify(orderItemMapper, never()).selectByOrderId(anyLong());
    }

    // ==================== 四、用户主动取消 cancelOrder（文档 §7.4） ====================

    /**
     * 用例 13：用户取消待支付订单 → 取消 + 恢复库存
     */
    @Test
    void cancelOrder_成功_取消并恢复库存() {
        when(orderMapper.selectById(100L)).thenReturn(order(100L, 1L, 0, "NO1", null, null));
        when(orderMapper.cancelPendingOrder(100L)).thenReturn(1);
        when(orderItemMapper.selectByOrderId(100L)).thenReturn(List.of(orderItem(1L, 2)));
        when(orderMapper.restoreStock(1L, 2)).thenReturn(1);

        service.cancelOrder(1L, 100L);

        verify(orderMapper).cancelPendingOrder(100L);
        verify(orderMapper).restoreStock(1L, 2);
        verify(productService).clearProductDetailCache(1L);
    }

    /**
     * 用例 14：取消时订单状态已变化（并发支付成功）→ 抛状态异常（文档 §7.4 条件更新防并发）
     */
    @Test
    void cancelOrder_状态已变化_抛状态异常() {
        when(orderMapper.selectById(100L)).thenReturn(order(100L, 1L, 0, "NO1", null, null));
        when(orderMapper.cancelPendingOrder(100L)).thenReturn(0); // 并发下已被支付

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.cancelOrder(1L, 100L));

        assertEquals(ResultCodeEnum.ORDER_STATUS_ERROR.getCode(), ex.getCode());
        verify(orderMapper, never()).restoreStock(anyLong(), anyInt());
    }

    // ==================== 五、支付回调 handlePaid（文档 §7.3） ====================

    /**
     * 用例 15：支付回调成功 → 标记已支付（条件更新 status 0→1）
     */
    @Test
    void handlePaid_成功_标记已支付() {
        when(orderMapper.selectByOrderNo("NO1")).thenReturn(order(100L, 1L, 0, "NO1", null, "200.00"));
        when(orderMapper.markOrderPaid(100L, "TRADE001")).thenReturn(1);

        service.handlePaid("NO1", "TRADE001");

        verify(orderMapper).markOrderPaid(100L, "TRADE001");
    }

    /**
     * 用例 16：支付宝重复通知 → 幂等跳过（文档 §7.3 markOrderPaid 的 WHERE status=0 防重复处理）
     */
    @Test
    void handlePaid_重复回调_幂等跳过() {
        // 订单已经是"已支付"状态（status=1），支付宝第二次发通知
        when(orderMapper.selectByOrderNo("NO1")).thenReturn(order(100L, 1L, 1, "NO1", "TRADE001", "200.00"));
        when(orderMapper.markOrderPaid(100L, "TRADE001")).thenReturn(0); // 已处理过，影响行数=0

        assertDoesNotThrow(() -> service.handlePaid("NO1", "TRADE001")); // 不抛异常，静默返回
    }

    // ==================== 测试数据构造辅助方法 ====================

    /** 构造一个购物车项（CartVO），模拟 cart JOIN product 联表查询结果 */
    private CartVO cartItem(Long productId, String price, int stock, int quantity) {
        CartVO vo = new CartVO();
        vo.setProductId(productId);
        vo.setName("商品" + productId);
        vo.setPrice(new BigDecimal(price));
        vo.setStock(stock);
        vo.setQuantity(quantity);
        return vo;
    }

    /** 构造一个订单实体 */
    private Order order(Long id, Long userId, int status, String orderNo, String transactionId, String totalAmount) {
        Order o = new Order();
        o.setId(id);
        o.setUserId(userId);
        o.setStatus(status);
        o.setOrderNo(orderNo);
        o.setTransactionId(transactionId);
        o.setTotalAmount(totalAmount == null ? null : new BigDecimal(totalAmount));
        return o;
    }

    /** 构造一个订单项（用于恢复库存时遍历 order_item） */
    private OrderItem orderItem(Long productId, int quantity) {
        OrderItem item = new OrderItem();
        item.setProductId(productId);
        item.setQuantity(quantity);
        return item;
    }
}