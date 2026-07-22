package com.happymart.service.impl;                // 包声明 → Service 实现类放在 impl 子包下

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper; // MyBatis-Plus 的条件构造器 → 用来拼接 WHERE 条件
import com.happymart.common.exception.BusinessException;  // 业务异常 → 手动抛异常让全局处理器拦截
import com.happymart.common.result.ResultCodeEnum;        // 错误码枚举 → 统一错误码和错误信息
import com.happymart.dto.LoginDTO;                 // 登录请求参数
import com.happymart.dto.RegisterDTO;              // 注册请求参数
import com.happymart.entity.User;                  // 用户实体类 → 对应数据库 user 表
import com.happymart.mapper.UserMapper;            // 用户 Mapper → 操作数据库
import com.happymart.service.UserService;          // 自己实现的接口
import com.happymart.util.JwtUtil;                 // JWT 工具类 → 生成 token
import com.happymart.vo.LoginVO;                   // 登录返回值
import com.happymart.vo.UserVO;                    // 用户信息返回值
import lombok.RequiredArgsConstructor;              // @RequiredArgsConstructor → 自动生成构造器（final 字段的注入）
import lombok.extern.slf4j.Slf4j;                   // @Slf4j → 自动生成 log 对象，方便打日志
import org.springframework.beans.BeanUtils;         // BeanUtils → 属性拷贝工具，用来把 User 转成 UserVO
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder; // BCrypt 加密器 → 加密密码 / 比对密码
import org.springframework.stereotype.Service;      // @Service → 标记这是一个 Service 类，Spring 会自动扫描并管理
import org.springframework.transaction.annotation.Transactional; // @Transactional → 事务注解，方法执行出错自动回滚

/**
 * 用户服务实现类
 * <p>
 * 这是真正的业务逻辑所在地。
 * 注册：查重 → 加密密码 → 入库 → 返回用户信息
 * 登录：查用户 → 比对密码 → 生成 token → 返回 token + 用户信息
 * 查用户：根据 ID 查数据库 → 返回用户信息
 */
@Slf4j                                               // Lombok → 自动生成 log 变量，代码里直接用 log.info() 打日志
@Service                                              // 标记为 Service 层，Spring 会自动创建这个类的实例（单例）
@RequiredArgsConstructor                              // Lombok → 为 final 字段自动生成构造器（也就是构造器注入）
@Transactional(rollbackFor = Exception.class)         // 类级别事务：类中所有方法抛出异常时都回滚
public class UserServiceImpl implements UserService {

    // ↓↓↓ 以下字段都是 final，@RequiredArgsConstructor 会自动生成构造器注入它们 ↓↓↓

    private final UserMapper userMapper;               // 操作 user 表
    private final BCryptPasswordEncoder passwordEncoder; // 密码加密/比对
    private final JwtUtil jwtUtil;                      // JWT token 生成

    // ⚠️ 你没看到构造器，因为 @RequiredArgsConstructor 自动生成了一个
    // 三个参数的构造器，Spring 自动把这三个 Bean 传进来

    /**
     * 用户注册
     * <p>
     * 流程：
     * 1. 检查用户名是否已被注册 → 已存在就抛异常
     * 2. BCrypt 加密密码
     * 3. 插入数据库
     * 4. 返回用户信息（不含密码）
     */
    @Override
    public UserVO register(RegisterDTO dto) {
        log.info("用户注册: username={}", dto.getUsername());

        // ---------- 1. 检查用户名是否已存在 ----------
        // LambdaQueryWrapper：MyBatis-Plus 的条件查询构造器
        // eq(User::getUsername, dto.getUsername()) → WHERE username = 'xxx'
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, dto.getUsername());

        // selectOne：查一条记录，有数据说明用户名已经被注册了
        User existUser = userMapper.selectOne(wrapper);
        if (existUser != null) {
            log.warn("注册失败，用户名已存在: {}", dto.getUsername());
            // 抛业务异常 → 全局异常处理器会拦截并返回 {code: 1001, message: "用户已存在"}
            throw new BusinessException(ResultCodeEnum.USER_EXIST);
        }

        // ---------- 2. 创建用户实体，准备入库 ----------
        User user = new User();
        user.setUsername(dto.getUsername());

        // BCrypt 加密密码：passwordEncoder.encode(明文) → 返回加密后的密文
        // 数据库中存的是密文，不是明文！
        String encodedPassword = passwordEncoder.encode(dto.getPassword());
        user.setPassword(encodedPassword);

        // 手机号可选，不为空才设置
        if (dto.getPhone() != null && !dto.getPhone().isEmpty()) {
            user.setPhone(dto.getPhone());
        }

        // 默认角色：普通用户（管理员需要手动在数据库改）
        user.setRole("USER");

        // ---------- 3. 插入数据库 ----------
        // BaseMapper<User> 自带的 insert() 方法，直接 INSERT INTO user ...
        // MyBatis-Plus 自动填充 createTime 和 updateTime（看 BaseEntity + MetaObjectHandler）
        userMapper.insert(user);
        log.info("注册成功: userId={}", user.getId());

        // ---------- 4. 返回 UserVO（去掉密码） ----------
        return convertToUserVO(user);
    }

    /**
     * 用户登录
     * <p>
     * 流程：
     * 1. 根据用户名查用户 → 查不到抛异常
     * 2. 比对密码 → 不正确抛异常
     * 3. 生成 JWT token → 返回 LoginVO(token + 用户信息)
     */
    @Override
    public LoginVO login(LoginDTO dto) {
        log.info("用户登录: username={}", dto.getUsername());

        // ---------- 1. 根据用户名查用户 ----------
        // WHERE username = 'xxx'
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, dto.getUsername());

        User user = userMapper.selectOne(wrapper);
        if (user == null) {
            log.warn("登录失败，用户不存在: {}", dto.getUsername());
            // 这里故意不告诉用户"是用户名还是密码错了"（防止黑客试探）
            // 统一提示：用户名或密码错误
            throw new BusinessException(ResultCodeEnum.LOGIN_FAIL);
        }

        // ---------- 2. 比对密码 ----------
        // passwordEncoder.matches(明文, 密文) → true=密码正确，false=错误
        // 注意：第一个参数是用户输入的明文，第二个参数是数据库里存的密文
        boolean matches = passwordEncoder.matches(dto.getPassword(), user.getPassword());
        if (!matches) {
            log.warn("登录失败，密码错误: username={}", dto.getUsername());
            throw new BusinessException(ResultCodeEnum.LOGIN_FAIL);
        }

        // ---------- 3. 生成 JWT token ----------
        // JwtUtil.generateToken(用户ID, 用户名)
        // token 中包含了用户 ID 和用户名，后面请求时通过 @Auth 拦截器解析出来
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        log.info("登录成功: userId={}", user.getId());

        // ---------- 4. 组装返回值 ----------
        LoginVO loginVO = new LoginVO();
        loginVO.setToken(token);                           // 设置 JWT token
        loginVO.setUserInfo(convertToUserVO(user));        // 设置用户信息（不含密码）

        return loginVO;
    }

    /**
     * 根据用户 ID 获取用户信息
     * <p>
     * 这个方法是给其他模块用的（比如订单模块要查用户信息）。
     * Controller 中 @Auth 拦截器解析出 userId 后，调用这个方法。
     */
    @Override
    public UserVO getUserById(Long id) {
        log.debug("查询用户信息: userId={}", id);

        // selectById：BaseMapper 自带方法，根据主键查询
        User user = userMapper.selectById(id);

        if (user == null) {
            log.warn("用户不存在: userId={}", id);
            throw new BusinessException(ResultCodeEnum.USER_NOT_EXIST);
        }

        return convertToUserVO(user);
    }

    /**
     * 工具方法：把 User 实体转换成 UserVO（去掉了密码）
     * <p>
     * BeanUtils.copyProperties(源对象, 目标对象)：
     * 自动把源对象中"字段名相同"的属性复制到目标对象。
     * User 和 UserVO 都有 id/username/phone/avatar/role/createTime，所以一行就复完了。
     * User 里的 password 字段在 UserVO 中没有，所以自动跳过。
     */
    private UserVO convertToUserVO(User user) {
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        return userVO;
    }
}