package com.happymart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.happymart.entity.Category;
import org.apache.ibatis.annotations.Mapper;

/**
 * 分类 Mapper 接口
 * 继承 BaseMapper 自动获得 CRUD 方法，无需额外定义
 */
@Mapper
public interface CategoryMapper extends BaseMapper<Category> {
}
