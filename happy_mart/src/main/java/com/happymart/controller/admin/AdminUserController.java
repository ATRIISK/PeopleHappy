package com.happymart.controller.admin;            // 包声明 → 管理后台 Controller 统一放 controller/admin 包

import com.baomidou.mybatisplus.core.metadata.IPage; // 分页结果接口
import com.happymart.common.annotation.Auth;                    // 自定义 @Auth 注解 → requireAdmin=true 需要管理员权限
import com.happymart.common.result.Result;                      // 统一返回结果
import com.happymart.dto.ResetPwdDTO;                           // 重置密码请求参数
import com.happymart.dto.UserStatusDTO;                         // 修改用户状态请求参数
import com.happymart.service.AdminUserService;                  // 管理后台用户服务
import com.happymart.vo.UserVO;                                 // 用户视图对象
import jakarta.servlet.http.HttpServletRequest;                 // 请求对象 → 从属性里取当前管理员ID
import jakarta.validation.Valid;                                // @Valid → 开启参数校验
import lombok.RequiredArgsConstructor;                          // @RequiredArgsConstructor → 自动构造器注入
import lombok.extern.slf4j.Slf4j;                               // @Slf4j → 日志
import org.springframework.web.bind.annotation.GetMapping;      // @GetMapping → GET 请求
import org.springframework.web.bind.annotation.PathVariable;   // @PathVariable → 路径参数
import org.springframework.web.bind.annotation.PutMapping;      // @PutMapping → PUT 请求
import org.springframework.web.bind.annotation.RequestBody;     // @RequestBody → 把请求体 JSON 转成 Java 对象
import org.springframework.web.bind.annotation.RequestMapping;  // @RequestMapping → 类级别路径前缀
import org.springframework.web.bind.annotation.RequestParam;    // @RequestParam → 查询参数
import org.springframework.web.bind.annotation.RestController;  // @RestController → 返回 JSON

/**
 * 管理后台：用户管理接口
 * <p>
 * 管理员能：查看用户列表、禁用/启用用户、重置用户密码。
 * <p>
 * 接口清单：
 * GET  /api/admin/user/list             → 分页查用户列表（可搜索 username/phone）
 * PUT  /api/admin/user/status/{id}      → 禁用/启用用户（status：0=启用，1=禁用）
 * PUT  /api/admin/user/resetPwd/{id}    → 重置用户密码
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/user")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;   // 注入管理后台用户服务

    /**
     * 分页查询用户列表（可按用户名/手机号搜索）
     * <p>
     * GET /api/admin/user/list?keyword=张三&page=1&size=10
     */
    @Auth(requireAdmin = true)
    @GetMapping("/list")
    public Result<IPage<UserVO>> getUserPage(
            @RequestParam(required = false) String keyword,  // 搜索关键词（可选）
            @RequestParam(defaultValue = "1") Integer page,  // 页码，默认第 1 页
            @RequestParam(defaultValue = "10") Integer size) // 每页条数，默认 10
    {
        return Result.success(adminUserService.getAdminUserPage(keyword, page, size));
    }

    /**
     * 禁用/启用用户
     * <p>
     * PUT /api/admin/user/status/3
     * body：{ "status": 1 }  （0=启用，1=禁用）
     * <p>
     * 禁用后该用户：登录提示"账号已被禁用"；已登录的 token 访问任何 @Auth 接口被 401 踢出。
     * 自我保护：不能禁用自己的账号，不能禁用一个管理员账号（Service 层校验）。
     */
    @Auth(requireAdmin = true)
    @PutMapping("/status/{id}")
    public Result<Void> updateStatus(
            HttpServletRequest request,        // 请求对象 → 取当前登录管理员ID（用于"禁止禁用自己的账号"校验）
            @PathVariable Long id,             // 目标用户ID（路径参数）
            @Valid @RequestBody UserStatusDTO dto) {   // 目标状态（body）
        // 从请求属性取当前登录管理员ID（JwtAuthInterceptor 里设置的）
        Long currentUserId = (Long) request.getAttribute("currentUserId");
        adminUserService.updateUserStatus(currentUserId, id, dto.getStatus());
        return Result.success();
    }

    /**
     * 重置用户密码
     * <p>
     * PUT /api/admin/user/resetPwd/3
     * body：{ "password": "新密码" }  （至少 6 位）
     */
    @Auth(requireAdmin = true)
    @PutMapping("/resetPwd/{id}")
    public Result<Void> resetPwd(
            @PathVariable Long id,                       // 目标用户ID（路径参数）
            @Valid @RequestBody ResetPwdDTO dto) {       // 新密码（body）
        adminUserService.resetUserPassword(id, dto.getPassword());
        return Result.success();
    }
}
