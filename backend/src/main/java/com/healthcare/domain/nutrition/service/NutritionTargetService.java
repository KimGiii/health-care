package com.healthcare.domain.nutrition.service;

import com.healthcare.domain.goals.entity.Goal;
import com.healthcare.domain.goals.repository.GoalRepository;
import com.healthcare.domain.nutrition.dto.NutritionTargets;
import com.healthcare.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자별 영양 목표 자동 계산·저장.
 *
 * - {@link #applyToUser(User)}: User 프로필 변경 시 호출 (UserService.updateProfile).
 *   활성 목표가 있으면 그 타입을, 없으면 기본(GENERAL_HEALTH 가정 → TDEE 유지)을 기준으로 계산.
 * - {@link #computeForGoal(User, Goal.GoalType)}: 목표 생성 시 GoalService에서 호출.
 *   계산만 하고 저장은 호출자가 결정.
 *
 * 필수 프로필(성별/생년월일/키/체중/활동 수준) 미충족 시 no-op으로 동작.
 */
@Service
@RequiredArgsConstructor
public class NutritionTargetService {

    private final GoalRepository goalRepository;

    /**
     * 사용자 프로필 + 활성 목표(있다면) → 영양 타겟 계산해 User에 저장.
     * 프로필 정보 부족 시 null 반환 (예외 없음).
     */
    @Transactional
    public NutritionTargets applyToUser(User user) {
        if (!NutritionCalculator.canCalculate(user)) {
            return null;
        }
        Goal.GoalType activeGoalType = goalRepository
            .findActiveGoalByUserId(user.getId())
            .map(Goal::getGoalType)
            .orElse(null);

        NutritionTargets targets = NutritionCalculator.computeFor(user, activeGoalType);
        user.updateTargets(
            targets.calorieTarget(),
            targets.proteinTargetG(),
            targets.carbTargetG(),
            targets.fatTargetG()
        );
        return targets;
    }

    /**
     * 주어진 목표 타입 기준으로 영양 타겟 계산만 수행 (저장 안 함).
     * GoalService가 새 목표 생성 시 Goal/User 둘 다 업데이트하기 위해 호출.
     */
    @Transactional(readOnly = true)
    public NutritionTargets computeForGoal(User user, Goal.GoalType goalType) {
        if (!NutritionCalculator.canCalculate(user)) {
            return null;
        }
        return NutritionCalculator.computeFor(user, goalType);
    }
}
