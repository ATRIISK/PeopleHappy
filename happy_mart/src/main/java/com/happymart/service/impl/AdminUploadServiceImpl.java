package com.happymart.service.impl;                // 包声明 → Service 实现类放在 impl 子包下

import com.happymart.common.exception.BusinessException;            // 业务异常
import com.happymart.common.result.ResultCodeEnum;                  // 错误码枚举
import com.happymart.service.AdminUploadService;                    // 本类实现的接口
import lombok.extern.slf4j.Slf4j;                                   // @Slf4j → 日志
import org.springframework.beans.factory.annotation.Value;          // @Value → 读取配置文件里的值
import org.springframework.stereotype.Service;                      // @Service → 标记 Service 类
import org.springframework.web.multipart.MultipartFile;             // MultipartFile → 上传文件对象

import java.io.IOException;                                         // IOException → 文件读写异常
import java.io.InputStream;                                         // InputStream → 读文件流
import java.nio.file.Files;                                         // Files → 文件操作（创建目录、复制）
import java.nio.file.Path;                                          // Path → 文件路径
import java.nio.file.Paths;                                         // Paths → 路径工具
import java.nio.file.StandardCopyOption;                            // StandardCopyOption → 复制选项
import java.time.LocalDate;                                         // LocalDate → 当前日期（做子目录）
import java.time.format.DateTimeFormatter;                          // DateTimeFormatter → 日期格式化
import java.util.List;                                              // List → 白名单列表
import java.util.Locale;                                            // Locale → 转小写用
import java.util.UUID;                                              // UUID → 生成唯一文件名

/**
 * 管理后台：文件上传服务实现类
 * <p>
 * 商品图片存到后端 upload 目录（app.upload-dir，默认 ./upload），
 * 按「日期子目录 + UUID 文件名」组织，返回相对 URL（/upload/yyyyMMdd/&lt;uuid&gt;.ext）。
 * <p>
 * 安全设计：
 * - 扩展名白名单（jpg/jpeg/png/gif/webp），排除 svg（可内嵌脚本，是 XSS 载体）
 * - 文件名完全由服务端生成（UUID），客户端文件名只用来取扩展名 → 杜绝路径穿越
 * - 大小双重校验：容器层 spring.servlet.multipart.max-file-size 先拦 + 这里 Service 层兜底
 */
@Slf4j                                               // Lombok → 自动生成 log 变量
@Service                                              // 标记为 Service 层，Spring 自动管理
public class AdminUploadServiceImpl implements AdminUploadService {

    /** 单文件最大 5MB（服务层兜底；容器层 spring.servlet.multipart.max-file-size=5MB 会先拦一次） */
    private static final long MAX_SIZE = 5L * 1024 * 1024;

    /** 允许的图片扩展名白名单（客户端文件名不可信，只取扩展名判断） */
    private static final List<String> ALLOWED_EXT = List.of("jpg", "jpeg", "png", "gif", "webp");

    /**
     * 上传根目录（application.yml 的 app.upload-dir，默认 ./upload，相对后端工作目录）
     * - 开发：E:/PeopleHappy/happy_mart/upload（注释里一律用正斜杠，避免反斜杠序列被 Java 当成 Unicode 转义）
     * - Docker 容器（WORKDIR=/app）：/app/upload
     */
    @Value("${app.upload-dir:./upload}")
    private String uploadDir;

    /**
     * 保存一张商品图片
     * <p>
     * 返回的 URL 是相对路径 /upload/yyyyMMdd/&lt;uuid&gt;.ext：
     * - 开发：前端 <img src="/upload/..."> 走 Vite 代理 /upload → 后端 8074
     * - 生产：Nginx location ^~ /upload/ 反代到后端
     * - 后端 WebMvcConfig 把 /upload/** 静态映射到 upload 目录
     */
    @Override
    public String storeImage(MultipartFile file) {

        // ===== 1. 非空校验 =====
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "上传文件不能为空");
        }

        // ===== 2. 大小校验（服务层兜底，容器层已先拦一次） =====
        if (file.getSize() > MAX_SIZE) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "图片大小不能超过 5MB");
        }

        // ===== 3. 扩展名白名单 =====
        // 客户端文件名不可信，只从原始文件名取扩展名做白名单判断；
        // 文件名本身后面用 UUID 生成，绝不用客户端文件名（防路径穿越/覆盖）
        String original = file.getOriginalFilename();
        String ext = (original != null && original.contains("."))
                ? original.substring(original.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT)
                : "";
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "仅支持 jpg/jpeg/png/gif/webp 格式");
        }

        // ===== 4. UUID 重命名 + 按日期子目录存盘 =====
        // 目录结构：upload/yyyyMMdd/UUID.ext（yyyyMMdd = 当天，便于按天归档）
        // 路径完全由服务端生成，任何客户端输入都不进路径 → 杜绝路径穿越
        String dateDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String newName = UUID.randomUUID().toString().replace("-", "") + "." + ext;

        // toAbsolutePath()：把相对路径转成绝对路径（开发 E:/.../upload，容器 /app/upload）
        Path dir = Paths.get(uploadDir, dateDir).toAbsolutePath();
        Path target = dir.resolve(newName);

        try {
            // 目录不存在则创建（mkdirs 会创建多级目录）
            Files.createDirectories(dir);
            // 写盘：用 Files.copy + 输入流（try-with-resources 自动关流），
            // 比 file.transferTo() 跨实现更稳
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("商品图片保存失败: {}", e.getMessage());
            throw new BusinessException(ResultCodeEnum.SYSTEM_ERROR, "图片保存失败，请稍后重试");
        }

        log.info("商品图片上传成功: {}", target);

        // ===== 5. 返回相对访问 URL（前端 <img> 直接用） =====
        return "/upload/" + dateDir + "/" + newName;
    }
}
