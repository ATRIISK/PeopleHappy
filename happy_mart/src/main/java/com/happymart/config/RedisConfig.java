package com.happymart.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Duration;

/**
 * Redis 配置类
 *
 * 这个类负责三件事（对应开发文档 §8）：
 * 1. CacheManager      → @Cacheable 注解的缓存管理器（商品详情、分类树的缓存）
 * 2. RedisTemplate     → 通用 Redis 操作预留（当前热门榜改用 StringRedisTemplate，本 Bean 暂无调用方）
 * 3. CacheErrorHandler → Redis 故障时的缓存降级处理（打日志 + 不抛异常 → 方法体照常查库）
 *
 * 注解说明：
 * @EnableCaching   → 开启 Spring Cache，让 @Cacheable/@CacheEvict 注解生效
 * @EnableScheduling → 开启定时任务，让 @Scheduled 注解生效（热门榜每小时刷新）
 */
@Slf4j
@Configuration
@EnableCaching        // 开启缓存抽象
@EnableScheduling     // 开启定时任务（热门榜定时刷新用）
public class RedisConfig {

    // ==================== RedisTemplate（通用 Redis 操作，预留） ====================

    /**
     * RedisTemplate：通用 Redis 操作工具
     *
     * ⚠️ 注意（code-review 修正）：当前热门榜 ZSet 实际用的是 Spring Boot 自动配置的
     *    StringRedisTemplate（ProductServiceImpl 注入），不是这个 Bean。
     *    这个 Bean 保留作为"通用 Redis 操作"的预留：
     *    - 后续购物车 Hash（cart:{userId}）、订单号 Redis 自增等手写 Redis 场景可复用
     *    - 当前没有调用方（grep 不到注入点），属于"配置预留"
     *
     * 序列化规则（关键）：
     * - key 用 String 序列化 → Redis 里的 key 是人类可读的字符串
     * - value 用 JSON 序列化 → Redis 里的 value 是 JSON 字符串，ARDM 里可直接查看
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // 统一的 JSON 序列化器（抽成了下面的公共方法，保证两处格式一致）
        Jackson2JsonRedisSerializer<Object> jsonSerializer = buildJsonSerializer();
        // key 的序列化器
        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        // 分别设置 key / value / Hash 的 field / Hash 的 value 用哪种序列化
        template.setKeySerializer(stringSerializer);        // 普通 key：String
        template.setHashKeySerializer(stringSerializer);    // Hash 的 field：String
        template.setValueSerializer(jsonSerializer);        // 普通 value：JSON
        template.setHashValueSerializer(jsonSerializer);    // Hash 的 value：JSON

        template.afterPropertiesSet();
        return template;
    }

    // ==================== CacheManager（@Cacheable 注解的缓存管理器） ====================

    /**
     * Spring Cache 的缓存管理器
     *
     * @Cacheable(cacheNames="xxx") 注解默认用这里定义的 CacheManager 来读写 Redis。
     * 为什么必须自定义而不是用 Spring Boot 默认的？
     *   ProductVO / CategoryVO 没有实现 Serializable（Java 原生序列化接口），
     *   而 Spring Boot 默认的 Redis 缓存管理器用 JDK 序列化 → 会报错。
     *   所以这里必须改成 JSON 序列化，Redis 里存可读的 JSON 字符串。
     *
     * TTL 配置（对齐开发文档 §8）：
     *   - 默认 30 分钟 → 商品详情 product:detail
     *   - category:tree 单独 1 小时
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {

        // 1. 定义默认缓存配置（TTL 30 分钟）
        //    缓存 key 的最终格式是：cacheName::key
        //    比如 @Cacheable(cacheNames="product:detail", key="#id")，id=1
        //    → Redis 里的 key 是 product:detail::1（文档 §8 写的 product:detail:{id} 是概念写法）
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                // key 用 String 序列化
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                // value 用 JSON 序列化（关键！否则 ProductVO 无法序列化）
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(buildJsonSerializer()))
                // 默认过期时间 30 分钟
                .entryTtl(Duration.ofMinutes(30));

        // 2. 组装 CacheManager
        return RedisCacheManager.builder(factory)
                // 所有缓存默认按上面的配置（30 分钟）
                .cacheDefaults(defaultConfig)
                // 分类树是低频变动数据，单独覆盖成 1 小时
                // withCacheConfiguration(缓存名, 覆盖配置)
                .withCacheConfiguration("category:tree",
                        defaultConfig.entryTtl(Duration.ofHours(1)))
                .build();
    }

    // ==================== 缓存故障降级（code-review 修复） ====================

    /**
     * 缓存错误处理器：Redis 故障时"打日志 + 不抛异常"，让方法体照常查数据库
     * <p>
     * 为什么需要？（对应开发文档 §8 "Redis 故障自动降级"）
     * 默认情况下，@Cacheable 的方法如果连不上 Redis，会抛 RedisConnectionFailureException，
     * 导致商品详情 / 分类树接口直接报"数据库异常"（缓存把整个接口拖垮了）。
     * 有了这个处理器后：
     * - 缓存读取失败 → 当作"没命中缓存"处理 → 方法体照常执行查库 → 返回真实数据
     * - 缓存写入失败 → 打日志忽略 → 不影响方法正常返回
     * 也就是 Redis 挂了，商城功能照常用（只是暂时没有缓存加速），符合"降级"语义。
     * <p>
     * 注意：热门榜是手写的 try-catch 降级（不经过 CacheErrorHandler），
     * 这里处理的是 @Cacheable 注解的商品详情 / 分类树两个场景。
     * <p>
     * 实现方式：定义一个 CachingConfigurer，只覆盖 errorHandler() 一个方法，
     * 其他方法（cacheManager / keyGenerator 等）不覆盖 → Spring 用默认配置。
     */
    @Bean
    public CachingConfigurer cachingConfigurer() {
        return new CachingConfigurer() {
            @Override
            public CacheErrorHandler errorHandler() {
                return new CacheErrorHandler() {
                    @Override
                    public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                        // 读缓存失败 → 打日志 → 不抛异常
                        // CacheInterceptor 收到后把它当作"未命中缓存"，继续执行方法体查数据库
                        log.warn("Redis 缓存读取失败，降级查数据库: cache={}, key={}, error={}",
                                cache.getName(), key, exception.getMessage());
                    }

                    @Override
                    public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                        // 写缓存失败 → 打日志忽略（数据已经查出来了，只是没缓存，不影响返回）
                        log.warn("Redis 缓存写入失败（忽略）: cache={}, key={}, error={}",
                                cache.getName(), key, exception.getMessage());
                    }

                    @Override
                    public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                        // 清缓存失败 → 打日志忽略（最坏情况是缓存多留一会儿，TTL 过期后自然消失）
                        log.warn("Redis 缓存清除失败（忽略）: cache={}, key={}, error={}",
                                cache.getName(), key, exception.getMessage());
                    }

                    @Override
                    public void handleCacheClearError(RuntimeException exception, Cache cache) {
                        // 清空缓存失败 → 打日志忽略
                        log.warn("Redis 缓存清空失败（忽略）: cache={}, error={}",
                                cache.getName(), exception.getMessage());
                    }
                };
            }
        };
    }

    // ==================== 公共序列化器（两处共用，保证格式统一） ====================

    /**
     * 构建 JSON 序列化器
     * RedisTemplate 和 CacheManager 都用它，确保：
     * 1. 注解缓存写入的值，RedisTemplate 也能正确读取（或反之）
     * 2. Redis 里存的都是可读的 JSON 字符串
     */
    private Jackson2JsonRedisSerializer<Object> buildJsonSerializer() {
        ObjectMapper objectMapper = new ObjectMapper();
        // 所有字段（不管 public/private）都参与序列化
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        // JSON 里内嵌 @class 类型信息，反序列化时能还原成原来的 Java 类型（比如 ProductVO）
        objectMapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance, ObjectMapper.DefaultTyping.NON_FINAL);
        // 注册 Java 时间模块 → 支持 LocalDateTime 等时间类型的序列化
        objectMapper.registerModule(new JavaTimeModule());
        return new Jackson2JsonRedisSerializer<>(objectMapper, Object.class);
    }
}