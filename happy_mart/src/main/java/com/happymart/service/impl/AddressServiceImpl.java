package com.happymart.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.happymart.common.exception.BusinessException;
import com.happymart.common.result.ResultCodeEnum;
import com.happymart.dto.AddressDTO;
import com.happymart.entity.Address;
import com.happymart.mapper.AddressMapper;
import com.happymart.service.AddressService;
import com.happymart.vo.AddressVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 地址服务实现类
 *
 * 核心逻辑：默认地址互斥
 * 设置 A 为默认 → 先把 B/C/D 的 is_default 都置 0 → 再设 A 的 is_default=1
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class AddressServiceImpl implements AddressService {

    private final AddressMapper addressMapper;

    @Override
    public void addAddress(Long userId, AddressDTO dto) {
        log.info("新增地址: userId={}, name={}, phone={}", userId, dto.getName(), dto.getPhone());

        // 如果新增的地址设为默认，先把该用户其他地址的默认标记清掉
        if (dto.getIsDefault() != null && dto.getIsDefault() == 1) {
            clearDefaultFlag(userId);
        }

        // DTO → Entity 转换
        Address address = new Address();
        BeanUtils.copyProperties(dto, address);
        address.setUserId(userId);  // userId 从 token 来，不是前端传的

        // ⚠️ 一定要调 insert，否则数据不进数据库
        addressMapper.insert(address);
        log.info("新增地址成功: id={}", address.getId());
    }

    @Override
    public List<AddressVO> getAddressList(Long userId) {
        log.info("查询地址列表: userId={}", userId);

        // 按 is_default 降序（默认=1 排最前），再按 id 降序（最新排前面）
        LambdaQueryWrapper<Address> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Address::getUserId, userId)
               .orderByDesc(Address::getIsDefault)
               .orderByDesc(Address::getId);

        List<Address> addressList = addressMapper.selectList(wrapper);

        // Entity → VO 转换
        List<AddressVO> voList = addressList.stream().map(a -> {
            AddressVO vo = new AddressVO();
            BeanUtils.copyProperties(a, vo);
            return vo;
        }).collect(Collectors.toList());

        log.info("地址列表查询完成: 共 {} 条", voList.size());
        return voList;
    }

    @Override
    public void updateAddress(Long userId, AddressDTO dto) {
        log.info("修改地址: id={}, userId={}", dto.getId(), userId);

        // 查出原地址，校验所有权
        Address existing = addressMapper.selectById(dto.getId());
        if (existing == null || !existing.getUserId().equals(userId)) {
            log.warn("地址不存在或无权限: id={}, userId={}", dto.getId(), userId);
            throw new BusinessException(ResultCodeEnum.ADDRESS_NOT_FOUND);
        }

        // 如果设为默认，先把其他地址取消默认
        if (dto.getIsDefault() != null && dto.getIsDefault() == 1) {
            clearDefaultFlag(userId);
        }

        // DTO → Entity
        Address address = new Address();
        BeanUtils.copyProperties(dto, address);
        address.setUserId(userId);  // 防止 userId 被篡改

        addressMapper.updateById(address);
        log.info("修改地址成功: id={}", dto.getId());
    }

    @Override
    public void deleteAddress(Long userId, Long addressId) {
        log.info("删除地址: id={}, userId={}", addressId, userId);

        // 校验所有权
        Address existing = addressMapper.selectById(addressId);
        if (existing == null || !existing.getUserId().equals(userId)) {
            log.warn("地址不存在或无权限: id={}, userId={}", addressId, userId);
            throw new BusinessException(ResultCodeEnum.ADDRESS_NOT_FOUND);
        }

        addressMapper.deleteById(addressId);
        log.info("删除地址成功: id={}", addressId);
    }

    /**
     * 清除该用户所有地址的默认标记
     * 在设置新默认地址前调用，保证只有一个默认地址
     */
    private void clearDefaultFlag(Long userId) {
        LambdaUpdateWrapper<Address> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Address::getUserId, userId)
               .set(Address::getIsDefault, 0);
        addressMapper.update(null, wrapper);
    }
}
