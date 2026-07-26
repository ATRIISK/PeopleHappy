package com.happymart.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.happymart.vo.OrderVO;

import java.util.List;

/**
 * 订单服务接口
 *
 * 提供创建订单、查看订单列表、查看订单详情、取消订单、确认收货
 * 所有操作都需要 userId（从 token 解析），确保只能操作自己的订单
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
     *
     * @param userId  当前用户 ID
     * @param orderId 订单 ID
     */
    void cancelOrder(Long userId, Long orderId);

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
}
