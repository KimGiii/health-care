package com.healthcare.domain.diet.recommendation.dto;

import com.healthcare.domain.diet.allergen.AllergenConfidenceLevel;
import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.entity.FoodServingOption;
import com.healthcare.domain.diet.external.importer.ServingOptionDeriver;
import com.healthcare.domain.diet.recommendation.candidate.DietRecommendationCandidate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RecommendedFoodEntry — 추천 식품 표시 항목")
class RecommendedFoodEntryTest {

    @Test
    @DisplayName("낱개 식품은 선택된 제공량의 개수 라벨을 servingLabel에 담는다 (계란 100g → 2개)")
    void countableFoodCarriesCountLabel() {
        DietRecommendationCandidate egg = eggCandidate();

        RecommendedFoodEntry entry = RecommendedFoodEntry.from(egg, 100.0);

        assertThat(entry.servingLabel()).isEqualTo("2개");
        assertThat(entry.servingG()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("매칭되는 제공량 옵션이 없으면 servingLabel은 null이다")
    void noMatchingOptionLeavesLabelNull() {
        DietRecommendationCandidate egg = eggCandidate();

        RecommendedFoodEntry entry = RecommendedFoodEntry.from(egg, 77.0);

        assertThat(entry.servingLabel()).isNull();
    }

    private DietRecommendationCandidate eggCandidate() {
        FoodCatalog egg = FoodCatalog.builder()
                .id(11L)
                .name("계란").nameKo("계란")
                .category(FoodCategory.PROTEIN_SOURCE)
                .caloriesPer100g(155.0)
                .proteinPer100g(13.0)
                .carbsPer100g(1.1)
                .fatPer100g(11.0)
                .build();
        List<FoodServingOption> options =
                new ServingOptionDeriver().derive(11L, FoodCategory.PROTEIN_SOURCE, "100g", "계란");
        return DietRecommendationCandidate.from(egg, AllergenConfidenceLevel.UNKNOWN, options);
    }
}
