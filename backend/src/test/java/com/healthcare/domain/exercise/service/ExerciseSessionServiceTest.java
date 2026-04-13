package com.healthcare.domain.exercise.service;

import com.healthcare.common.exception.ResourceNotFoundException;
import com.healthcare.common.exception.UnauthorizedException;
import com.healthcare.domain.exercise.dto.*;
import com.healthcare.domain.exercise.entity.ExerciseCatalog;
import com.healthcare.domain.exercise.entity.ExerciseCatalog.ExerciseType;
import com.healthcare.domain.exercise.entity.ExerciseCatalog.MuscleGroup;
import com.healthcare.domain.exercise.entity.ExerciseSession;
import com.healthcare.domain.exercise.entity.ExerciseSession.CalorieEstimateMethod;
import com.healthcare.domain.exercise.entity.ExerciseSet;
import com.healthcare.domain.exercise.entity.ExerciseSet.SetType;
import com.healthcare.domain.exercise.repository.ExerciseCatalogRepository;
import com.healthcare.domain.exercise.repository.ExerciseSessionRepository;
import com.healthcare.domain.exercise.repository.ExerciseSetRepository;
import com.healthcare.domain.user.entity.User;
import com.healthcare.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * RED: ExerciseSessionService, 관련 Repository, DTO 클래스가 없으므로 컴파일 실패 상태.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ExerciseSessionService 단위 테스트")
class ExerciseSessionServiceTest {

    @Mock private ExerciseSessionRepository sessionRepository;
    @Mock private ExerciseSetRepository setRepository;
    @Mock private ExerciseCatalogRepository catalogRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private ExerciseSessionService sessionService;

    // ─────────────────────────── 세션 생성 ───────────────────────────

    @Test
    @DisplayName("WEIGHTED 세트 2개 생성 시 totalVolumeKg = sum(weight * reps)로 계산된다")
    void createSession_weightedSets_calculatesTotalVolume() {
        // given
        Long userId = 1L;
        ExerciseCatalog benchPress = buildCatalog(42L, ExerciseType.STRENGTH, 5.0);

        CreateSessionRequest request = CreateSessionRequest.builder()
                .sessionDate(LocalDate.of(2026, 4, 9))
                .startedAt(OffsetDateTime.of(2026, 4, 9, 10, 0, 0, 0, ZoneOffset.ofHours(9)))
                .endedAt(OffsetDateTime.of(2026, 4, 9, 11, 5, 0, 0, ZoneOffset.ofHours(9)))
                .sets(List.of(
                        buildWeightedSetRequest(42L, 1, 80.0, 8),
                        buildWeightedSetRequest(42L, 2, 82.5, 6)
                ))
                .build();

        User user = buildUser(userId, 75.0);
        given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(user));
        given(catalogRepository.findById(42L)).willReturn(Optional.of(benchPress));

        ExerciseSession savedSession = buildSavedSession(5821L, userId,
                LocalDate.of(2026, 4, 9), 65,
                // totalVolumeKg = (80*8) + (82.5*6) = 640 + 495 = 1135
                1135.0, 312.0, CalorieEstimateMethod.MET);
        given(sessionRepository.save(any(ExerciseSession.class))).willReturn(savedSession);
        given(setRepository.saveAll(anyList())).willReturn(List.of());

        // when
        CreateSessionResponse response = sessionService.createSession(userId, request);

        // then
        assertThat(response.getSessionId()).isEqualTo(5821L);
        assertThat(response.getTotalVolumeKg()).isEqualTo(1135.0);
        assertThat(response.getDurationMinutes()).isEqualTo(65);

        // 저장된 엔티티의 totalVolumeKg 값 검증
        ArgumentCaptor<ExerciseSession> sessionCaptor = ArgumentCaptor.forClass(ExerciseSession.class);
        verify(sessionRepository).save(sessionCaptor.capture());
        assertThat(sessionCaptor.getValue().getTotalVolumeKg()).isEqualTo(1135.0);
    }

    @Test
    @DisplayName("이전 최고 중량을 초과하는 WEIGHTED 세트는 isPersonalRecord=true 로 저장된다")
    void createSession_newMaxWeight_marksSetAsPersonalRecord() {
        // given
        Long userId = 1L;
        ExerciseCatalog benchPress = buildCatalog(42L, ExerciseType.STRENGTH, 5.0);

        CreateSessionRequest request = CreateSessionRequest.builder()
                .sessionDate(LocalDate.of(2026, 4, 9))
                .sets(List.of(buildWeightedSetRequest(42L, 1, 85.0, 5)))
                .build();

        User user = buildUser(userId, 75.0);
        given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(user));
        given(catalogRepository.findById(42L)).willReturn(Optional.of(benchPress));
        // 이전 최고 중량은 82.5kg
        given(setRepository.findMaxWeightKgForUserAndExercise(userId, 42L))
                .willReturn(Optional.of(82.5));

        ExerciseSession savedSession = buildSavedSession(5821L, userId,
                LocalDate.of(2026, 4, 9), null, 425.0, null, CalorieEstimateMethod.NONE);
        given(sessionRepository.save(any(ExerciseSession.class))).willReturn(savedSession);
        given(setRepository.saveAll(anyList())).willReturn(List.of());

        // when
        CreateSessionResponse response = sessionService.createSession(userId, request);

        // then — 새 PR이 1개 포함되어야 함
        assertThat(response.getNewPersonalRecords()).hasSize(1);
        assertThat(response.getNewPersonalRecords().get(0).getWeightKg()).isEqualTo(85.0);

        // 저장 시 isPersonalRecord=true 인 세트가 포함되었는지 검증
        ArgumentCaptor<List<ExerciseSet>> setsCaptor = ArgumentCaptor.forClass(List.class);
        verify(setRepository).saveAll(setsCaptor.capture());
        assertThat(setsCaptor.getValue().get(0).getIsPersonalRecord()).isTrue();
    }

    @Test
    @DisplayName("이전 최고 중량 이하의 세트는 PR이 감지되지 않는다")
    void createSession_notNewMaxWeight_doesNotMarkAsPersonalRecord() {
        // given
        Long userId = 1L;
        ExerciseCatalog benchPress = buildCatalog(42L, ExerciseType.STRENGTH, 5.0);

        CreateSessionRequest request = CreateSessionRequest.builder()
                .sessionDate(LocalDate.of(2026, 4, 9))
                .sets(List.of(buildWeightedSetRequest(42L, 1, 80.0, 8)))
                .build();

        User user = buildUser(userId, 75.0);
        given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(user));
        given(catalogRepository.findById(42L)).willReturn(Optional.of(benchPress));
        // 이전 최고 중량이 동일한 80.0kg — 초과 아님
        given(setRepository.findMaxWeightKgForUserAndExercise(userId, 42L))
                .willReturn(Optional.of(80.0));

        ExerciseSession savedSession = buildSavedSession(100L, userId,
                LocalDate.of(2026, 4, 9), null, 640.0, null, CalorieEstimateMethod.NONE);
        given(sessionRepository.save(any(ExerciseSession.class))).willReturn(savedSession);
        given(setRepository.saveAll(anyList())).willReturn(List.of());

        // when
        CreateSessionResponse response = sessionService.createSession(userId, request);

        // then
        assertThat(response.getNewPersonalRecords()).isEmpty();

        ArgumentCaptor<List<ExerciseSet>> setsCaptor = ArgumentCaptor.forClass(List.class);
        verify(setRepository).saveAll(setsCaptor.capture());
        assertThat(setsCaptor.getValue().get(0).getIsPersonalRecord()).isFalse();
    }

    @Test
    @DisplayName("존재하지 않는 exerciseCatalogId 사용 시 ResourceNotFoundException 발생")
    void createSession_invalidCatalogId_throwsResourceNotFoundException() {
        // given
        Long userId = 1L;
        given(userRepository.findByIdAndDeletedAtIsNull(userId))
                .willReturn(Optional.of(buildUser(userId, 70.0)));
        given(catalogRepository.findById(999L)).willReturn(Optional.empty());

        CreateSessionRequest request = CreateSessionRequest.builder()
                .sessionDate(LocalDate.of(2026, 4, 9))
                .sets(List.of(buildWeightedSetRequest(999L, 1, 80.0, 5)))
                .build();

        // when & then
        assertThatThrownBy(() -> sessionService.createSession(userId, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─────────────────────────── 세션 단건 조회 ───────────────────────────

    @Test
    @DisplayName("본인 세션 조회 성공")
    void getSessionById_success_returnsSessionDetail() {
        // given
        Long userId = 1L;
        Long sessionId = 5821L;
        ExerciseSession session = buildSavedSession(sessionId, userId,
                LocalDate.of(2026, 4, 9), 65, 1135.0, 312.0, CalorieEstimateMethod.MET);

        given(sessionRepository.findById(sessionId)).willReturn(Optional.of(session));
        given(setRepository.findBySessionIdOrderBySetNumber(sessionId)).willReturn(List.of());

        // when
        SessionDetailResponse response = sessionService.getSessionById(userId, sessionId);

        // then
        assertThat(response.getSessionId()).isEqualTo(sessionId);
        assertThat(response.getDurationMinutes()).isEqualTo(65);
    }

    @Test
    @DisplayName("다른 사용자의 세션 조회 시 UnauthorizedException 발생")
    void getSessionById_otherUserSession_throwsUnauthorizedException() {
        // given
        Long currentUserId = 1L;
        Long anotherUserId = 99L;
        Long sessionId = 5821L;
        ExerciseSession session = buildSavedSession(sessionId, anotherUserId,
                LocalDate.of(2026, 4, 9), 65, null, null, CalorieEstimateMethod.NONE);

        given(sessionRepository.findById(sessionId)).willReturn(Optional.of(session));

        // when & then
        assertThatThrownBy(() -> sessionService.getSessionById(currentUserId, sessionId))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("존재하지 않는 세션 ID 조회 시 ResourceNotFoundException 발생")
    void getSessionById_notFound_throwsResourceNotFoundException() {
        // given
        given(sessionRepository.findById(9999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> sessionService.getSessionById(1L, 9999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─────────────────────────── 세션 목록 조회 ───────────────────────────

    @Test
    @DisplayName("세션 목록 조회 시 페이지네이션 결과 반환")
    void listSessions_returnsPaginatedResults() {
        // given
        Long userId = 1L;
        Pageable pageable = PageRequest.of(0, 20);
        ExerciseSession session = buildSavedSession(1L, userId,
                LocalDate.of(2026, 4, 9), 60, 1000.0, null, CalorieEstimateMethod.NONE);

        Page<ExerciseSession> page = new PageImpl<>(List.of(session), pageable, 1);
        given(sessionRepository.findByUserIdAndDateRange(userId, null, null, pageable))
                .willReturn(page);

        // when
        SessionListResponse response = sessionService.listSessions(userId, null, null, pageable);

        // then
        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getTotalElements()).isEqualTo(1);
        assertThat(response.isFirst()).isTrue();
    }

    // ─────────────────────────── 세션 삭제 ───────────────────────────

    @Test
    @DisplayName("본인 세션 소프트 삭제 성공")
    void deleteSession_success_softDeletesSession() {
        // given
        Long userId = 1L;
        Long sessionId = 5821L;
        ExerciseSession session = buildSavedSession(sessionId, userId,
                LocalDate.of(2026, 4, 9), 65, null, null, CalorieEstimateMethod.NONE);

        given(sessionRepository.findById(sessionId)).willReturn(Optional.of(session));

        // when
        sessionService.deleteSession(userId, sessionId);

        // then — session.softDelete() 가 호출되어 deletedAt 이 설정되어야 함
        verify(sessionRepository).findById(sessionId);
        // 저장 시 deletedAt 이 null 이 아닌지 확인
        ArgumentCaptor<ExerciseSession> captor = ArgumentCaptor.forClass(ExerciseSession.class);
        verify(sessionRepository).save(captor.capture());
        assertThat(captor.getValue().getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("다른 사용자의 세션 삭제 시 UnauthorizedException 발생")
    void deleteSession_otherUserSession_throwsUnauthorizedException() {
        // given
        Long sessionId = 5821L;
        ExerciseSession session = buildSavedSession(sessionId, 99L,
                LocalDate.of(2026, 4, 9), null, null, null, CalorieEstimateMethod.NONE);
        given(sessionRepository.findById(sessionId)).willReturn(Optional.of(session));

        // when & then
        assertThatThrownBy(() -> sessionService.deleteSession(1L, sessionId))
                .isInstanceOf(UnauthorizedException.class);
    }

    // ─────────────────────────── 헬퍼 ───────────────────────────

    private ExerciseCatalog buildCatalog(Long id, ExerciseType exerciseType, Double metValue) {
        return ExerciseCatalog.builder()
                .id(id).name("Bench Press").nameKo("벤치 프레스")
                .muscleGroup(MuscleGroup.CHEST).exerciseType(exerciseType)
                .metValue(metValue).isCustom(false).build();
    }

    private User buildUser(Long id, Double weightKg) {
        return User.builder()
                .id(id).email("test@example.com").passwordHash("hash")
                .displayName("Tester").weightKg(weightKg).build();
    }

    private CreateSetRequest buildWeightedSetRequest(Long catalogId, int setNumber,
            double weightKg, int reps) {
        return CreateSetRequest.builder()
                .exerciseCatalogId(catalogId)
                .setNumber((short) setNumber)
                .setType(SetType.WEIGHTED)
                .weightKg(weightKg)
                .reps((short) reps)
                .build();
    }

    private ExerciseSession buildSavedSession(Long id, Long userId, LocalDate sessionDate,
            Integer durationMinutes, Double totalVolumeKg, Double caloriesBurned,
            CalorieEstimateMethod method) {
        ExerciseSession session = ExerciseSession.builder()
                .id(id).userId(userId).sessionDate(sessionDate)
                .durationMinutes(durationMinutes).totalVolumeKg(totalVolumeKg)
                .caloriesBurned(caloriesBurned).calorieEstimateMethod(method)
                .build();
        return session;
    }
}
