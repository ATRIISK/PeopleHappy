package com.happymart.interceptor;                    // 包声明 → 拦截器统一放在 interceptor 包下

import com.happymart.common.annotation.Auth;                    // 自定义 @Auth 注解
import com.happymart.common.result.Result;                      // 统一返回结果
import com.happymart.common.result.ResultCodeEnum;              // 错误码枚举
import com.happymart.service.UserService;                       // 用户服务 → 拦截器查用户状态/角色
import com.happymart.vo.UserVO;                                 // 用户信息 VO → 拦截器读 role/status
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

    // 用户服务 → 拦截器查当前登录用户的 status（禁用拦截）和 role（管理员校验）
    // 依赖链：WebMvcConfig → JwtAuthInterceptor → UserService → UserMapper（单向，无循环依赖）
    // @RequiredArgsConstructor 会自动把这个 final 字段加进构造器注入
    private final UserService userService;

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

        // ---------- 6. 解析用户 ID（token 里的 subject） ----------
        Long userId = jwtUtil.getUserIdFromToken(token);

        // ---------- 6.5 查用户状态（禁用即时生效 + 管理员角色） ----------
        // 为什么每次 @Auth 请求都要查一次数据库？
        //   token 里只存了 userId 和 username，没有 status/role。
        //   如果只靠 token，管理后台改了用户状态（禁用/改角色）后，
        //   已登录用户拿着旧 token 照样能访问，禁用就不"即时生效"了。
        //   所以这里每次 @Auth 请求都查一次 user 表（主键查询，代价很小），拿最新的 status 和 role。
        // 为什么不把 role 写进 token？
        //   角色被改（如 USER 升 ADMIN）要重新登录才生效；查库则立即生效，不用重新登录。
        UserVO authUser;
        try {
            // 查数据库拿最新的 status 和 role
            authUser = userService.getAuthUser(userId);
        } catch (Exception e) {
            // 数据库暂时不可用时认证查询会失败。拦截器在 Controller 之前执行，
            // 异常不会走 @RestControllerAdvice，直接抛会变成非 JSON 的 500（code-review 修复）。
            // 这里捕获并返回可读的 500 JSON：前端 request.js 弹"服务器异常"，且不会误清用户 token。
            log.error("认证时查询用户失败: userId={}, error={}", userId, e.getMessage());
            writeServerError(response, "服务暂时不可用，请稍后重试");
            return false;
        }

        // 用户不存在（被删除）→ 当作未登录处理
        if (authUser == null) {
            log.warn("认证失败：用户不存在或已被删除, userId={}", userId);
            writeUnauthorized(response, "用户不存在，请重新登录");
            return false;
        }

        // 账号被禁用（管理后台 status=1）→ 立即踢出，所有需要登录的接口都进不去
        // 用 Integer.valueOf(1).equals(...)：authUser.getStatus() 为 null 时返回 false（不空指针）
        if (Integer.valueOf(1).equals(authUser.getStatus())) {
            log.warn("认证失败：账号已被禁用, userId={}", userId);
            writeUnauthorized(response, "账号已被禁用，请联系管理员");
            return false;
        }

        // 把用户 ID 放到请求属性中，Controller 里用 request.getAttribute("currentUserId") 取
        request.setAttribute("currentUserId", userId);
        log.debug("token 校验通过，userId={}", userId);

        // ---------- 7. 需要管理员权限 → 校验角色（强制，不再只是打日志） ----------
        if (auth.requireAdmin()) {
            // 只有 role = "ADMIN" 的管理员能访问带 @Auth(requireAdmin = true) 的接口
            if (!"ADMIN".equals(authUser.getRole())) {
                log.warn("权限不足：非管理员访问管理接口, userId={}", userId);
                // 返回 403 → 前端 request.js 响应拦截器会弹"无权限访问"
                writeForbidden(response, "无权限访问");
                return false;
            }
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

    /**
     * 返回 403 无权限错误给前端（普通用户访问管理员接口时用）
     * <p>
     * 和 writeUnauthorized 一样的套路，只是状态码从 401 变成 403。
     * 前端 request.js 已处理：HTTP 403 → ElMessage.error("无权限访问")
     */
    private void writeForbidden(HttpServletResponse response, String message) throws Exception {
        // 设置响应状态码 403（Forbidden = 已登录但没权限）和 Content-Type
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8"); // 告诉前端返回的是 JSON

        // 构建统一的返回格式 Result.fail(FORBIDDEN, message)
        Result<Void> result = Result.fail(ResultCodeEnum.FORBIDDEN, message);

        // 用 ObjectMapper 把 Result 对象转成 JSON 字符串，写到响应体里
        String json = objectMapper.writeValueAsString(result);
        response.getWriter().write(json);
    }

    /**
     * 返回 500 服务器错误给前端（认证时数据库不可用等场景）
     * <p>
     * 前端 request.js 已处理：HTTP 500 → ElMessage.error("服务器异常")
     * 用 500 而不是 401：数据库故障不是"未登录"，不应让前端清掉用户 token
     */
    private void writeServerError(HttpServletResponse response, String message) throws Exception {
        // 设置响应状态码 500（服务器内部错误）和 Content-Type
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        response.setContentType("application/json;charset=UTF-8"); // 告诉前端返回的是 JSON

        // 构建统一的返回格式 Result.fail(SYSTEM_ERROR, message)
        Result<Void> result = Result.fail(ResultCodeEnum.SYSTEM_ERROR, message);

        // 用 ObjectMapper 把 Result 对象转成 JSON 字符串，写到响应体里
        String json = objectMapper.writeValueAsString(result);
        response.getWriter().write(json);
    }
}
