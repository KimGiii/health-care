package com.healthcare.domain.nutrition.dto;

/**
 * 일일 권장 영양 목표 (BMR/TDEE/매크로 계산 결과).
 */
public record NutritionTargets(
    int calorieTarget,
    int proteinTargetG,
    int carbTargetG,
    int fatTargetG
) {}
