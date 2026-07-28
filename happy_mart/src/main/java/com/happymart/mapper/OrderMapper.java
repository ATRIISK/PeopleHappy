package com.happymart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.happymart.entity.Order;
import com.happymart.vo.OrderVO;
import org.apache.ibatis.annotations.Param;

/**
 * 订单 Mapper 接口
 *
 * 继承 BaseMapper<Order>，自带标准 CRUD
 * 自定义方法：
 * - selectOrderVOById → 查单个订单详情（含订单项和商品信息）
 * - selectOrderVOListByUserId → 分页查用户订单列表（含订单项和商品信息）
 * - updateStock → 安全扣减商品库存（带库存检查）
 * - selectByOrderNo → 根据商户订单号查订单（支付宝异步通知只带 out_trade_no，没有订单主键ID）
 */
public interface OrderMapper extends BaseMapper<Order> {

    /**
     * 根据订单ID查询订单详情（含订单项和商品名称/图片）
     * 联表：order LEFT JOIN order_item LEFT JOIN product
     *
     * @param id 订单ID
     * @return 订单 VO（含 items 列表）
     */
    OrderVO selectOrderVOById(@Param("id") Long id);

    /**
     * 分页查询用户的订单列表（含订单项和商品信息）
     *
     * @param page   分页对象（MyBatis-Plus 自动处理）
     * @param userId 用户ID
     * @param status 订单状态（null=查全部）
     * @return 分页结果
     */
    IPage<OrderVO> selectOrderVOListByUserId(
            Page<?> page,
            @Param("userId") Long userId,
            @Param("status") Integer status);

    /**
     * 扣减商品库存（安全扣减）
     *
     * UPDATE product SET stock = stock - #{quantity}
     * WHERE id = #{productId} AND stock >= #{quantity}
     *
     * WHERE stock >= #{quantity} 是关键：
     * 如果库存不够，影响行数为 0，Java 代码抛异常回滚
     *
     * @param productId 商品ID
     * @param quantity  扣减数量
     * @return 影响行数（0=库存不足）
     */
    int updateStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);

    /**
     * 根据商户订单号查询订单
     * 支付宝异步通知回调里只带 out_trade_no（对应 order.order_no），不带订单主键ID
     *
     * @param orderNo 商户订单号
     * @return 订单实体
     */
    Order selectByOrderNo(@Param("orderNo") String orderNo);
}
