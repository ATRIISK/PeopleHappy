package com.happymart.service.impl;

import com.happymart.entity.Category;
import com.happymart.mapper.CategoryMapper;
import com.happymart.service.CategoryService;
import com.happymart.vo.CategoryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
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

    @Override
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