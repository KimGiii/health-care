package com.healthcare.domain.goals.service;

import com.healthcare.common.exception.BusinessRuleViolationException;
import com.healthcare.common.exception.ResourceNotFoundException;
import com.healthcare.common.exception.UnauthorizedException;
import com.healthcare.domain.bodymeasurement.entity.BodyMeasurement;
import com.healthcare.domain.bodymeasurement.repository.BodyMeasurementRepository;
import com.healthcare.domain.goals.dto.*;
import com.healthcare.domain.goals.entity.Goal;
import com.healthcare.domain.goals.entity.Goal.GoalStatus;
import com.healthcare.domain.goals.entity.Goal.GoalType;
import com.healthcare.domain.goals.entity.GoalCheckpoint;
import com.healthcare.domain.goals.repository.GoalCheckpointRepository;
import com.healthcare.domain.goals.repository.GoalRepository;
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
import java.time.LocalDate;
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
                new BigDecimal("15.0"), "pct",
                LocalDate.now().plusMonths(6),
                new BigDecimal("18.5"), new BigDecimal("-0.25"));

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
                .build();

        given(goalRepository.findById(goalId)).willReturn(Optional.of(goal));
        given(goalRepository.save(any(Goal.class))).willReturn(goal);

        GoalResponse response = goalService.updateGoal(userId, goalId, request);

        assertThat(response.getGoalId()).isEqualTo(goalId);
        verify(goalRepository).save(any(Goal.class));
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
    @DisplayName("신체 측정 기록이 있는 경우 진행률이 계산된다 (체중 감량 목표 50% 진행)")
    void getGoalProgress_withBodyMeasurement_calculatesPercent() {
        Long userId = 1L;
        Long goalId = 10L;
        // 시작: 80kg, 목표: 70kg (-10kg), 현재: 75kg → 50% 진행
        Goal goal = buildWeightGoal(goalId, userId,
                new BigDecimal("80.0"), new BigDecimal("70.0"),
                LocalDate.now().minusDays(30), LocalDate.now().plusDays(30));
        BodyMeasurement measurement = buildMeasurement(userId, 75.0);

        given(goalRepository.findById(goalId)).willReturn(Optional.of(goal));
        given(bodyMeasurementRepository.findFirstByUserIdOrderByMeasuredAtDesc(userId))
                .willReturn(Optional.of(measurement));
        given(goalCheckpointRepository.findByGoalIdAndCheckpointDate(any(), any()))
                .willReturn(Optional.empty());
        given(goalCheckpointRepository.save(any(GoalCheckpoint.class))).willAnswer(inv -> inv.getArgument(0));
        given(goalCheckpointRepository.findByGoalIdOrderByCheckpointDate(goalId))
                .willReturn(List.of());

        GoalProgressResponse response = goalService.getGoalProgress(userId, goalId);

        assertThat(response.getPercentComplete()).isEqualTo(50.0);
        assertThat(response.getCurrentValue()).isEqualByComparingTo(new BigDecimal("75.0"));
        assertThat(response.getDaysRemaining()).isEqualTo(30L);
    }

    @Test
    @DisplayName("신체 측정 기록이 없으면 현재값이 시작값과 동일하고 진행률은 0%이다")
    void getGoalProgress_noBodyMeasurement_zeroPercent() {
        Long userId = 1L;
        Long goalId = 10L;
        Goal goal = buildWeightGoal(goalId, userId,
                new BigDecimal("80.0"), new BigDecimal("70.0"),
                LocalDate.now().minusDays(10), LocalDate.now().plusDays(50));

        given(goalRepository.findById(goalId)).willReturn(Optional.of(goal));
        given(bodyMeasurementRepository.findFirstByUserIdOrderByMeasuredAtDesc(userId))
                .willReturn(Optional.empty());
        given(goalCheckpointRepository.findByGoalIdOrderByCheckpointDate(goalId))
                .willReturn(List.of());

        GoalProgressResponse response = goalService.getGoalProgress(userId, goalId);

        assertThat(response.getPercentComplete()).isEqualTo(0.0);
        assertThat(response.getCurrentValue()).isEqualByComparingTo(new BigDecimal("80.0"));
    }

    @Test
    @DisplayName("목표보다 빠르게 진행 중이면 trackingStatus 가 AHEAD 이다")
    void getGoalProgress_aheadOfSchedule_returnsAhead() {
        Long userId = 1L;
        Long goalId = 10L;
        // 10일 경과(총 60일), 기대 진행률 ~16%, 실제 80%
        Goal goal = buildWeightGoal(goalId, userId,
                new BigDecimal("80.0"), new BigDecimal("70.0"),
                LocalDate.now().minusDays(10), LocalDate.now().plusDays(50));
        BodyMeasurement measurement = buildMeasurement(userId, 72.0); // -8kg / -10kg = 80%

        given(goalRepository.findById(goalId)).willReturn(Optional.of(goal));
        given(bodyMeasurementRepository.findFirstByUserIdOrderByMeasuredAtDesc(userId))
                .willReturn(Optional.of(measurement));
        given(goalCheckpointRepository.findByGoalIdAndCheckpointDate(any(), any()))
                .willReturn(Optional.empty());
        given(goalCheckpointRepository.save(any(GoalCheckpoint.class))).willAnswer(inv -> inv.getArgument(0));
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
        // 50일 경과(총 60일), 기대 진행률 ~83%, 실제 0%
        Goal goal = buildWeightGoal(goalId, userId,
                new BigDecimal("80.0"), new BigDecimal("70.0"),
                LocalDate.now().minusDays(50), LocalDate.now().plusDays(10));
        BodyMeasurement measurement = buildMeasurement(userId, 80.0); // 진행 없음

        given(goalRepository.findById(goalId)).willReturn(Optional.of(goal));
        given(bodyMeasurementRepository.findFirstByUserIdOrderByMeasuredAtDesc(userId))
                .willReturn(Optional.of(measurement));
        given(goalCheckpointRepository.findByGoalIdAndCheckpointDate(any(), any()))
                .willReturn(Optional.empty());
        given(goalCheckpointRepository.save(any(GoalCheckpoint.class))).willAnswer(inv -> inv.getArgument(0));
        given(goalCheckpointRepository.findByGoalIdOrderByCheckpointDate(goalId))
                .willReturn(List.of());

        GoalProgressResponse response = goalService.getGoalProgress(userId, goalId);

        assertThat(response.getTrackingStatus()).isEqualTo("BEHIND");
        assertThat(response.getTrackingColor()).isEqualTo("RED");
        assertThat(response.getIsOnTrack()).isFalse();
    }

    @Test
    @DisplayName("이미 오늘 체크포인트가 있으면 중복 저장하지 않는다")
    void getGoalProgress_checkpointAlreadyExists_noNewSave() {
        Long userId = 1L;
        Long goalId = 10L;
        Goal goal = buildWeightGoal(goalId, userId,
                new BigDecimal("80.0"), new BigDecimal("70.0"),
                LocalDate.now().minusDays(10), LocalDate.now().plusDays(50));
        BodyMeasurement measurement = buildMeasurement(userId, 75.0);
        GoalCheckpoint existing = GoalCheckpoint.builder()
                .goalId(goalId).checkpointDate(LocalDate.now())
                .actualValue(new BigDecimal("75.0")).isOnTrack(true).build();

        given(goalRepository.findById(goalId)).willReturn(Optional.of(goal));
        given(bodyMeasurementRepository.findFirstByUserIdOrderByMeasuredAtDesc(userId))
                .willReturn(Optional.of(measurement));
        given(goalCheckpointRepository.findByGoalIdAndCheckpointDate(goalId, LocalDate.now()))
                .willReturn(Optional.of(existing));
        given(goalCheckpointRepository.findByGoalIdOrderByCheckpointDate(goalId))
                .willReturn(List.of(existing));

        goalService.getGoalProgress(userId, goalId);

        verify(goalCheckpointRepository, times(0)).save(any());
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

    private BodyMeasurement buildMeasurement(Long userId, double weightKg) {
        return BodyMeasurement.builder()
                .id(1L).userId(userId)
                .measuredAt(LocalDate.now())
                .weightKg(weightKg)
                .build();
    }
}
