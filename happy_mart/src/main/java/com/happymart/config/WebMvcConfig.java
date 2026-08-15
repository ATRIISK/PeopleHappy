package com.happymart.config;                    // 包声明 → 配置类统一放 config 包

import com.happymart.interceptor.JwtAuthInterceptor; // 拦截器 → 从 interceptor 包导入
import lombok.RequiredArgsConstructor;             // @RequiredArgsConstructor → 自动构造器注入
import org.springframework.beans.factory.annotation.Value; // @Value → 读取配置（上传目录）
import org.springframework.context.annotation.Configuration; // @Configuration → 标记这是一个配置类
import org.springframework.web.servlet.config.annotation.InterceptorRegistry; // 拦截器注册器 → 注册拦截器用的
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry; // 静态资源注册器 → 映射 /upload 目录
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;   // WebMvcConfigurer → Spring MVC 配置接口

import java.nio.file.Paths;                        // Paths → 把上传目录转成绝对路径 URI

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
     * 商品图片本地上传目录（application.yml 的 app.upload-dir，默认 ./upload）
     * 注意：非 final 字段，用 @Value 注入，不影响 @RequiredArgsConstructor（它只注入 final 字段）
     */
    @Value("${app.upload-dir:./upload}")
    private String uploadDir;

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

    /**
     * 静态资源映射：把磁盘 upload 目录暴露为 /upload/** 静态资源（v1.10 商品图片本地上传）
     * <p>
     * 前端 <img src="/upload/yyyyMMdd/xxx.jpg"> 不需要登录即可访问（商品图游客也要看）。
     * 拦截器对非 Controller 方法（静态资源由 ResourceHttpRequestHandler 处理）直接放行，
     * 所以 /upload/** 不需要加进上面拦截器的排除列表，天然公开。
     * <p>
     * 为什么用 Paths.get(uploadDir).toAbsolutePath().toUri() 而不是写死 file:./upload/？
     *   toUri() 会生成正确的 file:// 协议 URL：
     *   - Windows 开发：file:///E:/PeopleHappy/happy_mart/upload/
     *   - Linux Docker 容器（WORKDIR=/app）：file:///app/upload/
     *   两种环境通吃，避免相对路径 + 反斜杠在 Windows 下解析出问题。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 把 app.upload-dir（默认 ./upload）转成绝对路径的 file:// URI
        String location = Paths.get(uploadDir).toAbsolutePath().toUri().toString();
        // ★ 必须保证以 / 结尾（code-review 修复）：
        // Path.toUri() 在目录不存在时不补尾斜杠（file:///E:/.../upload），
        // Spring 静态资源解析会用 new URL(base, "20260815/x.jpg") 拼接，
        // base 无尾斜杠时会把最后一段 "upload" 当文件名替换掉 → 映射到错误目录、图片 404。
        // upload 目录被 .gitignore 忽略，全新 clone 时不存在，最容易触发。
        if (!location.endsWith("/")) {
            location += "/";
        }
        // /upload/** 的请求 → 从磁盘 upload 目录找文件返回
        registry.addResourceHandler("/upload/**").addResourceLocations(location);
    }
}