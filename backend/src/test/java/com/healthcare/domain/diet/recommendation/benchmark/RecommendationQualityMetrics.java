package com.healthcare.domain.diet.recommendation.benchmark;

import com.healthcare.domain.diet.recommendation.dto.RecommendedFoodEntry;
import com.healthcare.domain.diet.recommendation.dto.RecommendedMeal;

import java.util.List;

/**
 * 추천 결과 하나의 객관적 품질 지표. benchmark 회귀 게이트가 baseline 대비 비교하는 측정값이다.
 *
 * <p>순수 계산만 한다(부수효과 없음). production 소비자가 생기기 전까지 test 소스셋에 둔다.
 */
public record RecommendationQualityMetrics(
        double totalCalories,
        double totalProteinG,
        double totalCarbsG,
        double totalFatG,
        List<Double> perMealProteinG,
        int distinctFoodCount) {

    public RecommendationQualityMetrics {
        perMealProteinG = List.copyOf(perMealProteinG);
    }

    public static RecommendationQualityMetrics of(List<RecommendedMeal> meals) {
        double calories = meals.stream().mapToDouble(RecommendedMeal::totalCalories).sum();
        double protein = meals.stream().mapToDouble(RecommendedMeal::totalProteinG).sum();
        double carbs = meals.stream().mapToDouble(RecommendedMeal::totalCarbsG).sum();
        double fat = meals.stream().mapToDouble(RecommendedMeal::totalFatG).sum();
        List<Double> perMealProtein = meals.stream().map(RecommendedMeal::totalProteinG).toList();
        int distinctFoods = (int) meals.stream()
                .flatMap(m -> m.items().stream())
                .map(RecommendedFoodEntry::foodCatalogId)
                .distinct()
                .count();
        return new RecommendationQualityMetrics(
                calories, protein, carbs, fat, perMealProtein, distinctFoods);
    }

    /**
     * 가장 단백질이 적은 끼니의 단백질(g). <b>원시 신호일 뿐</b>이다.
     *
     * <p>§2(끼니 단백질 분배) 회귀의 완전한 판정은 이 절대값으로 못 한다: (1) 근단백합성 역치
     * 0.4 g/kg는 체중 함수라 g/kg가 필요하고, (2) 편중도는 최소값이 아니라 분산/변동계수로 봐야 하며,
     * (3) 간식까지 포함하면 정상 5–6끼 구성을 오탐한다(메인 끼니 한정 필요). 체중·끼니 역할을 주입하는
     * 지표는 항목 2 착수 시 그 코드와 함께 추가한다.
     */
    public double minMealProteinG() {
        return perMealProteinG.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
    }

    private static final double KCAL_PER_G_PROTEIN = 4.0;
    private static final double KCAL_PER_G_CARB = 4.0;
    private static final double KCAL_PER_G_FAT = 9.0;

    /**
     * 매크로에서 환산한 에너지(Atwater 4/4/9). 분포 %의 분모로 의도적으로 이 값을 쓴다(보고 칼로리 아님):
     * 분자합과 분모가 같은 계수라 세 매크로 %의 합이 100%로 정합하고, 음식 DB의 라벨 칼로리 오차·반올림이
     * 분포 지표에 노이즈로 섞이지 않아 재현성(§0.3)에 부합한다. 보고 칼로리(totalCalories)와는 미세하게
     * 다를 수 있다. 주의: carbsG가 총 탄수(식이섬유 포함)면 탄수 %가 다소 과대 — 회귀 상대비교엔 무해하나
     * 정책 §1 밴드 구현 시 분모 정의를 일치시킬 것.
     */
    public double macroEnergyKcal() {
        return totalProteinG * KCAL_PER_G_PROTEIN
                + totalCarbsG * KCAL_PER_G_CARB
                + totalFatG * KCAL_PER_G_FAT;
    }

    public double proteinCaloriePercent() {
        return percentOfEnergy(totalProteinG * KCAL_PER_G_PROTEIN);
    }

    public double carbCaloriePercent() {
        return percentOfEnergy(totalCarbsG * KCAL_PER_G_CARB);
    }

    public double fatCaloriePercent() {
        return percentOfEnergy(totalFatG * KCAL_PER_G_FAT);
    }

    private double percentOfEnergy(double macroKcal) {
        double energy = macroEnergyKcal();
        return energy == 0.0 ? 0.0 : macroKcal / energy * 100.0;
    }
}
