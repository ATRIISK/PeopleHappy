package com.happymart.config;                    // 包声明 → 配置类统一放 config 包

import com.happymart.interceptor.JwtAuthInterceptor; // 拦截器 → 从 interceptor 包导入
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
                        "/api/user/register",                // 注册接口 → 没登录也能注册
                        "/api/user/login",                   // 登录接口 → 没登录也能登录
                        "/api/product/list",                 // 商品列表 → 游客也能看
                        "/api/product/detail/**",            // 商品详情 → 游客也能看
                        "/api/product/hot",                  // 热门商品 → 游客也能看
                        "/api/category/**"                   // 分类查询 → 游客也能看
                        // ⚠️ 新增公开接口一定要加在这里！
                        // 否则会被 JWT 拦截器拦截，返回 401 未登录
                        // 比如：
                        // "/api/cart/list",   ← 这个不能加！购物车需要登录才能看
                        // "/api/order/**",    ← 这个不能加！订单需要登录才能看
                        // ⏳ AI 购物助手预留（阶段四，见开发文档 §14）：
                        // 未来需放行 "/api/ai/**"（游客可问商品），开发时取消注释下面这行
                        // "/api/ai/**",
                );
    }
}