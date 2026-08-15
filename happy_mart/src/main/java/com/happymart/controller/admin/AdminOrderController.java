package com.happymart.controller.admin;            // 包声明 → 管理后台 Controller 统一放 controller/admin 包

import com.baomidou.mybatisplus.core.metadata.IPage; // 分页结果接口
import com.happymart.common.annotation.Auth;                    // 自定义 @Auth 注解 → requireAdmin=true 需要管理员权限
import com.happymart.common.result.Result;                      // 统一返回结果
import com.happymart.dto.OrderStatusDTO;                        // 修改订单状态请求参数
import com.happymart.service.AdminOrderService;                 // 管理后台订单服务
import com.happymart.vo.OrderVO;                                // 订单视图对象
import jakarta.validation.Valid;                                // @Valid → 开启参数校验
import lombok.RequiredArgsConstructor;                          // @RequiredArgsConstructor → 自动构造器注入
import lombok.extern.slf4j.Slf4j;                               // @Slf4j → 日志
import org.springframework.web.bind.annotation.GetMapping;      // @GetMapping → GET 请求
import org.springframework.web.bind.annotation.PutMapping;      // @PutMapping → PUT 请求
import org.springframework.web.bind.annotation.RequestBody;     // @RequestBody → 把请求体 JSON 转成 Java 对象
import org.springframework.web.bind.annotation.RequestMapping;  // @RequestMapping → 类级别路径前缀
import org.springframework.web.bind.annotation.RequestParam;    // @RequestParam → 查询参数
import org.springframework.web.bind.annotation.RestController;  // @RestController → 返回 JSON

/**
 * 管理后台：订单管理接口
 * <p>
 * 管理员订单操作：查看全部订单 + 发货（已支付 1 → 已发货 2）。
 * 取消/退单/确认收货这些操作属于用户端，管理员不越权。
 * <p>
 * 接口清单：
 * GET /api/admin/order/list        → 分页查全部订单（可按状态筛选）
 * PUT /api/admin/order/status      → 修改订单状态（发货）
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/order")
@RequiredArgsConstructor
public class AdminOrderController {

    private final AdminOrderService adminOrderService;   // 注入管理后台订单服务

    /**
     * 分页查询全部订单（可按状态筛选）
     * <p>
     * GET /api/admin/order/list?status=1&page=1&size=10
     */
    @Auth(requireAdmin = true)
    @GetMapping("/list")
    public Result<IPage<OrderVO>> getOrderPage(
            @RequestParam(required = false) Integer status,  // 订单状态筛选（可选，null=查全部）
            @RequestParam(defaultValue = "1") Integer page,  // 页码，默认第 1 页
            @RequestParam(defaultValue = "10") Integer size) // 每页条数，默认 10
    {
        return Result.success(adminOrderService.getAdminOrderPage(status, page, size));
    }

    /**
     * 修改订单状态（目前仅支持"发货"：status 1 → 2）
     * <p>
     * PUT /api/admin/order/status
     * body：{ "id": 1, "status": 2 }
     */
    @Auth(requireAdmin = true)
    @PutMapping("/status")
    public Result<Void> updateStatus(@Valid @RequestBody OrderStatusDTO dto) {
        adminOrderService.updateOrderStatus(dto.getId(), dto.getStatus());
        return Result.success();
    }
}
