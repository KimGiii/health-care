package com.healthcare.domain.diet.policy;

import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.entity.FoodCatalogSource;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * 식품 카탈로그 데이터의 source별 신선도(최대 허용 연령) 정책.
 *
 * <p>source별 만료 창:
 * <ul>
 *   <li>MFDS 계열 (PROCESSED, DISH, NUTRIENT_DB) — 2년</li>
 *   <li>BRAND_OFFICIAL — 1년 (브랜드 메뉴 변경 주기 반영)</li>
 *   <li>SEED, USER_CUSTOM — 만료 없음</li>
 * </ul>
 *
 * <p>만료 창이 있는 source에서 {@code lastVerifiedAt}이 null이면 검증 시점 미기록으로 간주해 구식 처리한다.
 */
@Component
public class DataFreshnessPolicy {

    private static final Duration MFDS_MAX_AGE = Duration.ofDays(730);  // 2년
    private static final Duration BRAND_MAX_AGE = Duration.ofDays(365); // 1년

    private final Clock clock;

    public DataFreshnessPolicy() {
        this(Clock.systemUTC());
    }

    public DataFreshnessPolicy(Clock clock) {
        this.clock = clock;
    }

    /**
     * 식품의 데이터가 현재 시점 기준으로 유효한지 판단한다.
     */
    public boolean isCurrent(FoodCatalog food) {
        Duration maxAge = maxAgeFor(food.getSource());
        if (maxAge == null) {
            return true;
        }
        if (food.getLastVerifiedAt() == null) {
            return false;
        }
        return food.getLastVerifiedAt().plus(maxAge).isAfter(OffsetDateTime.now(clock));
    }

    private Duration maxAgeFor(FoodCatalogSource source) {
        return switch (source) {
            case SEED, USER_CUSTOM -> null;
            case MFDS_STANDARD_PROCESSED, MFDS_STANDARD_DISH, MFDS_FOOD_NUTRIENT_DB -> MFDS_MAX_AGE;
            case BRAND_OFFICIAL -> BRAND_MAX_AGE;
        };
    }
}
