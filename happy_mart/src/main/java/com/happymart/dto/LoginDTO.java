package com.happymart.dto;                          // DTO = Data Transfer Object，用于接收前端传过来的请求参数

import jakarta.validation.constraints.NotBlank;       // @NotBlank：检查字符串不能为 null 也不能是空串（" " 也不行）
import lombok.Data;                                   // @Data：自动生成 getter/setter/toString

/**
 * 登录请求参数
 * 前端 POST /api/user/login 时传的 JSON 会转成这个对象
 */
@Data
public class LoginDTO {

    @NotBlank(message = "用户名不能为空")              // 校验：必须填，否则返回提示信息
    private String username;                          // 用户名

    @NotBlank(message = "密码不能为空")
    private String password;                          // 密码（明文传过来，后端用 BCrypt 比对）
}
