package com.happymart.service;                    // 包声明 → Service 接口层

import com.happymart.vo.DashboardStatsVO;         // 数据看板统计 VO
import com.happymart.vo.OrderVO;                  // 订单视图对象

import java.util.List;                            // List → 最近订单列表

/**
 * 管理后台：数据看板服务接口
 * <p>
 * 首页 Dashboard 需要：四项统计数据 + 最近订单列表。
 */
public interface AdminDashboardService {

    /**
     * 获取数据看板统计（商品数/订单数/用户数/总交易额）
     *
     * @return 统计 VO
     */
    DashboardStatsVO getStats();

    /**
     * 获取最近订单（最新 5 条，含下单人用户名）
     *
     * @return 订单列表
     */
    List<OrderVO> getRecentOrders();
}
