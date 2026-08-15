package com.happymart.service;                    // 包声明 → Service 接口层

import com.baomidou.mybatisplus.core.metadata.IPage; // 分页结果接口
import com.happymart.vo.UserVO;                   // 用户视图对象

/**
 * 管理后台：用户管理服务接口
 * <p>
 * 管理员能：查看用户列表、禁用/启用用户、重置用户密码。
 */
public interface AdminUserService {

    /**
     * 分页查询用户列表（支持用户名/手机号模糊搜索）
     *
     * @param keyword 搜索关键词（可为 null）
     * @param page    当前页码
     * @param size    每页条数
     * @return 分页结果（UserVO 不含密码）
     */
    IPage<UserVO> getAdminUserPage(String keyword, Integer page, Integer size);

    /**
     * 禁用/启用用户
     *
     * @param currentUserId 当前登录管理员ID（用于"禁止禁用自己的账号"校验）
     * @param targetUserId  目标用户ID
     * @param status        状态：0=启用，1=禁用
     */
    void updateUserStatus(Long currentUserId, Long targetUserId, Integer status);

    /**
     * 重置用户密码（管理员直接设置新密码，BCrypt 加密覆盖，不需要旧密码）
     *
     * @param targetUserId 目标用户ID
     * @param newPassword  新密码（明文，后端加密后入库）
     */
    void resetUserPassword(Long targetUserId, String newPassword);
}
