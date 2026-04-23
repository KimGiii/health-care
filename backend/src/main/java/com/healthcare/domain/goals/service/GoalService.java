package com.healthcare.domain.goals.service;

import com.healthcare.common.exception.BusinessRuleViolationException;
import com.healthcare.common.exception.ResourceNotFoundException;
import com.healthcare.common.exception.UnauthorizedException;
import com.healthcare.domain.bodymeasurement.entity.BodyMeasurement;
import com.healthcare.domain.bodymeasurement.repository.BodyMeasurementRepository;
import com.healthcare.domain.exercise.repository.ExerciseSessionRepository;
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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GoalService {

    private final GoalRepository goalRepository;
    private final GoalCheckpointRepository goalCheckpointRepository;
    private final UserRepository userRepository;
    private final BodyMeasurementRepository bodyMeasurementRepository;
    private final ExerciseSessionRepository exerciseSessionRepository;

    // ─────────────────────────── 목표 생성 ───────────────────────────

    @Transactional
    public GoalResponse createGoal(Long userId, CreateGoalRequest request) {
        userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (request.getTargetDate().isBefore(LocalDate.now())) {
            throw new BusinessRuleViolationException("목표 날짜는 오늘 이후여야 합니다.");
        }

        Goal.GoalType goalType = request.getGoalType();
        String normalizedTargetUnit = normalizeTargetUnit(goalType);
        BigDecimal normalizedTargetValue = normalizeTargetValue(
                goalType, request.getTargetUnit(), request.getTargetValue());
        BigDecimal normalizedStartValue = normalizeTargetValue(
                goalType, request.getTargetUnit(), request.getStartValue());
        BigDecimal normalizedWeeklyRateTarget = normalizeWeeklyRateTarget(goalType, request.getWeeklyRateTarget());

        // 기존 ACTIVE 목표 → ABANDONED
        goalRepository.findActiveGoalByUserId(userId).ifPresent(active -> {
            active.abandon();
            goalRepository.save(active);
        });

        Goal goal = Goal.builder()
                .userId(userId)
                .goalType(goalType)
                .targetValue(normalizedTargetValue)
                .targetUnit(normalizedTargetUnit)
                .targetDate(request.getTargetDate())
                .startValue(normalizedStartValue)
                .startDate(LocalDate.now())
                .status(GoalStatus.ACTIVE)
                .weeklyRateTarget(normalizedWeeklyRateTarget)
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
        return GoalListResponse.from(page, goal -> calculatePercentCompleteReadOnly(userId, goal));
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
        goal.updateTarget(
                request.getTargetValue(),
                request.getTargetDate(),
                normalizeWeeklyRateTarget(goal.getGoalType(), request.getWeeklyRateTarget())
        );
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
        BigDecimal startValue = goal.getStartValue();
        BigDecimal targetValue = goal.getTargetValue();

        List<MeasurementPoint> measurementPoints = loadMeasurementPoints(userId, goal, today);
        if (measurementPoints.isEmpty()) {
            String msg = goal.getGoalType() == Goal.GoalType.ENDURANCE
                    ? "운동 기록이 없어 지구력 목표 진행률을 계산할 수 없습니다."
                    : "신체 측정 기록이 없어 목표 진행률을 계산할 수 없습니다.";
            throw new BusinessRuleViolationException(msg);
        }

        upsertMissingWeeklyCheckpoints(goal, measurementPoints, today);

        BigDecimal currentValue = measurementPoints.get(measurementPoints.size() - 1).value();
        long daysElapsed = resolveDaysElapsed(startDate, today);
        long totalDays = resolveTotalDays(startDate, targetDate);
        Long daysRemaining = targetDate != null ? ChronoUnit.DAYS.between(today, targetDate) : null;
        ProgressMetrics metrics = calculateMetrics(
                startValue, targetValue, currentValue, daysElapsed, totalDays, startDate);

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
                .weeklyRateTarget(goal.getWeeklyRateTarget())
                .trackingColor(metrics.trackingColor())
                .checkpoints(checkpoints)
                .build();
    }

    private List<MeasurementPoint> loadMeasurementPoints(Long userId, Goal goal, LocalDate today) {
        if (goal.getStartDate() == null) {
            return List.of();
        }

        if (goal.getGoalType() == Goal.GoalType.ENDURANCE) {
            return loadExercisePoints(userId, goal, today);
        }

        return bodyMeasurementRepository
                .findByUserIdAndMeasuredAtBetweenOrderByMeasuredAtAsc(userId, goal.getStartDate(), today)
                .stream()
                .map(measurement -> new MeasurementPoint(
                        measurement.getMeasuredAt(),
                        extractValueByGoalType(goal.getGoalType(), measurement)))
                .filter(point -> point.value() != null)
                .toList();
    }

    private List<MeasurementPoint> loadExercisePoints(Long userId, Goal goal, LocalDate today) {
        long weeksSinceStart = Math.max(1, ChronoUnit.WEEKS.between(goal.getStartDate(), today));
        int totalMinutes = exerciseSessionRepository.sumDurationMinutesByUserIdAndDateRange(
                userId, goal.getStartDate(), today);
        BigDecimal weeklyAvg = BigDecimal.valueOf(totalMinutes)
                .divide(BigDecimal.valueOf(weeksSinceStart), 2, RoundingMode.HALF_UP);
        return List.of(new MeasurementPoint(today, weeklyAvg));
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

    private Double calculatePercentCompleteReadOnly(Long userId, Goal goal) {
        if (goal.getGoalType() == Goal.GoalType.ENDURANCE) {
            return null;
        }
        if (goal.getStartDate() == null || goal.getStartValue() == null || goal.getTargetValue() == null) {
            return null;
        }
        LocalDate today = LocalDate.now();
        return bodyMeasurementRepository
                .findFirstByUserIdAndMeasuredAtLessThanEqualOrderByMeasuredAtDesc(userId, today)
                .map(m -> extractValueByGoalType(goal.getGoalType(), m))
                .map(current -> {
                    if (current == null) return null;
                    long daysElapsed = resolveDaysElapsed(goal.getStartDate(), today);
                    long totalDays = resolveTotalDays(goal.getStartDate(), goal.getTargetDate());
                    return calculateMetrics(
                            goal.getStartValue(), goal.getTargetValue(), current,
                            daysElapsed, totalDays, goal.getStartDate()
                    ).percentComplete();
                })
                .orElse(null);
    }

    private String normalizeTargetUnit(Goal.GoalType goalType) {
        return switch (goalType) {
            case WEIGHT_LOSS, MUSCLE_GAIN -> "kg";
            case BODY_RECOMPOSITION -> "pct";
            case ENDURANCE -> "minutes";
            case GENERAL_HEALTH -> null;
        };
    }

    private BigDecimal normalizeTargetValue(Goal.GoalType goalType, String requestUnit, BigDecimal value) {
        if (value == null) {
            return null;
        }

        if (goalType == Goal.GoalType.ENDURANCE && "seconds".equalsIgnoreCase(requestUnit)) {
            return value.divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
        }

        return value;
    }

    private BigDecimal normalizeWeeklyRateTarget(Goal.GoalType goalType, BigDecimal weeklyRateTarget) {
        if (weeklyRateTarget == null) {
            return null;
        }

        BigDecimal magnitude = weeklyRateTarget.abs();
        return switch (goalType) {
            case WEIGHT_LOSS, BODY_RECOMPOSITION -> magnitude.negate();
            case MUSCLE_GAIN -> magnitude;
            case ENDURANCE, GENERAL_HEALTH -> null;
        };
    }

    private record MeasurementPoint(LocalDate measuredAt, BigDecimal value) {}

    private record ProgressMetrics(
            double percentComplete,
            boolean isOnTrack,
            String trackingStatus,
            String trackingColor,
            LocalDate projectedCompletionDate
    ) {}

    private ProgressMetrics calculateMetrics(BigDecimal startValue, BigDecimal targetValue,
            BigDecimal currentValue, long daysElapsed, long totalDays, LocalDate startDate) {

        if (startValue == null || targetValue == null || currentValue == null) {
            return new ProgressMetrics(0.0, true, "ON_TRACK", "GREEN", null);
        }

        BigDecimal totalChange = targetValue.subtract(startValue);
        if (totalChange.compareTo(BigDecimal.ZERO) == 0) {
            return new ProgressMetrics(100.0, true, "ON_TRACK", "GREEN", startDate);
        }

        BigDecimal currentChange = currentValue.subtract(startValue);
        double percent = currentChange.divide(totalChange, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
        percent = Math.max(0.0, Math.min(100.0, percent));

        long cappedDaysElapsed = Math.min(daysElapsed, totalDays);
        double expectedPercent = (double) cappedDaysElapsed / totalDays * 100.0;
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
        if (daysElapsed > 0
                && currentChange.compareTo(BigDecimal.ZERO) != 0
                && currentChange.signum() == totalChange.signum()) {
            try {
                BigDecimal daysNeeded = totalChange.multiply(BigDecimal.valueOf(daysElapsed))
                        .divide(currentChange, 0, RoundingMode.CEILING);
                projectedDate = startDate.plusDays(daysNeeded.longValue());
            } catch (ArithmeticException ignored) {}
        }

        return new ProgressMetrics(percent, isOnTrack, trackingStatus, trackingColor, projectedDate);
    }

    private long resolveDaysElapsed(LocalDate startDate, LocalDate referenceDate) {
        if (startDate == null || referenceDate == null) {
            return 0L;
        }
        return Math.max(0, ChronoUnit.DAYS.between(startDate, referenceDate));
    }

    private long resolveTotalDays(LocalDate startDate, LocalDate targetDate) {
        if (startDate == null || targetDate == null) {
            return 1L;
        }
        return Math.max(1, ChronoUnit.DAYS.between(startDate, targetDate));
    }

    private void upsertMissingWeeklyCheckpoints(Goal goal, List<MeasurementPoint> measurementPoints, LocalDate today) {
        LocalDate firstCheckpointDate = resolveFirstCheckpointDate(goal.getStartDate());
        LocalDate lastCheckpointDate = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));

        if (firstCheckpointDate == null || firstCheckpointDate.isAfter(lastCheckpointDate)) {
            return;
        }

        List<GoalCheckpoint> checkpointsToSave = new ArrayList<>();
        for (LocalDate checkpointDate = firstCheckpointDate;
             !checkpointDate.isAfter(lastCheckpointDate);
             checkpointDate = checkpointDate.plusWeeks(1)) {

            boolean exists = goalCheckpointRepository
                    .findByGoalIdAndCheckpointDate(goal.getId(), checkpointDate)
                    .isPresent();
            if (exists) {
                continue;
            }

            BigDecimal actualValue = findLatestValueAtOrBefore(measurementPoints, checkpointDate);
            BigDecimal projectedValue = calculateProjectedValue(goal, checkpointDate);
            Boolean isOnTrack = determineCheckpointOnTrack(goal, actualValue, projectedValue);

            checkpointsToSave.add(GoalCheckpoint.builder()
                    .goalId(goal.getId())
                    .checkpointDate(checkpointDate)
                    .actualValue(actualValue)
                    .projectedValue(projectedValue)
                    .isOnTrack(isOnTrack)
                    .build());
        }

        if (!checkpointsToSave.isEmpty()) {
            goalCheckpointRepository.saveAll(checkpointsToSave);
        }
    }

    private LocalDate resolveFirstCheckpointDate(LocalDate startDate) {
        if (startDate == null) {
            return null;
        }
        return startDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
    }

    private BigDecimal findLatestValueAtOrBefore(List<MeasurementPoint> measurementPoints, LocalDate checkpointDate) {
        BigDecimal latestValue = null;
        for (MeasurementPoint point : measurementPoints) {
            if (point.measuredAt().isAfter(checkpointDate)) {
                break;
            }
            latestValue = point.value();
        }
        return latestValue;
    }

    private BigDecimal calculateProjectedValue(Goal goal, LocalDate checkpointDate) {
        if (goal.getStartValue() == null || goal.getTargetValue() == null
                || goal.getStartDate() == null || goal.getTargetDate() == null) {
            return null;
        }

        long totalDays = resolveTotalDays(goal.getStartDate(), goal.getTargetDate());
        long daysElapsed = Math.min(
                resolveDaysElapsed(goal.getStartDate(), checkpointDate),
                totalDays
        );

        BigDecimal totalChange = goal.getTargetValue().subtract(goal.getStartValue());
        BigDecimal projectedChange = totalChange.multiply(BigDecimal.valueOf(daysElapsed))
                .divide(BigDecimal.valueOf(totalDays), 2, RoundingMode.HALF_UP);
        return goal.getStartValue().add(projectedChange);
    }

    private Boolean determineCheckpointOnTrack(Goal goal, BigDecimal actualValue, BigDecimal projectedValue) {
        if (actualValue == null || projectedValue == null
                || goal.getStartValue() == null || goal.getTargetValue() == null) {
            return null;
        }

        int direction = goal.getTargetValue().compareTo(goal.getStartValue());
        if (direction == 0) {
            return actualValue.compareTo(projectedValue) == 0;
        }
        return direction < 0
                ? actualValue.compareTo(projectedValue) <= 0
                : actualValue.compareTo(projectedValue) >= 0;
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
