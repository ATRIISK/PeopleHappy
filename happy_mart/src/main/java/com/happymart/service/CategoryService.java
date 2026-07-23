package com.happymart.service;
import com.happymart.vo.CategoryVO;
import java.util.List;
/**
 * 分类服务接口
 */
public interface CategoryService {
    /**
     * 获取分类树(一级分类+各自的子分类)
     */
    List<CategoryVO> getCategoryTree();
}
