package com.happymart.controller.admin;            // 包声明 → 管理后台 Controller 统一放 controller/admin 包

import com.happymart.common.annotation.Auth;                    // 自定义 @Auth 注解 → requireAdmin=true 需要管理员权限
import com.happymart.common.result.Result;                      // 统一返回结果
import com.happymart.service.AdminDashboardService;             // 管理后台数据看板服务
import com.happymart.vo.DashboardStatsVO;                       // 数据看板统计 VO
import com.happymart.vo.OrderVO;                                // 订单视图对象
import lombok.RequiredArgsConstructor;                          // @RequiredArgsConstructor → 自动构造器注入
import lombok.extern.slf4j.Slf4j;                               // @Slf4j → 日志
import org.springframework.web.bind.annotation.GetMapping;      // @GetMapping → GET 请求
import org.springframework.web.bind.annotation.RequestMapping;  // @RequestMapping → 类级别路径前缀
import org.springframework.web.bind.annotation.RestController;  // @RestController → 返回 JSON

import java.util.List;                                          // List → 最近订单列表

/**
 * 管理后台：数据看板接口
 * <p>
 * 首页 Dashboard 展示：商品/订单/用户数量 + 总交易额 + 最近订单。
 * <p>
 * 接口清单：
 * GET /api/admin/dashboard/stats           → 四项统计数据
 * GET /api/admin/dashboard/recent-orders   → 最近 5 条订单
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;   // 注入数据看板服务

    /**
     * 数据看板统计（商品数/订单数/用户数/总交易额）
     * <p>
     * GET /api/admin/dashboard/stats
     */
    @Auth(requireAdmin = true)
    @GetMapping("/stats")
    public Result<DashboardStatsVO> getStats() {
        return Result.success(adminDashboardService.getStats());
    }

    /**
     * 最近订单（最新 5 条，含下单人用户名）
     * <p>
     * GET /api/admin/dashboard/recent-orders
     */
    @Auth(requireAdmin = true)
    @GetMapping("/recent-orders")
    public Result<List<OrderVO>> getRecentOrders() {
        return Result.success(adminDashboardService.getRecentOrders());
    }
}
