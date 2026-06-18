package com.healthcare.domain.diet.policy;

import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.entity.FoodCatalogSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DataFreshnessPolicy")
class DataFreshnessPolicyTest {

    private static final Instant NOW = Instant.parse("2026-06-19T00:00:00Z");
    private final DataFreshnessPolicy policy = new DataFreshnessPolicy(Clock.fixed(NOW, ZoneOffset.UTC));

    @Nested
    @DisplayName("만료 없는 source — 항상 유효")
    class NeverExpiring {

        @Test
        @DisplayName("SEED: lastVerifiedAt이 null이어도 유효하다")
        void seed_nullVerifiedAt_isCurrent() {
            assertThat(policy.isCurrent(food(FoodCatalogSource.SEED, null))).isTrue();
        }

        @Test
        @DisplayName("SEED: 오래된 lastVerifiedAt이어도 유효하다")
        void seed_oldVerifiedAt_isCurrent() {
            OffsetDateTime longAgo = OffsetDateTime.ofInstant(NOW.minusSeconds(86400L * 3650), ZoneOffset.UTC); // 10년 전
            assertThat(policy.isCurrent(food(FoodCatalogSource.SEED, longAgo))).isTrue();
        }

        @Test
        @DisplayName("USER_CUSTOM: lastVerifiedAt 무관하게 유효하다")
        void userCustom_nullVerifiedAt_isCurrent() {
            assertThat(policy.isCurrent(food(FoodCatalogSource.USER_CUSTOM, null))).isTrue();
        }
    }

    @Nested
    @DisplayName("MFDS 계열 — 2년 만료")
    class MfdsExpiry {

        @Test
        @DisplayName("lastVerifiedAt이 null이면 구식이다")
        void nullVerifiedAt_isStale() {
            assertThat(policy.isCurrent(food(FoodCatalogSource.MFDS_FOOD_NUTRIENT_DB, null))).isFalse();
        }

        @Test
        @DisplayName("1년 전 검증은 유효하다")
        void oneYearAgo_isCurrent() {
            OffsetDateTime oneYearAgo = OffsetDateTime.ofInstant(NOW.minusSeconds(86400L * 365), ZoneOffset.UTC);
            assertThat(policy.isCurrent(food(FoodCatalogSource.MFDS_STANDARD_PROCESSED, oneYearAgo))).isTrue();
        }

        @Test
        @DisplayName("3년 전 검증은 구식이다")
        void threeYearsAgo_isStale() {
            OffsetDateTime threeYearsAgo = OffsetDateTime.ofInstant(NOW.minusSeconds(86400L * 1095), ZoneOffset.UTC);
            assertThat(policy.isCurrent(food(FoodCatalogSource.MFDS_STANDARD_DISH, threeYearsAgo))).isFalse();
        }

        @Test
        @DisplayName("만료 경계 직전은 유효하다 — MFDS_FOOD_NUTRIENT_DB")
        void justBeforeExpiry_isCurrent() {
            // 730일 전 + 1초 검증 → 만료까지 1초 남음
            OffsetDateTime almostExpired = OffsetDateTime.ofInstant(NOW.minusSeconds(86400L * 730 - 1), ZoneOffset.UTC);
            assertThat(policy.isCurrent(food(FoodCatalogSource.MFDS_FOOD_NUTRIENT_DB, almostExpired))).isTrue();
        }

        @Test
        @DisplayName("만료 경계 이후는 구식이다")
        void justAfterExpiry_isStale() {
            // 730일 전 - 1초 검증 → 1초 초과
            OffsetDateTime justExpired = OffsetDateTime.ofInstant(NOW.minusSeconds(86400L * 730 + 1), ZoneOffset.UTC);
            assertThat(policy.isCurrent(food(FoodCatalogSource.MFDS_STANDARD_PROCESSED, justExpired))).isFalse();
        }
    }

    @Nested
    @DisplayName("BRAND_OFFICIAL — 1년 만료")
    class BrandExpiry {

        @Test
        @DisplayName("lastVerifiedAt이 null이면 구식이다")
        void nullVerifiedAt_isStale() {
            assertThat(policy.isCurrent(food(FoodCatalogSource.BRAND_OFFICIAL, null))).isFalse();
        }

        @Test
        @DisplayName("6개월 전 검증은 유효하다")
        void sixMonthsAgo_isCurrent() {
            OffsetDateTime sixMonthsAgo = OffsetDateTime.ofInstant(NOW.minusSeconds(86400L * 180), ZoneOffset.UTC);
            assertThat(policy.isCurrent(food(FoodCatalogSource.BRAND_OFFICIAL, sixMonthsAgo))).isTrue();
        }

        @Test
        @DisplayName("2년 전 검증은 구식이다")
        void twoYearsAgo_isStale() {
            OffsetDateTime twoYearsAgo = OffsetDateTime.ofInstant(NOW.minusSeconds(86400L * 730), ZoneOffset.UTC);
            assertThat(policy.isCurrent(food(FoodCatalogSource.BRAND_OFFICIAL, twoYearsAgo))).isFalse();
        }
    }

    // ─── 헬퍼 ───

    private FoodCatalog food(FoodCatalogSource source, OffsetDateTime lastVerifiedAt) {
        return FoodCatalog.builder()
                .id(1L)
                .name("테스트식품")
                .category(FoodCategory.OTHER)
                .caloriesPer100g(100.0)
                .isCustom(false)
                .source(source)
                .lastVerifiedAt(lastVerifiedAt)
                .build();
    }
}
