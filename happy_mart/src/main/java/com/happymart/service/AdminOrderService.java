package com.happymart.service;                    // 包声明 → Service 接口层

import com.baomidou.mybatisplus.core.metadata.IPage; // 分页结果接口
import com.happymart.vo.OrderVO;                  // 订单视图对象

/**
 * 管理后台：订单管理服务接口
 * <p>
 * 管理员能做的订单操作比用户少得多（用户能取消/退单/确认收货），
 * 管理员只负责"查看全部订单"和"发货"。
 */
public interface AdminOrderService {

    /**
     * 分页查询全部订单（可按状态筛选）
     *
     * @param status 订单状态（null=查全部）
     * @param page   当前页码
     * @param size   每页条数
     * @return 分页结果（含下单人用户名 username）
     */
    IPage<OrderVO> getAdminOrderPage(Integer status, Integer page, Integer size);

    /**
     * 修改订单状态（目前仅支持"发货"：已支付 1 → 已发货 2）
     * <p>
     * 用白名单状态机校验合法流转，不合法直接抛异常；
     * 用条件更新（WHERE status=当前值）防并发。
     *
     * @param orderId      订单ID
     * @param targetStatus 目标状态
     */
    void updateOrderStatus(Long orderId, Integer targetStatus);
}
