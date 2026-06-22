package com.healthcare.domain.diet.recommendation.engine;

import com.healthcare.domain.diet.allergen.AllergenConfidenceLevel;
import com.healthcare.domain.diet.entity.DietLog.MealType;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.entity.FoodServingOption.ServingType;
import com.healthcare.domain.diet.entity.ServingOptionSnapshot;
import com.healthcare.domain.diet.recommendation.candidate.DietRecommendationCandidate;
import com.healthcare.domain.diet.recommendation.dto.RecommendedFoodEntry;
import com.healthcare.domain.diet.recommendation.dto.RecommendedMeal;
import com.healthcare.domain.nutrition.policy.NutrientBudget;
import com.healthcare.domain.nutrition.policy.RemainingNutritionBudget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ConstraintRecommendationEngine — 제약 최적화 추천 엔진")
class ConstraintRecommendationEngineTest {

    private final ConstraintRecommendationEngine engine = new ConstraintRecommendationEngine();
    private static final LocalDate DATE = LocalDate.of(2026, 6, 19);
    private static final List<MealType> THREE_MEALS =
            List.of(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER);

    @Test
    @DisplayName("feasible 해는 끼니 간 동일 foodId를 절대 반복하지 않는다 (달걀·돼지고기 반복 회귀)")
    void success_neverRepeatsFoodAcrossMeals() {
        // 단백질 후보가 2종뿐인 빈약한 풀 — greedy였다면 매 끼니 같은 고기가 반복됐다.
        List<DietRecommendationCandidate> pool = List.of(
                food(1L, "달걀", FoodCategory.PROTEIN_SOURCE, 150, 13, 1, 10),
                food(2L, "돼지안심", FoodCategory.PROTEIN_SOURCE, 140, 21, 0, 6),
                food(3L, "현미밥", FoodCategory.GRAIN, 350, 7, 75, 3),
                food(4L, "고구마", FoodCategory.GRAIN, 130, 2, 30, 0.2),
                food(5L, "브로콜리", FoodCategory.VEGETABLE, 34, 3, 7, 0.4),
                food(6L, "사과", FoodCategory.FRUIT, 52, 0.3, 14, 0.2)
        );
        RecommendationResult result = engine.recommend(DATE, wideBudget(), THREE_MEALS, pool,
                UserRepetitionPolicy.noRestrictions(), OnlinePreferencePolicy.noSignals(), 1, "v1");

        assertThat(result.succeeded()).isTrue();
        List<Long> ids = foodIds(((RecommendationResult.Success) result).primary().meals());
        assertThat(ids).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("성공 해의 영양 합계는 budget hard constraint를 충족한다")
    void success_totalsWithinBudget() {
        RemainingNutritionBudget budget = new RemainingNutritionBudget(
                new NutrientBudget(1500, 2500, true),
                new NutrientBudget(60, Double.POSITIVE_INFINITY, true),
                new NutrientBudget(0, Double.POSITIVE_INFINITY, true),
                new NutrientBudget(0, Double.POSITIVE_INFINITY, true));

        RecommendationResult result = engine.recommend(DATE, budget, THREE_MEALS, balancedPool(),
                UserRepetitionPolicy.noRestrictions(), OnlinePreferencePolicy.noSignals(), 1, "v1");

        assertThat(result.succeeded()).isTrue();
        RecommendationResult.Success success = (RecommendationResult.Success) result;
        double cal = total(success.primary().meals(), RecommendedMeal::totalCalories);
        double protein = total(success.primary().meals(), RecommendedMeal::totalProteinG);
        assertThat(cal).isBetween(1500.0, 2500.0);
        assertThat(protein).isGreaterThanOrEqualTo(60.0);
    }

    @Test
    @DisplayName("동일 입력은 동일 결과를 낸다 (결정성)")
    void deterministic() {
        RecommendationResult first = engine.recommend(DATE, wideBudget(), THREE_MEALS, balancedPool(),
                UserRepetitionPolicy.noRestrictions(), OnlinePreferencePolicy.noSignals(), 3, "v1");
        RecommendationResult second = engine.recommend(DATE, wideBudget(), THREE_MEALS, balancedPool(),
                UserRepetitionPolicy.noRestrictions(), OnlinePreferencePolicy.noSignals(), 3, "v1");

        assertThat(foodIds(((RecommendationResult.Success) first).primary().meals()))
                .isEqualTo(foodIds(((RecommendationResult.Success) second).primary().meals()));
    }

    @Test
    @DisplayName("선택된 각 끼니는 최소 1개 식품을 가진다")
    void everyMealHasAtLeastOneItem() {
        RecommendationResult result = engine.recommend(DATE, wideBudget(), THREE_MEALS, balancedPool(),
                UserRepetitionPolicy.noRestrictions(), OnlinePreferencePolicy.noSignals(), 1, "v1");

        RecommendationResult.Success success = (RecommendationResult.Success) result;
        assertThat(success.primary().meals()).hasSize(3);
        assertThat(success.primary().meals()).allSatisfy(meal ->
                assertThat(meal.items()).isNotEmpty());
    }

    @Test
    @DisplayName("budget이 infeasible이면 CALORIE_PROTEIN_CONFLICT를 반환한다")
    void infeasibleBudget_returnsConflict() {
        RemainingNutritionBudget infeasible = new RemainingNutritionBudget(
                new NutrientBudget(2000, 1500, false), // 이미 초과 등으로 infeasible
                new NutrientBudget(60, Double.POSITIVE_INFINITY, true),
                new NutrientBudget(0, Double.POSITIVE_INFINITY, true),
                new NutrientBudget(0, Double.POSITIVE_INFINITY, true));

        RecommendationResult result = engine.recommend(DATE, infeasible, THREE_MEALS, balancedPool(),
                UserRepetitionPolicy.noRestrictions(), OnlinePreferencePolicy.noSignals(), 1, "v1");

        assertThat(result).isInstanceOf(RecommendationResult.Failure.class);
        assertThat(((RecommendationResult.Failure) result).reason())
                .isEqualTo(RecommendationFailureReason.CALORIE_PROTEIN_CONFLICT);
    }

    @Test
    @DisplayName("도달 불가능한 단백질 하한은 REMAINING_MEALS_INFEASIBLE로 실패한다")
    void unreachableProtein_returnsInfeasible() {
        // 저단백 후보만으로는 절대 닿을 수 없는 단백질 하한
        List<DietRecommendationCandidate> lowProtein = List.of(
                food(1L, "사과", FoodCategory.FRUIT, 52, 0.3, 14, 0.2),
                food(2L, "오이", FoodCategory.VEGETABLE, 15, 0.6, 3, 0.1),
                food(3L, "양상추", FoodCategory.VEGETABLE, 14, 0.9, 3, 0.1),
                food(4L, "당근", FoodCategory.VEGETABLE, 41, 0.9, 10, 0.2)
        );
        RemainingNutritionBudget budget = new RemainingNutritionBudget(
                new NutrientBudget(300, 600, true),
                new NutrientBudget(200, Double.POSITIVE_INFINITY, true), // 비현실적 하한
                new NutrientBudget(0, Double.POSITIVE_INFINITY, true),
                new NutrientBudget(0, Double.POSITIVE_INFINITY, true));

        RecommendationResult result = engine.recommend(DATE, budget, THREE_MEALS, lowProtein,
                UserRepetitionPolicy.noRestrictions(), OnlinePreferencePolicy.noSignals(), 1, "v1");

        assertThat(result).isInstanceOf(RecommendationResult.Failure.class);
        assertThat(((RecommendationResult.Failure) result).reason())
                .isIn(RecommendationFailureReason.REMAINING_MEALS_INFEASIBLE,
                        RecommendationFailureReason.SEARCH_LIMIT_REACHED);
    }

    @Test
    @DisplayName("macro 결측 후보만 있으면 REMAINING_MEALS_INFEASIBLE로 실패한다")
    void allIncompleteMacro_returnsInfeasible() {
        List<DietRecommendationCandidate> incomplete = List.of(
                incompleteFood(1L, "정보없음A", FoodCategory.GRAIN),
                incompleteFood(2L, "정보없음B", FoodCategory.PROTEIN_SOURCE));

        RecommendationResult result = engine.recommend(DATE, wideBudget(), THREE_MEALS, incomplete,
                UserRepetitionPolicy.noRestrictions(), OnlinePreferencePolicy.noSignals(), 1, "v1");

        assertThat(result).isInstanceOf(RecommendationResult.Failure.class);
        assertThat(((RecommendationResult.Failure) result).reason())
                .isEqualTo(RecommendationFailureReason.REMAINING_MEALS_INFEASIBLE);
    }

    @Test
    @DisplayName("성공 해는 정책 버전과 채운 영양 근거를 담은 rationale을 갖는다")
    void success_carriesRationale() {
        RecommendationResult result = engine.recommend(DATE, wideBudget(), THREE_MEALS, balancedPool(),
                UserRepetitionPolicy.noRestrictions(), OnlinePreferencePolicy.noSignals(), 1, "2026-06-18.v1");

        RecommendationRationale rationale = ((RecommendationResult.Success) result).primary().rationale();
        assertThat(rationale.policyVersion()).isEqualTo("2026-06-18.v1");
        assertThat(rationale.filledCalories()).isGreaterThan(0);
        assertThat(rationale.note()).isNotBlank();
    }

    @Test
    @DisplayName("도달 가능한 4대 매크로 밴드(GENERAL_HEALTH식)에서는 날짜와 무관하게 항상 성공한다 (날짜 의존 간헐 실패 회귀)")
    void feasibleMacroBands_succeedRegardlessOfDate() {
        // GENERAL_HEALTH식 제약: 칼로리·단백·탄수·지방 모두 밴드. 칼로리만 맞추는
        // 오프셋 회전 선택이었다면 일부 날짜 시드에서 끼니가 밴드를 벗어나(칼로리 언더슛/
        // 탄수·지방 상한 초과) 전부 탈락 → REMAINING_MEALS_INFEASIBLE이 났다.
        List<DietRecommendationCandidate> pool = realisticSkewedPool();
        // 실현가능한 GENERAL_HEALTH식 밴드(칼로리 ±7%, 매크로 ±15%). 대부분 날 충족 가능하지만
        // 저밀도·가공식품이 풀을 압도해, 칼로리만 맞추는 선택은 일부 날짜 시드에서 밴드를 벗어났다.
        RemainingNutritionBudget budget = generalHealthBands(2200, 110, 270, 70);

        List<LocalDate> failedDates = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            LocalDate date = LocalDate.of(2026, 1, 1).plusDays(i);
            RecommendationResult result = engine.recommend(date, budget, THREE_MEALS, pool,
                    UserRepetitionPolicy.noRestrictions(), OnlinePreferencePolicy.noSignals(), 1, "v1");
            if (!result.succeeded()) {
                failedDates.add(date);
            }
        }




        assertThat(failedDates)
                .as("밴드 도달 가능한 풀인데 일부 날짜 시드에서 실패함")
                .isEmpty();
    }

    /** GENERAL_HEALTH 정책의 밴드 모양: 칼로리 ±5%, 매크로 ±10%. */
    private RemainingNutritionBudget generalHealthBands(double cal, double protein, double carbs, double fat) {
        return new RemainingNutritionBudget(
                new NutrientBudget(cal * 0.95, cal * 1.05, true),
                new NutrientBudget(protein * 0.90, protein * 1.10, true),
                new NutrientBudget(carbs * 0.90, carbs * 1.10, true),
                new NutrientBudget(fat * 0.90, fat * 1.10, true));
    }

    // ─── 헬퍼 ───

    /**
     * 실제 임포트 풀처럼 가공식품·저밀도 식품이 다수이고 균형 식품이 소수인 편중 풀.
     * 모든 식품이 검증된 1회 제공량 옵션을 가지므로 제공량은 현실적 범위에서만 선택된다.
     */
    private List<DietRecommendationCandidate> realisticSkewedPool() {
        List<DietRecommendationCandidate> pool = new ArrayList<>();
        long id = 1;
        // 단백질원 (1회 제공량 120g 기준)
        pool.add(foodS(id++, "닭가슴살", FoodCategory.PROTEIN_SOURCE, 165, 31, 0, 3.6, 120));
        pool.add(foodS(id++, "소고기", FoodCategory.PROTEIN_SOURCE, 250, 26, 0, 15, 120));
        pool.add(foodS(id++, "두부", FoodCategory.PROTEIN_SOURCE, 76, 8, 2, 4.8, 150));
        pool.add(foodS(id++, "연어", FoodCategory.PROTEIN_SOURCE, 208, 20, 0, 13, 120));
        pool.add(foodS(id++, "달걀", FoodCategory.PROTEIN_SOURCE, 150, 13, 1, 10, 100));
        pool.add(foodS(id++, "새우", FoodCategory.PROTEIN_SOURCE, 99, 24, 0, 0.3, 120));
        pool.add(foodS(id++, "참치", FoodCategory.PROTEIN_SOURCE, 130, 28, 0, 1, 100));
        // 곡류 (1회 제공량 150g 기준)
        pool.add(foodS(id++, "현미밥", FoodCategory.GRAIN, 130, 2.7, 28, 0.3, 150));
        pool.add(foodS(id++, "식빵", FoodCategory.GRAIN, 265, 9, 49, 3.2, 60));
        pool.add(foodS(id++, "귀리", FoodCategory.GRAIN, 389, 17, 66, 7, 50));
        pool.add(foodS(id++, "고구마", FoodCategory.GRAIN, 86, 1.6, 20, 0.1, 150));
        pool.add(foodS(id++, "파스타", FoodCategory.GRAIN, 131, 5, 25, 1.1, 200));
        pool.add(foodS(id++, "감자", FoodCategory.GRAIN, 77, 2, 17, 0.1, 150));
        // 채소 (1회 제공량 75g 기준)
        pool.add(foodS(id++, "브로콜리", FoodCategory.VEGETABLE, 34, 2.8, 7, 0.4, 75));
        pool.add(foodS(id++, "시금치", FoodCategory.VEGETABLE, 23, 2.9, 3.6, 0.4, 75));
        pool.add(foodS(id++, "당근", FoodCategory.VEGETABLE, 41, 0.9, 10, 0.2, 75));
        pool.add(foodS(id++, "양상추", FoodCategory.VEGETABLE, 15, 1.4, 2.9, 0.2, 75));
        pool.add(foodS(id++, "파프리카", FoodCategory.VEGETABLE, 31, 1, 6, 0.3, 75));
        pool.add(foodS(id++, "토마토", FoodCategory.VEGETABLE, 18, 0.9, 3.9, 0.2, 100));
        // 과일 (1회 제공량 150g 기준)
        pool.add(foodS(id++, "사과", FoodCategory.FRUIT, 52, 0.3, 14, 0.2, 150));
        pool.add(foodS(id++, "바나나", FoodCategory.FRUIT, 89, 1.1, 23, 0.3, 120));
        pool.add(foodS(id++, "배", FoodCategory.FRUIT, 57, 0.4, 15, 0.1, 150));
        pool.add(foodS(id++, "오렌지", FoodCategory.FRUIT, 47, 0.9, 12, 0.1, 150));
        pool.add(foodS(id++, "포도", FoodCategory.FRUIT, 69, 0.7, 18, 0.2, 100));
        // 유제품
        pool.add(foodS(id++, "우유", FoodCategory.DAIRY, 60, 3.2, 4.8, 3.3, 200));
        pool.add(foodS(id++, "요거트", FoodCategory.DAIRY, 59, 3.5, 4.7, 3.3, 150));
        pool.add(foodS(id++, "치즈", FoodCategory.DAIRY, 402, 25, 1.3, 33, 30));
        pool.add(foodS(id++, "코티지치즈", FoodCategory.DAIRY, 98, 11, 3.4, 4.3, 100));
        // 가공식품 (풀을 압도하는 고지방·고탄수, 1회 제공량 30g)
        for (int k = 0; k < 18; k++) {
            pool.add(foodS(id++, "가공식품" + k, FoodCategory.PROCESSED,
                    480 + k * 5, 5 + k % 4, 50 + k % 10, 28 + k % 8, 30));
        }
        return pool;
    }

    /** 미사용 헬퍼(과거 회귀 시나리오 참고용). */
    private List<DietRecommendationCandidate> proteinPoorPool() {
        List<DietRecommendationCandidate> pool = new ArrayList<>();
        // 단백질원 (소수)
        pool.add(food(1L, "닭가슴살", FoodCategory.PROTEIN_SOURCE, 165, 31, 0, 3.6));
        pool.add(food(2L, "두부", FoodCategory.PROTEIN_SOURCE, 76, 8, 2, 4.8));
        pool.add(food(3L, "연어", FoodCategory.PROTEIN_SOURCE, 208, 20, 0, 13));
        // 저단백 곡류·채소·과일·가공식품 (다수, 풀을 압도)
        pool.add(food(10L, "현미밥", FoodCategory.GRAIN, 350, 7, 75, 3));
        pool.add(food(11L, "고구마", FoodCategory.GRAIN, 130, 2, 30, 0.2));
        pool.add(food(12L, "식빵", FoodCategory.GRAIN, 265, 9, 49, 3.2));
        pool.add(food(13L, "브로콜리", FoodCategory.VEGETABLE, 34, 3, 7, 0.4));
        pool.add(food(14L, "시금치", FoodCategory.VEGETABLE, 23, 2.9, 3.6, 0.4));
        pool.add(food(15L, "양상추", FoodCategory.VEGETABLE, 14, 0.9, 3, 0.1));
        pool.add(food(16L, "사과", FoodCategory.FRUIT, 52, 0.3, 14, 0.2));
        pool.add(food(17L, "바나나", FoodCategory.FRUIT, 89, 1.1, 23, 0.3));
        pool.add(food(18L, "배", FoodCategory.FRUIT, 57, 0.4, 15, 0.1));
        pool.add(food(19L, "감자칩", FoodCategory.PROCESSED, 536, 7, 53, 35));
        pool.add(food(20L, "초콜릿", FoodCategory.PROCESSED, 546, 5, 61, 31));
        pool.add(food(21L, "크래커", FoodCategory.PROCESSED, 502, 8, 64, 23));
        pool.add(food(22L, "우유", FoodCategory.DAIRY, 65, 3.2, 4.7, 3.6));
        pool.add(food(23L, "요거트", FoodCategory.DAIRY, 59, 3.5, 4.7, 3.3));
        return pool;
    }

    private RemainingNutritionBudget wideBudget() {
        return new RemainingNutritionBudget(
                new NutrientBudget(1400, 2600, true),
                new NutrientBudget(50, Double.POSITIVE_INFINITY, true),
                new NutrientBudget(0, Double.POSITIVE_INFINITY, true),
                new NutrientBudget(0, Double.POSITIVE_INFINITY, true));
    }

    private List<DietRecommendationCandidate> balancedPool() {
        List<DietRecommendationCandidate> pool = new ArrayList<>();
        pool.add(food(1L, "닭가슴살", FoodCategory.PROTEIN_SOURCE, 165, 31, 0, 3.6));
        pool.add(food(2L, "현미밥", FoodCategory.GRAIN, 350, 7, 75, 3));
        pool.add(food(3L, "브로콜리", FoodCategory.VEGETABLE, 34, 3, 7, 0.4));
        pool.add(food(4L, "사과", FoodCategory.FRUIT, 52, 0.3, 14, 0.2));
        pool.add(food(5L, "두부", FoodCategory.PROTEIN_SOURCE, 76, 8, 2, 4.8));
        pool.add(food(6L, "고구마", FoodCategory.GRAIN, 130, 2, 30, 0.2));
        pool.add(food(7L, "시금치", FoodCategory.VEGETABLE, 23, 2.9, 3.6, 0.4));
        pool.add(food(8L, "바나나", FoodCategory.FRUIT, 89, 1.1, 23, 0.3));
        pool.add(food(9L, "연어", FoodCategory.PROTEIN_SOURCE, 208, 20, 0, 13));
        return pool;
    }

    private double total(List<RecommendedMeal> meals, java.util.function.ToDoubleFunction<RecommendedMeal> f) {
        return meals.stream().mapToDouble(f).sum();
    }

    private List<Long> foodIds(List<RecommendedMeal> meals) {
        return meals.stream()
                .flatMap(m -> m.items().stream())
                .map(RecommendedFoodEntry::foodCatalogId)
                .toList();
    }

    private DietRecommendationCandidate food(
            long id, String name, FoodCategory category,
            double cal, double protein, double carbs, double fat) {
        return new DietRecommendationCandidate(
                id, name, name, category, cal, protein, carbs, fat, 0L, id,
                AllergenConfidenceLevel.DIRECT_VERIFIED, null, true, List.of(), false);
    }

    /** 검증된 1회 제공량 옵션(0.5/1/1.5/2회)을 가진 식품. base는 1회 제공량(g). */
    private DietRecommendationCandidate foodS(
            long id, String name, FoodCategory category,
            double cal, double protein, double carbs, double fat, double baseServingG) {
        List<ServingOptionSnapshot> options = new ArrayList<>();
        double[] multipliers = {0.5, 1.0, 1.5, 2.0};
        for (int i = 0; i < multipliers.length; i++) {
            double g = Math.round(baseServingG * multipliers[i]);
            options.add(new ServingOptionSnapshot(
                    multipliers[i] + "회", multipliers[i] + "회", g, i,
                    ServingType.OFFICIAL_SERVING, true));
        }
        return new DietRecommendationCandidate(
                id, name, name, category, cal, protein, carbs, fat, 0L, id,
                AllergenConfidenceLevel.DIRECT_VERIFIED, null, true, options, true);
    }

    private DietRecommendationCandidate incompleteFood(long id, String name, FoodCategory category) {
        return new DietRecommendationCandidate(
                id, name, name, category, 200, 0, 0, 0, 0L, id,
                AllergenConfidenceLevel.DIRECT_VERIFIED, null, false, List.of(), false);
    }
}
