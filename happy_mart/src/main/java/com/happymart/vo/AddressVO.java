package com.happymart.vo;

import lombok.Data;

/**
 * 地址返回值 VO
 * GET /api/address/list 返回给前端的数据
 */
@Data
public class AddressVO {

    private Long id;
    private Long userId;
    private String name;
    private String phone;
    private String province;
    private String city;
    private String district;
    private String detail;

    /** 是否默认地址：0=否 1=是 */
    private Integer isDefault;
}
