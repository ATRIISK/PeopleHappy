package com.happymart.controller;

import com.happymart.common.result.Result;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查 Controller
 * <p>
 * 用途：给自动部署脚本（deploy/update.sh）判断后端是否真的起来了。
 * 部署脚本换完 jar 会重启容器，Spring 初始化要几十秒，
 * 这期间进程在但依赖没就绪，只看端口通不通会误判成功。
 * <p>
 * 探测两项关键依赖：
 * - MySQL：业务数据全在这里，连不上等于不可用
 * - Redis：分类树、商品详情、热门榜都走缓存
 * <p>
 * 不探 RabbitMQ：连接建立失败要等满 connectionTimeout（默认 60s），
 * 会把健康检查拖成一个慢接口，而 MQ 故障不影响应用启动。
 * <p>
 * 响应约定：
 * - 全部正常 → HTTP 200，body 里 status=UP
 * - 任一依赖异常 → HTTP 503，body 里 status=DOWN 且列出故障组件
 * 部署脚本只认 HTTP 状态码，body 的明细用于排查。
 * <p>
 * 权限：不加 @Auth，JwtAuthInterceptor 只拦截带 @Auth 的方法，
 * 因此本接口天然公开。公网侧可在宝塔站点配置里加
 * location = /api/health { allow 127.0.0.1; deny all; } 进一步收紧。
 */
@Slf4j
@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {

    private final DataSource dataSource;                 // 数据源 → 探 MySQL 连通性

    private final StringRedisTemplate redisTemplate;    // Redis 模板 → 探 Redis 连通性

    /**
     * 健康检查
     * GET /api/health
     */
    @GetMapping
    public Result<Map<String, Object>> health(HttpServletResponse response) {
        Map<String, Object> components = new LinkedHashMap<>();
        boolean up = true;

        // ---------- MySQL ----------
        long start = System.currentTimeMillis();
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("SELECT 1");
            components.put("mysql", "UP");
        } catch (Exception e) {
            log.warn("健康检查：MySQL 不可用: {}", e.getMessage());
            components.put("mysql", "DOWN");
            components.put("mysqlError", e.getClass().getSimpleName());
            up = false;
        }
        components.put("mysqlCostMs", System.currentTimeMillis() - start);

        // ---------- Redis ----------
        start = System.currentTimeMillis();
        RedisConnection conn = null;
        try {
            conn = redisTemplate.getConnectionFactory().getConnection();
            String pong = conn.ping();
            components.put("redis", "PONG".equalsIgnoreCase(pong) ? "UP" : "DOWN");
            if (!"PONG".equalsIgnoreCase(pong)) {
                up = false;
            }
        } catch (Exception e) {
            log.warn("健康检查：Redis 不可用: {}", e.getMessage());
            components.put("redis", "DOWN");
            components.put("redisError", e.getClass().getSimpleName());
            up = false;
        } finally {
            if (conn != null) {
                conn.close();
            }
        }
        components.put("redisCostMs", System.currentTimeMillis() - start);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", up ? "UP" : "DOWN");
        payload.put("components", components);

        // 状态码表达健康状况，body 结构与全站统一 Result 保持一致
        if (!up) {
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        }
        return Result.success(payload);
    }
}
