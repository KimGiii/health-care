package com.healthcare.domain.goals.service;

import com.healthcare.common.exception.BusinessRuleViolationException;
import com.healthcare.common.exception.ResourceNotFoundException;
import com.healthcare.common.exception.UnauthorizedException;
import com.healthcare.domain.goals.dto.*;
import com.healthcare.domain.goals.entity.Goal;
import com.healthcare.domain.goals.entity.Goal.GoalStatus;
import com.healthcare.domain.goals.repository.GoalCheckpointRepository;
import com.healthcare.domain.goals.repository.GoalRepository;
import com.healthcare.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GoalService {

    private final GoalRepository goalRepository;
    private final GoalCheckpointRepository goalCheckpointRepository;
    private final UserRepository userRepository;

    // ─────────────────────────── 목표 생성 ───────────────────────────

    @Transactional
    public GoalResponse createGoal(Long userId, CreateGoalRequest request) {
        userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (request.getTargetDate().isBefore(LocalDate.now())) {
            throw new BusinessRuleViolationException("목표 날짜는 오늘 이후여야 합니다.");
        }

        // 기존 ACTIVE 목표 → ABANDONED
        goalRepository.findActiveGoalByUserId(userId).ifPresent(active -> {
            active.abandon();
            goalRepository.save(active);
        });

        Goal goal = Goal.builder()
                .userId(userId)
                .goalType(request.getGoalType())
                .targetValue(request.getTargetValue())
                .targetUnit(request.getTargetUnit())
                .targetDate(request.getTargetDate())
                .startValue(request.getStartValue())
                .startDate(LocalDate.now())
                .status(GoalStatus.ACTIVE)
                .weeklyRateTarget(request.getWeeklyRateTarget())
                .build();

        Goal saved = goalRepository.save(goal);
        return GoalResponse.from(saved);
    }

    // ─────────────────────────── 목표 단건 조회 ───────────────────────────

    public GoalResponse getGoalById(Long userId, Long goalId) {
        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new ResourceNotFoundException("Goal", goalId));
        if (!goal.isOwnedBy(userId)) {
            throw new UnauthorizedException("다른 사용자의 목표에 접근할 수 없습니다.");
        }
        return GoalResponse.from(goal);
    }

    // ─────────────────────────── 목표 목록 조회 ───────────────────────────

    public GoalListResponse listGoals(Long userId, GoalStatus status, Pageable pageable) {
        Page<Goal> page = goalRepository.findByUserIdAndStatus(userId, status, pageable);
        return GoalListResponse.from(page);
    }

    // ─────────────────────────── 목표 수정 ───────────────────────────

    @Transactional
    public GoalResponse updateGoal(Long userId, Long goalId, UpdateGoalRequest request) {
        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new ResourceNotFoundException("Goal", goalId));
        if (!goal.isOwnedBy(userId)) {
            throw new UnauthorizedException("다른 사용자의 목표를 수정할 수 없습니다.");
        }
        if (!goal.isActive()) {
            throw new BusinessRuleViolationException("COMPLETED 또는 ABANDONED 상태의 목표는 수정할 수 없습니다.");
        }
        goal.updateTarget(request.getTargetValue(), request.getTargetDate(), request.getWeeklyRateTarget());
        Goal saved = goalRepository.save(goal);
        return GoalResponse.from(saved);
    }

    // ─────────────────────────── 목표 포기 ───────────────────────────

    @Transactional
    public void abandonGoal(Long userId, Long goalId) {
        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new ResourceNotFoundException("Goal", goalId));
        if (!goal.isOwnedBy(userId)) {
            throw new UnauthorizedException("다른 사용자의 목표를 포기할 수 없습니다.");
        }
        if (!goal.isActive()) {
            throw new BusinessRuleViolationException("이미 완료되었거나 포기된 목표입니다.");
        }
        goal.abandon();
        goalRepository.save(goal);
    }
}
