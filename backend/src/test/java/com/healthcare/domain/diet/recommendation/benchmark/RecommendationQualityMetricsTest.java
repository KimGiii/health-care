package com.healthcare.domain.diet.recommendation.benchmark;

import com.healthcare.domain.diet.allergen.AllergenConfidenceLevel;
import com.healthcare.domain.diet.entity.DietLog.MealType;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.recommendation.dto.RecommendedFoodEntry;
import com.healthcare.domain.diet.recommendation.dto.RecommendedMeal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

/**
 * 추천 결과의 객관적 품질 지표 계산기 테스트.
 *
 * <p>후속 보강(매크로 밴드·끼니 분배)이 benchmark baseline 대비 회귀했는지 측정하는 공용 도구.
 */
@DisplayName("추천 품질 지표 계산기")
class RecommendationQualityMetricsTest {

    @Test
    @DisplayName("매크로 합계는 끼니 합계를 더한 값이다")
    void totalMacros_sumAcrossMeals() {
        RecommendationQualityMetrics metrics = RecommendationQualityMetrics.of(List.of(
                meal(MealType.BREAKFAST, 500, 30, 60, 10),
                meal(MealType.LUNCH, 700, 40, 80, 20)));

        assertThat(metrics.totalCalories()).isEqualTo(1200);
        assertThat(metrics.totalProteinG()).isEqualTo(70);
        assertThat(metrics.totalCarbsG()).isEqualTo(140);
        assertThat(metrics.totalFatG()).isEqualTo(30);
    }

    @Test
    @DisplayName("매크로 칼로리 분포 %는 Atwater(4/4/9) 기준으로 계산된다")
    void macroCaloriePercent_atwater() {
        // 단백질 90g(360kcal) + 탄수 90g(360kcal) + 지방 80g(720kcal) = 1440kcal
        RecommendationQualityMetrics metrics = RecommendationQualityMetrics.of(List.of(
                meal(MealType.LUNCH, 1440, 90, 90, 80)));

        assertThat(metrics.proteinCaloriePercent()).isCloseTo(25.0, offset(0.01));
        assertThat(metrics.carbCaloriePercent()).isCloseTo(25.0, offset(0.01));
        assertThat(metrics.fatCaloriePercent()).isCloseTo(50.0, offset(0.01));
    }

    @Test
    @DisplayName("끼니별 단백질 목록과 최소 끼니 단백질을 노출한다")
    void perMealProtein_andMinimum() {
        RecommendationQualityMetrics metrics = RecommendationQualityMetrics.of(List.of(
                meal(MealType.BREAKFAST, 500, 30, 60, 10),
                meal(MealType.LUNCH, 700, 45, 80, 20),
                meal(MealType.SNACK, 200, 11, 20, 5))); // 작은 끼니 — 단백질 낮음

        assertThat(metrics.perMealProteinG()).containsExactly(30.0, 45.0, 11.0);
        assertThat(metrics.minMealProteinG()).isEqualTo(11.0);
    }

    @Test
    @DisplayName("다양성: 끼니 전체에서 서로 다른 음식 수를 센다")
    void diversity_distinctFoodCount() {
        RecommendedMeal breakfast = mealWith(MealType.BREAKFAST,
                entry(1L, 100, 10, 5, 2), entry(2L, 100, 10, 5, 2));
        RecommendedMeal lunch = mealWith(MealType.LUNCH,
                entry(2L, 100, 10, 5, 2), entry(3L, 100, 10, 5, 2)); // food 2 끼니 간 중복

        RecommendationQualityMetrics metrics = RecommendationQualityMetrics.of(List.of(breakfast, lunch));

        assertThat(metrics.distinctFoodCount()).isEqualTo(3); // 1, 2, 3
    }

    @Test
    @DisplayName("매크로 에너지가 0이면 분포 %는 NaN이 아니라 0이다")
    void zeroEnergy_percentIsZeroNotNaN() {
        RecommendationQualityMetrics metrics = RecommendationQualityMetrics.of(List.of());

        assertThat(metrics.fatCaloriePercent()).isEqualTo(0.0);
        assertThat(metrics.proteinCaloriePercent()).isEqualTo(0.0);
        assertThat(metrics.carbCaloriePercent()).isEqualTo(0.0);
        assertThat(metrics.minMealProteinG()).isEqualTo(0.0);
    }

    // ─── 테스트 헬퍼 ───

    private RecommendedMeal meal(MealType type, double cal, double protein, double carbs, double fat) {
        return new RecommendedMeal(type, cal, cal, protein, carbs, fat, List.of());
    }

    private RecommendedMeal mealWith(MealType type, RecommendedFoodEntry... items) {
        return new RecommendedMeal(type, 0, 0, 0, 0, 0, List.of(items));
    }

    private RecommendedFoodEntry entry(long foodId, double cal, double protein, double carbs, double fat) {
        return new RecommendedFoodEntry(foodId, "food" + foodId, "음식" + foodId,
                FoodCategory.PROTEIN_SOURCE, 100, cal, protein, carbs, fat,
                AllergenConfidenceLevel.DIRECT_VERIFIED, null);
    }
}
