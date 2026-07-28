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

    /**
     * 主动查询支付宝交易状态（兜底方案）
     *
     * 当支付宝异步通知因网络原因未到达时，前端轮询可通过此接口主动查询支付宝，
     * 避免订单状态卡在"待付款"无法更新的问题。
     * 调用支付宝 alipay.trade.query 接口。
     *
     * @param orderNo 商户订单号
     * @return 查询结果对象（含 tradeStatus 和 tradeNo），若交易不存在则 tradeStatus 为 null
     */
    TradeQueryResult queryTrade(String orderNo);

    /**
     * 主动查询支付宝交易状态的结果
     */
    class TradeQueryResult {
        /** 支付宝交易状态：WAIT_BUYER_PAY / TRADE_SUCCESS / TRADE_FINISHED / TRADE_CLOSED */
        private String tradeStatus;
        /** 支付宝交易号（trade_no），支付成功后有值 */
        private String tradeNo;

        public TradeQueryResult(String tradeStatus, String tradeNo) {
            this.tradeStatus = tradeStatus;
            this.tradeNo = tradeNo;
        }

        public String getTradeStatus() { return tradeStatus; }
        public String getTradeNo() { return tradeNo; }
    }
}