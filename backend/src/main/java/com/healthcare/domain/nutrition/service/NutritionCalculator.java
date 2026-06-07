package com.healthcare.domain.nutrition.service;

import com.healthcare.domain.goals.entity.Goal;
import com.healthcare.domain.nutrition.dto.NutritionTargets;
import com.healthcare.domain.user.entity.User;

import java.time.LocalDate;
import java.time.Period;

/**
 * 영양 권장량 계산 — 순수 정적 메서드.
 *
 * 공식:
 *   - BMR: Mifflin-St Jeor (1990) — ADA·대한비만학회 표준
 *   - TDEE: BMR × 활동계수 (Harris-Benedict 활동계수 표)
 *   - 매크로: 단백질 g/kg → 지방 % → 나머지 탄수화물 (ACSM·ISSN 가이드라인)
 *
 * 사이드이펙트 없음. 단위 테스트로 검증.
 */
public final class NutritionCalculator {

    private NutritionCalculator() {}

    // ─────────── 안전 하한 ───────────
    private static final int MIN_KCAL_FEMALE = 1200;
    private static final int MIN_KCAL_MALE = 1500;

    // ─────────── 칼로리 환산 (Atwater factor) ───────────
    private static final double KCAL_PER_GRAM_PROTEIN = 4.0;
    private static final double KCAL_PER_GRAM_CARB = 4.0;
    private static final double KCAL_PER_GRAM_FAT = 9.0;

    /**
     * BMR — Mifflin-St Jeor (1990).
     *   남: 10W + 6.25H − 5A + 5
     *   여: 10W + 6.25H − 5A − 161
     *   기타: 남녀 평균 (보수적 추정)
     */
    public static double calculateBmr(User.Sex sex, double weightKg, double heightCm, int age) {
        double base = 10.0 * weightKg + 6.25 * heightCm - 5.0 * age;
        return switch (sex) {
            case MALE -> base + 5;
            case FEMALE -> base - 161;
            case OTHER -> base - 78;   // (5 + -161) / 2
        };
    }

    /** TDEE = BMR × 활동계수. */
    public static double calculateTdee(double bmr, User.ActivityLevel activityLevel) {
        double factor = switch (activityLevel) {
            case SEDENTARY        -> 1.2;
            case LIGHTLY_ACTIVE   -> 1.375;
            case MODERATELY_ACTIVE -> 1.55;
            case VERY_ACTIVE      -> 1.725;
            case EXTRA_ACTIVE     -> 1.9;
        };
        return bmr * factor;
    }

    /** 목표별 칼로리 조정 (활성 목표 없으면 TDEE 그대로 — 유지). */
    public static double adjustKcalForGoal(double tdee, Goal.GoalType goalType) {
        if (goalType == null) return tdee;
        return switch (goalType) {
            case WEIGHT_LOSS       -> tdee - 500;   // 주 0.5kg 감량 (7,700 kcal/kg)
            case MUSCLE_GAIN       -> tdee + 300;   // 린벌크
            case BODY_RECOMPOSITION -> tdee - 200;  // 가벼운 결손
            case ENDURANCE         -> tdee + 200;   // 글리코겐 회복 보강
            case GENERAL_HEALTH    -> tdee;         // 유지
        };
    }

    /** 안전 하한 적용 — 여성 1,200 / 남성·기타 1,500 kcal. */
    public static int applySafetyFloor(double kcal, User.Sex sex) {
        int floor = sex == User.Sex.FEMALE ? MIN_KCAL_FEMALE : MIN_KCAL_MALE;
        return Math.max((int) Math.round(kcal), floor);
    }

    /**
     * 매크로 분배 — 단백질 g/kg 우선 → 지방 % → 나머지 탄수화물.
     * goalType이 null이면 GENERAL_HEALTH로 처리.
     */
    public static NutritionTargets distributeMacros(
            int targetKcal, double weightKg, Goal.GoalType goalType) {
        Goal.GoalType type = goalType != null ? goalType : Goal.GoalType.GENERAL_HEALTH;

        double proteinPerKg = switch (type) {
            case WEIGHT_LOSS                            -> 2.0;
            case MUSCLE_GAIN, BODY_RECOMPOSITION        -> 1.8;
            case ENDURANCE                              -> 1.4;
            case GENERAL_HEALTH                         -> 1.2;
        };
        double fatRatio = switch (type) {
            case WEIGHT_LOSS, MUSCLE_GAIN,
                 BODY_RECOMPOSITION, ENDURANCE         -> 0.25;
            case GENERAL_HEALTH                         -> 0.30;
        };

        int proteinG = (int) Math.round(weightKg * proteinPerKg);
        double proteinKcal = proteinG * KCAL_PER_GRAM_PROTEIN;
        double fatKcal = targetKcal * fatRatio;
        int fatG = (int) Math.round(fatKcal / KCAL_PER_GRAM_FAT);

        // 남은 칼로리를 탄수화물에. 매크로 합이 음수가 되지 않도록 가드.
        double remainingKcal = Math.max(0, targetKcal - proteinKcal - fatKcal);
        int carbG = (int) Math.round(remainingKcal / KCAL_PER_GRAM_CARB);

        return new NutritionTargets(targetKcal, proteinG, carbG, fatG);
    }

    /**
     * User + (있다면) 활성 목표 → 영양 타겟 일괄 계산.
     * 필수 프로필 누락 시 IllegalStateException — 호출 전 {@link #canCalculate(User)}로 확인할 것.
     */
    public static NutritionTargets computeFor(User user, Goal.GoalType activeGoalType) {
        if (!canCalculate(user)) {
            throw new IllegalStateException(
                "영양 목표 계산에 필요한 프로필 정보가 부족합니다. " +
                "성별/생년월일/키/체중/활동 수준 모두 입력되어야 합니다."
            );
        }
        int age = ageOf(user.getDateOfBirth());
        double bmr = calculateBmr(user.getSex(), user.getWeightKg(), user.getHeightCm(), age);
        double tdee = calculateTdee(bmr, user.getActivityLevel());
        double adjusted = adjustKcalForGoal(tdee, activeGoalType);
        int safeKcal = applySafetyFloor(adjusted, user.getSex());
        return distributeMacros(safeKcal, user.getWeightKg(), activeGoalType);
    }

    /** 만 나이 (당해 생일이 지났는지 반영). */
    public static int ageOf(LocalDate dateOfBirth) {
        return Period.between(dateOfBirth, LocalDate.now()).getYears();
    }

    /** 필수 프로필 정보가 전부 있는지 확인. */
    public static boolean canCalculate(User user) {
        return user.getSex() != null
            && user.getDateOfBirth() != null
            && user.getHeightCm() != null
            && user.getWeightKg() != null
            && user.getActivityLevel() != null;
    }
}
