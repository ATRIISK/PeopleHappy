package com.happymart.service;

import com.happymart.vo.PayVO;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 支付宝支付服务接口
 * 对应开发文档 §7.3 支付宝支付流程
 */
public interface AlipayService {

    /**
     * 创建扫码支付订单（调用支付宝 alipay.trade.precreate 接口）
     *
     * @param orderNo     商户订单号（对应 order.order_no）
     * @param totalAmount 订单金额，单位：元（支付宝要求"元"，不是分，和微信不同）
     * @param subject     订单标题（展示在支付宝收银台/沙箱钱包里）
     * @return 封装了 qr_code 的 PayVO
     */
    PayVO createQrOrder(String orderNo, BigDecimal totalAmount, String subject);

    /**
     * 验证支付宝异步通知的签名（RSA2）
     *
     * @param params 回调请求的全部表单参数
     * @return true=验签通过
     */
    boolean verifyNotify(Map<String, String> params);
}