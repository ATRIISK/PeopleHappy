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
}
