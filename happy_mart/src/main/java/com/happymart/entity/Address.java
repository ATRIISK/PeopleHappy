package com.happymart.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 地址实体类
 * 映射数据库 address 表
 *
 * ⚠️ 不继承 BaseEntity
 * 因为 address 表没有 update_time 和 is_deleted 字段
 * 和 Cart 一样，是独立实体，物理删除
 */
@Data
@TableName("address")
public class Address {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID（关联 user 表，标识属于哪个用户） */
    private Long userId;

    /** 收件人姓名 */
    private String name;

    /** 手机号 */
    private String phone;

    /** 省份 */
    private String province;

    /** 城市 */
    private String city;

    /** 区/县（选填） */
    private String district;

    /** 详细地址 */
    private String detail;

    /** 是否默认地址：0=否 1=是 */
    private Integer isDefault;
}
