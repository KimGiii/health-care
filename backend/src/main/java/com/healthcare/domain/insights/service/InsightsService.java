package com.healthcare.domain.insights.service;

import com.healthcare.domain.bodymeasurement.entity.BodyMeasurement;
import com.healthcare.domain.bodymeasurement.repository.BodyMeasurementRepository;
import com.healthcare.domain.diet.entity.DietLog;
import com.healthcare.domain.diet.repository.DietLogRepository;
import com.healthcare.domain.exercise.entity.ExerciseSession;
import com.healthcare.domain.exercise.repository.ExerciseSessionRepository;
import com.healthcare.domain.goals.entity.Goal;
import com.healthcare.domain.goals.repository.GoalRepository;
import com.healthcare.domain.insights.dto.ChangeAnalysisResponse;
import com.healthcare.domain.insights.dto.WeeklySummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InsightsService {

    private final BodyMeasurementRepository bodyMeasurementRepository;
    private final ExerciseSessionRepository exerciseSessionRepository;
    private final DietLogRepository dietLogRepository;
    private final GoalRepository goalRepository;

    public WeeklySummaryResponse getWeeklySummary(Long userId, int weekOffset) {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .minusWeeks(weekOffset);
        LocalDate weekEnd = weekStart.plusDays(6);

        List<ExerciseSession> sessions = exerciseSessionRepository
                .findByUserIdAndDateRangeOrdered(userId, weekStart, weekEnd);
        int exerciseCount = sessions.size();
        int totalExerciseMinutes = sessions.stream()
                .mapToInt(s -> s.getDurationMinutes() != null ? s.getDurationMinutes() : 0)
                .sum();
        Double totalCaloriesBurned = sessions.stream()
                .mapToDouble(s -> s.getCaloriesBurned() != null ? s.getCaloriesBurned() : 0.0)
                .sum();
        if (totalCaloriesBurned == 0.0) totalCaloriesBurned = null;

        List<DietLog> dietLogs = dietLogRepository.findAllByUserIdAndDateRange(userId, weekStart, weekEnd);
        int dietLogCount = dietLogs.size();
        Double avgDailyCalories = dietLogs.isEmpty() ? null :
                dietLogs.stream().mapToDouble(d -> d.getTotalCalories() != null ? d.getTotalCalories() : 0.0).sum()
                        / Math.max(1, dietLogs.stream().map(DietLog::getLogDate).distinct().count());
        Double avgDailyProteinG = dietLogs.isEmpty() ? null :
                dietLogs.stream().mapToDouble(d -> d.getTotalProteinG() != null ? d.getTotalProteinG() : 0.0).sum()
                        / Math.max(1, dietLogs.stream().map(DietLog::getLogDate).distinct().count());

        List<BodyMeasurement> measurements = bodyMeasurementRepository
                .findByUserIdAndDateRange(userId, weekStart, weekEnd);
        Double latestWeightKg = measurements.isEmpty() ? null : measurements.get(0).getWeightKg();
        Double latestBodyFatPct = measurements.isEmpty() ? null : measurements.get(0).getBodyFatPct();
        Double weightChangeKg = resolveWeightChange(measurements);

        Optional<Goal> activeGoal = goalRepository.findActiveGoalByUserId(userId);
        Double activeGoalPercent = null;
        String activeGoalStatus = null;
        if (activeGoal.isPresent()) {
            Goal goal = activeGoal.get();
            activeGoalStatus = goal.getStatus().name();
            if (goal.getGoalType() != Goal.GoalType.ENDURANCE
                    && goal.getStartValue() != null && goal.getTargetValue() != null) {
                activeGoalPercent = bodyMeasurementRepository
                        .findFirstByUserIdAndMeasuredAtLessThanEqualOrderByMeasuredAtDesc(userId, weekEnd)
                        .map(m -> extractBodyValueByGoalType(goal.getGoalType(), m))
                        .map(current -> {
                            if (current == null) return null;
                            java.math.BigDecimal totalChange = goal.getTargetValue().subtract(goal.getStartValue());
                            if (totalChange.compareTo(java.math.BigDecimal.ZERO) == 0) return 100.0;
                            java.math.BigDecimal currentChange = current.subtract(goal.getStartValue());
                            double pct = currentChange
                                    .divide(totalChange, 6, java.math.RoundingMode.HALF_UP)
                                    .multiply(java.math.BigDecimal.valueOf(100))
                                    .doubleValue();
                            return Math.max(0.0, Math.min(100.0, pct));
                        })
                        .orElse(null);
            }
        }

        return WeeklySummaryResponse.builder()
                .weekStart(weekStart)
                .weekEnd(weekEnd)
                .weekOffset(weekOffset)
                .exerciseSessionCount(exerciseCount)
                .totalExerciseMinutes(totalExerciseMinutes)
                .totalCaloriesBurned(totalCaloriesBurned)
                .dietLogCount(dietLogCount)
                .avgDailyCalories(avgDailyCalories)
                .avgDailyProteinG(avgDailyProteinG)
                .latestWeightKg(latestWeightKg)
                .latestBodyFatPct(latestBodyFatPct)
                .weightChangeKg(weightChangeKg)
                .activeGoalPercentComplete(activeGoalPercent)
                .activeGoalTrackingStatus(activeGoalStatus)
                .build();
    }

    public ChangeAnalysisResponse getChangeAnalysis(Long userId, LocalDate from, LocalDate to) {
        Optional<BodyMeasurement> fromMeasurement = bodyMeasurementRepository
                .findFirstByUserIdAndMeasuredAtLessThanEqualOrderByMeasuredAtDesc(userId, from);
        Optional<BodyMeasurement> toMeasurement = bodyMeasurementRepository
                .findFirstByUserIdAndMeasuredAtLessThanEqualOrderByMeasuredAtDesc(userId, to);

        int exerciseCount = 0;
        int totalExerciseMinutes = 0;
        List<ExerciseSession> sessions = exerciseSessionRepository
                .findByUserIdAndDateRangeOrdered(userId, from, to);
        exerciseCount = sessions.size();
        totalExerciseMinutes = sessions.stream()
                .mapToInt(s -> s.getDurationMinutes() != null ? s.getDurationMinutes() : 0)
                .sum();

        ChangeAnalysisResponse.BodySnapshot fromSnapshot = fromMeasurement.map(this::toSnapshot).orElse(null);
        ChangeAnalysisResponse.BodySnapshot toSnapshot = toMeasurement.map(this::toSnapshot).orElse(null);

        Double weightChange = delta(
                fromMeasurement.map(BodyMeasurement::getWeightKg).orElse(null),
                toMeasurement.map(BodyMeasurement::getWeightKg).orElse(null));
        Double bodyFatChange = delta(
                fromMeasurement.map(BodyMeasurement::getBodyFatPct).orElse(null),
                toMeasurement.map(BodyMeasurement::getBodyFatPct).orElse(null));
        Double muscleChange = delta(
                fromMeasurement.map(BodyMeasurement::getMuscleMassKg).orElse(null),
                toMeasurement.map(BodyMeasurement::getMuscleMassKg).orElse(null));
        Double bmiChange = delta(
                fromMeasurement.map(BodyMeasurement::getBmi).orElse(null),
                toMeasurement.map(BodyMeasurement::getBmi).orElse(null));
        Double waistChange = delta(
                fromMeasurement.map(BodyMeasurement::getWaistCm).orElse(null),
                toMeasurement.map(BodyMeasurement::getWaistCm).orElse(null));
        Double chestChange = delta(
                fromMeasurement.map(BodyMeasurement::getChestCm).orElse(null),
                toMeasurement.map(BodyMeasurement::getChestCm).orElse(null));

        return ChangeAnalysisResponse.builder()
                .fromDate(from)
                .toDate(to)
                .weightChangeKg(weightChange)
                .bodyFatPctChange(bodyFatChange)
                .muscleMassChangeKg(muscleChange)
                .bmiChange(bmiChange)
                .waistChangeCm(waistChange)
                .chestChangeCm(chestChange)
                .exerciseSessionCount(exerciseCount)
                .totalExerciseMinutes(totalExerciseMinutes)
                .fromSnapshot(fromSnapshot)
                .toSnapshot(toSnapshot)
                .build();
    }

    private java.math.BigDecimal extractBodyValueByGoalType(Goal.GoalType goalType, BodyMeasurement m) {
        Double raw = switch (goalType) {
            case WEIGHT_LOSS, GENERAL_HEALTH -> m.getWeightKg();
            case MUSCLE_GAIN -> m.getMuscleMassKg();
            case BODY_RECOMPOSITION -> m.getBodyFatPct();
            case ENDURANCE -> null;
        };
        return raw != null ? java.math.BigDecimal.valueOf(raw) : null;
    }

    private Double resolveWeightChange(List<BodyMeasurement> measurements) {
        if (measurements.size() < 2) return null;
        Double first = measurements.get(measurements.size() - 1).getWeightKg();
        Double last = measurements.get(0).getWeightKg();
        return delta(first, last);
    }

    private Double delta(Double from, Double to) {
        if (from == null || to == null) return null;
        return Math.round((to - from) * 100.0) / 100.0;
    }

    private ChangeAnalysisResponse.BodySnapshot toSnapshot(BodyMeasurement m) {
        return ChangeAnalysisResponse.BodySnapshot.builder()
                .measuredAt(m.getMeasuredAt())
                .weightKg(m.getWeightKg())
                .bodyFatPct(m.getBodyFatPct())
                .muscleMassKg(m.getMuscleMassKg())
                .bmi(m.getBmi())
                .waistCm(m.getWaistCm())
                .chestCm(m.getChestCm())
                .build();
    }
}
