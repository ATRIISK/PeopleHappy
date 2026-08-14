package com.happymart.controller;

import com.happymart.common.annotation.Auth;
import com.happymart.common.exception.BusinessException;
import com.happymart.common.result.Result;
import com.happymart.common.result.ResultCodeEnum;
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
        // 安全解析数量：前端可能传小数/空值/非法值，直接 Integer.valueOf 会抛
        // NumberFormatException（变成 500），这里统一转成合法整数或返回参数错误
        Integer quantity = parseQuantity(param.get("quantity"));

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
        // 安全解析数量：前端可能传小数/空值/非法值，直接 Integer.valueOf 会抛
        // NumberFormatException（变成 500），这里统一转成合法整数或返回参数错误
        Integer quantity = parseQuantity(param.get("quantity"));

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

    /**
     * 安全解析购物车数量参数
     * <p>
     * 为什么不能直接用 {@code Integer.valueOf(param.get("quantity").toString())}？
     *  - 前端传小数（JSON 里 2.5 会被 Jackson 反序列化成 Double）→ NumberFormatException → 500
     *  - 前端不传 quantity（param.get 返回 null）→ 空指针 NPE → 500
     * 后端永远不要相信前端数据（code-review 二轮修复）：这里统一把数量转成合法整数，
     * 非法/空值返回友好的"参数错误"（400 业务码），而不是 500 服务器异常。
     *
     * @param quantity 请求参数里的 quantity 原始值（可能为 null / Number / String）
     * @return 取整后的整数数量
     */
    private Integer parseQuantity(Object quantity) {
        if (quantity == null) {
            log.warn("购物车数量为空");
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "数量不能为空");
        }
        try {
            // JSON 数字（Integer/Double 等 Number 类型）→ 直接取整（小数如 2.5 → 2）
            if (quantity instanceof Number number) {
                return number.intValue();
            }
            // 字符串数字 → 解析为整数；"2.5"/"abc" 等会抛异常走下面兜底
            return Integer.parseInt(quantity.toString());
        } catch (Exception e) {
            log.warn("购物车数量格式非法: quantity={}", quantity);
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "数量必须是整数");
        }
    }
}