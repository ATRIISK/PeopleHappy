package com.happymart.service;                    // 包声明 → Service 接口层

import com.happymart.dto.LoginDTO;                 // 登录请求参数
import com.happymart.dto.RegisterDTO;              // 注册请求参数
import com.happymart.vo.LoginVO;                   // 登录返回值（token + 用户信息）
import com.happymart.vo.UserVO;                    // 用户信息返回值（不含密码）
/**
 * 用户服务接口
 * <p>
 * Service 层负责"业务逻辑"，Controller 只负责"接收请求、返回结果"。
 * 这里定义 3 个方法：注册、登录、查用户信息。
 * 接口的好处：后面增删改查方法多了，实现类可以随时扩展，Controller 那边不用改。
 */
public interface UserService {

    /**
     * 用户注册
     *
     * @param dto 注册参数（用户名、密码、手机号）
     * @return 注册成功的用户信息（不含密码）
     */
    UserVO register(RegisterDTO dto);

    /**
     * 用户登录
     *
     * @param dto 登录参数（用户名、密码）
     * @return 登录结果（JWT token + 用户信息）
     */
    LoginVO login(LoginDTO dto);

    /**
     * 根据用户 ID 获取用户信息
     *
     * @param id 用户 ID
     * @return 用户信息（不含密码）
     */
    UserVO getUserById(Long id);

    /**
     * 根据用户 ID 获取用户信息（供 JWT 拦截器校验用）
     *
     * 和 getUserById 的区别：
     * - getUserById：用户不存在会抛 USER_NOT_EXIST 异常（给正常业务接口用）
     * - getAuthUser：用户不存在直接返回 null，不抛异常（给拦截器用）
     *
     * ⚠️ 为什么拦截器不能用 getUserById？
     * 拦截器在 Controller 之前执行，@RestControllerAdvice 管不到它。
     * 如果 getUserById 抛异常，不会走全局异常处理器，会直接变成 HTTP 500。
     * 所以专门做一个"查不到就返回 null"的方法，让拦截器自己判断。
     *
     * @param id 用户 ID
     * @return 用户信息（不含密码）；用户不存在返回 null
     */
    UserVO getAuthUser(Long id);
}
