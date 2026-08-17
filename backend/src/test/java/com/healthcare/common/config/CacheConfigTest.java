package com.healthcare.common.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.healthcare.domain.user.dto.UserProfileResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CacheConfigTest {

    private CacheConfig cacheConfig;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        cacheConfig = new CacheConfig();
        meterRegistry = new SimpleMeterRegistry();
        ReflectionTestUtils.setField(cacheConfig, "userProfileTtlMinutes", 60);
        ReflectionTestUtils.setField(cacheConfig, "userProfileMaxEntries", 10_000L);
        ReflectionTestUtils.setField(cacheConfig, "foodSearchTtlDays", 30);
        ReflectionTestUtils.setField(cacheConfig, "foodSearchMaxEntries", 2_000L);
    }

    @Test
    @DisplayName("외부 식품 검색 캐시는 전용 크기 상한과 함께 등록된다")
    void cacheManager_registersExternalFoodSearchCacheWithOwnBound() {
        CaffeineCacheManager cacheManager = (CaffeineCacheManager) cacheConfig.cacheManager(meterRegistry);

        CaffeineCache cache = (CaffeineCache) cacheManager.getCache(CacheConfig.EXTERNAL_FOOD_SEARCH_CACHE);

        assertThat(cache).isNotNull();
        assertThat(maximumSizeOf(cache)).isEqualTo(2_000L);
    }

    @Test
    @DisplayName("사용자 프로필 캐시는 기본 설정으로 생성되며 별도 크기 상한을 갖는다")
    void cacheManager_createsUserProfileCacheFromDefaultSpec() {
        CaffeineCacheManager cacheManager = (CaffeineCacheManager) cacheConfig.cacheManager(meterRegistry);

        CaffeineCache cache = (CaffeineCache) cacheManager.getCache(CacheConfig.USER_PROFILE_CACHE);

        assertThat(cache).isNotNull();
        assertThat(maximumSizeOf(cache)).isEqualTo(10_000L);
    }

    @Test
    @DisplayName("두 캐시 모두 Micrometer에 cache.gets 지표로 등록된다")
    void cacheManager_bindsCacheMetricsForBothCaches() {
        cacheConfig.cacheManager(meterRegistry);

        assertThat(meterRegistry.find("cache.gets").tag("cache", CacheConfig.USER_PROFILE_CACHE).meter()).isNotNull();
        assertThat(meterRegistry.find("cache.gets").tag("cache", CacheConfig.EXTERNAL_FOOD_SEARCH_CACHE).meter()).isNotNull();
    }

    @Test
    @DisplayName("Java Time 필드를 가진 사용자 프로필을 캐시에 넣고 그대로 꺼낼 수 있다")
    void cacheManager_roundTripsProfileWithJavaTimeFields() {
        CaffeineCacheManager cacheManager = (CaffeineCacheManager) cacheConfig.cacheManager(meterRegistry);
        UserProfileResponse response = UserProfileResponse.builder()
            .id(10L)
            .email("test@example.com")
            .displayName("테스터")
            .dateOfBirth(LocalDate.of(1995, 5, 10))
            .createdAt(OffsetDateTime.parse("2026-04-22T22:37:50+09:00"))
            .onboardingCompleted(true)
            .build();

        org.springframework.cache.Cache cache = cacheManager.getCache(CacheConfig.USER_PROFILE_CACHE);
        cache.put(10L, response);

        UserProfileResponse restored = cache.get(10L, UserProfileResponse.class);

        assertThat(restored).isNotNull();
        assertThat(restored.getId()).isEqualTo(response.getId());
        assertThat(restored.getEmail()).isEqualTo(response.getEmail());
        assertThat(restored.getDisplayName()).isEqualTo(response.getDisplayName());
        assertThat(restored.getDateOfBirth()).isEqualTo(response.getDateOfBirth());
        assertThat(restored.getCreatedAt().toInstant()).isEqualTo(response.getCreatedAt().toInstant());
        assertThat(restored.isOnboardingCompleted()).isEqualTo(response.isOnboardingCompleted());
    }

    @Test
    @DisplayName("null 값은 캐시에 저장하지 않는다 — Redis 시절 disableCachingNullValues와 동일한 계약")
    void cacheManager_rejectsNullValues() {
        CaffeineCacheManager cacheManager = (CaffeineCacheManager) cacheConfig.cacheManager(meterRegistry);
        org.springframework.cache.Cache cache = cacheManager.getCache(CacheConfig.USER_PROFILE_CACHE);

        assertThatThrownBy(() -> cache.put(1L, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private long maximumSizeOf(CaffeineCache cache) {
        Cache<Object, Object> nativeCache = cache.getNativeCache();
        return nativeCache.policy().eviction().orElseThrow().getMaximum();
    }
}
