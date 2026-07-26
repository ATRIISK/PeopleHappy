package com.happymart.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 地址请求参数 DTO
 * 前端新增/修改地址时传的 JSON 转成这个对象
 *
 * 新增时 id=null，更新时 id 有值
 * userId 不从 DTO 取，从 token 解析
 */
@Data
public class AddressDTO {

    /** 地址ID（新增时 null，更新时有值） */
    private Long id;

    /** 收件人姓名 */
    @NotBlank(message = "姓名不能为空")
    private String name;

    /** 手机号 */
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式错误")
    private String phone;

    /** 省份 */
    @NotBlank(message = "省份不能为空")
    private String province;

    /** 城市 */
    @NotBlank(message = "城市不能为空")
    private String city;

    /** 区/县（选填） */
    private String district;

    /** 详细地址 */
    @NotBlank(message = "详细地址不能为空")
    private String detail;

    /** 是否默认地址：0=否 1=是 */
    private Integer isDefault;
}
