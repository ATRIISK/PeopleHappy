package com.happymart.controller.admin;            // 包声明 → 管理后台 Controller 统一放 controller/admin 包

import com.happymart.common.annotation.Auth;                    // 自定义 @Auth 注解 → requireAdmin=true 需要管理员权限
import com.happymart.common.result.Result;                      // 统一返回结果
import com.happymart.service.AdminUploadService;                // 管理后台文件上传服务
import lombok.RequiredArgsConstructor;                          // @RequiredArgsConstructor → 自动构造器注入
import lombok.extern.slf4j.Slf4j;                               // @Slf4j → 日志
import org.springframework.web.bind.annotation.PostMapping;     // @PostMapping → POST 请求
import org.springframework.web.bind.annotation.RequestMapping;  // @RequestMapping → 类级别路径前缀
import org.springframework.web.bind.annotation.RequestParam;    // @RequestParam → 表单字段
import org.springframework.web.bind.annotation.RestController;  // @RestController → 返回 JSON
import org.springframework.web.multipart.MultipartFile;         // MultipartFile → 上传文件对象

/**
 * 管理后台：文件上传接口
 * <p>
 * 商品图片本地上传：商家在前端选本地图片 → 这里接收 → Service 存盘 → 返回相对 URL。
 * <p>
 * 接口清单：
 * POST /api/admin/upload/image → 上传一张商品图片（返回 /upload/yyyyMMdd/&lt;uuid&gt;.ext）
 * <p>
 * 前端 el-upload 的 action="/api/admin/upload/image"，表单字段名必须是 file
 * （与 @RequestParam("file") 对应）。
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/upload")
@RequiredArgsConstructor
public class AdminUploadController {

    private final AdminUploadService adminUploadService;   // 注入上传服务

    /**
     * 上传商品图片（仅管理员）
     * <p>
     * POST /api/admin/upload/image（multipart/form-data，字段名 file）
     * 返回：{ code: 200, message: "成功", data: "/upload/20260815/xxx.jpg" }
     * <p>
     * 必须管理员权限：@Auth(requireAdmin=true)，普通用户调用返回 403。
     */
    @Auth(requireAdmin = true)
    @PostMapping("/image")
    public Result<String> uploadImage(@RequestParam("file") MultipartFile file) {
        // 严格 MVC：Controller 只接收参数 + 调 Service + 包装 Result，文件存盘逻辑在 Service
        return Result.success(adminUploadService.storeImage(file));
    }
}
