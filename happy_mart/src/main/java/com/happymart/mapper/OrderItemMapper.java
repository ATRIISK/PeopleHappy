package com.happymart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.happymart.entity.OrderItem;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 订单项 Mapper 接口
 *
 * 继承 BaseMapper<OrderItem>，自带标准 CRUD
 * 自定义方法：
 * - insertBatch → 批量插入订单项（下单时用）
 * - selectByOrderId → 根据订单ID查订单项（取消/退单恢复库存时用）
 *
 * 为什么要批量插入？
 * 一个订单可能包含多个商品，要一次性插入多条 order_item 记录
 * MyBatis-Plus 的 Service 层有 saveBatch，但 Mapper 层没有
 * 所以手写一个批量 insert
 */
public interface OrderItemMapper extends BaseMapper<OrderItem> {

    /**
     * 批量插入订单项
     *
     * @param items 订单项列表
     * @return 插入的条数
     */
    int insertBatch(@Param("items") List<OrderItem> items);

    /**
     * 根据订单ID查询所有订单项
     *
     * 取消订单/退单时需要遍历订单项来恢复每个商品的库存。
     *
     * @param orderId 订单ID
     * @return 订单项列表
     */
    List<OrderItem> selectByOrderId(@Param("orderId") Long orderId);
}
