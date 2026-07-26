package com.happymart.service;

import com.happymart.dto.AddressDTO;
import com.happymart.vo.AddressVO;

import java.util.List;

/**
 * 地址服务接口
 *
 * 每个方法都需要 userId（从 token 解析），确保只能操作自己的地址
 */
public interface AddressService {

    /** 新增地址 */
    void addAddress(Long userId, AddressDTO dto);

    /** 获取地址列表（默认地址排最前） */
    List<AddressVO> getAddressList(Long userId);

    /** 修改地址（校验所有权） */
    void updateAddress(Long userId, AddressDTO dto);

    /** 删除地址（校验所有权） */
    void deleteAddress(Long userId, Long addressId);
}
