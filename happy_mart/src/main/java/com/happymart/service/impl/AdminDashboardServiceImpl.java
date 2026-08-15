package com.happymart.service.impl;                // 包声明 → Service 实现类放在 impl 子包下

import com.baomidou.mybatisplus.extension.plugins.pagination.Page; // 分页对象
import com.happymart.mapper.OrderMapper;                           // 订单 Mapper（总交易额 + 最近订单）
import com.happymart.mapper.ProductMapper;                         // 商品 Mapper（商品数）
import com.happymart.mapper.UserMapper;                            // 用户 Mapper（用户数）
import com.happymart.service.AdminDashboardService;                // 本类实现的接口
import com.happymart.vo.DashboardStatsVO;                          // 统计 VO
import com.happymart.vo.OrderVO;                                   // 订单视图对象
import lombok.RequiredArgsConstructor;                              // @RequiredArgsConstructor → 构造器注入
import lombok.extern.slf4j.Slf4j;                                   // @Slf4j → 日志
import org.springframework.stereotype.Service;                      // @Service → 标记 Service 类

import java.util.List;                                              // List → 最近订单列表

/**
 * 管理后台：数据看板服务实现类
 * <p>
 * 首页 Dashboard 的统计数字 + 最近订单。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements AdminDashboardService {

    /** 商品 Mapper → 数商品总数 */
    private final ProductMapper productMapper;

    /** 订单 Mapper → 数订单总数 + 算总交易额 + 查最近订单 */
    private final OrderMapper orderMapper;

    /** 用户 Mapper → 数用户总数 */
    private final UserMapper userMapper;

    /**
     * 获取数据看板统计
     * <p>
     * 总数都用 selectCount(null)（不带条件 = 查总数），
     * MyBatis-Plus 会自动过滤逻辑删除（Product/User 的 is_deleted=1）。
     * 总交易额调 OrderMapper.sumPaidAmount（status 1/2/3 的 totalAmount 之和，元）。
     */
    @Override
    public DashboardStatsVO getStats() {
        DashboardStatsVO stats = new DashboardStatsVO();

        // 商品总数（逻辑删除的自动不算）
        stats.setProductCount(productMapper.selectCount(null));
        // 订单总数（order 表无逻辑删除字段，就是全量）
        stats.setOrderCount(orderMapper.selectCount(null));
        // 用户总数（逻辑删除的自动不算）
        stats.setUserCount(userMapper.selectCount(null));
        // 已成交总金额（status 1/2/3，单位：元；COALESCE 兜底，无订单返回 0）
        stats.setTotalSales(orderMapper.sumPaidAmount());

        log.info("数据看板统计: 商品={}, 订单={}, 用户={}, 交易额={}",
                stats.getProductCount(), stats.getOrderCount(),
                stats.getUserCount(), stats.getTotalSales());
        return stats;
    }

    /**
     * 获取最近订单（最新 5 条）
     * <p>
     * 复用管理员订单分页查询 SQL（selectOrderVOListAll），
     * 传 page=1 size=5，status=null（不看状态，都展示）。
     */
    @Override
    public List<OrderVO> getRecentOrders() {
        return orderMapper.selectOrderVOListAll(new Page<>(1, 5), null).getRecords();
    }
}
