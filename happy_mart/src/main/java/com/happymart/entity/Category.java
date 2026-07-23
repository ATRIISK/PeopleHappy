package com.happymart.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
/**
 * 商品分类实体类
 * 映射数据库 category 表，继承 BaseEntity 自动有 createTime/updateTime/deleted
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("category")
public class Category extends BaseEntity {

    @TableId(type = IdType.AUTO)                     // 自增主键
    private Long id;

    private String name;                             // 分类名称，如"手机数码"

    private Long parentId;                           // 父分类ID，0表示一级分类

    private Integer level;                           // 层级：1=一级分类，2=二级分类

    private Integer sort;                            // 排序序号，数字越小越靠前

    // ===== 非数据库字段 =====
    // children 不存数据库，只是用来装子分类列表返回给前端
    @TableField(exist = false)                       // 告诉 MyBatis-Plus 这不是数据库字段
    private List<Category> children;                 // 子分类列表（树形结构用）
}
