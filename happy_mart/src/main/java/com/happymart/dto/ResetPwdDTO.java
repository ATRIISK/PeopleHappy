package com.happymart.dto;                          // DTO = Data Transfer Object，用于接收前端传过来的请求参数

import jakarta.validation.constraints.NotBlank;     // @NotBlank → 校验字符串不能为 null 也不能是空串
import jakarta.validation.constraints.Size;         // @Size → 校验长度范围
import lombok.Data;                                 // @Data → 自动生成 getter/setter/toString

/**
 * 重置密码请求参数（管理后台用）
 * <p>
 * 前端 PUT /api/admin/user/resetPwd/{id} 时传的 JSON 会转成这个对象。
 * 管理员输入新密码，后端 BCrypt 加密后覆盖原密码（不用知道旧密码）。
 */
@Data
public class ResetPwdDTO {

    /** 新密码（必填，至少 6 位，和注册页的校验规则保持一致） */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, message = "密码至少 6 位")
    private String password;
}
