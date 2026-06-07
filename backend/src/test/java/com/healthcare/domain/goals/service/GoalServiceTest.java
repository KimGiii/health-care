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
import com.healthcare.domain.goals.entity.Goal.GoalType;
import com.healthcare.domain.goals.entity.GoalCheckpoint;
import com.healthcare.domain.goals.repository.GoalCheckpointRepository;
import com.healthcare.domain.goals.repository.GoalRepository;
import com.healthcare.domain.nutrition.service.NutritionTargetService;
import com.healthcare.domain.user.entity.User;
import com.healthcare.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * RED: GoalService, 관련 Repository, DTO 클래스가 없으므로 컴파일 실패 상태.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GoalService 단위 테스트")
class GoalServiceTest {

    @Mock private GoalRepository goalRepository;
    @Mock private GoalCheckpointRepository goalCheckpointRepository;
    @Mock private UserRepository userRepository;
    @Mock private BodyMeasurementRepository bodyMeasurementRepository;
    @Mock private ExerciseSessionRepository exerciseSessionRepository;
    @Mock private NutritionTargetService nutritionTargetService;

    @InjectMocks
    private GoalService goalService;

    // ─────────────────────────── 목표 생성 ───────────────────────────

    @Test
    @DisplayName("새 목표 생성 시 기존 ACTIVE 목표는 ABANDONED 처리되고 새 목표가 ACTIVE로 저장된다")
    void createGoal_withExistingActive_abandonsOldAndCreatesNew() {
        Long userId = 1L;
        Goal existingActive = buildGoal(10L, userId, GoalType.WEIGHT_LOSS, GoalStatus.ACTIVE);
        CreateGoalRequest request = buildCreateRequest(
                GoalType.BODY_RECOMPOSITION,
                new BigDecimal("15.0"), "kg",
                LocalDate.now().plusMonths(6),
                new BigDecimal("18.5"), new BigDecimal("0.25"));

        given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(buildUser(userId)));
        given(goalRepository.findActiveGoalByUserId(userId)).willReturn(Optional.of(existingActive));
        Goal savedGoal = buildGoal(20L, userId, GoalType.BODY_RECOMPOSITION, GoalStatus.ACTIVE);
        given(goalRepository.save(any(Goal.class))).willReturn(savedGoal);

        GoalResponse response = goalService.createGoal(userId, request);

        assertThat(response.getGoalId()).isEqualTo(20L);
        assertThat(response.getStatus()).isEqualTo(GoalStatus.ACTIVE);

        // save() 2번 호출: 기존 목표 ABANDONED + 신규 목표 저장
        ArgumentCaptor<Goal> captor = ArgumentCaptor.forClass(Goal.class);
        verify(goalRepository, times(2)).save(captor.capture());
        List<Goal> savedGoals = captor.getAllValues();
        assertThat(savedGoals.get(0).getStatus()).isEqualTo(GoalStatus.ABANDONED);
        assertThat(savedGoals.get(1).getTargetUnit()).isEqualTo("pct");
        assertThat(savedGoals.get(1).getWeeklyRateTarget()).isEqualByComparingTo("-0.25");
    }

    @Test
    @DisplayName("기존 ACTIVE 목표 없이 새 목표 생성 성공")
    void createGoal_noExistingActive_createsNewGoal() {
        Long userId = 1L;
        CreateGoalRequest request = buildCreateRequest(
                GoalType.MUSCLE_GAIN,
                new BigDecimal("75.0"), "kg",
                LocalDate.now().plusMonths(12),
                new BigDecimal("70.0"), new BigDecimal("0.25"));

        given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(buildUser(userId)));
        given(goalRepository.findActiveGoalByUserId(userId)).willReturn(Optional.empty());
        Goal savedGoal = buildGoal(30L, userId, GoalType.MUSCLE_GAIN, GoalStatus.ACTIVE);
        given(goalRepository.save(any(Goal.class))).willReturn(savedGoal);

        GoalResponse response = goalService.createGoal(userId, request);

        assertThat(response.getGoalId()).isEqualTo(30L);
        assertThat(response.getGoalType()).isEqualTo(GoalType.MUSCLE_GAIN);
    }

    @Test
    @DisplayName("존재하지 않는 사용자로 목표 생성 시 ResourceNotFoundException 발생")
    void createGoal_userNotFound_throwsResourceNotFoundException() {
        Long userId = 999L;
        given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.empty());
        CreateGoalRequest request = buildCreateRequest(
                GoalType.WEIGHT_LOSS,
                new BigDecimal("70.0"), "kg",
                LocalDate.now().plusMonths(3),
                new BigDecimal("80.0"), new BigDecimal("-0.5"));

        assertThatThrownBy(() -> goalService.createGoal(userId, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("startValue 미입력 시 최신 신체 측정값으로 자동 채워지고 시작 체크포인트가 생성된다")
    void createGoal_missingStartValue_autofillsFromLatestMeasurementAndCreatesStartCheckpoint() {
        Long userId = 1L;
        LocalDate today = LocalDate.now();
        // startValue 없이 요청 (WEIGHT_LOSS, 목표 70kg)
        CreateGoalRequest request = buildCreateRequest(
                GoalType.WEIGHT_LOSS,
                new BigDecimal("70.0"), "kg",
                today.plusMonths(3),
                null, new BigDecimal("-0.5"));

        BodyMeasurement latest = buildMeasurement(userId, today.minusDays(1), 80.5);

        given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(buildUser(userId)));
        given(bodyMeasurementRepository.findFirstByUserIdAndMeasuredAtLessThanEqualOrderByMeasuredAtDesc(userId, today))
                .willReturn(Optional.of(latest));
        given(goalRepository.findActiveGoalByUserId(userId)).willReturn(Optional.empty());
        Goal savedGoal = buildWeightGoal(40L, userId,
                new BigDecimal("80.5"), new BigDecimal("70.0"),
                today, today.plusMonths(3));
        given(goalRepository.save(any(Goal.class))).willReturn(savedGoal);

        goalService.createGoal(userId, request);

        // 저장된 Goal의 startValue가 최신 측정값으로 채워졌는지 확인.
        ArgumentCaptor<Goal> goalCaptor = ArgumentCaptor.forClass(Goal.class);
        verify(goalRepository).save(goalCaptor.capture());
        assertThat(goalCaptor.getValue().getStartValue()).isEqualByComparingTo("80.5");

        // 시작 체크포인트가 저장됐는지 확인.
        ArgumentCaptor<GoalCheckpoint> checkpointCaptor = ArgumentCaptor.forClass(GoalCheckpoint.class);
        verify(goalCheckpointRepository).save(checkpointCaptor.capture());
        GoalCheckpoint startCheckpoint = checkpointCaptor.getValue();
        assertThat(startCheckpoint.getCheckpointDate()).isEqualTo(today);
        assertThat(startCheckpoint.getActualValue()).isEqualByComparingTo("80.5");
        assertThat(startCheckpoint.getNotes()).isEqualTo("시작");
        assertThat(startCheckpoint.getIsOnTrack()).isTrue();
    }

    @Test
    @DisplayName("startValue 미입력 시 사용자 프로필 weightKg가 측정 기록보다 우선 사용된다")
    void createGoal_missingStartValue_prefersUserProfileOverMeasurement() {
        Long userId = 1L;
        LocalDate today = LocalDate.now();
        CreateGoalRequest request = buildCreateRequest(
                GoalType.WEIGHT_LOSS,
                new BigDecimal("70.0"), "kg",
                today.plusMonths(3),
                null, new BigDecimal("-0.5"));

        // 프로필에 78.0kg, 측정 기록은 80.5kg — 프로필이 우선되어야 함
        User userWithWeight = User.builder()
                .id(userId).email("a@b.c").passwordHash("h").displayName("T")
                .weightKg(78.0).build();
        given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(userWithWeight));
        given(goalRepository.findActiveGoalByUserId(userId)).willReturn(Optional.empty());
        Goal savedGoal = buildWeightGoal(41L, userId,
                new BigDecimal("78.0"), new BigDecimal("70.0"),
                today, today.plusMonths(3));
        given(goalRepository.save(any(Goal.class))).willReturn(savedGoal);

        goalService.createGoal(userId, request);

        ArgumentCaptor<Goal> goalCaptor = ArgumentCaptor.forClass(Goal.class);
        verify(goalRepository).save(goalCaptor.capture());
        assertThat(goalCaptor.getValue().getStartValue()).isEqualByComparingTo("78.0");

        // 시작 체크포인트도 프로필 값으로 생성됨
        ArgumentCaptor<GoalCheckpoint> checkpointCaptor = ArgumentCaptor.forClass(GoalCheckpoint.class);
        verify(goalCheckpointRepository).save(checkpointCaptor.capture());
        assertThat(checkpointCaptor.getValue().getActualValue()).isEqualByComparingTo("78.0");
    }

    @Test
    @DisplayName("startValue 미입력 + 측정 기록 없으면 startValue는 null로 저장되고 시작 체크포인트는 생성되지 않는다")
    void createGoal_missingStartValueAndNoMeasurement_savesNullStartValueAndSkipsCheckpoint() {
        Long userId = 1L;
        LocalDate today = LocalDate.now();
        CreateGoalRequest request = buildCreateRequest(
                GoalType.WEIGHT_LOSS,
                new BigDecimal("70.0"), "kg",
                today.plusMonths(3),
                null, new BigDecimal("-0.5"));

        given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(buildUser(userId)));
        given(bodyMeasurementRepository.findFirstByUserIdAndMeasuredAtLessThanEqualOrderByMeasuredAtDesc(userId, today))
                .willReturn(Optional.empty());
        given(goalRepository.findActiveGoalByUserId(userId)).willReturn(Optional.empty());
        Goal savedGoal = buildGoal(41L, userId, GoalType.WEIGHT_LOSS, GoalStatus.ACTIVE);
        given(goalRepository.save(any(Goal.class))).willReturn(savedGoal);

        goalService.createGoal(userId, request);

        ArgumentCaptor<Goal> goalCaptor = ArgumentCaptor.forClass(Goal.class);
        verify(goalRepository).save(goalCaptor.capture());
        assertThat(goalCaptor.getValue().getStartValue()).isNull();
        verify(goalCheckpointRepository, times(0)).save(any(GoalCheckpoint.class));
    }

    @Test
    @DisplayName("startValue 입력 시 자동 채우기는 동작하지 않고 입력값으로 시작 체크포인트가 생성된다")
    void createGoal_explicitStartValue_skipsAutofillAndCreatesCheckpointFromRequest() {
        Long userId = 1L;
        LocalDate today = LocalDate.now();
        CreateGoalRequest request = buildCreateRequest(
                GoalType.WEIGHT_LOSS,
                new BigDecimal("70.0"), "kg",
                today.plusMonths(3),
                new BigDecimal("82.0"), new BigDecimal("-0.5"));

        given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(buildUser(userId)));
        given(goalRepository.findActiveGoalByUserId(userId)).willReturn(Optional.empty());
        Goal savedGoal = buildWeightGoal(42L, userId,
                new BigDecimal("82.0"), new BigDecimal("70.0"),
                today, today.plusMonths(3));
        given(goalRepository.save(any(Goal.class))).willReturn(savedGoal);

        goalService.createGoal(userId, request);

        // 사용자 입력값이 우선 — 측정 기록 조회를 호출하지 않아야 한다.
        verify(bodyMeasurementRepository, times(0))
                .findFirstByUserIdAndMeasuredAtLessThanEqualOrderByMeasuredAtDesc(any(), any());

        ArgumentCaptor<GoalCheckpoint> captor = ArgumentCaptor.forClass(GoalCheckpoint.class);
        verify(goalCheckpointRepository).save(captor.capture());
        assertThat(captor.getValue().getActualValue()).isEqualByComparingTo("82.0");
        assertThat(captor.getValue().getNotes()).isEqualTo("시작");
    }

    @Test
    @DisplayName("ENDURANCE 목표는 startValue 자동 채우기를 시도하지 않는다")
    void createGoal_endurance_skipsStartValueAutofill() {
        Long userId = 1L;
        LocalDate today = LocalDate.now();
        CreateGoalRequest request = buildCreateRequest(
                GoalType.ENDURANCE,
                new BigDecimal("60"), "minutes",
                today.plusMonths(3),
                null, null);

        given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(buildUser(userId)));
        given(goalRepository.findActiveGoalByUserId(userId)).willReturn(Optional.empty());
        Goal savedGoal = buildGoal(43L, userId, GoalType.ENDURANCE, GoalStatus.ACTIVE);
        given(goalRepository.save(any(Goal.class))).willReturn(savedGoal);

        goalService.createGoal(userId, request);

        verify(bodyMeasurementRepository, times(0))
                .findFirstByUserIdAndMeasuredAtLessThanEqualOrderByMeasuredAtDesc(any(), any());
        // ENDURANCE는 시작 측정값 개념이 다르므로 시작 체크포인트도 생성하지 않는다(startValue null).
        verify(goalCheckpointRepository, times(0)).save(any(GoalCheckpoint.class));
    }

    @Test
    @DisplayName("과거 날짜를 목표 날짜로 지정 시 BusinessRuleViolationException 발생")
    void createGoal_targetDateInPast_throwsBusinessRuleViolationException() {
        Long userId = 1L;
        given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(buildUser(userId)));
        CreateGoalRequest request = buildCreateRequest(
                GoalType.WEIGHT_LOSS,
                new BigDecimal("70.0"), "kg",
                LocalDate.now().minusDays(1),
                new BigDecimal("80.0"), new BigDecimal("-0.5"));

        assertThatThrownBy(() -> goalService.createGoal(userId, request))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    // ─────────────────────────── 목표 단건 조회 ───────────────────────────

    @Test
    @DisplayName("본인 목표 단건 조회 성공")
    void getGoalById_success_returnsGoalResponse() {
        Long userId = 1L;
        Long goalId = 10L;
        Goal goal = buildGoal(goalId, userId, GoalType.WEIGHT_LOSS, GoalStatus.ACTIVE);
        given(goalRepository.findById(goalId)).willReturn(Optional.of(goal));

        GoalResponse response = goalService.getGoalById(userId, goalId);

        assertThat(response.getGoalId()).isEqualTo(goalId);
        assertThat(response.getGoalType()).isEqualTo(GoalType.WEIGHT_LOSS);
    }

    @Test
    @DisplayName("존재하지 않는 목표 ID 조회 시 ResourceNotFoundException 발생")
    void getGoalById_notFound_throwsResourceNotFoundException() {
        given(goalRepository.findById(9999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> goalService.getGoalById(1L, 9999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("다른 사용자의 목표 조회 시 UnauthorizedException 발생")
    void getGoalById_otherUserGoal_throwsUnauthorizedException() {
        Long currentUserId = 1L;
        Long goalId = 10L;
        Goal goal = buildGoal(goalId, 99L, GoalType.WEIGHT_LOSS, GoalStatus.ACTIVE);
        given(goalRepository.findById(goalId)).willReturn(Optional.of(goal));

        assertThatThrownBy(() -> goalService.getGoalById(currentUserId, goalId))
                .isInstanceOf(UnauthorizedException.class);
    }

    // ─────────────────────────── 목표 목록 조회 ───────────────────────────

    @Test
    @DisplayName("목표 목록 조회 시 페이지네이션 결과 반환")
    void listGoals_returnsPaginatedResults() {
        Long userId = 1L;
        Pageable pageable = PageRequest.of(0, 20);
        Goal goal = buildGoal(10L, userId, GoalType.WEIGHT_LOSS, GoalStatus.ACTIVE);
        Page<Goal> page = new PageImpl<>(List.of(goal), pageable, 1);
        given(goalRepository.findByUserIdAndStatus(userId, null, pageable)).willReturn(page);

        GoalListResponse response = goalService.listGoals(userId, null, pageable);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getTotalElements()).isEqualTo(1);
    }

    // ─────────────────────────── 목표 수정 ───────────────────────────

    @Test
    @DisplayName("본인 ACTIVE 목표 수정 성공 (목표 날짜 변경)")
    void updateGoal_success_returnsUpdatedGoalResponse() {
        Long userId = 1L;
        Long goalId = 10L;
        Goal goal = buildGoal(goalId, userId, GoalType.WEIGHT_LOSS, GoalStatus.ACTIVE);
        UpdateGoalRequest request = UpdateGoalRequest.builder()
                .targetDate(LocalDate.now().plusMonths(9))
                .targetValue(new BigDecimal("68.0"))
                .weeklyRateTarget(new BigDecimal("0.40"))
                .build();

        given(goalRepository.findById(goalId)).willReturn(Optional.of(goal));
        given(goalRepository.save(any(Goal.class))).willReturn(goal);

        GoalResponse response = goalService.updateGoal(userId, goalId, request);

        assertThat(response.getGoalId()).isEqualTo(goalId);
        ArgumentCaptor<Goal> captor = ArgumentCaptor.forClass(Goal.class);
        verify(goalRepository).save(captor.capture());
        assertThat(captor.getValue().getWeeklyRateTarget()).isEqualByComparingTo("-0.40");
    }

    @Test
    @DisplayName("ENDURANCE 목표 생성 시 seconds 입력값은 minutes 로 변환되고 weeklyRateTarget 은 null 로 정규화된다")
    void createGoal_endurance_normalizesTargetUnitAndWeeklyRate() {
        Long userId = 1L;
        CreateGoalRequest request = buildCreateRequest(
                GoalType.ENDURANCE,
                new BigDecimal("3600"), "seconds",
                LocalDate.now().plusMonths(3),
                new BigDecimal("1800"), new BigDecimal("15"));

        given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(buildUser(userId)));
        given(goalRepository.findActiveGoalByUserId(userId)).willReturn(Optional.empty());
        given(goalRepository.save(any(Goal.class))).willAnswer(inv -> inv.getArgument(0));

        GoalResponse response = goalService.createGoal(userId, request);

        assertThat(response.getTargetValue()).isEqualByComparingTo("60.00");
        assertThat(response.getTargetUnit()).isEqualTo("minutes");
        assertThat(response.getStartValue()).isEqualByComparingTo("30.00");
        assertThat(response.getWeeklyRateTarget()).isNull();
    }

    @Test
    @DisplayName("다른 사용자의 목표 수정 시 UnauthorizedException 발생")
    void updateGoal_otherUserGoal_throwsUnauthorizedException() {
        Long goalId = 10L;
        Goal goal = buildGoal(goalId, 99L, GoalType.WEIGHT_LOSS, GoalStatus.ACTIVE);
        given(goalRepository.findById(goalId)).willReturn(Optional.of(goal));

        assertThatThrownBy(() -> goalService.updateGoal(1L, goalId,
                UpdateGoalRequest.builder().targetDate(LocalDate.now().plusMonths(6)).build()))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("COMPLETED 목표 수정 시 BusinessRuleViolationException 발생")
    void updateGoal_completedGoal_throwsBusinessRuleViolationException() {
        Long userId = 1L;
        Long goalId = 10L;
        Goal goal = buildGoal(goalId, userId, GoalType.WEIGHT_LOSS, GoalStatus.COMPLETED);
        given(goalRepository.findById(goalId)).willReturn(Optional.of(goal));

        assertThatThrownBy(() -> goalService.updateGoal(userId, goalId,
                UpdateGoalRequest.builder().targetDate(LocalDate.now().plusMonths(6)).build()))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    // ─────────────────────────── 목표 포기 ───────────────────────────

    @Test
    @DisplayName("ACTIVE 목표 포기 성공 — status ABANDONED, abandonedAt 설정")
    void abandonGoal_success_setsStatusToAbandoned() {
        Long userId = 1L;
        Long goalId = 10L;
        Goal goal = buildGoal(goalId, userId, GoalType.WEIGHT_LOSS, GoalStatus.ACTIVE);
        given(goalRepository.findById(goalId)).willReturn(Optional.of(goal));
        given(goalRepository.save(any(Goal.class))).willReturn(goal);

        goalService.abandonGoal(userId, goalId);

        ArgumentCaptor<Goal> captor = ArgumentCaptor.forClass(Goal.class);
        verify(goalRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(GoalStatus.ABANDONED);
        assertThat(captor.getValue().getAbandonedAt()).isNotNull();
    }

    @Test
    @DisplayName("다른 사용자의 목표 포기 시 UnauthorizedException 발생")
    void abandonGoal_otherUserGoal_throwsUnauthorizedException() {
        Long goalId = 10L;
        Goal goal = buildGoal(goalId, 99L, GoalType.WEIGHT_LOSS, GoalStatus.ACTIVE);
        given(goalRepository.findById(goalId)).willReturn(Optional.of(goal));

        assertThatThrownBy(() -> goalService.abandonGoal(1L, goalId))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("이미 COMPLETED 목표 포기 시 BusinessRuleViolationException 발생")
    void abandonGoal_completedGoal_throwsBusinessRuleViolationException() {
        Long userId = 1L;
        Long goalId = 10L;
        Goal goal = buildGoal(goalId, userId, GoalType.WEIGHT_LOSS, GoalStatus.COMPLETED);
        given(goalRepository.findById(goalId)).willReturn(Optional.of(goal));

        assertThatThrownBy(() -> goalService.abandonGoal(userId, goalId))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    // ─────────────────────────── 목표 진행률 조회 ───────────────────────────

    @Test
    @DisplayName("신체 측정 기록이 있으면 진행률을 계산한다 — 조회는 순수 읽기, 체크포인트 쓰기 없음(H-3)")
    void getGoalProgress_withBodyMeasurement_calculatesPercent() {
        Long userId = 1L;
        Long goalId = 10L;
        LocalDate today = LocalDate.now();
        Goal goal = buildWeightGoal(goalId, userId,
                new BigDecimal("80.0"), new BigDecimal("70.0"),
                today.minusDays(30), today.plusDays(30));
        List<BodyMeasurement> measurements = List.of(
                buildMeasurement(userId, today.minusDays(21), 78.5),
                buildMeasurement(userId, today.minusDays(7), 76.8),
                buildMeasurement(userId, today, 75.0)
        );

        given(goalRepository.findById(goalId)).willReturn(Optional.of(goal));
        given(bodyMeasurementRepository.findByUserIdAndMeasuredAtBetweenOrderByMeasuredAtAsc(
                userId, goal.getStartDate(), today))
                .willReturn(measurements);
        given(goalCheckpointRepository.findByGoalIdOrderByCheckpointDate(goalId))
                .willReturn(List.of());

        GoalProgressResponse response = goalService.getGoalProgress(userId, goalId);

        assertThat(response.getPercentComplete()).isEqualTo(50.0);
        assertThat(response.getCurrentValue()).isEqualByComparingTo(new BigDecimal("75.0"));
        assertThat(response.getDaysRemaining()).isEqualTo(30L);

        // 조회 경로에서 체크포인트 쓰기가 발생하지 않아야 한다 — 동시 INSERT 위험 제거.
        verify(goalCheckpointRepository, times(0)).saveAll(any());
    }

    @Test
    @DisplayName("신체 측정 기록이 없으면 BusinessRuleViolationException 이 발생한다")
    void getGoalProgress_noBodyMeasurement_throwsBusinessRuleViolationException() {
        Long userId = 1L;
        Long goalId = 10L;
        LocalDate today = LocalDate.now();
        Goal goal = buildWeightGoal(goalId, userId,
                new BigDecimal("80.0"), new BigDecimal("70.0"),
                today.minusDays(10), today.plusDays(50));

        given(goalRepository.findById(goalId)).willReturn(Optional.of(goal));
        given(bodyMeasurementRepository.findByUserIdAndMeasuredAtBetweenOrderByMeasuredAtAsc(
                userId, goal.getStartDate(), today))
                .willReturn(List.of());

        assertThatThrownBy(() -> goalService.getGoalProgress(userId, goalId))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("신체 측정 기록");
    }

    @Test
    @DisplayName("목표보다 빠르게 진행 중이면 trackingStatus 가 AHEAD 이다")
    void getGoalProgress_aheadOfSchedule_returnsAhead() {
        Long userId = 1L;
        Long goalId = 10L;
        LocalDate today = LocalDate.now();
        // 10일 경과(총 60일), 기대 진행률 ~16%, 실제 80%
        Goal goal = buildWeightGoal(goalId, userId,
                new BigDecimal("80.0"), new BigDecimal("70.0"),
                today.minusDays(10), today.plusDays(50));
        List<BodyMeasurement> measurements = List.of(
                buildMeasurement(userId, today.minusDays(3), 72.0)
        );

        given(goalRepository.findById(goalId)).willReturn(Optional.of(goal));
        given(bodyMeasurementRepository.findByUserIdAndMeasuredAtBetweenOrderByMeasuredAtAsc(
                userId, goal.getStartDate(), today))
                .willReturn(measurements);
        given(goalCheckpointRepository.findByGoalIdOrderByCheckpointDate(goalId))
                .willReturn(List.of());

        GoalProgressResponse response = goalService.getGoalProgress(userId, goalId);

        assertThat(response.getTrackingStatus()).isEqualTo("AHEAD");
        assertThat(response.getTrackingColor()).isEqualTo("GREEN");
        assertThat(response.getIsOnTrack()).isTrue();
    }

    @Test
    @DisplayName("크게 뒤처진 경우 trackingStatus 가 BEHIND 이다")
    void getGoalProgress_behindSchedule_returnsBehind() {
        Long userId = 1L;
        Long goalId = 10L;
        LocalDate today = LocalDate.now();
        // 50일 경과(총 60일), 기대 진행률 ~83%, 실제 0%
        Goal goal = buildWeightGoal(goalId, userId,
                new BigDecimal("80.0"), new BigDecimal("70.0"),
                today.minusDays(50), today.plusDays(10));
        List<BodyMeasurement> measurements = List.of(
                buildMeasurement(userId, today.minusDays(1), 80.0)
        );

        given(goalRepository.findById(goalId)).willReturn(Optional.of(goal));
        given(bodyMeasurementRepository.findByUserIdAndMeasuredAtBetweenOrderByMeasuredAtAsc(
                userId, goal.getStartDate(), today))
                .willReturn(measurements);
        given(goalCheckpointRepository.findByGoalIdOrderByCheckpointDate(goalId))
                .willReturn(List.of());

        GoalProgressResponse response = goalService.getGoalProgress(userId, goalId);

        assertThat(response.getTrackingStatus()).isEqualTo("BEHIND");
        assertThat(response.getTrackingColor()).isEqualTo("RED");
        assertThat(response.getIsOnTrack()).isFalse();
    }

    @Test
    @DisplayName("maintainCheckpointsForGoal — 이미 해당 일요일 체크포인트가 있으면 새로 저장하지 않는다")
    void maintainCheckpointsForGoal_existingCheckpoint_noNewSave() {
        Long userId = 1L;
        Long goalId = 10L;
        LocalDate today = LocalDate.now();
        LocalDate lastSunday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        Goal goal = buildWeightGoal(goalId, userId,
                new BigDecimal("80.0"), new BigDecimal("70.0"),
                lastSunday.minusDays(2), today.plusDays(50));
        List<BodyMeasurement> measurements = List.of(
                buildMeasurement(userId, today.minusDays(1), 75.0)
        );
        GoalCheckpoint existing = GoalCheckpoint.builder()
                .goalId(goalId).checkpointDate(lastSunday)
                .actualValue(new BigDecimal("75.0")).isOnTrack(true).build();

        given(goalRepository.findById(goalId)).willReturn(Optional.of(goal));
        given(bodyMeasurementRepository.findByUserIdAndMeasuredAtBetweenOrderByMeasuredAtAsc(
                userId, goal.getStartDate(), today))
                .willReturn(measurements);
        given(goalCheckpointRepository.findByGoalIdAndCheckpointDate(goalId, lastSunday))
                .willReturn(Optional.of(existing));

        goalService.maintainCheckpointsForGoal(goalId);

        verify(goalCheckpointRepository, times(0)).saveAll(any());
    }

    @Test
    @DisplayName("약간 뒤처진 경우 trackingStatus 가 SLIGHTLY_BEHIND, trackingColor 가 YELLOW 이다")
    void getGoalProgress_slightlyBehind_returnsSlightlyBehind() {
        Long userId = 1L;
        Long goalId = 10L;
        LocalDate today = LocalDate.now();
        // 경과: 30일 / 총 60일 → 기대 50%, 실제 38% → diff = -12% → SLIGHTLY_BEHIND
        Goal goal = buildWeightGoal(goalId, userId,
                new BigDecimal("80.0"), new BigDecimal("70.0"),
                today.minusDays(30), today.plusDays(30));
        List<BodyMeasurement> measurements = List.of(
                buildMeasurement(userId, today.minusDays(2), 76.2)
        );

        given(goalRepository.findById(goalId)).willReturn(Optional.of(goal));
        given(bodyMeasurementRepository.findByUserIdAndMeasuredAtBetweenOrderByMeasuredAtAsc(
                userId, goal.getStartDate(), today))
                .willReturn(measurements);
        given(goalCheckpointRepository.findByGoalIdOrderByCheckpointDate(goalId))
                .willReturn(List.of());

        GoalProgressResponse response = goalService.getGoalProgress(userId, goalId);

        assertThat(response.getTrackingStatus()).isEqualTo("SLIGHTLY_BEHIND");
        assertThat(response.getTrackingColor()).isEqualTo("YELLOW");
        assertThat(response.getIsOnTrack()).isFalse();
        // diff = 38% - 50% = -12% → SLIGHTLY_BEHIND 범위(-15 ≤ diff < -5) 검증
        assertThat(response.getPercentComplete()).isBetween(30.0, 45.0);
    }

    @Test
    @DisplayName("ENDURANCE 목표는 운동 세션 합산 기반으로 진행률을 계산한다")
    void getGoalProgress_enduranceGoalWithExercise_calculatesProgressFromSessions() {
        Long userId = 1L;
        Long goalId = 10L;
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(14);
        Goal goal = Goal.builder()
                .id(goalId).userId(userId)
                .goalType(GoalType.ENDURANCE).status(Goal.GoalStatus.ACTIVE)
                .startValue(BigDecimal.valueOf(30)).targetValue(BigDecimal.valueOf(60)).targetUnit("minutes")
                .startDate(startDate).targetDate(today.plusDays(50))
                .build();

        // 2주 동안 총 60분 → 주당 평균 30분 (시작값과 동일 → 0% 순진행)
        given(goalRepository.findById(goalId)).willReturn(Optional.of(goal));
        given(exerciseSessionRepository.sumDurationMinutesByUserIdAndDateRange(
                userId, startDate, today)).willReturn(60);
        given(goalCheckpointRepository.findByGoalIdOrderByCheckpointDate(goalId))
                .willReturn(List.of());

        GoalProgressResponse response = goalService.getGoalProgress(userId, goalId);

        assertThat(response).isNotNull();
        assertThat(response.getGoalType()).isEqualTo(GoalType.ENDURANCE);
        assertThat(response.getPercentComplete()).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    @DisplayName("목표 시작일 이후 측정이 없어도 시작일 이전 최근 측정으로 진행률을 계산한다")
    void getGoalProgress_noMeasurementsAfterStartDate_fallsBackToLatestPriorMeasurement() {
        Long userId = 1L;
        Long goalId = 12L;
        LocalDate today = LocalDate.now();
        // 목표는 어제 생성, 이후 측정 0건. 하지만 3개월치 이력은 보유.
        LocalDate startDate = today.minusDays(1);
        Goal goal = Goal.builder()
                .id(goalId).userId(userId)
                .goalType(GoalType.WEIGHT_LOSS).status(Goal.GoalStatus.ACTIVE)
                .startValue(BigDecimal.valueOf(80.0)).targetValue(BigDecimal.valueOf(70.0)).targetUnit("kg")
                .startDate(startDate).targetDate(today.plusMonths(3))
                .build();

        given(goalRepository.findById(goalId)).willReturn(Optional.of(goal));
        // 시작일 이후 측정은 비어 있음
        given(bodyMeasurementRepository.findByUserIdAndMeasuredAtBetweenOrderByMeasuredAtAsc(
                userId, startDate, today)).willReturn(List.of());
        // fallback — 가장 최근 측정
        BodyMeasurement priorLatest = buildMeasurement(userId, today.minusDays(5), 78.2);
        given(bodyMeasurementRepository.findFirstByUserIdAndMeasuredAtLessThanEqualOrderByMeasuredAtDesc(
                userId, today)).willReturn(Optional.of(priorLatest));
        given(goalCheckpointRepository.findByGoalIdOrderByCheckpointDate(goalId))
                .willReturn(List.of());

        GoalProgressResponse response = goalService.getGoalProgress(userId, goalId);

        assertThat(response).isNotNull();
        assertThat(response.getCurrentValue()).isEqualByComparingTo("78.2");
    }

    @Test
    @DisplayName("ENDURANCE 목표는 운동 기록이 0건이면 NPE 없이 0분 평균으로 계산된다")
    void getGoalProgress_enduranceGoalWithNoSessions_returnsZeroAverage() {
        Long userId = 1L;
        Long goalId = 11L;
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(7);
        Goal goal = Goal.builder()
                .id(goalId).userId(userId)
                .goalType(GoalType.ENDURANCE).status(Goal.GoalStatus.ACTIVE)
                .startValue(BigDecimal.valueOf(0)).targetValue(BigDecimal.valueOf(60)).targetUnit("minutes")
                .startDate(startDate).targetDate(today.plusDays(30))
                .build();

        given(goalRepository.findById(goalId)).willReturn(Optional.of(goal));
        // SUM 결과가 null인 경우(운동 기록 0건) — 과거에 NPE 유발하던 회귀 케이스
        given(exerciseSessionRepository.sumDurationMinutesByUserIdAndDateRange(
                userId, startDate, today)).willReturn(null);
        given(goalCheckpointRepository.findByGoalIdOrderByCheckpointDate(goalId))
                .willReturn(List.of());

        GoalProgressResponse response = goalService.getGoalProgress(userId, goalId);

        assertThat(response).isNotNull();
        assertThat(response.getCurrentValue()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("maintainCheckpointsForGoal — 체크포인트 on-track 여부는 실제값과 예상값 비교로 계산된다")
    @SuppressWarnings("unchecked")
    void maintainCheckpointsForGoal_checkpointOnTrack_comparesActualAndProjected() {
        Long userId = 1L;
        Long goalId = 10L;
        LocalDate today = LocalDate.now();
        Goal goal = buildWeightGoal(goalId, userId,
                new BigDecimal("80.0"), new BigDecimal("70.0"),
                today.minusDays(20), today.plusDays(40));
        LocalDate firstSunday = goal.getStartDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        List<BodyMeasurement> measurements = List.of(
                buildMeasurement(userId, firstSunday, 80.5),
                buildMeasurement(userId, today, 75.0)
        );

        given(goalRepository.findById(goalId)).willReturn(Optional.of(goal));
        given(bodyMeasurementRepository.findByUserIdAndMeasuredAtBetweenOrderByMeasuredAtAsc(
                userId, goal.getStartDate(), today))
                .willReturn(measurements);
        given(goalCheckpointRepository.findByGoalIdAndCheckpointDate(any(), any()))
                .willReturn(Optional.empty());

        goalService.maintainCheckpointsForGoal(goalId);

        ArgumentCaptor<List<GoalCheckpoint>> captor = ArgumentCaptor.forClass(List.class);
        verify(goalCheckpointRepository).saveAll(captor.capture());
        GoalCheckpoint firstCheckpoint = captor.getValue().stream()
                .filter(checkpoint -> checkpoint.getCheckpointDate().equals(firstSunday))
                .findFirst()
                .orElseThrow();

        assertThat(firstCheckpoint.getActualValue()).isEqualByComparingTo("80.5");
        assertThat(firstCheckpoint.getProjectedValue()).isNotNull();
        assertThat(firstCheckpoint.getIsOnTrack()).isFalse();
    }

    @Test
    @DisplayName("다른 사용자의 목표 진행률 조회 시 UnauthorizedException 발생")
    void getGoalProgress_otherUserGoal_throwsUnauthorizedException() {
        Long goalId = 10L;
        Goal goal = buildGoal(goalId, 99L, GoalType.WEIGHT_LOSS, GoalStatus.ACTIVE);
        given(goalRepository.findById(goalId)).willReturn(Optional.of(goal));

        assertThatThrownBy(() -> goalService.getGoalProgress(1L, goalId))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("존재하지 않는 목표 진행률 조회 시 ResourceNotFoundException 발생")
    void getGoalProgress_goalNotFound_throwsResourceNotFoundException() {
        given(goalRepository.findById(9999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> goalService.getGoalProgress(1L, 9999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─────────────────────────── 헬퍼 ───────────────────────────

    private Goal buildGoal(Long id, Long userId, GoalType goalType, GoalStatus status) {
        return Goal.builder()
                .id(id).userId(userId).goalType(goalType).status(status)
                .targetValue(new BigDecimal("15.0")).targetUnit("pct")
                .targetDate(LocalDate.now().plusMonths(6))
                .startValue(new BigDecimal("18.5"))
                .startDate(LocalDate.now())
                .weeklyRateTarget(new BigDecimal("-0.25"))
                .build();
    }

    private CreateGoalRequest buildCreateRequest(GoalType goalType, BigDecimal targetValue,
            String targetUnit, LocalDate targetDate, BigDecimal startValue, BigDecimal weeklyRateTarget) {
        return CreateGoalRequest.builder()
                .goalType(goalType).targetValue(targetValue).targetUnit(targetUnit)
                .targetDate(targetDate).startValue(startValue).weeklyRateTarget(weeklyRateTarget)
                .build();
    }

    private User buildUser(Long id) {
        return User.builder()
                .id(id).email("test@example.com").passwordHash("hash")
                .displayName("Tester").build();
    }

    private Goal buildWeightGoal(Long id, Long userId, BigDecimal startValue, BigDecimal targetValue,
            LocalDate startDate, LocalDate targetDate) {
        return Goal.builder()
                .id(id).userId(userId)
                .goalType(GoalType.WEIGHT_LOSS).status(GoalStatus.ACTIVE)
                .startValue(startValue).targetValue(targetValue).targetUnit("kg")
                .startDate(startDate).targetDate(targetDate)
                .weeklyRateTarget(new BigDecimal("-0.5"))
                .build();
    }

    private BodyMeasurement buildMeasurement(Long userId, LocalDate measuredAt, double weightKg) {
        return BodyMeasurement.builder()
                .id(1L).userId(userId)
                .measuredAt(measuredAt)
                .weightKg(weightKg)
                .build();
    }
}
