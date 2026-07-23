package com.happymart.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.happymart.common.exception.BusinessException;
import com.happymart.common.result.ResultCodeEnum;
import com.happymart.entity.Product;
import com.happymart.mapper.ProductMapper;
import com.happymart.service.ProductService;
import com.happymart.vo.ProductVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;
/**
 * 商品服务实现
 * 核心逻辑：MyBatis-Plus
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class ProductServiceImpl implements ProductService{

    private final ProductMapper productMapper;
    private  final ObjectMapper objectMapper;//用于JSON字符串和LIST转换

    @Override
    public Page<ProductVO> getProductPage(Long categoryId, String keyword, String sortBy, Integer page, Integer size){

        log.info("商品分类查询:categoryId={}, keyword={}, sortBy={}, page={}, size={}",categoryId,keyword,sortBy,page,size);


        //1.构建查询条件
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();

        // 按分类筛选：如果传了一级分类ID，需要查该分类下所有子分类的商品
        // 这里简单处理：直接用 categoryId 匹配，前端传二级分类ID
        if(categoryId != null){
            wrapper.eq(Product::getCategoryId,categoryId);
        }

        //按关键词模糊搜索商品名称
        if(StringUtils.hasText(keyword)){
            wrapper.like(Product::getName,keyword);
        }

        //只查上架商品(status = 0)
        wrapper.eq(Product::getStatus,0);

        //2.排序
        if(StringUtils.hasText(sortBy)) {
            switch (sortBy) {
                case "sales" -> wrapper.orderByDesc(Product::getSales);  //销量最高
                case "price_asc" -> wrapper.orderByAsc(Product::getPrice);  //价格从低到高
                case "price_desc" -> wrapper.orderByDesc(Product::getPrice); //价格从高到低
                case "newest" -> wrapper.orderByDesc(Product::getCreateTime); //最新上架
                case "rating" -> wrapper.orderByDesc(Product::getRating);  //默认按销量
            }
        }else {
            wrapper.orderByDesc(Product::getSales); //默认按销量
        }

        //3.分页查询
        Page<Product> productPage = productMapper.selectPage(
                new Page<>(page, size), wrapper);

        // 4. Entity → VO 转换（分页内容转换）
        Page<ProductVO> voPage = new Page<>(productPage.getCurrent(),
                productPage.getSize(), productPage.getTotal());
        List<ProductVO> voList = productPage.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
        voPage.setRecords(voList);

        log.info("商品分页查询完成: 总数={}, 当前页={}", voPage.getTotal(), voPage.getCurrent());
        return voPage;
    }

    @Override
    public ProductVO getProductById(Long id) {
        log.info("查询商品详情: id={}", id);

        Product product = productMapper.selectById(id);
        if (product == null) {
            log.warn("商品不存在: id={}", id);
            throw new BusinessException(ResultCodeEnum.NOT_FOUND);  // 需要在 ResultCodeEnum 加这个
        }

        return convertToVO(product);
    }

    /**
     * Product 实体 → ProductVO 转换
     * 关键：images 字段在数据库存的是 JSON 字符串，转成 List<String> 再给前端
     */
    private ProductVO convertToVO(Product product) {
        ProductVO vo = new ProductVO();
        BeanUtils.copyProperties(product, vo);

        // 把 images JSON 字符串转成 List<String>
        if (product.getImages() != null && !product.getImages().isEmpty()) {
            try {
                List<String> imageList = objectMapper.readValue(
                        product.getImages(),
                        new TypeReference<List<String>>() {});
                vo.setImages(imageList);
            } catch (Exception e) {
                log.warn("商品 images 字段 JSON 解析失败: id={}, images={}",
                        product.getId(), product.getImages());
                vo.setImages(List.of(product.getImage()));  // 解析失败就用主图兜底
            }
        }

        return vo;
    }
}

