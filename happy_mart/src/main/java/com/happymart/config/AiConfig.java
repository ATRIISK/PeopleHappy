package com.happymart.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * AI 购物助手配置类（v1.11，阶段四，见开发文档 §14）
 * <p>
 * API Key 和模型名由 spring-ai-alibaba-starter-dashscope 自动配置
 * 从 application.yml 的 spring.ai.dashscope.* 读取（api-key 走环境变量 AI_DASHSCOPE_API_KEY），
 * 这里只需注册 ChatClient 对话客户端 + 自定义 RestClient 超时。
 * <p>
 * 代码结构复用自 AI 博客项目 D:\ai_blog_show（同 Boot 3.5.14，已验证可运行）。
 */
@Configuration
public class AiConfig {

    /**
     * 自定义 RestClient，配置超时时间
     * <p>
     * 大模型生成回答比较慢（可能十几秒），而 Spring 默认 RestClient 超时很短，
     * 容易被中断。这里改成：连接 60 秒、读取 120 秒，给模型留足生成时间。
     * <p>
     * 注意：这会覆盖 Spring Boot 自动配置的 RestClient.Builder（Spring AI 内部的 HTTP 调用会用到它）。
     */
    @Bean
    public RestClient.Builder restClientBuilder() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(60).toMillis()); // 连接超时：60s
        factory.setReadTimeout((int) Duration.ofSeconds(120).toMillis());   // 读取超时：120s
        return RestClient.builder().requestFactory(factory);
    }

    /**
     * ChatClient Bean —— AI 对话客户端
     * <p>
     * 第一版【不配置会话记忆】：
     * 接口 /api/ai/** 对游客开放（WebMvcConfig 已放行），如果后端用全局内存记忆，
     * 不同用户的对话会互相串上下文（A 问的问题 B 的下一条能"接上"），隐私和体验都不对。
     * 所以聊天历史由前端 AiAssistant.vue 自己维护（界面上的消息列表），
     * 每次请求是"无状态"的：只基于当前问题 + 商品知识库检索结果回答。
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
