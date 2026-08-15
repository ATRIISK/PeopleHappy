package com.happymart.service.impl;

import com.happymart.entity.Category;
import com.happymart.mapper.CategoryMapper;
import com.happymart.service.CategoryService;
import com.happymart.vo.CategoryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
/**
 * 分类服务实现
 * 核心逻辑：查全部->按parentID分组->组装树
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)// 所有运行时异常都回滚事务
public class CategoryServiceImpl implements CategoryService{

    private final CategoryMapper categoryMapper;

    /**
     * 获取分类树（一级分类 + 各自的子分类）
     * <p>
     * 缓存说明（开发文档 §8 场景三，String 结构 + 1 小时 TTL）：
     * {@code @Cacheable(cacheNames = "category:tree")} 的含义：
     * - 第一次调用：执行方法体 → 查数据库 → 把整棵分类树存进 Redis（1 小时后过期）
     * - 第二次调用（1 小时内）：不执行方法体，直接返回 Redis 里的缓存值 → 不再查数据库
     * - cacheNames = "category:tree" → 对应 RedisConfig 里**单独配了 1 小时 TTL** 的那个缓存名
     *   （CacheManager 里 withCacheConfiguration("category:tree", ...) 单独覆盖了 TTL，其他缓存默认 30 分钟）
     * - key = "'tree'" → 给缓存指定一个常量 key（带单引号表示字符串，SpEL 语法）
     *   为什么必须指定？因为方法没有参数，Spring Cache 默认会用 SimpleKey.EMPTY 当 key，
     *   导致 Redis 里的 key 变成 category:tree::SimpleKey []（难看且不规范）。
     *   指定后实际 Redis 里的 key 是 category:tree::tree（格式 = 缓存名::key，文档 §8 写的 category:tree 是概念写法）
     * <p>
     * 为什么分类树适合缓存？
     *   分类是"低频变动"数据（只有管理员新增/修改/删分类才变），但前台每个页面都要查，
     *   缓存 1 小时可以大大减少数据库压力。
     * <p>
     * ⏳ 预留：管理后台目前没有"分类管理"功能（后台只做商品/订单/用户），
     *   将来若加分类增删改，需在保存方法上加
     *   {@code @CacheEvict(cacheNames = "category:tree", allEntries = true)}
     *   清除整棵分类树缓存，否则前台要等 1 小时缓存过期才看到新分类。
     * <p>
     * 注意：缓存命中时方法体不执行，所以"查询全部分类树"这句日志在命中缓存时不会打印。
     *
     * @return 分类树（一级分类列表，每个含 children 子分类）
     */
    @Override
    @Cacheable(cacheNames = "category:tree", key = "'tree'")
    public List<CategoryVO> getCategoryTree() {
        log.info("查询全部分类树");

        // 1. 查全部一级分类（parentId = 0），按 sort 正序排
        List<Category> allCategories = categoryMapper.selectList(null);

        // 2. 找到所有一级分类（parentId = 0）
        List<Category> parentCategories = allCategories.stream()
                .filter(c -> c.getParentId() == 0)
                .collect(Collectors.toList());

        // 3. 遍历一级分类，找到各自的子分类
        List<CategoryVO> tree = new ArrayList<>();
        for (Category parent : parentCategories) {
            CategoryVO parentVO = convertToVO(parent);
            // 找当前一级分类下的所有子分类
            List<CategoryVO> children = allCategories.stream()
                    .filter(c -> c.getParentId() != null && c.getParentId().equals(parent.getId()))
                    .map(this::convertToVO)
                    .collect(Collectors.toList());
            parentVO.setChildren(children);
            tree.add(parentVO);
        }

        log.info("分类树查询完成，一级分类数: {}", tree.size());
        return tree;
    }

    /**
     * Category 实体 → CategoryVO 转换
     */
    private CategoryVO convertToVO(Category category) {
        CategoryVO vo = new CategoryVO();
        BeanUtils.copyProperties(category, vo);   // 同名属性一键复制
        return vo;
    }
}