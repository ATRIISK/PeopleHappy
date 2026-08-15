package com.happymart.service.impl;                // 包声明 → Service 实现类放在 impl 子包下

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper; // MyBatis-Plus 条件查询构造器
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper; // MyBatis-Plus 条件更新构造器
import com.baomidou.mybatisplus.core.metadata.IPage;  // 分页结果接口
import com.baomidou.mybatisplus.extension.plugins.pagination.Page; // 分页对象
import com.happymart.common.exception.BusinessException;            // 业务异常
import com.happymart.common.result.ResultCodeEnum;                  // 错误码枚举
import com.happymart.entity.User;                                  // 用户实体
import com.happymart.mapper.UserMapper;                            // 用户 Mapper
import com.happymart.service.AdminUserService;                     // 本类实现的接口
import com.happymart.vo.UserVO;                                    // 用户视图对象
import lombok.RequiredArgsConstructor;                              // @RequiredArgsConstructor → 构造器注入
import lombok.extern.slf4j.Slf4j;                                   // @Slf4j → 日志
import org.springframework.beans.BeanUtils;                         // BeanUtils → 属性拷贝
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder; // BCrypt 加密器
import org.springframework.stereotype.Service;                      // @Service → 标记 Service 类
import org.springframework.util.StringUtils;                        // StringUtils → 字符串判断

import java.util.stream.Collectors;                                 // Collectors → 流收集

/**
 * 管理后台：用户管理服务实现类
 * <p>
 * 功能：用户列表（搜索）、禁用/启用、重置密码。
 * <p>
 * 自我保护设计：
 * - 不能禁用自己的账号（否则把自己禁了，系统就没管理员能操作了）
 * - 不能禁用一个管理员账号（避免把系统管理员全禁掉导致后台瘫痪）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    /** 用户 Mapper → 操作 user 表 */
    private final UserMapper userMapper;

    /** BCrypt 加密器 → 重置密码时加密新密码（和注册/登录用同一个 Bean） */
    private final BCryptPasswordEncoder passwordEncoder;

    /**
     * 分页查询用户列表（支持用户名/手机号模糊搜索）
     */
    @Override
    public IPage<UserVO> getAdminUserPage(String keyword, Integer page, Integer size) {

        // ===== 1. 构建查询条件 =====
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();

        // 按用户名或手机号模糊搜索（可选）
        // wrapper.and(...) 把两个 like 用括号包起来：(username LIKE ? OR phone LIKE ?)
        // 否则后面的 orderBy 会混进 OR 条件里，产生错误 SQL
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w
                    .like(User::getUsername, keyword)   // 用户名模糊
                    .or()                               // 或
                    .like(User::getPhone, keyword));    // 手机号模糊
        }

        // 注册时间倒序（新注册的排前面）
        wrapper.orderByDesc(User::getCreateTime);

        // ===== 2. 分页查询 =====
        Page<User> userPage = userMapper.selectPage(
                new Page<>(page, size),
                wrapper
        );

        // ===== 3. Entity 转 VO（去掉密码） =====
        Page<UserVO> voPage = new Page<>(
                userPage.getCurrent(),
                userPage.getSize(),
                userPage.getTotal()
        );
        voPage.setRecords(userPage.getRecords().stream()
                .map(this::convertToUserVO)
                .collect(Collectors.toList()));

        return voPage;
    }

    /**
     * 禁用/启用用户
     * <p>
     * 禁用后效果（拦截器实现）：
     * - 该用户下次登录 → 提示"账号已被禁用"
     * - 该用户已登录的 token 访问任何 @Auth 接口 → 401 被踢出
     */
    @Override
    public void updateUserStatus(Long currentUserId, Long targetUserId, Integer status) {

        // ===== 1. 自我保护：不能禁用自己的账号 =====
        // currentUserId 是拦截器从 token 解析存进 request attribute 的当前登录管理员ID
        if (currentUserId != null && currentUserId.equals(targetUserId)) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "不能禁用自己的账号");
        }

        // ===== 2. 目标用户必须存在 =====
        User target = userMapper.selectById(targetUserId);
        if (target == null) {
            throw new BusinessException(ResultCodeEnum.USER_NOT_EXIST);
        }

        // ===== 3. 自我保护：不能禁用一个管理员账号 =====
        // 防止把所有管理员都禁掉导致后台无人可用
        if ("ADMIN".equals(target.getRole())) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "不能禁用一个管理员账号");
        }

        // ===== 3.5 状态值必须合法：0=启用，1=禁用 =====
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "用户状态只能为 0（启用）或 1（禁用）");
        }

        // ===== 4. 只更新 status 字段（code-review 修复） =====
        // 不能"读整个实体再 updateById"：并发下管理员重置密码等操作会被整实体写回的
        // 旧 password 覆盖（互相丢失更新）。用 LambdaUpdateWrapper 只 set status，不碰其他字段。
        LambdaUpdateWrapper<User> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(User::getId, targetUserId).set(User::getStatus, status);   // 0=启用，1=禁用
        userMapper.update(null, wrapper);
        log.info("管理后台修改用户状态: userId={}, status={}", targetUserId, status);
    }

    /**
     * 重置用户密码
     * <p>
     * 管理员直接设置新密码，不需要验证旧密码（用户忘了密码时用）。
     * 新密码明文传过来，后端 BCrypt 加密后覆盖入库（和注册同一套加密方式）。
     */
    @Override
    public void resetUserPassword(Long targetUserId, String newPassword) {
        // 目标用户必须存在
        User target = userMapper.selectById(targetUserId);
        if (target == null) {
            throw new BusinessException(ResultCodeEnum.USER_NOT_EXIST);
        }

        // 只更新 password 字段（code-review 修复：避免整实体 updateById 覆盖并发修改的 status 等字段）
        // BCrypt 加密后覆盖（加密后同一明文每次结果不同，但 matches 校验都能通过）
        LambdaUpdateWrapper<User> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(User::getId, targetUserId).set(User::getPassword, passwordEncoder.encode(newPassword));
        userMapper.update(null, wrapper);
        log.info("管理后台重置用户密码: userId={}", targetUserId);
    }

    /**
     * 用户转换方法：User → UserVO（去掉密码）
     */
    private UserVO convertToUserVO(User user) {
        UserVO vo = new UserVO();
        // BeanUtils 自动拷贝同名属性；User 的 password 在 UserVO 里没有，自动跳过
        BeanUtils.copyProperties(user, vo);
        return vo;
    }
}
