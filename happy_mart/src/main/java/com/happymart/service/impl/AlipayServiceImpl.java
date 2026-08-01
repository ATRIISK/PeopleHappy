package com.happymart.service.impl;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.domain.AlipayTradePrecreateModel;
import com.alipay.api.domain.AlipayTradeQueryModel;
import com.alipay.api.domain.AlipayTradeRefundModel;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePrecreateRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradePrecreateResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.happymart.common.exception.BusinessException;
import com.happymart.common.result.ResultCodeEnum;
import com.happymart.entity.PaymentLog;
import com.happymart.mapper.PaymentLogMapper;
import com.happymart.service.AlipayService;
import com.happymart.vo.PayVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 支付宝支付服务实现类
 *
 * 对接 alipay.trade.precreate（当面付-扫码支付预下单），对应微信 NATIVE 模式
 * 同时提供：
 * - alipay.trade.refund（退款）
 * - alipay.trade.query（主动查询交易状态，兜底方案）
 * - RSA2 异步通知验签
 * - 支付流水记录
 *
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
    private final PaymentLogMapper paymentLogMapper;

    public AlipayServiceImpl(
            AlipayClient alipayClient,
            @Value("${alipay.notify-url}") String notifyUrl,
            @Value("${alipay.alipay-public-key}") String alipayPublicKey,
            @Value("${alipay.charset}") String charset,
            @Value("${alipay.sign-type}") String signType,
            PaymentLogMapper paymentLogMapper) {
        this.alipayClient = alipayClient;
        this.notifyUrl = notifyUrl;
        this.alipayPublicKey = alipayPublicKey;
        this.charset = charset;
        this.signType = signType;
        this.paymentLogMapper = paymentLogMapper;
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
    public TradeQueryResult queryTrade(String orderNo) {
        log.info("主动查询支付宝交易状态: orderNo={}", orderNo);

        AlipayTradeQueryModel model = new AlipayTradeQueryModel();
        model.setOutTradeNo(orderNo);

        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        request.setBizModel(model);

        try {
            AlipayTradeQueryResponse response = alipayClient.execute(request);
            if (response.isSuccess()) {
                String tradeStatus = response.getTradeStatus();
                String tradeNo = response.getTradeNo();
                log.info("支付宝交易查询成功: orderNo={}, tradeStatus={}, tradeNo={}",
                        orderNo, tradeStatus, tradeNo);
                return new TradeQueryResult(tradeStatus, tradeNo);
            } else {
                log.warn("支付宝交易查询返回失败: orderNo={}, code={}, msg={}, subMsg={}",
                        orderNo, response.getCode(), response.getMsg(), response.getSubMsg());
                return new TradeQueryResult(null, null);
            }
        } catch (AlipayApiException e) {
            log.error("调用支付宝交易查询接口失败: orderNo={}", orderNo, e);
            return new TradeQueryResult(null, null);
        }
    }

    @Override
    public void savePaymentLog(String orderNo, String tradeNo, String totalAmount, String tradeState, String notifyRaw) {
        log.info("记录支付流水: orderNo={}, tradeNo={}, tradeState={}", orderNo, tradeNo, tradeState);

        PaymentLog paymentLog = new PaymentLog();
        paymentLog.setOrderNo(orderNo);
        paymentLog.setTransactionId(tradeNo);
        paymentLog.setPayType("PRECREATE");
        // 支付宝金额单位是"元"字符串，这里转成"分"存，和 payment_log.total_fee 的设计保持一致
        if (totalAmount != null) {
            paymentLog.setTotalFee(new BigDecimal(totalAmount).multiply(BigDecimal.valueOf(100)).intValue());
        }
        paymentLog.setTradeState(tradeState);
        paymentLog.setNotifyRaw(notifyRaw);
        paymentLog.setCreateTime(LocalDateTime.now());
        paymentLogMapper.insert(paymentLog);
        log.debug("支付流水已记录: orderNo={}", orderNo);
    }

    @Override
    public boolean tradeRefund(String tradeNo, BigDecimal refundAmount, String orderNo, String outRequestNo) {
        log.info("发起支付宝退款: tradeNo={}, refundAmount={}, orderNo={}, outRequestNo={}",
                tradeNo, refundAmount, orderNo, outRequestNo);

        // ===== 1. 组装退款请求参数 =====
        AlipayTradeRefundModel model = new AlipayTradeRefundModel();
        // 用 trade_no（支付宝交易号）退款，而不是 out_trade_no（商户订单号）
        // trade_no 在支付成功时由支付宝异步通知回填到 order.transaction_id
        model.setTradeNo(tradeNo);
        // 退款金额，单位：元（和支付宝预下单接口一致，用字符串传）
        model.setRefundAmount(refundAmount.toPlainString());
        model.setRefundReason("用户主动退单");
        // ★ out_request_no = 退款请求号（幂等键）
        // 同一笔交易、同一个 out_request_no 重复调用退款接口，
        // 支付宝返回相同结果而不会重复退款。
        // 防止"支付宝退款成功但本地事务回滚"后重试时被支付宝拒绝 / 重复退款。
        model.setOutRequestNo(outRequestNo);

        AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
        request.setBizModel(model);

        // ===== 2. 调用支付宝网关 =====
        try {
            AlipayTradeRefundResponse response = alipayClient.execute(request);
            if (response.isSuccess()) {
                log.info("支付宝退款成功: tradeNo={}, refundAmount={}, orderNo={}",
                        tradeNo, refundAmount, orderNo);
                return true;
            } else {
                log.error("支付宝退款失败: tradeNo={}, code={}, msg={}, subMsg={}",
                        tradeNo, response.getCode(), response.getMsg(), response.getSubMsg());
                return false;
            }
        } catch (AlipayApiException e) {
            log.error("调用支付宝退款接口异常: tradeNo={}", tradeNo, e);
            return false;
        }
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