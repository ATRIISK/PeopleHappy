package com.happymart.controller;

import com.happymart.common.annotation.Auth;
import com.happymart.common.result.Result;
import com.happymart.dto.AddressDTO;
import com.happymart.service.AddressService;
import com.happymart.vo.AddressVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 地址 Controller
 *
 * 全部 @Auth（所有地址操作都需要登录）
 * 路径前缀：/api/address
 * 不需要在 WebMvcConfig 加排除路径（没有公开接口）
 */
@Slf4j
@RestController
@RequestMapping("/api/address")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    /**
     * 新增地址
     * POST /api/address/add
     */
    @Auth
    @PostMapping("/add")
    public Result<Void> addAddress(HttpServletRequest request,
                                   @Valid @RequestBody AddressDTO dto) {
        Long userId = (Long) request.getAttribute("currentUserId");
        log.info("新增地址: userId={}", userId);
        addressService.addAddress(userId, dto);
        return Result.success();
    }

    /**
     * 地址列表
     * GET /api/address/list
     * 返回当前用户所有地址，默认排最前
     */
    @Auth
    @GetMapping("/list")
    public Result<List<AddressVO>> getAddressList(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        log.info("查询地址列表: userId={}", userId);
        List<AddressVO> list = addressService.getAddressList(userId);
        return Result.success(list);
    }

    /**
     * 修改地址
     * PUT /api/address/update
     */
    @Auth
    @PutMapping("/update")
    public Result<Void> updateAddress(HttpServletRequest request,
                                      @Valid @RequestBody AddressDTO dto) {
        Long userId = (Long) request.getAttribute("currentUserId");
        log.info("修改地址: id={}, userId={}", dto.getId(), userId);
        addressService.updateAddress(userId, dto);
        return Result.success();
    }

    /**
     * 删除地址
     * DELETE /api/address/delete/{id}
     */
    @Auth
    @DeleteMapping("/delete/{id}")
    public Result<Void> deleteAddress(HttpServletRequest request,
                                      @PathVariable Long id) {
        Long userId = (Long) request.getAttribute("currentUserId");
        log.info("删除地址: id={}, userId={}", id, userId);
        addressService.deleteAddress(userId, id);
        return Result.success();
    }
}
