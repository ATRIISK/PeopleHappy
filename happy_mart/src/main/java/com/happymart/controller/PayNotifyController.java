package com.happymart.controller;

import com.happymart.common.annotation.Auth;
import com.happymart.common.result.Result;
import com.happymart.common.result.ResultCodeEnum;
import com.happymart.entity.PaymentLog;
import com.happymart.mapper.PaymentLogMapper;
import com.happymart.service.AlipayService;
import com.happymart.service.OrderService;
import com.happymart.vo.OrderVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 支付宝异步通知回调 Controller
 *
 * 路径前缀：/api/pay（不是 /api/order，和 OrderController 分开）
 * 方法上没有 @Auth 注解 → JwtAuthInterceptor 天然放行，不需要改 WebMvcConfig
 *
 * ⚠️ 和普通接口的关键差异（对应开发文档 §7.3）：
 * 1. 支付宝异步通知是 application/x-www-form-urlencoded 表单参数，不是 JSON body，
 *    所以用 HttpServletRequest 手动取 getParameterMap()，不能用 @RequestBody
 * 2. 响应体不能用 Result<T>，也不是 JSON；支付宝要求返回纯文本字符串 "success"/"failure"，
 *    所以方法返回类型是 String（RestController 默认用 StringHttpMessageConverter 原样写出，不会包一层 JSON）
 */
@Slf4j
@RestController
@RequestMapping("/api/pay")
@RequiredArgsConstructor
public class PayNotifyController {

    private final AlipayService alipayService;
    private final OrderService orderService;
    private final PaymentLogMapper paymentLogMapper;

    /**
     * 支付宝异步通知回调
     * POST /api/pay/notify
     */
    @PostMapping("/notify")
    public String notify(HttpServletRequest request) {
        // ===== 1. 把表单参数组装成 Map<String, String>（验签要用） =====
        Map<String, String[]> parameterMap = request.getParameterMap();
        Map<String, String> params = new HashMap<>();
        StringBuilder rawBuilder = new StringBuilder();
        for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
            String value = entry.getValue().length > 0 ? entry.getValue()[0] : "";
            params.put(entry.getKey(), value);
            rawBuilder.append(entry.getKey()).append("=").append(value).append("&");
        }
        String notifyRaw = rawBuilder.toString();
        log.info("收到支付宝异步通知: {}", notifyRaw);

        String orderNo = params.get("out_trade_no");
        String tradeNo = params.get("trade_no");
        String tradeStatus = params.get("trade_status");
        String totalAmount = params.get("total_amount");

        // ===== 2. 验签（RSA2） =====
        boolean verified = alipayService.verifyNotify(params);

        // ===== 3. 验签通过 + 交易成功状态 → 更新订单为已支付 =====
        if (verified && ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus))) {
            orderService.handlePaid(orderNo, tradeNo);
        } else if (!verified) {
            log.warn("支付宝异步通知验签失败: orderNo={}", orderNo);
        }

        // ===== 4. 无论成功与否都留一条流水记录，方便排查问题 =====
        PaymentLog paymentLog = new PaymentLog();
        paymentLog.setOrderNo(orderNo);
        paymentLog.setTransactionId(tradeNo);
        paymentLog.setPayType("PRECREATE");
        // 支付宝金额单位是"元"字符串，这里转成"分"存，和 payment_log.total_fee 的设计保持一致
        if (totalAmount != null) {
            paymentLog.setTotalFee(new BigDecimal(totalAmount).multiply(BigDecimal.valueOf(100)).intValue());
        }
        paymentLog.setTradeState(tradeStatus);
        paymentLog.setNotifyRaw(notifyRaw);
        paymentLog.setCreateTime(LocalDateTime.now());
        paymentLogMapper.insert(paymentLog);

        // ===== 5. 必须返回纯文本，不能是 JSON =====
        return verified ? "success" : "failure";
    }

    /**
     * 【开发调试用】模拟支付宝异步通知，直接标记订单为已支付
     *
     * POST /api/pay/simulate/{orderId}
     *
     * 纯开发辅助接口，跳过支付宝验签流程，直接调 orderService.handlePaid() 更新订单状态。
     * 配合前端轮询 GET /api/order/status/{id} 使用，方便本地调试"支付成功→状态更新→跳转"的全流程。
     * ⚠️ 上线前需关闭或移除该接口，防止被恶意调用绕过支付。
     *
     * @param orderId 订单 ID
     */
    @Auth
    @PostMapping("/simulate/{orderId}")
    public Result<Void> simulatePayment(HttpServletRequest request, @PathVariable Long orderId) {
        Long userId = (Long) request.getAttribute("currentUserId");
        log.info("模拟支付回调: orderId={}, userId={}", orderId, userId);
        // 通过 OrderService 校验订单存在 + 状态（不直接调 Mapper，符合 MVC 规范）
        OrderVO orderVO = orderService.getOrderDetail(userId, orderId);
        if (orderVO.getStatus() != 0) {
            return Result.fail(ResultCodeEnum.ORDER_STATUS_ERROR,
                    "当前订单状态不允许模拟支付，status=" + orderVO.getStatus());
        }
        orderService.handlePaid(orderVO.getOrderNo(), "SIMULATE_" + System.currentTimeMillis());
        log.info("模拟支付回调成功: orderId={}, orderNo={}, userId={}", orderId, orderVO.getOrderNo(), userId);
        return Result.success();
    }
}