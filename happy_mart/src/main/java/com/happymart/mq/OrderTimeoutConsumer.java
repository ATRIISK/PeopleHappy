package com.happymart.mq;

import com.happymart.config.RabbitMQConfig;
import com.happymart.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ 消息消费者
 * 监听订单取消队列，处理超时未支付的订单
 *
 * 工作流程（对应开发文档 §7.4 超时取消流程）：
 * 1. 下单 30 分钟后，消息从 order.delay.queue 过期转到 order.cancel.queue
 * 2. 这里收到消息（消息体 = 订单ID）
 * 3. 查订单状态 → 如果还是 0（待支付）→ 取消订单 + 恢复库存
 * 4. 如果已经支付了（status ≠ 0）→ 不做任何操作（用户已经付了）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutConsumer {

    private final OrderService orderService;

    /**
     * 处理订单超时取消
     *
     * @RabbitListener 自动监听 order.cancel.queue
     * 收到消息后会调用这个方法
     * 消息体是订单 ID（Long 类型），由 RabbitMQ 自动反序列化
     *
     * acknowledge-mode=auto（在 application.yml 中配置）
     * 方法正常返回 → 自动 ack，消息从队列删除
     * 方法抛异常 → 消息重回队列重试
     */
    @RabbitListener(queues = RabbitMQConfig.ORDER_CANCEL_QUEUE)
    public void handleOrderTimeout(Long orderId) {
        log.info("收到订单超时取消消息: orderId={}", orderId);

        try {
            // 调用 OrderService 的超时取消方法
            // 内部逻辑：查订单 → 只有 status=0（待支付）才取消 → 恢复库存 → status=4
            orderService.cancelOrderByTimeout(orderId);
            log.info("订单超时取消处理完成: orderId={}", orderId);
        } catch (Exception e) {
            // 捕获异常但不抛出，防止消息一直重回队列死循环
            // 打日志告警，人工介入处理
            log.error("订单超时取消处理失败: orderId={}, error={}", orderId, e.getMessage(), e);
        }
    }
}