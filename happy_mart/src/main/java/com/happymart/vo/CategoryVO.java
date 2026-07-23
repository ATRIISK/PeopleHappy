package com.happymart.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/**
 * 分类返回值 VO
 * 返回给前端，不包含数据库内部字段（如 createTime 等）
 * @JsonInclude(NON_EMPTY) 让空数组不返回，减少数据量
 */
@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class CategoryVO {

    private Long id;
    private String name;
    private Long parentId;
    private Integer level;
    private Integer sort;
    private List<CategoryVO> children;   // 子分类，树形结构用
}