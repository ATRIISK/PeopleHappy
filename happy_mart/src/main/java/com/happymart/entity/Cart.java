package com.happymart.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 购物车实体类
 * <p>
 * 映射数据库 cart 表（开发文档 4.4 节）。
 * <p>
 * ⚠️ 为什么不继承 BaseEntity？
 * <br>
 * 因为开发文档中 cart 表只有 create_time，没有 update_time 和 is_deleted。
 * 如果继承 BaseEntity，MyBatis-Plus 的 @TableLogic 会自动给所有查询加
 * WHERE is_deleted = 0，但 cart 表根本没这个列，会报错。
 * <p>
 * 所以 Cart 是一个"独立"的实体类，不继承 BaseEntity。
 * 删除使用物理删除（deleteById），不用逻辑删除。
 */
@Data
@TableName("cart")
public class Cart {

    /**
     * 主键，自增（IdType.AUTO = 数据库 AUTO_INCREMENT）
     * insert 时不用设 id，数据库自动生成
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     * 关联 user 表的 id，标识这个购物车项属于哪个用户
     */
    private Long userId;

    /**
     * 商品ID
     * 关联 product 表的 id，标识加了哪个商品到购物车
     */
    private Long productId;

    /**
     * 数量
     * 这件商品加了几件，默认 1 件
     */
    private Integer quantity;

    /**
     * 创建时间
     * 注意：没有用自动填充，insert 前手动 set
     */
    private LocalDateTime createTime;

}