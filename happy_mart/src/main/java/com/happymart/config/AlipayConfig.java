package com.happymart.config;

import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 支付宝支付配置类
 * 对应开发文档第12节"支付宝沙箱支付配置说明"
 *
 * 构建一个全局唯一的 AlipayClient Bean，后续 AlipayServiceImpl 直接注入使用。
 * AlipayClient 内部已经封装好了签名（RSA2）和请求发送逻辑，
 * 不需要像微信支付 V3 那样自己处理证书/AEAD解密。
 */
@Configuration
public class AlipayConfig {

    @Bean
    public AlipayClient alipayClient(
            @Value("${alipay.gateway-url}") String gatewayUrl,
            @Value("${alipay.app-id}") String appId,
            @Value("${alipay.private-key}") String privateKey,
            @Value("${alipay.format}") String format,
            @Value("${alipay.charset}") String charset,
            @Value("${alipay.alipay-public-key}") String alipayPublicKey,
            @Value("${alipay.sign-type}") String signType) {
        // DefaultAlipayClient 参数顺序：网关地址、appId、商户私钥、格式、字符集、支付宝公钥、签名类型
        return new DefaultAlipayClient(gatewayUrl, appId, privateKey, format, charset, alipayPublicKey, signType);
    }
}