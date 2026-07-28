package com.happymart.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.happymart.common.annotation.Auth;
import com.happymart.common.result.Result;
import com.happymart.dto.OrderCreateDTO;
import com.happymart.service.OrderService;
import com.happymart.vo.OrderVO;
import com.happymart.vo.PayVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 订单 Controller
 *
 * 全部 @Auth（所有订单操作都需要登录）
 * 路径前缀：/api/order
 */
@Slf4j
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * 创建订单
     * POST /api/order/create
     * 请求体：{ "addressId": 1, "productIds": [1, 2, 3] }
     *
     * productIds 是可选字段，表示要结算的购物车商品 ID 列表。
     * 不传这个字段 → 结算全部购物车商品（兼容旧版本行为）。
     * 传了 → 只结算被勾选的商品，其他的留在购物车里。
     */
    @Auth
    @PostMapping("/create")
    public Result<OrderVO> createOrder(HttpServletRequest request,
                                       @Valid @RequestBody OrderCreateDTO dto) {
        Long userId = (Long) request.getAttribute("currentUserId");
        log.info("创建订单: userId={}, productIds={}", userId, dto.getProductIds());
        OrderVO orderVO = orderService.createOrder(userId, dto.getAddressId(), dto.getProductIds());
        return Result.success(orderVO);
    }

    /**
     * 查询订单列表（分页）
     * GET /api/order/list?status=&page=1&size=10
     */
    @Auth
    @GetMapping("/list")
    public Result<IPage<OrderVO>> getOrderList(
            HttpServletRequest request,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        Long userId = (Long) request.getAttribute("currentUserId");
        log.info("查询订单列表: userId={}, status={}, page={}, size={}", userId, status, page, size);
        IPage<OrderVO> result = orderService.getOrderList(userId, status, page, size);
        return Result.success(result);
    }

    /**
     * 查询订单详情
     * GET /api/order/detail/{id}
     */
    @Auth
    @GetMapping("/detail/{id}")
    public Result<OrderVO> getOrderDetail(HttpServletRequest request, @PathVariable Long id) {
        Long userId = (Long) request.getAttribute("currentUserId");
        log.info("查询订单详情: id={}, userId={}", id, userId);
        OrderVO orderVO = orderService.getOrderDetail(userId, id);
        return Result.success(orderVO);
    }

    /**
     * 取消订单（只能取消待付款的）
     * PUT /api/order/cancel/{id}
     */
    @Auth
    @PutMapping("/cancel/{id}")
    public Result<Void> cancelOrder(HttpServletRequest request, @PathVariable Long id) {
        Long userId = (Long) request.getAttribute("currentUserId");
        log.info("取消订单: id={}, userId={}", id, userId);
        orderService.cancelOrder(userId, id);
        return Result.success();
    }

    /**
     * 修改订单收货地址
     * PUT /api/order/updateAddress/{id}
     *
     * 只能在"待付款（0）"或"待发货（1）"状态下修改。
     * 请求体传新地址的 ID：{ "addressId": 2 }
     */
    @Auth
    @PutMapping("/updateAddress/{id}")
    public Result<Void> updateOrderAddress(
            HttpServletRequest request,
            @PathVariable Long id,
            @RequestBody Map<String, Object> param) {
        Long userId = (Long) request.getAttribute("currentUserId");
        Long newAddressId = Long.valueOf(param.get("addressId").toString());
        log.info("修改订单地址: orderId={}, newAddressId={}", id, newAddressId);
        orderService.updateOrderAddress(userId, id, newAddressId);
        return Result.success();
    }

    /**
     * 确认收货
     * PUT /api/order/confirm/{id}
     */
    @Auth
    @PutMapping("/confirm/{id}")
    public Result<Void> confirmOrder(HttpServletRequest request, @PathVariable Long id) {
        Long userId = (Long) request.getAttribute("currentUserId");
        log.info("确认收货: id={}, userId={}", id, userId);
        orderService.confirmOrder(userId, id);
        return Result.success();
    }

    /**
     * 发起支付宝扫码支付（只能对"待付款"的订单发起）
     * POST /api/order/pay/{id}
     * 返回 codeUrl（支付宝 qr_code），前端渲染成二维码，用支付宝沙箱钱包App扫码
     */
    @Auth
    @PostMapping("/pay/{id}")
    public Result<PayVO> pay(HttpServletRequest request, @PathVariable Long id) {
        Long userId = (Long) request.getAttribute("currentUserId");
        log.info("发起支付: id={}, userId={}", id, userId);
        PayVO payVO = orderService.pay(userId, id);
        return Result.success(payVO);
    }

    /**
     * 查询订单支付状态（前端下单后轮询用）
     * GET /api/order/status/{id}
     * 返回订单状态：0待付款 1已支付 2已发货 3已完成 4已取消
     */
    @Auth
    @GetMapping("/status/{id}")
    public Result<Integer> getPayStatus(HttpServletRequest request, @PathVariable Long id) {
        Long userId = (Long) request.getAttribute("currentUserId");
        Integer status = orderService.getPayStatus(userId, id);
        return Result.success(status);
    }
}
