package com.happymart.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置
 * 用于订单超时取消等异步场景
 *
 * 延迟方案：死信队列（DLX）
 * ========================
 * 不依赖 rabbitmq_delayed_message_exchange 插件
 * 用 RabbitMQ 原生 TTL + 死信交换机实现延迟效果
 *
 * 消息流向：
 *   Producer → order.exchange → order.delay.ttl.queue（TTL=30min）
 *     → 消息过期 → order.cancel.exchange → order.cancel.queue
 *     → OrderTimeoutConsumer 消费
 */
@Configuration
public class RabbitMQConfig {

    /** ========== 订单延迟队列（一级） ========== */
    public static final String ORDER_EXCHANGE = "order.exchange";

    /**
     * 订单延迟队列名
     *
     * ⚠️ 用 order.delay.ttl.queue 而不是 order.delay.queue 的原因：
     * RabbitMQ 的队列参数（x-message-ttl / x-dead-letter-*）是创建队列时确定的，创建后不可修改。
     * 如果本机之前跑过旧版应用，order.delay.queue 已经以"无参数"的形式存在，
     * 再次声明会报 406 PRECONDITION_FAILED 或新参数被忽略 → 超时取消永远不触发。
     * 换个新名字，RabbitMQ 会以带 TTL/死信参数的配置重新创建队列（旧队列残留无害，可手动删除）。
     */
    public static final String ORDER_DELAY_QUEUE = "order.delay.ttl.queue";
    public static final String ORDER_DELAY_ROUTING_KEY = "order.delay";

    /** ========== 死信队列（二级，存过期的取消消息） ========== */
    public static final String ORDER_CANCEL_EXCHANGE = "order.cancel.exchange";
    public static final String ORDER_CANCEL_QUEUE = "order.cancel.queue";
    public static final String ORDER_CANCEL_ROUTING_KEY = "order.cancel";

    /** 订单超时时间（毫秒）：30 分钟 */
    private static final int ORDER_TIMEOUT_MS = 30 * 60 * 1000;

    // ==================== 一级：延迟队列 ====================

    /**
     * 订单交换机（Direct 类型）
     * Producer 发消息到这个交换机 → 路由到 order.delay.ttl.queue
     */
    @Bean
    public DirectExchange orderExchange() {
        return ExchangeBuilder.directExchange(ORDER_EXCHANGE)
                .durable(true)
                .build();
    }

    /**
     * 订单延迟队列
     *
     * 关键配置（DLX 实现延迟）：
     * - x-message-ttl = 1800000（30分钟）→ 消息在这里停留 30 分钟
     * - x-dead-letter-exchange = order.cancel.exchange → 过期后投递到这个交换机
     * - x-dead-letter-routing-key = order.cancel → 过期后使用这个路由键
     *
     * 30 分钟内用户付了款 → 消息还在队列里，不用处理
     * 30 分钟后还没付款 → 消息过期 → 自动进入取消队列 → 消费者取消订单
     */
    @Bean
    public Queue orderDelayQueue() {
        return QueueBuilder.durable(ORDER_DELAY_QUEUE)
                // 消息存活 30 分钟后过期
                .withArgument("x-message-ttl", ORDER_TIMEOUT_MS)
                // 过期后投递到取消交换机
                .withArgument("x-dead-letter-exchange", ORDER_CANCEL_EXCHANGE)
                // 过期后使用的路由键
                .withArgument("x-dead-letter-routing-key", ORDER_CANCEL_ROUTING_KEY)
                .build();
    }

    /**
     * 绑定：order.exchange → order.delay.ttl.queue（路由键 order.delay）
     */
    @Bean
    public Binding orderDelayBinding() {
        return BindingBuilder.bind(orderDelayQueue())
                .to(orderExchange())
                .with(ORDER_DELAY_ROUTING_KEY);
    }

    // ==================== 二级：取消队列（死信队列） ====================

    /**
     * 订单取消交换机（死信交换机）
     * 延迟队列里的消息过期后，由 RabbitMQ 自动投递到这里
     */
    @Bean
    public DirectExchange orderCancelExchange() {
        return ExchangeBuilder.directExchange(ORDER_CANCEL_EXCHANGE)
                .durable(true)
                .build();
    }

    /**
     * 订单取消队列
     * OrderTimeoutConsumer 监听这个队列
     * 收到消息 → 查订单 → 未支付就取消 + 恢复库存
     */
    @Bean
    public Queue orderCancelQueue() {
        return QueueBuilder.durable(ORDER_CANCEL_QUEUE)
                .build();
    }

    /**
     * 绑定：order.cancel.exchange → order.cancel.queue（路由键 order.cancel）
     */
    @Bean
public Binding orderCancelBinding() {
        return BindingBuilder.bind(orderCancelQueue())
                .to(orderCancelExchange())
                .with(ORDER_CANCEL_ROUTING_KEY);
    }
}