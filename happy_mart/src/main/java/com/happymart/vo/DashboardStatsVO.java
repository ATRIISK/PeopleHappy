package com.happymart.vo;                          // VO = View Object，用于返回给前端的数据

import lombok.Data;                                 // @Data → 自动生成 getter/setter/toString

import java.math.BigDecimal;                        // BigDecimal → 金额类型，保证精度不丢

/**
 * 管理后台首页数据看板统计 VO
 * <p>
 * 前端 GET /api/admin/dashboard/stats 时返回这个对象，
 * Dashboard.vue 用 4 张统计卡片展示。
 */
@Data
public class DashboardStatsVO {

    /** 商品总数（MyBatis-Plus selectCount 会自动过滤逻辑删除 is_deleted=1 的） */
    private Long productCount;

    /** 订单总数（order 表无逻辑删除字段，selectCount(null) 就是全量） */
    private Long orderCount;

    /** 用户总数（自动过滤逻辑删除） */
    private Long userCount;

    /** 已成交总金额（status 1/2/3 的 totalAmount 之和，单位：元） */
    private BigDecimal totalSales;
}
