package com.happymart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.happymart.entity.PaymentLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PaymentLogMapper extends BaseMapper<PaymentLog> {
}