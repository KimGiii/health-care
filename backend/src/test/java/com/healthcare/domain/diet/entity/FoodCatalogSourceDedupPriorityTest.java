package com.healthcare.domain.diet.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FoodCatalogSource — dedup 우선순위")
class FoodCatalogSourceDedupPriorityTest {

    @Test
    @DisplayName("우선순위는 SEED > BRAND_OFFICIAL > PROCESSED > NUTRIENT_DB > DISH 순이다")
    void priorityOrdering() {
        assertThat(FoodCatalogSource.SEED.dedupPriority())
                .isGreaterThan(FoodCatalogSource.BRAND_OFFICIAL.dedupPriority());
        assertThat(FoodCatalogSource.BRAND_OFFICIAL.dedupPriority())
                .isGreaterThan(FoodCatalogSource.MFDS_STANDARD_PROCESSED.dedupPriority());
        assertThat(FoodCatalogSource.MFDS_STANDARD_PROCESSED.dedupPriority())
                .isGreaterThan(FoodCatalogSource.MFDS_FOOD_NUTRIENT_DB.dedupPriority());
        assertThat(FoodCatalogSource.MFDS_FOOD_NUTRIENT_DB.dedupPriority())
                .isGreaterThan(FoodCatalogSource.MFDS_STANDARD_DISH.dedupPriority());
    }

    @Test
    @DisplayName("음식·영양DB 코드 공유 시 영양DB가 음식을 흡수하도록 우선순위가 높다")
    void nutrientDbOutranksDish() {
        assertThat(FoodCatalogSource.MFDS_FOOD_NUTRIENT_DB.dedupPriority())
                .isGreaterThan(FoodCatalogSource.MFDS_STANDARD_DISH.dedupPriority());
    }

    @Test
    @DisplayName("USER_CUSTOM은 dedup 비대상(우선순위 0, isDedupEligible=false)이다")
    void userCustomNotEligible() {
        assertThat(FoodCatalogSource.USER_CUSTOM.dedupPriority()).isZero();
        assertThat(FoodCatalogSource.USER_CUSTOM.isDedupEligible()).isFalse();
    }

    @Test
    @DisplayName("USER_CUSTOM 외 모든 출처는 dedup 대상이다")
    void publicSourcesAreEligible() {
        for (FoodCatalogSource source : FoodCatalogSource.values()) {
            if (source == FoodCatalogSource.USER_CUSTOM) {
                continue;
            }
            assertThat(source.isDedupEligible()).as(source.name()).isTrue();
            assertThat(source.dedupPriority()).as(source.name()).isPositive();
        }
    }
}
