package com.healthcare.domain.exercise.service;

import com.healthcare.common.exception.ResourceNotFoundException;
import com.healthcare.domain.exercise.dto.CatalogSearchParams;
import com.healthcare.domain.exercise.dto.CreateCustomExerciseRequest;
import com.healthcare.domain.exercise.dto.ExerciseCatalogResponse;
import com.healthcare.domain.exercise.entity.ExerciseCatalog;
import com.healthcare.domain.exercise.entity.ExerciseCatalog.ExerciseType;
import com.healthcare.domain.exercise.entity.ExerciseCatalog.MuscleGroup;
import com.healthcare.domain.exercise.repository.ExerciseCatalogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * RED: 아직 ExerciseCatalogService, ExerciseCatalogRepository, DTO 클래스가 없으므로
 *      이 테스트들은 컴파일 실패 상태입니다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ExerciseCatalogService 단위 테스트")
class ExerciseCatalogServiceTest {

    @Mock
    private ExerciseCatalogRepository catalogRepository;

    @InjectMocks
    private ExerciseCatalogService catalogService;

    // ─────────────────────────── 카탈로그 조회 ───────────────────────────

    @Test
    @DisplayName("필터 없이 조회 시 글로벌 + 사용자 커스텀 운동을 반환한다")
    void searchCatalog_noFilter_returnsGlobalAndUserCustomExercises() {
        // given
        Long userId = 1L;
        ExerciseCatalog benchPress = buildGlobalExercise(1L, "Bench Press", "벤치 프레스",
                MuscleGroup.CHEST, ExerciseType.STRENGTH, 5.0);
        ExerciseCatalog customExercise = buildCustomExercise(2L, "My Pullover", MuscleGroup.BACK,
                ExerciseType.STRENGTH, userId);

        given(catalogRepository.findAccessibleToUser(userId, null, null, null, false))
                .willReturn(List.of(benchPress, customExercise));

        // when
        List<ExerciseCatalogResponse> result = catalogService.searchCatalog(
                userId, CatalogSearchParams.of(null, null, null, false));

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("Bench Press");
        assertThat(result.get(0).isCustom()).isFalse();
        assertThat(result.get(1).getName()).isEqualTo("My Pullover");
        assertThat(result.get(1).isCustom()).isTrue();
    }

    @Test
    @DisplayName("exerciseType 필터로 조회 시 해당 타입만 반환한다")
    void searchCatalog_withExerciseTypeFilter_returnsOnlyMatchingType() {
        // given
        Long userId = 1L;
        ExerciseCatalog squat = buildGlobalExercise(3L, "Squat", "스쿼트",
                MuscleGroup.QUADRICEPS, ExerciseType.STRENGTH, 6.0);

        given(catalogRepository.findAccessibleToUser(userId, null, ExerciseType.STRENGTH, null, false))
                .willReturn(List.of(squat));

        // when
        List<ExerciseCatalogResponse> result = catalogService.searchCatalog(
                userId, CatalogSearchParams.of(null, ExerciseType.STRENGTH, null, false));

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getExerciseType()).isEqualTo(ExerciseType.STRENGTH);
    }

    @Test
    @DisplayName("customOnly=true 조회 시 해당 사용자의 커스텀 운동만 반환한다")
    void searchCatalog_customOnly_returnsOnlyUserCustomExercises() {
        // given
        Long userId = 1L;
        ExerciseCatalog customExercise = buildCustomExercise(10L, "Custom Run", MuscleGroup.CARDIO,
                ExerciseType.CARDIO, userId);

        given(catalogRepository.findAccessibleToUser(userId, null, null, null, true))
                .willReturn(List.of(customExercise));

        // when
        List<ExerciseCatalogResponse> result = catalogService.searchCatalog(
                userId, CatalogSearchParams.of(null, null, null, true));

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).isCustom()).isTrue();
        assertThat(result.get(0).getCreatedByUserId()).isEqualTo(userId);
    }

    // ─────────────────────────── 커스텀 운동 생성 ───────────────────────────

    @Test
    @DisplayName("커스텀 운동 생성 성공 시 저장된 운동을 반환한다")
    void createCustomExercise_success_returnsCreatedExercise() {
        // given
        Long userId = 1L;
        CreateCustomExerciseRequest request = CreateCustomExerciseRequest.builder()
                .name("Wall Sit")
                .nameKo("월 싯")
                .muscleGroup(MuscleGroup.QUADRICEPS)
                .exerciseType(ExerciseType.BODYWEIGHT)
                .metValue(null)
                .build();

        ExerciseCatalog saved = buildCustomExercise(99L, "Wall Sit", MuscleGroup.QUADRICEPS,
                ExerciseType.BODYWEIGHT, userId);

        given(catalogRepository.save(any(ExerciseCatalog.class))).willReturn(saved);

        // when
        ExerciseCatalogResponse result = catalogService.createCustomExercise(userId, request);

        // then
        assertThat(result.getId()).isEqualTo(99L);
        assertThat(result.getName()).isEqualTo("Wall Sit");
        assertThat(result.isCustom()).isTrue();
        assertThat(result.getCreatedByUserId()).isEqualTo(userId);

        // 저장되는 엔티티가 isCustom=true, createdByUserId=userId 인지 확인
        ArgumentCaptor<ExerciseCatalog> captor = ArgumentCaptor.forClass(ExerciseCatalog.class);
        verify(catalogRepository).save(captor.capture());
        ExerciseCatalog capturedEntity = captor.getValue();
        assertThat(capturedEntity.getIsCustom()).isTrue();
        assertThat(capturedEntity.getCreatedByUserId()).isEqualTo(userId);
    }

    @Test
    @DisplayName("다른 사용자의 커스텀 운동 ID로 커스텀 운동을 생성할 때 본인 소유로 저장된다")
    void createCustomExercise_alwaysAssignsOwnerToCurrentUser() {
        // given
        Long userId = 42L;
        CreateCustomExerciseRequest request = CreateCustomExerciseRequest.builder()
                .name("Plank Variation")
                .muscleGroup(MuscleGroup.CORE)
                .exerciseType(ExerciseType.BODYWEIGHT)
                .build();

        ExerciseCatalog saved = buildCustomExercise(200L, "Plank Variation", MuscleGroup.CORE,
                ExerciseType.BODYWEIGHT, userId);
        given(catalogRepository.save(any(ExerciseCatalog.class))).willReturn(saved);

        // when
        catalogService.createCustomExercise(userId, request);

        // then — 저장 시 createdByUserId = 현재 userId 로 고정
        ArgumentCaptor<ExerciseCatalog> captor = ArgumentCaptor.forClass(ExerciseCatalog.class);
        verify(catalogRepository).save(captor.capture());
        assertThat(captor.getValue().getCreatedByUserId()).isEqualTo(userId);
    }

    // ─────────────────────────── 헬퍼 ───────────────────────────

    private ExerciseCatalog buildGlobalExercise(Long id, String name, String nameKo,
            MuscleGroup muscleGroup, ExerciseType exerciseType, Double metValue) {
        return ExerciseCatalog.builder()
                .id(id).name(name).nameKo(nameKo)
                .muscleGroup(muscleGroup).exerciseType(exerciseType)
                .metValue(metValue).isCustom(false).createdByUserId(null)
                .build();
    }

    private ExerciseCatalog buildCustomExercise(Long id, String name, MuscleGroup muscleGroup,
            ExerciseType exerciseType, Long createdByUserId) {
        return ExerciseCatalog.builder()
                .id(id).name(name)
                .muscleGroup(muscleGroup).exerciseType(exerciseType)
                .metValue(null).isCustom(true).createdByUserId(createdByUserId)
                .build();
    }
}
