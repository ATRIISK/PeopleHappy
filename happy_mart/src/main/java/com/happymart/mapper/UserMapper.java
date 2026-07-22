package com.happymart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.happymart.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper  // 告诉 Spring：这是一个 Mapper，启动时要扫描它
public interface UserMapper extends BaseMapper<User> {
    // 继承 BaseMapper<User> 后，自动拥有以下方法（不用你写）：
    // insert()   → 插入用户
    // deleteById() → 删除用户
    // updateById() → 更新用户
    // selectById() → 根据ID查用户
    // selectOne()  → 按条件查一个用户
    // selectList() → 查用户列表
    // selectCount() → 查总数
    // 等等...
}
