package com.happymart.service.impl;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.domain.AlipayTradePrecreateModel;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePrecreateRequest;
import com.alipay.api.response.AlipayTradePrecreateResponse;
import com.happymart.common.exception.BusinessException;
import com.happymart.common.result.ResultCodeEnum;
import com.happymart.service.AlipayService;
import com.happymart.vo.PayVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 支付宝支付服务实现类
 *
 * 对接 alipay.trade.precreate（当面付-扫码支付预下单），对应微信 NATIVE 模式
 * 这里只返回 qr_code，没有"预支付ID"这个概念（和微信 code_url 一样不带 prepay_id）
 */
@Slf4j
@Service
public class AlipayServiceImpl implements AlipayService {

    private final AlipayClient alipayClient;
    private final String notifyUrl;
    private final String alipayPublicKey;
    private final String charset;
    private final String signType;

    public AlipayServiceImpl(
            AlipayClient alipayClient,
            @Value("${alipay.notify-url}") String notifyUrl,
            @Value("${alipay.alipay-public-key}") String alipayPublicKey,
            @Value("${alipay.charset}") String charset,
            @Value("${alipay.sign-type}") String signType) {
        this.alipayClient = alipayClient;
        this.notifyUrl = notifyUrl;
        this.alipayPublicKey = alipayPublicKey;
        this.charset = charset;
        this.signType = signType;
    }

    @Override
    public PayVO createQrOrder(String orderNo, BigDecimal totalAmount, String subject) {
        log.info("创建支付宝扫码支付订单: orderNo={}, totalAmount={}", orderNo, totalAmount);

        // ===== 1. 组装请求参数（走 bizModel，SDK 内部处理成 JSON） =====
        AlipayTradePrecreateModel model = new AlipayTradePrecreateModel();
        model.setOutTradeNo(orderNo);
        // ⚠️ 注意：支付宝金额单位是"元"，用字符串传，不是微信的"分"（整数）
        model.setTotalAmount(totalAmount.toPlainString());
        model.setSubject(subject);

        AlipayTradePrecreateRequest request = new AlipayTradePrecreateRequest();
        request.setNotifyUrl(notifyUrl);
        request.setBizModel(model);

        // ===== 2. 调用支付宝网关 =====
        AlipayTradePrecreateResponse response;
        try {
            response = alipayClient.execute(request);
        } catch (AlipayApiException e) {
            log.error("调用支付宝预下单接口失败: orderNo={}", orderNo, e);
            throw new BusinessException(ResultCodeEnum.PAY_FAIL, "支付宝下单失败，请稍后重试");
        }

        if (!response.isSuccess()) {
            log.error("支付宝预下单返回失败: orderNo={}, code={}, msg={}",
                    orderNo, response.getCode(), response.getMsg());
            throw new BusinessException(ResultCodeEnum.PAY_FAIL, "支付宝下单失败：" + response.getSubMsg());
        }

        // ===== 3. 只有 qr_code，没有"预支付ID" =====
        log.info("支付宝预下单成功: orderNo={}, qrCode={}", orderNo, response.getQrCode());
        PayVO payVO = new PayVO();
        payVO.setCodeUrl(response.getQrCode());
        return payVO;
    }

    @Override
    public boolean verifyNotify(Map<String, String> params) {
        try {
            boolean result = AlipaySignature.rsaCheckV1(params, alipayPublicKey, charset, signType);
            if (!result) {
                log.warn("支付宝异步通知验签失败: params={}", params);
            }
            return result;
        } catch (AlipayApiException e) {
            log.error("支付宝异步通知验签异常", e);
            return false;
        }
    }
}