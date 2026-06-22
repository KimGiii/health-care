package com.healthcare.domain.diet.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class FoodCatalogCurationTest {

    private FoodCatalog minimalFood(RecommendationStatus status, String reason) {
        return FoodCatalog.builder()
                .name("테스트 식품")
                .category(FoodCatalog.FoodCategory.OTHER)
                .caloriesPer100g(100.0)
                .recommendationStatus(status)
                .recommendationReason(reason)
                .isCustom(false)
                .build();
    }

    @Nested
    @DisplayName("curation() — DB 필드에서 값 객체 재구성")
    class CurationRead {

        @Test
        @DisplayName("RECOMMENDABLE 상태는 Recommendable 반환")
        void curation_recommendable_returnsRecommendable() {
            FoodCatalog food = minimalFood(RecommendationStatus.RECOMMENDABLE, null);
            assertThat(food.curation()).isInstanceOf(RecommendationCuration.Recommendable.class);
        }

        @Test
        @DisplayName("RECOMMENDABLE_WITH_CAUTION 상태는 WithCaution 반환 + reason 보존")
        void curation_withCaution_returnsWithCautionAndReason() {
            FoodCatalog food = minimalFood(RecommendationStatus.RECOMMENDABLE_WITH_CAUTION, "고지방·고나트륨");
            var curation = food.curation();
            assertThat(curation).isInstanceOf(RecommendationCuration.WithCaution.class);
            assertThat(((RecommendationCuration.WithCaution) curation).reason()).isEqualTo("고지방·고나트륨");
        }

        @Test
        @DisplayName("SEARCH_ONLY 상태는 SearchOnly 반환")
        void curation_searchOnly_returnsSearchOnly() {
            FoodCatalog food = minimalFood(RecommendationStatus.SEARCH_ONLY, null);
            assertThat(food.curation()).isInstanceOf(RecommendationCuration.SearchOnly.class);
        }
    }

    @Nested
    @DisplayName("updateCuration() — 두 DB 필드 동시 업데이트")
    class CurationWrite {

        @Test
        @DisplayName("WithCaution 으로 업데이트하면 status와 reason 모두 설정")
        void updateCuration_withCaution_setsBothFields() {
            FoodCatalog food = minimalFood(RecommendationStatus.RECOMMENDABLE, null);
            food.updateCuration(new RecommendationCuration.WithCaution("고지방"));
            assertThat(food.getRecommendationStatus()).isEqualTo(RecommendationStatus.RECOMMENDABLE_WITH_CAUTION);
            assertThat(food.getRecommendationReason()).isEqualTo("고지방");
        }

        @Test
        @DisplayName("Recommendable 로 업데이트하면 reason null")
        void updateCuration_recommendable_clearsReason() {
            FoodCatalog food = minimalFood(RecommendationStatus.RECOMMENDABLE_WITH_CAUTION, "고지방");
            food.updateCuration(new RecommendationCuration.Recommendable());
            assertThat(food.getRecommendationStatus()).isEqualTo(RecommendationStatus.RECOMMENDABLE);
            assertThat(food.getRecommendationReason()).isNull();
        }

        @Test
        @DisplayName("SearchOnly 로 업데이트하면 reason null")
        void updateCuration_searchOnly_clearsReason() {
            FoodCatalog food = minimalFood(RecommendationStatus.RECOMMENDABLE_WITH_CAUTION, "고지방");
            food.updateCuration(new RecommendationCuration.SearchOnly());
            assertThat(food.getRecommendationStatus()).isEqualTo(RecommendationStatus.SEARCH_ONLY);
            assertThat(food.getRecommendationReason()).isNull();
        }
    }
}
