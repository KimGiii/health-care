package com.healthcare.domain.nutrition.dto;

/**
 * 일일 권장 영양 목표 (BMR/TDEE/매크로 계산 결과).
 */
public record NutritionTargets(
    int calorieTarget,
    int proteinTargetG,
    int carbTargetG,
    int fatTargetG
) {
    /** 이미 섭취한 영양량을 차감한 남은 목표를 반환한다. 음수는 0으로 클램핑. */
    public NutritionTargets minus(ConsumedNutrients consumed) {
        return new NutritionTargets(
                Math.max(0, calorieTarget - consumed.calories()),
                Math.max(0, proteinTargetG - consumed.proteinG()),
                Math.max(0, carbTargetG - consumed.carbsG()),
                Math.max(0, fatTargetG - consumed.fatG())
        );
    }
}
