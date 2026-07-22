package com.happymart.dto;                          // DTO = 接收前端请求参数的"信箱"

import jakarta.validation.constraints.NotBlank;       // @NotBlank：不能为空（null / "" / " " 都会报错）
import jakarta.validation.constraints.Size;           // @Size：检查字符串长度是否在范围内
import lombok.Data;                                   // @Data：自动生成 getter/setter/toString

/**
 * 注册请求参数
 * 前端 POST /api/user/register 时传的 JSON 会转成这个对象
 */
@Data
public class RegisterDTO {

    @NotBlank(message = "用户名不能为空")              // 必填，否则返回提示语
    private String username;                          // 用户名

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, message = "密码至少6位")            // 密码最少 6 位
    private String password;                          // 密码（注册时明文传，后端 BCrypt 加密后存库）

    private String phone;                             // 手机号（选填，不传就是 null）
}
