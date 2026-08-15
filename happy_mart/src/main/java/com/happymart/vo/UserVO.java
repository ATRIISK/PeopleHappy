package com.happymart.vo;                      // VO = View Object，用于返回给前端的数据

import lombok.Data;                            // @Data：自动生成 getter/setter/toString

import java.time.LocalDateTime;                // LocalDateTime：Java 8 的时间类型，对应 MySQL 的 datetime

/**
 * 用户信息 VO（返回给前端，不含密码）
 * 和实体类 User 的区别：
 *   User 类对应数据库，有 password 字段
 *   UserVO 是返回给前端看的，去掉敏感信息（密码）
 */
@Data
public class UserVO {

    private Long id;                           // 用户ID

    private String username;                   // 用户名

    private String phone;                      // 手机号

    private String avatar;                     // 头像 URL

    private String role;                       // 角色：USER / ADMIN

    // 账号状态：0=正常，1=禁用（登录返回 + 管理后台用户列表共用）
    // UserServiceImpl.convertToUserVO 用 BeanUtils.copyProperties，加了这个字段自动带过去
    private Integer status;

    private LocalDateTime createTime;          // 注册时间（从 BaseEntity 继承来的）
}