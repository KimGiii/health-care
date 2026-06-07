package com.healthcare.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

@Configuration
@EnableCaching
public class RedisConfig implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(RedisConfig.class);

    @Value("${app.cache.food-search-ttl-days:30}")
    private int foodSearchTtlDays;

    /**
     * 캐시(Redis) 작업 실패를 서비스 장애로 전파하지 않는다.
     *
     * Redis 타임아웃·연결 실패 시 예외를 삼키고 로그만 남긴 뒤:
     *   - GET 실패 → 캐시 미스로 취급되어 원본 메서드(DB 조회)가 실행됨
     *   - PUT/EVICT/CLEAR 실패 → 무시 (TTL로 결국 정리, Redis 복구 후 정상화)
     *
     * 캐시는 성능 최적화일 뿐 필수 의존성이 아니므로, Redis가 느리거나 죽어도
     * 사용자 요청은 DB 경로로 계속 처리되어야 한다.
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException ex, Cache cache, Object key) {
                log.warn("[Cache] GET 실패 — 캐시 미스로 처리 후 DB 진행: cache={}, key={}, error={}",
                    cache.getName(), key, ex.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException ex, Cache cache, Object key, Object value) {
                log.warn("[Cache] PUT 실패 — 무시: cache={}, key={}, error={}",
                    cache.getName(), key, ex.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException ex, Cache cache, Object key) {
                log.warn("[Cache] EVICT 실패 — 무시(TTL로 정리): cache={}, key={}, error={}",
                    cache.getName(), key, ex.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException ex, Cache cache) {
                log.warn("[Cache] CLEAR 실패 — 무시: cache={}, error={}",
                    cache.getName(), ex.getMessage());
            }
        };
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(
        RedisConnectionFactory connectionFactory,
        ObjectMapper objectMapper
    ) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        GenericJackson2JsonRedisSerializer jsonSerializer = jsonSerializer(objectMapper);

        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(jsonSerializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(jsonSerializer);
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public RedisCacheManager cacheManager(
        RedisConnectionFactory connectionFactory,
        ObjectMapper objectMapper
    ) {
        GenericJackson2JsonRedisSerializer jsonSerializer = jsonSerializer(objectMapper);
        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofHours(1))
            .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(stringSerializer))
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer))
            .disableCachingNullValues();

        // 외부 식품 검색 캐시: 30일 TTL (식품 데이터는 자주 변하지 않음)
        RedisCacheConfiguration foodSearchConfig = defaultConfig
            .entryTtl(Duration.ofDays(foodSearchTtlDays));

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(Map.of(
                "external-food-search",  foodSearchConfig,
                "external-food-barcode", foodSearchConfig
            ))
            .build();
    }

    private GenericJackson2JsonRedisSerializer jsonSerializer(ObjectMapper objectMapper) {
        return GenericJackson2JsonRedisSerializer.builder()
            .objectMapper(objectMapper.copy())
            .defaultTyping(true)
            .build();
    }
}
