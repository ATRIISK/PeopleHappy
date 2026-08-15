package com.happymart.service.impl;                // 包声明 → Service 实现类放在 impl 子包下

import com.baomidou.mybatisplus.core.metadata.IPage;  // 分页结果接口
import com.baomidou.mybatisplus.extension.plugins.pagination.Page; // 分页对象
import com.happymart.common.exception.BusinessException;            // 业务异常
import com.happymart.common.result.ResultCodeEnum;                  // 错误码枚举
import com.happymart.entity.Order;                                 // 订单实体
import com.happymart.mapper.OrderMapper;                           // 订单 Mapper
import com.happymart.service.AdminOrderService;                    // 本类实现的接口
import com.happymart.vo.OrderVO;                                   // 订单视图对象
import lombok.RequiredArgsConstructor;                              // @RequiredArgsConstructor → 构造器注入
import lombok.extern.slf4j.Slf4j;                                   // @Slf4j → 日志
import org.springframework.stereotype.Service;                      // @Service → 标记 Service 类

import java.util.List;                                              // List → 白名单值
import java.util.Map;                                               // Map → 白名单结构

/**
 * 管理后台：订单管理服务实现类
 * <p>
 * 管理员对订单的操作原则：只做"发货"这种平台侧动作，
 * 其他状态变更（取消/退单/确认收货）仍由用户端完成。
 * <p>
 * 用"白名单状态机"限定合法流转：key=当前状态，value=允许的目标状态列表。
 * 以后要加新流转（如 2→3 确认完成），只需往 Map 加一行。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminOrderServiceImpl implements AdminOrderService {

    /**
     * 管理员允许的订单流转白名单
     * <p>
     * 目前只有：已支付(1) → 已发货(2)，也就是"发货"操作。
     * 为什么不用 if 判断？扩展性差，每加一个流转就要改 if；
     * 用 Map 数据结构，加流转只是加一行，代码也更直观。
     */
    private static final Map<Integer, List<Integer>> ADMIN_TRANSITIONS =
            Map.of(1, List.of(2));   // key=当前状态1（已支付），value=可转到的状态2（已发货）

    /** 订单 Mapper → 操作 order 表 */
    private final OrderMapper orderMapper;

    /**
     * 分页查询全部订单（管理后台用）
     */
    @Override
    public IPage<OrderVO> getAdminOrderPage(Integer status, Integer page, Integer size) {
        // 直接调 Mapper 的联表分页方法（含下单人 username），MyBatis-Plus 自动处理分页
        return orderMapper.selectOrderVOListAll(new Page<>(page, size), status);
    }

    /**
     * 修改订单状态（目前仅支持"发货" 1→2）
     * <p>
     * 三步：
     * 1. 查订单，不存在报错
     * 2. 白名单校验：当前状态能否转到目标状态，不合法直接抛异常
     * 3. 条件更新（WHERE status=当前值）防并发：状态被其他操作改了 → 影响行数 0 → 抛异常提示刷新
     */
    @Override
    public void updateOrderStatus(Long orderId, Integer targetStatus) {

        // ===== 1. 查订单 =====
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ResultCodeEnum.ORDER_NOT_FOUND);
        }

        // ===== 2. 白名单状态机校验 =====
        // 从当前状态取出"允许转到的目标状态列表"
        List<Integer> allowed = ADMIN_TRANSITIONS.get(order.getStatus());
        // 当前状态不在白名单里（如已取消/已退款），或目标状态不在允许列表里 → 非法流转
        if (allowed == null || !allowed.contains(targetStatus)) {
            throw new BusinessException(ResultCodeEnum.ORDER_STATUS_ERROR, "非法的订单状态流转");
        }

        // ===== 3. 条件更新（防并发，和 cancelPendingOrder/markOrderPaid 同一套路） =====
        // WHERE status = 当前值：只有状态还是 from 才能改成 to
        int affected = orderMapper.updateStatusIf(orderId, order.getStatus(), targetStatus);
        if (affected == 0) {
            // 状态已被其他操作改掉（比如用户刚好申请退款了）→ 提示刷新重试
            throw new BusinessException(ResultCodeEnum.ORDER_STATUS_ERROR, "订单状态已被修改，请刷新重试");
        }

        log.info("管理后台修改订单状态: orderId={}, {}→{}", orderId, order.getStatus(), targetStatus);
    }
}
