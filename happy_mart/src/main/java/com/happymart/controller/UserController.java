package com.happymart.controller;                 // 包声明 → Controller 层统一放 controller 包

import com.happymart.common.annotation.Auth;                    // 自定义 @Auth 注解 → 需要登录的接口加这个
import com.happymart.common.result.Result;                      // 统一返回结果
import com.happymart.dto.LoginDTO;                              // 登录请求参数
import com.happymart.dto.RegisterDTO;                           // 注册请求参数
import com.happymart.service.UserService;                       // 用户服务
import com.happymart.vo.LoginVO;                                // 登录返回值
import com.happymart.vo.UserVO;                                 // 用户信息返回值
import jakarta.servlet.http.HttpServletRequest;                 // 请求对象 → 从属性里取 userId
import jakarta.validation.Valid;                                // @Valid → 开启参数校验（配合 DTO 中的 @NotBlank）
import lombok.RequiredArgsConstructor;                          // @RequiredArgsConstructor → 自动构造器注入
import lombok.extern.slf4j.Slf4j;                               // @Slf4j → 日志
import org.springframework.web.bind.annotation.GetMapping;       // @GetMapping → GET 请求
import org.springframework.web.bind.annotation.PostMapping;      // @PostMapping → POST 请求
import org.springframework.web.bind.annotation.RequestBody;      // @RequestBody → 把请求体 JSON 转成 Java 对象
import org.springframework.web.bind.annotation.RequestMapping;  // @RequestMapping → 类级别路径前缀
import org.springframework.web.bind.annotation.RestController;   // @RestController → 标记这是一个 Controller，返回 JSON

/**
 * 用户控制器
 * <p>
 * 接口清单：
 * POST   /api/user/register  → 注册（无需登录）
 * POST   /api/user/login     → 登录（无需登录）
 * GET    /api/user/info      → 获取当前登录用户信息（需要登录）
 * <p>
 * 数据流向：
 * 前端请求 → Controller（接收参数、校验）→ Service（业务逻辑）→ 数据库
 *                                                       ↓
 *                                       返回 Result<VO>（JSON 给前端）
 */
@Slf4j                                               // Lombok → 自动生成 log 变量
@RestController                                       // @Controller + @ResponseBody = 返回 JSON
@RequestMapping("/api/user")                          // 所有接口以 /api/user 开头
@RequiredArgsConstructor                              // Lombok → 自动生成构造器注入
public class UserController {

    private final UserService userService;             // 注入 Service 层

    /**
     * 用户注册
     * <p>
     * 请求方式：POST
     * 路径：/api/user/register
     * 参数：{ "username": "xxx", "password": "xxx", "phone": "xxx" }（JSON 格式）
     * 返回：{ code: 200, message: "成功", data: { id, username, phone, avatar, role, createTime } }
     * <p>
     * 不需要 @Auth 注解 → 注册不需要登录
     */
    @PostMapping("/register")                         // POST 请求，路径 /api/user/register
    public Result<UserVO> register(@Valid @RequestBody RegisterDTO dto) {
        // @Valid → 校验参数（检查 username 是不是为空、password 长度够不够）
        // @RequestBody → 把请求体 JSON 转成 RegisterDTO 对象
        // 校验不通过 → 全局异常处理器拦截，返回参数错误

        log.info("接收到注册请求: username={}", dto.getUsername());
        UserVO userVO = userService.register(dto);    // 调用 Service 层注册
        return Result.success(userVO);                // 包装成统一格式返回
    }

    /**
     * 用户登录
     * <p>
     * 请求方式：POST
     * 路径：/api/user/login
     * 参数：{ "username": "xxx", "password": "xxx" }
     * 返回：{ code: 200, message: "成功", data: { token: "xxx", userInfo: { ... } } }
     * <p>
     * 不需要 @Auth 注解 → 登录不需要登录（废话😂）
     */
    @PostMapping("/login")                            // POST 请求，路径 /api/user/login
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        log.info("接收到登录请求: username={}", dto.getUsername());
        LoginVO loginVO = userService.login(dto);     // 调用 Service 层登录
        return Result.success(loginVO);               // 包装返回
    }

    /**
     * 获取当前登录用户信息
     * <p>
     * 请求方式：GET
     * 路径：/api/user/info
     * 请求头：Authorization: Bearer xxxxx.yyyyy.zzzzz
     * 返回：{ code: 200, message: "成功", data: { id, username, phone, avatar, role, createTime } }
     * <p>
     * 需要 @Auth 注解 → 没 token 会被拦截器拦截，返回 401
     * 拦截器验证通过后，把 userId 放在 request 的属性中
     * 这里直接从 request 取 userId，然后查用户信息
     */
    @Auth                                                // 需要登录！没 token 会被拦截
    @GetMapping("/info")                                 // GET 请求，路径 /api/user/info
    public Result<UserVO> getUserInfo(HttpServletRequest request) {
        // 从请求属性中获取当前登录用户 ID（JwtAuthInterceptor 里设置的）
        Long userId = (Long) request.getAttribute("currentUserId");
        log.info("查询当前用户信息: userId={}", userId);

        UserVO userVO = userService.getUserById(userId); // 调用 Service 查用户
        return Result.success(userVO);                   // 包装返回
    }
}