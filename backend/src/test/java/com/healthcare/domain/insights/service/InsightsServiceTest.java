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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("InsightsService 단위 테스트")
class InsightsServiceTest {

    @Mock private BodyMeasurementRepository bodyMeasurementRepository;
    @Mock private ExerciseSessionRepository exerciseSessionRepository;
    @Mock private DietLogRepository dietLogRepository;
    @Mock private GoalRepository goalRepository;

    @InjectMocks
    private InsightsService insightsService;

    private static final Long USER_ID = 1L;

    // ─────────────────────────── 주간 요약 ───────────────────────────

    @Test
    @DisplayName("이번 주(offset=0) 운동·식단·신체 데이터가 모두 있을 때 집계값이 정확히 반환된다")
    void getWeeklySummary_withAllData_returnsAggregatedValues() {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(6);

        ExerciseSession s1 = session(30, 250.0);
        ExerciseSession s2 = session(60, 400.0);
        given(exerciseSessionRepository.findByUserIdAndDateRangeOrdered(USER_ID, weekStart, weekEnd))
                .willReturn(List.of(s1, s2));

        DietLog d1 = dietLog(weekStart, 2000.0, 130.0);
        DietLog d2 = dietLog(weekStart.plusDays(1), 2200.0, 140.0);
        given(dietLogRepository.findAllByUserIdAndDateRange(USER_ID, weekStart, weekEnd))
                .willReturn(List.of(d1, d2));

        BodyMeasurement latest = measurement(weekEnd, 75.0, 18.0, null);
        BodyMeasurement older  = measurement(weekStart, 75.5, 18.2, null);
        given(bodyMeasurementRepository.findByUserIdAndDateRange(USER_ID, weekStart, weekEnd))
                .willReturn(List.of(latest, older));

        given(goalRepository.findActiveGoalByUserId(USER_ID)).willReturn(Optional.empty());

        WeeklySummaryResponse result = insightsService.getWeeklySummary(USER_ID, 0);

        assertThat(result.getExerciseSessionCount()).isEqualTo(2);
        assertThat(result.getTotalExerciseMinutes()).isEqualTo(90);
        assertThat(result.getTotalCaloriesBurned()).isEqualTo(650.0);
        assertThat(result.getDietLogCount()).isEqualTo(2);
        assertThat(result.getAvgDailyCalories()).isEqualTo(2100.0);
        assertThat(result.getAvgDailyProteinG()).isEqualTo(135.0);
        assertThat(result.getLatestWeightKg()).isEqualTo(75.0);
        assertThat(result.getLatestBodyFatPct()).isEqualTo(18.0);
        assertThat(result.getWeightChangeKg()).isEqualTo(-0.5);
    }

    @Test
    @DisplayName("운동·식단·신체 데이터가 모두 없을 때 카운트는 0, optional 필드는 null")
    void getWeeklySummary_noData_returnsZeroCountsAndNullOptionals() {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(6);

        given(exerciseSessionRepository.findByUserIdAndDateRangeOrdered(USER_ID, weekStart, weekEnd))
                .willReturn(List.of());
        given(dietLogRepository.findAllByUserIdAndDateRange(USER_ID, weekStart, weekEnd))
                .willReturn(List.of());
        given(bodyMeasurementRepository.findByUserIdAndDateRange(USER_ID, weekStart, weekEnd))
                .willReturn(List.of());
        given(goalRepository.findActiveGoalByUserId(USER_ID)).willReturn(Optional.empty());

        WeeklySummaryResponse result = insightsService.getWeeklySummary(USER_ID, 0);

        assertThat(result.getExerciseSessionCount()).isZero();
        assertThat(result.getTotalExerciseMinutes()).isZero();
        assertThat(result.getTotalCaloriesBurned()).isNull();
        assertThat(result.getDietLogCount()).isZero();
        assertThat(result.getAvgDailyCalories()).isNull();
        assertThat(result.getLatestWeightKg()).isNull();
        assertThat(result.getWeightChangeKg()).isNull();
        assertThat(result.getActiveGoalPercentComplete()).isNull();
        assertThat(result.getActiveGoalTrackingStatus()).isNull();
    }

    @Test
    @DisplayName("운동 세션 칼로리 합계가 0이면 totalCaloriesBurned는 null")
    void getWeeklySummary_zeroCaloricBurn_returnsNullCalories() {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(6);

        given(exerciseSessionRepository.findByUserIdAndDateRangeOrdered(USER_ID, weekStart, weekEnd))
                .willReturn(List.of(session(45, null)));
        given(dietLogRepository.findAllByUserIdAndDateRange(USER_ID, weekStart, weekEnd))
                .willReturn(List.of());
        given(bodyMeasurementRepository.findByUserIdAndDateRange(USER_ID, weekStart, weekEnd))
                .willReturn(List.of());
        given(goalRepository.findActiveGoalByUserId(USER_ID)).willReturn(Optional.empty());

        WeeklySummaryResponse result = insightsService.getWeeklySummary(USER_ID, 0);

        assertThat(result.getTotalCaloriesBurned()).isNull();
        assertThat(result.getExerciseSessionCount()).isEqualTo(1);
        assertThat(result.getTotalExerciseMinutes()).isEqualTo(45);
    }

    @Test
    @DisplayName("weekOffset=1 이면 지난주 날짜 범위로 조회한다")
    void getWeeklySummary_withOffset1_queriesPreviousWeek() {
        LocalDate today = LocalDate.now();
        LocalDate thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate lastMonday = thisMonday.minusWeeks(1);
        LocalDate lastSunday = lastMonday.plusDays(6);

        given(exerciseSessionRepository.findByUserIdAndDateRangeOrdered(USER_ID, lastMonday, lastSunday))
                .willReturn(List.of());
        given(dietLogRepository.findAllByUserIdAndDateRange(USER_ID, lastMonday, lastSunday))
                .willReturn(List.of());
        given(bodyMeasurementRepository.findByUserIdAndDateRange(USER_ID, lastMonday, lastSunday))
                .willReturn(List.of());
        given(goalRepository.findActiveGoalByUserId(USER_ID)).willReturn(Optional.empty());

        WeeklySummaryResponse result = insightsService.getWeeklySummary(USER_ID, 1);

        assertThat(result.getWeekOffset()).isEqualTo(1);
        assertThat(result.getWeekStart()).isEqualTo(lastMonday);
        assertThat(result.getWeekEnd()).isEqualTo(lastSunday);
    }

    @Test
    @DisplayName("WEIGHT_LOSS 활성 목표가 있을 때 체중 기반 달성률을 계산한다")
    void getWeeklySummary_withActiveWeightLossGoal_calculatesPercentComplete() {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(6);

        given(exerciseSessionRepository.findByUserIdAndDateRangeOrdered(USER_ID, weekStart, weekEnd))
                .willReturn(List.of());
        given(dietLogRepository.findAllByUserIdAndDateRange(USER_ID, weekStart, weekEnd))
                .willReturn(List.of());
        given(bodyMeasurementRepository.findByUserIdAndDateRange(USER_ID, weekStart, weekEnd))
                .willReturn(List.of());

        // 시작 80kg → 목표 70kg, 현재 75kg → 50% 달성
        Goal goal = buildGoal(Goal.GoalType.WEIGHT_LOSS, Goal.GoalStatus.ACTIVE,
                new BigDecimal("80.0"), new BigDecimal("70.0"));
        given(goalRepository.findActiveGoalByUserId(USER_ID)).willReturn(Optional.of(goal));

        BodyMeasurement current = measurement(weekEnd, 75.0, null, null);
        given(bodyMeasurementRepository
                .findFirstByUserIdAndMeasuredAtLessThanEqualOrderByMeasuredAtDesc(USER_ID, weekEnd))
                .willReturn(Optional.of(current));

        WeeklySummaryResponse result = insightsService.getWeeklySummary(USER_ID, 0);

        assertThat(result.getActiveGoalPercentComplete()).isEqualTo(50.0);
        assertThat(result.getActiveGoalTrackingStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("ENDURANCE 목표는 달성률 계산 없이 상태만 반환한다")
    void getWeeklySummary_enduranceGoal_skipsPercentCalculation() {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(6);

        given(exerciseSessionRepository.findByUserIdAndDateRangeOrdered(USER_ID, weekStart, weekEnd))
                .willReturn(List.of());
        given(dietLogRepository.findAllByUserIdAndDateRange(USER_ID, weekStart, weekEnd))
                .willReturn(List.of());
        given(bodyMeasurementRepository.findByUserIdAndDateRange(USER_ID, weekStart, weekEnd))
                .willReturn(List.of());

        Goal goal = buildGoal(Goal.GoalType.ENDURANCE, Goal.GoalStatus.ACTIVE,
                new BigDecimal("30.0"), new BigDecimal("60.0"));
        given(goalRepository.findActiveGoalByUserId(USER_ID)).willReturn(Optional.of(goal));

        WeeklySummaryResponse result = insightsService.getWeeklySummary(USER_ID, 0);

        assertThat(result.getActiveGoalPercentComplete()).isNull();
        assertThat(result.getActiveGoalTrackingStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("신체 측정값이 1개뿐이면 주간 체중 변화는 null")
    void getWeeklySummary_singleMeasurement_weightChangeIsNull() {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(6);

        given(exerciseSessionRepository.findByUserIdAndDateRangeOrdered(USER_ID, weekStart, weekEnd))
                .willReturn(List.of());
        given(dietLogRepository.findAllByUserIdAndDateRange(USER_ID, weekStart, weekEnd))
                .willReturn(List.of());
        given(bodyMeasurementRepository.findByUserIdAndDateRange(USER_ID, weekStart, weekEnd))
                .willReturn(List.of(measurement(weekEnd, 75.0, 18.0, null)));
        given(goalRepository.findActiveGoalByUserId(USER_ID)).willReturn(Optional.empty());

        WeeklySummaryResponse result = insightsService.getWeeklySummary(USER_ID, 0);

        assertThat(result.getLatestWeightKg()).isEqualTo(75.0);
        assertThat(result.getWeightChangeKg()).isNull();
    }

    // ─────────────────────────── 변화 분석 ───────────────────────────

    @Test
    @DisplayName("측정값과 운동 세션이 모두 있을 때 델타와 스냅샷이 정확히 반환된다")
    void getChangeAnalysis_withMeasurementsAndSessions_returnsDeltas() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to   = LocalDate.of(2026, 4, 1);

        BodyMeasurement fromM = measurement(from, 80.0, 22.0, 58.0);
        BodyMeasurement toM   = measurement(to,   75.0, 18.5, 60.5);

        given(bodyMeasurementRepository
                .findFirstByUserIdAndMeasuredAtLessThanEqualOrderByMeasuredAtDesc(USER_ID, from))
                .willReturn(Optional.of(fromM));
        given(bodyMeasurementRepository
                .findFirstByUserIdAndMeasuredAtLessThanEqualOrderByMeasuredAtDesc(USER_ID, to))
                .willReturn(Optional.of(toM));
        given(exerciseSessionRepository.findByUserIdAndDateRangeOrdered(USER_ID, from, to))
                .willReturn(List.of(session(60, 400.0), session(45, 300.0)));

        ChangeAnalysisResponse result = insightsService.getChangeAnalysis(USER_ID, from, to);

        assertThat(result.getFromDate()).isEqualTo(from);
        assertThat(result.getToDate()).isEqualTo(to);
        assertThat(result.getWeightChangeKg()).isEqualTo(-5.0);
        assertThat(result.getBodyFatPctChange()).isEqualTo(-3.5);
        assertThat(result.getMuscleMassChangeKg()).isEqualTo(2.5);
        assertThat(result.getExerciseSessionCount()).isEqualTo(2);
        assertThat(result.getTotalExerciseMinutes()).isEqualTo(105);
        assertThat(result.getFromSnapshot()).isNotNull();
        assertThat(result.getFromSnapshot().getWeightKg()).isEqualTo(80.0);
        assertThat(result.getToSnapshot()).isNotNull();
        assertThat(result.getToSnapshot().getWeightKg()).isEqualTo(75.0);
    }

    @Test
    @DisplayName("from/to 측정값이 없을 때 델타는 null, 스냅샷은 null")
    void getChangeAnalysis_noMeasurements_returnsNullDeltasAndSnapshots() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to   = LocalDate.of(2026, 4, 1);

        given(bodyMeasurementRepository
                .findFirstByUserIdAndMeasuredAtLessThanEqualOrderByMeasuredAtDesc(USER_ID, from))
                .willReturn(Optional.empty());
        given(bodyMeasurementRepository
                .findFirstByUserIdAndMeasuredAtLessThanEqualOrderByMeasuredAtDesc(USER_ID, to))
                .willReturn(Optional.empty());
        given(exerciseSessionRepository.findByUserIdAndDateRangeOrdered(USER_ID, from, to))
                .willReturn(List.of());

        ChangeAnalysisResponse result = insightsService.getChangeAnalysis(USER_ID, from, to);

        assertThat(result.getWeightChangeKg()).isNull();
        assertThat(result.getBodyFatPctChange()).isNull();
        assertThat(result.getMuscleMassChangeKg()).isNull();
        assertThat(result.getFromSnapshot()).isNull();
        assertThat(result.getToSnapshot()).isNull();
        assertThat(result.getExerciseSessionCount()).isZero();
    }

    @Test
    @DisplayName("from 측정값만 없을 때 델타는 null, toSnapshot만 존재한다")
    void getChangeAnalysis_missingFromMeasurement_deltasNullToSnapshotPresent() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to   = LocalDate.of(2026, 4, 1);

        BodyMeasurement toM = measurement(to, 75.0, 18.0, 60.0);

        given(bodyMeasurementRepository
                .findFirstByUserIdAndMeasuredAtLessThanEqualOrderByMeasuredAtDesc(USER_ID, from))
                .willReturn(Optional.empty());
        given(bodyMeasurementRepository
                .findFirstByUserIdAndMeasuredAtLessThanEqualOrderByMeasuredAtDesc(USER_ID, to))
                .willReturn(Optional.of(toM));
        given(exerciseSessionRepository.findByUserIdAndDateRangeOrdered(USER_ID, from, to))
                .willReturn(List.of());

        ChangeAnalysisResponse result = insightsService.getChangeAnalysis(USER_ID, from, to);

        assertThat(result.getWeightChangeKg()).isNull();
        assertThat(result.getFromSnapshot()).isNull();
        assertThat(result.getToSnapshot()).isNotNull();
        assertThat(result.getToSnapshot().getWeightKg()).isEqualTo(75.0);
    }

    @Test
    @DisplayName("delta 계산 — 소수점 둘째 자리에서 반올림된다")
    void getChangeAnalysis_deltaRounding_roundsToTwoDecimalPlaces() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to   = LocalDate.of(2026, 4, 1);

        BodyMeasurement fromM = measurement(from, 80.333, null, null);
        BodyMeasurement toM   = measurement(to,   75.0,   null, null);

        given(bodyMeasurementRepository
                .findFirstByUserIdAndMeasuredAtLessThanEqualOrderByMeasuredAtDesc(USER_ID, from))
                .willReturn(Optional.of(fromM));
        given(bodyMeasurementRepository
                .findFirstByUserIdAndMeasuredAtLessThanEqualOrderByMeasuredAtDesc(USER_ID, to))
                .willReturn(Optional.of(toM));
        given(exerciseSessionRepository.findByUserIdAndDateRangeOrdered(USER_ID, from, to))
                .willReturn(List.of());

        ChangeAnalysisResponse result = insightsService.getChangeAnalysis(USER_ID, from, to);

        // 75.0 - 80.333 = -5.333 → 반올림 → -5.33
        assertThat(result.getWeightChangeKg()).isEqualTo(-5.33);
    }

    // ─────────────────────────── 헬퍼 ───────────────────────────

    private ExerciseSession session(int durationMinutes, Double caloriesBurned) {
        return ExerciseSession.builder()
                .userId(USER_ID)
                .sessionDate(LocalDate.now())
                .durationMinutes(durationMinutes)
                .caloriesBurned(caloriesBurned)
                .build();
    }

    private DietLog dietLog(LocalDate date, Double calories, Double proteinG) {
        return DietLog.builder()
                .userId(USER_ID)
                .logDate(date)
                .totalCalories(calories)
                .totalProteinG(proteinG)
                .mealType(DietLog.MealType.LUNCH)
                .build();
    }

    private BodyMeasurement measurement(LocalDate date, Double weightKg, Double bodyFatPct, Double muscleMassKg) {
        return BodyMeasurement.builder()
                .userId(USER_ID)
                .measuredAt(date)
                .weightKg(weightKg)
                .bodyFatPct(bodyFatPct)
                .muscleMassKg(muscleMassKg)
                .build();
    }

    private Goal buildGoal(Goal.GoalType goalType, Goal.GoalStatus status,
                           BigDecimal startValue, BigDecimal targetValue) {
        return Goal.builder()
                .userId(USER_ID)
                .goalType(goalType)
                .status(status)
                .startValue(startValue)
                .targetValue(targetValue)
                .targetDate(LocalDate.now().plusMonths(6))
                .targetUnit("kg")
                .build();
    }
}
