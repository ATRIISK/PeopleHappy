package com.happymart.config;                      // 包名 → 必须和目录一致

import org.springframework.context.annotation.Bean;               // @Bean：告诉 Spring，这个方法返回的对象要交给 Spring 容器管理
import org.springframework.context.annotation.Configuration;       // @Configuration：标记这是一个配置类，Spring 启动时会加载它
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder; // BCrypt 加密工具 → 用于密码加密和比对

/**
 * Security 配置类
 *
 * 这里只引入了一个 BCryptPasswordEncoder，用来加密用户密码。
 * 我们没有用 Spring Security 的拦截器/过滤器链，认证走的是自定义 @Auth 注解 + JWT。
 * 之所以只用 spring-security-crypto 这个依赖，是因为它只负责密码加密，不引入安全框架。
 *
 * 为什么要加密存密码？
 * 数据库一旦泄露，明文密码就全没了。
 * BCrypt 是单向哈希 + 自动加盐，每次加密结果都不一样，黑客拿到的哈希也没法反推原文。
 */
@Configuration                                       // 告诉 Spring：这是一个配置类
public class SecurityConfig {

    /**
     * 把 BCryptPasswordEncoder 交给 Spring 管理
     * 其他地方（比如 Service 层）直接用 @Autowired 注入就能用
     */
    @Bean                                              // @Bean：将这个方法的返回值注册为 Spring Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();            // BCrypt 加密器，默认强度 10（可以自己传参调强度）
    }
}