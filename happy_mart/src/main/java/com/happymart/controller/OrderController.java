package com.happymart.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.happymart.common.annotation.Auth;
import com.happymart.common.result.Result;
import com.happymart.dto.OrderCreateDTO;
import com.happymart.service.OrderService;
import com.happymart.vo.OrderVO;
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
}
