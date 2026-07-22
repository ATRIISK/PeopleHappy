package com.happymart.entity;                // 包声明 → 这个文件在项目中的"文件夹路径"，必须和目录一致

// ↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓
// import = 导包，让你能在代码里用别人写好的类/注解
// 不 import 的话，Java 不认识 @Data、@TableName 这些是啥
// ↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓

import com.baomidou.mybatisplus.annotation.IdType;      // 让 @TableId 里的 IdType.AUTO 能识别 → 自增主键
import com.baomidou.mybatisplus.annotation.TableId;     // 让 @TableId 注解能用 → 标记哪一个是主键
import com.baomidou.mybatisplus.annotation.TableName;   // 让 @TableName 注解能用 → 映射到哪张表

import lombok.Data;                      // 让 @Data 注解能用 → 自动生成 getter/setter/toString...
import lombok.EqualsAndHashCode;         // 让 @EqualsAndHashCode 注解能用 → 比较对象时把父类也算上

/**
 * 用户实体类
 * 继承 BaseEntity，自动拥有 createTime / updateTime / deleted 三个字段
 */
@Data                                       // Lombok → 自动生成 getter、setter、toString、equals、hashCode
@EqualsAndHashCode(callSuper = true)        // Lombok → equals/hashCode 把父类字段也算进去，否则 createTime 等会被忽略
@TableName("user")                          // MyBatis-Plus → 告诉框架这个类对应数据库的 user 表
public class User extends BaseEntity {

    @TableId(type = IdType.AUTO)            // MyBatis-Plus → 标记 id 是主键，AUTO 表示数据库自增
    private Long id;

    private String username;                // 用户名（字段名自动驼峰转下划线 username → username）

    private String password;                // 密码（存的是 BCrypt 加密后的密文，不是明文！）

    private String phone;                   // 手机号

    private String avatar;                  // 头像 URL

    private String role;                    // 角色：USER（普通用户）/ ADMIN（管理员）

    // ⚠️ 你没看到 createTime / updateTime / deleted 这三个字段
    // 因为它们定义在父类 BaseEntity 里，User 继承了它们，所以可以直接用
}
