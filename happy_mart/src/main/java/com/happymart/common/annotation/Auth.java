package com.happymart.common.annotation;          // 包声明 → 所有自定义注解放在 annotation 包下

import java.lang.annotation.ElementType;           // ElementType → 限制这个注解可以用在什么地方（方法、类...）
import java.lang.annotation.Retention;             // Retention → 注解保留到什么时候（源码、编译、运行）
import java.lang.annotation.RetentionPolicy;       // RetentionPolicy → Retention 的参数
import java.lang.annotation.Target;                // Target → 指定注解可以加在哪里

/**
 * 登录认证注解
 * <p>
 * 用法：在 Controller 方法上加上 @Auth，表示这个接口需要登录才能访问。
 * <p>
 * 例子：
 * <pre>
 * &#64;Auth
 * &#64;GetMapping("/user/info")
 * public Result&lt;UserVO&gt; getUserInfo() { ... }
 * </pre>
 * <p>
 * 如果没传 token 或 token 过期，拦截器会自动返回 401。
 * 如果加了 requireAdmin = true，普通用户（USER）会被拒绝，只有管理员（ADMIN）能访问。
 */
@Target(ElementType.METHOD)                        // 这个注解只能加在方法上（不能加在类上）
@Retention(RetentionPolicy.RUNTIME)                // 运行时保留（JVM 加载类时能读到这个注解，拦截器里才能判断）
public @interface Auth {

    /**
     * 是否必须登录（默认 true）
     * <p>
     * 设为 false 的话，有 token 就解析用户信息，没有也能访问。
     * 通常用不上，留着备用而已。
     */
    boolean required() default true;

    /**
     * 是否需要管理员权限（默认 false）
     * <p>
     * 设为 true 的话，只有 role = "ADMIN" 的用户能访问。
     * 以后后台管理接口用得上。
     */
    boolean requireAdmin() default false;
}