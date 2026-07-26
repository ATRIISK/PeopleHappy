package com.happymart.interceptor;                    // 包声明 → 拦截器统一放在 interceptor 包下

import com.happymart.common.annotation.Auth;                    // 自定义 @Auth 注解
import com.happymart.common.result.Result;                      // 统一返回结果
import com.happymart.common.result.ResultCodeEnum;              // 错误码枚举
import com.happymart.util.JwtUtil;                              // JWT 工具类
import jakarta.servlet.http.HttpServletRequest;                 // 请求对象 → 取请求头、设置属性
import jakarta.servlet.http.HttpServletResponse;                // 响应对象 → 写返回数据
import lombok.RequiredArgsConstructor;                          // @RequiredArgsConstructor → 自动构造器注入
import lombok.extern.slf4j.Slf4j;                               // @Slf4j → 日志
import org.springframework.stereotype.Component;                // @Component → 让 Spring 管理这个拦截器
import org.springframework.web.method.HandlerMethod;            // HandlerMethod → 当前请求对应哪个 Controller 方法
import org.springframework.web.servlet.HandlerInterceptor;      // 拦截器接口 → Spring MVC 的拦截器

import com.fasterxml.jackson.databind.ObjectMapper;            // Jackson → 把 Java 对象转成 JSON 字符串写回前端

/**
 * JWT 认证拦截器
 * <p>
 * 拦截所有请求，检查 Controller 方法上有没有 @Auth 注解。
 * - 有 @Auth 注解 → 解析 token，校验身份
 * - 没有 @Auth 注解 → 直接放行（比如登录、注册接口）
 * <p>
 * 拦截器和过滤器的区别：
 * - Filter（过滤器）是 Servlet 层面的，所有请求都经过，比拦截器早
 * - Interceptor（拦截器）是 Spring MVC 层面的，经过 DispatcherServlet 后才执行
 * 我们这里用拦截器就够了，因为只关心 Controller 方法的注解。
 */
@Slf4j                                               // Lombok → 自动生成 log 变量
@Component                                            // 标记为 Spring 组件，让 Spring 管理
@RequiredArgsConstructor                              // Lombok → 为 final 字段自动生成构造器
public class JwtAuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;                     // JWT 工具 → 校验 token、解析用户 ID
    private final ObjectMapper objectMapper;           // Jackson 的 JSON 转换器 → 把 Result 转成 JSON 写回前端

    /**
     * 请求到达 Controller 之前执行
     * <p>
     * 返回 true = 放行，继续执行 Controller
     * 返回 false = 拦截，不执行 Controller
     */
    @Override
    public boolean preHandle(
            HttpServletRequest request,                // HTTP 请求
            HttpServletResponse response,              // HTTP 响应
            Object handler                             // 这次请求要执行的目标对象（一般就是 Controller 方法）
    ) throws Exception {

        // ---------- 1. 判断是不是 Controller 方法 ----------
        // 有时候请求的是静态资源（图片、CSS），handler 不是 HandlerMethod 类型
        // 这种情况直接放行，不用处理
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        // ---------- 2. 检查方法上有没有 @Auth 注解 ----------
        // getMethod() → 拿到 Method 对象
        // getAnnotation(Auth.class) → 看看这个方法上有没有 @Auth 注解
        Auth auth = handlerMethod.getMethod().getAnnotation(Auth.class);

        // 没有 @Auth 注解 → 说明这个接口不需要登录，直接放行
        if (auth == null) {
            return true;
        }

        // ---------- 3. 有 @Auth 注解 → 从请求头取 token ----------
        // 前端约定：token 放在 Authorization 请求头里，格式是 "Bearer xxxxx.yyyyy.zzzzz"
        String authHeader = request.getHeader("Authorization");
        log.debug("获取到 Authorization 头: {}", authHeader);

        // 请求头为空，或者不是以 Bearer 开头 → token 无效
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("认证失败：请求头中没有有效的 Authorization");
            // 返回 401 错误给前端
            writeUnauthorized(response, "未登录，请先登录");
            return false;
        }

        // ---------- 4. 提取 token（去掉 "Bearer " 前缀） ----------
        String token = authHeader.substring(7);        // "Bearer ".length() = 7

        // ---------- 5. 校验 token ----------
        if (!jwtUtil.validateToken(token)) {
            log.warn("认证失败：token 无效或已过期");
            writeUnauthorized(response, "token 已过期或无效，请重新登录");
            return false;
        }

        // ---------- 6. 解析用户 ID，放到请求属性中 ----------
        // 这样 Controller 方法里从 request 就能拿到当前登录用户 ID
        Long userId = jwtUtil.getUserIdFromToken(token);
        request.setAttribute("currentUserId", userId);
        log.debug("token 校验通过，userId={}", userId);

        // ---------- 7. 如果 @Auth 要求管理员权限，检查角色 ----------
        // TODO：如果需要管理员校验，可以从 token 中解析 role 字段
        // 目前先留着扩展点，后续开发管理后台再实现
        if (auth.requireAdmin()) {
            String role = jwtUtil.getUsernameFromToken(token); // 这里暂用，后续改成 getRoleFromToken
            // 这里先简单记录日志，完整的管理员校验以后加
            log.debug("需要管理员权限，当前用户 role={}", role);
        }

        // 全部校验通过 → 放行到 Controller
        return true;
    }

    /**
     * 返回 401 未认证错误给前端
     * <p>
     * 因为拦截器在 Controller 之前执行，没法用 @RestControllerAdvice 统一处理，
     * 所以只能手动写 JSON 到 response 里。
     */
    private void writeUnauthorized(HttpServletResponse response, String message) throws Exception {
        // 设置响应状态码 401 和 Content-Type
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);  // 401
        response.setContentType("application/json;charset=UTF-8"); // 告诉前端返回的是 JSON

        // 构建统一的返回格式 Result.fail(UNAUTHORIZED, message)
        Result<Void> result = Result.fail(ResultCodeEnum.UNAUTHORIZED, message);

        // 用 ObjectMapper 把 Result 对象转成 JSON 字符串，写到响应体里
        String json = objectMapper.writeValueAsString(result);
        response.getWriter().write(json);
    }
}
