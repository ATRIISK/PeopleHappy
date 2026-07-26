package com.happymart.controller;

import com.happymart.common.annotation.Auth;
import com.happymart.common.result.Result;
import com.happymart.service.CartService;
import com.happymart.vo.CartVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 购物车 Controller
 * <p>
 * 购物车相关的所有接口，全部需要登录（@Auth）。
 * 当前用户 ID 从 request 属性获取（由 JwtAuthInterceptor 设置）。
 * <p>
 * 路径前缀：/api/cart
 */
@Slf4j
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    /**
     * 购物车 Service
     * final + @RequiredArgsConstructor → Spring 自动注入
     */
    private final CartService cartService;

    /**
     * 获取购物车列表
     * <p>
     * GET /api/cart/list
     *
     * @param request 拿当前登录用户 ID
     * @return 购物车 VO 列表
     */
    @Auth
    @GetMapping("/list")
    public Result<List<CartVO>> getCartList(HttpServletRequest request) {
        // 从请求属性中获取当前用户 ID（由 JwtAuthInterceptor 设置）
        Long userId = (Long) request.getAttribute("currentUserId");
        log.info("获取购物车列表: userId={}", userId);

        // 调用 Service 层查询购物车列表（联表查商品信息）
        List<CartVO> cartList = cartService.getCartList(userId);

        return Result.success(cartList);
    }

    /**
     * 添加商品到购物车
     * <p>
     * POST /api/cart/add
     * 请求体：{ "productId": 1, "quantity": 1 }
     *
     * @param request 拿当前登录用户 ID
     * @param param   请求体，包含 productId 和 quantity
     * @return 成功（无数据）
     */
    @Auth
    @PostMapping("/add")
    public Result<Void> addCart(HttpServletRequest request, @RequestBody Map<String, Object> param) {
        Long userId = (Long) request.getAttribute("currentUserId");
        Long productId = Long.valueOf(param.get("productId").toString());
        Integer quantity = Integer.valueOf(param.get("quantity").toString());

        log.info("添加购物车: userId={}, productId={}, quantity={}", userId, productId, quantity);

        // 调用 Service 层处理业务（有则加数量，无则新增）
        cartService.addCart(userId, productId, quantity);

        return Result.success();
    }

    /**
     * 更新购物车商品数量
     * <p>
     * PUT /api/cart/update
     * 请求体：{ "productId": 1, "quantity": 3 }
     *
     * @param request 拿当前登录用户 ID
     * @param param   请求体，包含 productId 和 quantity
     * @return 成功（无数据）
     */
    @Auth
    @PutMapping("/update")
    public Result<Void> updateCart(HttpServletRequest request, @RequestBody Map<String, Object> param) {
        Long userId = (Long) request.getAttribute("currentUserId");
        Long productId = Long.valueOf(param.get("productId").toString());
        Integer quantity = Integer.valueOf(param.get("quantity").toString());

        log.info("更新购物车数量: userId={}, productId={}, quantity={}", userId, productId, quantity);

        // 调用 Service 层更新数量
        cartService.updateQuantity(userId, productId, quantity);

        return Result.success();
    }

    /**
     * 删除购物车中的某个商品
     * <p>
     * DELETE /api/cart/remove
     * 请求体：{ "productId": 1 }
     *
     * @param request 拿当前登录用户 ID
     * @param param   请求体，包含 productId
     * @return 成功（无数据）
     */
    @Auth
    @DeleteMapping("/remove")
    public Result<Void> removeCart(HttpServletRequest request, @RequestBody Map<String, Object> param) {
        Long userId = (Long) request.getAttribute("currentUserId");
        Long productId = Long.valueOf(param.get("productId").toString());

        log.info("删除购物车商品: userId={}, productId={}", userId, productId);

        // 调用 Service 层删除
        cartService.removeCart(userId, productId);

        return Result.success();
    }

    /**
     * 清空购物车
     * <p>
     * DELETE /api/cart/clear
     *
     * @param request 拿当前登录用户 ID
     * @return 成功（无数据）
     */
    @Auth
    @DeleteMapping("/clear")
    public Result<Void> clearCart(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");

        log.info("清空购物车: userId={}", userId);

        // 调用 Service 层清空当前用户所有购物车记录
        cartService.clearCart(userId);

        return Result.success();
    }
}