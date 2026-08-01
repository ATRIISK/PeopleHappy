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
     * 记录支付流水（遵循 MVC：Service 层操作 Mapper，Controller 不直接调 Mapper）
     *
     * 支付宝异步通知回调中需要记录每次回调的完整报文到 payment_log 表。
     * 这个方法封装了 PaymentLog 的创建和插入，Controller 只需要传参即可。
     *
     * @param orderNo     商户订单号
     * @param tradeNo     支付宝交易号
     * @param totalAmount 订单金额（元字符串，自动转分为 int）
     * @param tradeState  交易状态（TRADE_SUCCESS / TRADE_FINISHED 等）
     * @param notifyRaw   异步通知原始报文
     */
    void savePaymentLog(String orderNo, String tradeNo, String totalAmount, String tradeState, String notifyRaw);

    /**
     * 支付宝退款（alipay.trade.refund）
     *
     * 对已支付的订单进行退款操作。
     * 在沙箱环境下同样会执行退款逻辑。
     *
     * @param tradeNo      支付宝交易号（order.transaction_id，支付成功时由支付宝回填）
     * @param refundAmount 退款金额，单位：元（对应 order.total_amount）
     * @param orderNo      商户订单号（仅用于日志记录）
     * @param outRequestNo 退款请求号（out_request_no）——幂等键
     *                     ⚠️ 同一笔交易、同一个 out_request_no 重复请求退款，
     *                     支付宝会返回相同结果而不会重复退款。
     *                     这样即使本地事务回滚后重试，也不会"退两次钱"。
     *                     实际使用中传订单号 orderNo 即可。
     * @return true=退款成功，false=退款失败
     */
    boolean tradeRefund(String tradeNo, BigDecimal refundAmount, String orderNo, String outRequestNo);

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