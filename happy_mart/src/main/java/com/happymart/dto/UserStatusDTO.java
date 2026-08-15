package com.happymart.dto;                          // DTO = Data Transfer Object，用于接收前端传过来的请求参数

import jakarta.validation.constraints.NotNull;      // @NotNull → 校验不能为 null
import lombok.Data;                                 // @Data → 自动生成 getter/setter/toString

/**
 * 修改用户状态请求参数（管理后台禁用/启用用）
 * <p>
 * 前端 PUT /api/admin/user/status/{id} 时传的 JSON 会转成这个对象。
 * status 取值：0=启用，1=禁用。
 */
@Data
public class UserStatusDTO {

    /** 目标状态：0=启用，1=禁用（必填） */
    @NotNull(message = "用户状态不能为空")
    private Integer status;
}
