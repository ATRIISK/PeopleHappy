package com.happymart.service;                    // 包声明 → Service 接口层

import org.springframework.web.multipart.MultipartFile; // MultipartFile → Spring 封装的上传文件对象

/**
 * 管理后台：文件上传服务接口
 * <p>
 * 商品图片本地存盘，返回可访问的相对 URL。
 * 存盘逻辑放 Service 层（严格 MVC：Controller 只收参数 + 调 Service + 包 Result，文件 IO 是业务逻辑）。
 */
public interface AdminUploadService {

    /**
     * 保存一张商品图片
     * <p>
     * 处理：非空校验 → 大小校验 → 扩展名白名单 → UUID 重命名 + 按日期子目录存盘 → 返回相对 URL。
     *
     * @param file 上传的文件（前端 multipart/form-data，字段名 file）
     * @return 相对访问 URL，如 /upload/20260815/&lt;uuid&gt;.jpg（前端 &lt;img&gt; 直接用）
     */
    String storeImage(MultipartFile file);
}
