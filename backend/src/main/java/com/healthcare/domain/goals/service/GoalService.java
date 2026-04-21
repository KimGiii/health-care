package com.healthcare.domain.goals.service;

import com.healthcare.common.exception.BusinessRuleViolationException;
import com.healthcare.common.exception.ResourceNotFoundException;
import com.healthcare.common.exception.UnauthorizedException;
import com.healthcare.domain.bodymeasurement.entity.BodyMeasurement;
import com.healthcare.domain.bodymeasurement.repository.BodyMeasurementRepository;
import com.healthcare.domain.goals.dto.*;
import com.healthcare.domain.goals.entity.Goal;
import com.healthcare.domain.goals.entity.Goal.GoalStatus;
import com.healthcare.domain.goals.entity.GoalCheckpoint;
import com.healthcare.domain.goals.repository.GoalCheckpointRepository;
import com.healthcare.domain.goals.repository.GoalRepository;
import com.healthcare.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GoalService {

    private final GoalRepository goalRepository;
    private final GoalCheckpointRepository goalCheckpointRepository;
    private final UserRepository userRepository;
    private final BodyMeasurementRepository bodyMeasurementRepository;

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

    // ─────────────────────────── 목표 진행률 조회 ───────────────────────────

    @Transactional
    public GoalProgressResponse getGoalProgress(Long userId, Long goalId) {
        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new ResourceNotFoundException("Goal", goalId));
        if (!goal.isOwnedBy(userId)) {
            throw new UnauthorizedException("다른 사용자의 목표에 접근할 수 없습니다.");
        }

        LocalDate today = LocalDate.now();
        LocalDate startDate = goal.getStartDate();
        LocalDate targetDate = goal.getTargetDate();

        long daysPassed = Math.max(0, ChronoUnit.DAYS.between(startDate, today));
        long totalDays = Math.max(1, ChronoUnit.DAYS.between(startDate, targetDate));
        long daysRemaining = ChronoUnit.DAYS.between(today, targetDate);

        BigDecimal currentValue = resolveCurrentValue(userId, goal);
        BigDecimal startValue = goal.getStartValue();
        BigDecimal targetValue = goal.getTargetValue();

        ProgressMetrics metrics = calculateMetrics(
                startValue, targetValue, currentValue, daysPassed, totalDays, startDate);

        if (currentValue != null && startValue != null && targetValue != null) {
            upsertCheckpoint(goal, today, currentValue, metrics, startValue, targetValue, daysPassed, totalDays);
        }

        List<GoalCheckpointResponse> checkpoints = goalCheckpointRepository
                .findByGoalIdOrderByCheckpointDate(goalId)
                .stream()
                .map(GoalCheckpointResponse::from)
                .toList();

        return GoalProgressResponse.builder()
                .goalId(goal.getId())
                .goalType(goal.getGoalType())
                .targetValue(targetValue)
                .targetUnit(goal.getTargetUnit())
                .targetDate(targetDate)
                .startDate(startDate)
                .startValue(startValue)
                .currentValue(currentValue)
                .percentComplete(metrics.percentComplete())
                .daysRemaining(daysRemaining)
                .projectedCompletionDate(metrics.projectedCompletionDate())
                .isOnTrack(metrics.isOnTrack())
                .trackingStatus(metrics.trackingStatus())
                .trackingColor(metrics.trackingColor())
                .checkpoints(checkpoints)
                .build();
    }

    private BigDecimal resolveCurrentValue(Long userId, Goal goal) {
        return bodyMeasurementRepository.findFirstByUserIdOrderByMeasuredAtDesc(userId)
                .map(m -> extractValueByGoalType(goal.getGoalType(), m))
                .orElse(goal.getStartValue());
    }

    private BigDecimal extractValueByGoalType(Goal.GoalType goalType, BodyMeasurement m) {
        Double raw = switch (goalType) {
            case WEIGHT_LOSS, GENERAL_HEALTH -> m.getWeightKg();
            case MUSCLE_GAIN -> m.getMuscleMassKg();
            case BODY_RECOMPOSITION -> m.getBodyFatPct();
            case ENDURANCE -> null;
        };
        return raw != null ? BigDecimal.valueOf(raw) : null;
    }

    private record ProgressMetrics(
            double percentComplete,
            boolean isOnTrack,
            String trackingStatus,
            String trackingColor,
            LocalDate projectedCompletionDate
    ) {}

    private ProgressMetrics calculateMetrics(BigDecimal startValue, BigDecimal targetValue,
            BigDecimal currentValue, long daysPassed, long totalDays, LocalDate startDate) {

        if (startValue == null || targetValue == null || currentValue == null) {
            return new ProgressMetrics(0.0, true, "ON_TRACK", "GREEN", null);
        }

        BigDecimal totalChange = targetValue.subtract(startValue);
        if (totalChange.compareTo(BigDecimal.ZERO) == 0) {
            return new ProgressMetrics(100.0, true, "ON_TRACK", "GREEN", LocalDate.now());
        }

        BigDecimal currentChange = currentValue.subtract(startValue);
        double percent = currentChange.divide(totalChange, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
        percent = Math.max(0.0, Math.min(100.0, percent));

        double expectedPercent = (double) daysPassed / totalDays * 100.0;
        double diff = percent - expectedPercent;

        boolean isOnTrack = diff >= -5.0;
        String trackingStatus;
        String trackingColor;
        if (diff >= 5.0) {
            trackingStatus = "AHEAD";
            trackingColor = "GREEN";
        } else if (diff >= -5.0) {
            trackingStatus = "ON_TRACK";
            trackingColor = "GREEN";
        } else if (diff >= -15.0) {
            trackingStatus = "SLIGHTLY_BEHIND";
            trackingColor = "YELLOW";
        } else {
            trackingStatus = "BEHIND";
            trackingColor = "RED";
        }

        LocalDate projectedDate = null;
        if (daysPassed > 0 && currentChange.compareTo(BigDecimal.ZERO) != 0) {
            try {
                BigDecimal daysNeeded = totalChange.multiply(BigDecimal.valueOf(daysPassed))
                        .divide(currentChange, 0, RoundingMode.CEILING);
                projectedDate = startDate.plusDays(daysNeeded.longValue());
            } catch (ArithmeticException ignored) {}
        }

        return new ProgressMetrics(percent, isOnTrack, trackingStatus, trackingColor, projectedDate);
    }

    private void upsertCheckpoint(Goal goal, LocalDate today, BigDecimal currentValue,
            ProgressMetrics metrics, BigDecimal startValue, BigDecimal targetValue,
            long daysPassed, long totalDays) {
        boolean exists = goalCheckpointRepository
                .findByGoalIdAndCheckpointDate(goal.getId(), today).isPresent();
        if (exists) return;

        BigDecimal projectedValue = null;
        if (totalDays > 0) {
            BigDecimal totalChange = targetValue.subtract(startValue);
            BigDecimal projectedChange = totalChange.multiply(BigDecimal.valueOf(daysPassed))
                    .divide(BigDecimal.valueOf(totalDays), 2, RoundingMode.HALF_UP);
            projectedValue = startValue.add(projectedChange);
        }

        goalCheckpointRepository.save(GoalCheckpoint.builder()
                .goalId(goal.getId())
                .checkpointDate(today)
                .actualValue(currentValue)
                .projectedValue(projectedValue)
                .isOnTrack(metrics.isOnTrack())
                .build());
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
