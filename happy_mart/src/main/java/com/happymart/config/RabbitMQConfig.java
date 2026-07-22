package com.happymart.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置
 * 用于订单超时取消等异步场景
 */
@Configuration
public class RabbitMQConfig {

    /** 订单交换机 */
    public static final String ORDER_EXCHANGE = "order.exchange";
    /** 订单延迟队列 */
    public static final String ORDER_DELAY_QUEUE = "order.delay.queue";
    /** 订单延迟路由键 */
    public static final String ORDER_DELAY_ROUTING_KEY = "order.delay";

    /**
     * 订单交换机（延迟消息需要插件 rabbitmq_delayed_message_exchange）
     * 如果服务端未安装插件，可改用死信队列方式实现延迟
     */
    @Bean
    public DirectExchange orderExchange() {
        return ExchangeBuilder.directExchange(ORDER_EXCHANGE)
                .durable(true)
                .build();
    }

    /**
     * 订单延迟队列
     */
    @Bean
    public Queue orderDelayQueue() {
        return QueueBuilder.durable(ORDER_DELAY_QUEUE)
                .build();
    }

    /**
     * 绑定关系
     */
    @Bean
    public Binding orderDelayBinding() {
        return BindingBuilder.bind(orderDelayQueue())
                .to(orderExchange())
                .with(ORDER_DELAY_ROUTING_KEY);
    }
}