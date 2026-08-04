package com.healthcare.common.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 인프로세스 캐시 설정 (Caffeine).
 *
 * <p>기존에는 ElastiCache Redis를 캐시 저장소로 사용했으나, 애플리케이션이 단일 인스턴스로
 * 운영되고 캐시에 담기는 값이 모두 재계산 가능한 파생 데이터뿐이어서 전용 Redis 클러스터를
 * 유지할 이유가 없었다. 세션·리프레시 토큰은 Redis가 아니라 JPA({@code RefreshToken})에
 * 저장되므로 캐시 저장소 교체가 인증 상태에 영향을 주지 않는다. (#111)
 *
 * <p>Redis 시절에는 네트워크 타임아웃·연결 실패를 삼키기 위한 {@code CacheErrorHandler}가
 * 필요했다. 인프로세스 캐시에는 그런 실패 모드가 없으므로 핸들러를 두지 않는다. 캐시 계층에서
 * 예외가 발생한다면 그것은 네트워크 문제가 아니라 코드 결함이므로 삼키지 않고 드러내야 한다.
 *
 * <p>인프로세스 캐시는 힙을 점유하므로 모든 캐시에 최대 항목 수 상한을 둔다. 애플리케이션이
 * 재시작하면 캐시는 비워지고 원본 경로(DB·외부 API)로 다시 채워진다.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** 사용자 프로필 캐시 이름. {@code UserService}, {@code GoalService}, {@code BodyMeasurementService}에서 사용. */
    static final String USER_PROFILE_CACHE = "userProfile";

    /** 공공데이터 포털 식품 검색 결과 캐시 이름. {@code ExternalFoodSearchService}에서 사용. */
    static final String EXTERNAL_FOOD_SEARCH_CACHE = "external-food-search";

    @Value("${app.cache.user-profile-ttl-minutes:60}")
    private int userProfileTtlMinutes;

    @Value("${app.cache.user-profile-max-entries:10000}")
    private long userProfileMaxEntries;

    @Value("${app.cache.food-search-ttl-days:30}")
    private int foodSearchTtlDays;

    @Value("${app.cache.food-search-max-entries:2000}")
    private long foodSearchMaxEntries;

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();

        // null 캐싱 금지 — Redis 설정의 disableCachingNullValues()와 동일한 계약을 유지한다.
        cacheManager.setAllowNullValues(false);

        // 명시적으로 등록되지 않은 캐시의 기본값. 사용자 프로필 캐시가 여기에 해당한다.
        cacheManager.setCaffeine(Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(userProfileTtlMinutes))
            .maximumSize(userProfileMaxEntries));

        // 외부 식품 검색 캐시: 공공데이터 포털 호출을 줄이기 위해 TTL이 길다(기본 30일).
        // 재시작 시 유실되지만 캐시 미스는 외부 API 재조회로 복구된다.
        cacheManager.registerCustomCache(EXTERNAL_FOOD_SEARCH_CACHE,
            Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofDays(foodSearchTtlDays))
                .maximumSize(foodSearchMaxEntries)
                .build());

        return cacheManager;
    }
}
