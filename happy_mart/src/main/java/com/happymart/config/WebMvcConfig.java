package com.happymart.config;                    // 包声明 → 配置类统一放 config 包

import lombok.RequiredArgsConstructor;             // @RequiredArgsConstructor → 自动构造器注入
import org.springframework.context.annotation.Configuration; // @Configuration → 标记这是一个配置类
import org.springframework.web.servlet.config.annotation.InterceptorRegistry; // 拦截器注册器 → 注册拦截器用的
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;   // WebMvcConfigurer → Spring MVC 配置接口

/**
 * Web MVC 配置类
 * <p>
 * 这里通注册自定义拦截器 JwtAuthInterceptor。
 * WebMvcConfigurer 是 Spring 提供的"钩子"，实现这个接口可以定制 Spring MVC 的行为。
 * 我们只重写 addInterceptors 方法，其他用默认实现。
 * <p>
 * 排除了哪些路径不需要拦截：
 * - /api/user/register → 注册接口，没登录也能注册
 * - /api/user/login → 登录接口，没登录也能登录
 * - 其他模块的开放路径后续再加
 */
@Configuration                                       // 标记配置类，Spring 启动时会加载
@RequiredArgsConstructor                              // Lombok → 为 final 字段自动生成构造器
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtAuthInterceptor jwtAuthInterceptor; // 注入刚才写的拦截器

    /**
     * 注册拦截器
     * <p>
     * addInterceptor：添加拦截器
     * addPathPatterns：要拦截哪些路径（/** 表示所有路径）
     * excludePathPatterns：排除哪些路径（不拦截）
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtAuthInterceptor)          // 注册拦截器
                .addPathPatterns("/**")                      // 拦截所有请求
                .excludePathPatterns(                        // 不拦截以下路径
                        "/api/user/register",                // 注册接口
                        "/api/user/login"                    // 登录接口
                        // 以后其他不需要登录的接口也加在这里↓
                        // "/api/product/list",              // 商品列表（以后开放）
                );
    }
}