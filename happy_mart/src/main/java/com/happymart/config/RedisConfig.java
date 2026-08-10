package com.happymart.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
 * 这个类负责两件事（对应开发文档 §8）：
 * 1. RedisTemplate   → 手写 Redis 操作时用（热门榜 ZSet 的存取）
 * 2. CacheManager    → @Cacheable 注解的缓存管理器（商品详情、分类树的缓存）
 *
 * 注解说明：
 * @EnableCaching   → 开启 Spring Cache，让 @Cacheable/@CacheEvict 注解生效
 * @EnableScheduling → 开启定时任务，让 @Scheduled 注解生效（热门榜每小时刷新）
 */
@Configuration
@EnableCaching        // 开启缓存抽象
@EnableScheduling     // 开启定时任务（热门榜定时刷新用）
public class RedisConfig {

    // ==================== RedisTemplate（手写 Redis 操作） ====================

    /**
     * RedisTemplate：最核心的 Redis 操作工具
     * 热门榜的 ZSet 是"手写"存取的（不走 @Cacheable 注解），所以需要这个 Bean
     *
     * 序列化规则（关键）：
     * - key 用 String 序列化 → Redis 里的 key 是人类可读的字符串（product:hot）
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