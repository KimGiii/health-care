package com.healthcare.domain.bodymeasurement.service;

import com.healthcare.common.exception.ResourceNotFoundException;
import com.healthcare.common.exception.UnauthorizedException;
import com.healthcare.domain.bodymeasurement.dto.*;
import com.healthcare.domain.bodymeasurement.entity.BodyMeasurement;
import com.healthcare.domain.bodymeasurement.repository.BodyMeasurementRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("BodyMeasurementService 단위 테스트")
class BodyMeasurementServiceTest {

    @Mock
    private BodyMeasurementRepository measurementRepository;

    @InjectMocks
    private BodyMeasurementService measurementService;

    // ─────────────────────────── 측정 기록 생성 ───────────────────────────

    @Test
    @DisplayName("정상 요청으로 측정 기록 생성 시 저장된 기록 반환")
    void createMeasurement_validRequest_returnsSavedResponse() {
        Long userId = 1L;
        CreateMeasurementRequest request = buildCreateRequest(LocalDate.now(), 75.5, 18.0, null);
        BodyMeasurement saved = buildMeasurement(10L, userId, LocalDate.now(), 75.5, 18.0);
        given(measurementRepository.save(any(BodyMeasurement.class))).willReturn(saved);

        MeasurementResponse response = measurementService.createMeasurement(userId, request);

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getWeightKg()).isEqualTo(75.5);
        assertThat(response.getBodyFatPct()).isEqualTo(18.0);
        assertThat(response.getMeasuredAt()).isEqualTo(LocalDate.now());
        verify(measurementRepository).save(any(BodyMeasurement.class));
    }

    @Test
    @DisplayName("체중만 입력해도 측정 기록 생성 성공 (모든 필드 선택사항)")
    void createMeasurement_onlyWeightProvided_savesSuccessfully() {
        Long userId = 1L;
        CreateMeasurementRequest request = buildCreateRequest(LocalDate.now(), 68.0, null, null);
        BodyMeasurement saved = buildMeasurement(11L, userId, LocalDate.now(), 68.0, null);
        given(measurementRepository.save(any(BodyMeasurement.class))).willReturn(saved);

        MeasurementResponse response = measurementService.createMeasurement(userId, request);

        assertThat(response.getWeightKg()).isEqualTo(68.0);
        assertThat(response.getBodyFatPct()).isNull();
    }

    @Test
    @DisplayName("측정 기록 생성 시 userId와 measuredAt이 엔티티에 정확히 설정된다")
    void createMeasurement_capturesUserIdAndDate() {
        Long userId = 42L;
        LocalDate today = LocalDate.now();
        CreateMeasurementRequest request = buildCreateRequest(today, 80.0, 22.5, null);
        BodyMeasurement saved = buildMeasurement(1L, userId, today, 80.0, 22.5);
        given(measurementRepository.save(any(BodyMeasurement.class))).willReturn(saved);

        measurementService.createMeasurement(userId, request);

        ArgumentCaptor<BodyMeasurement> captor = ArgumentCaptor.forClass(BodyMeasurement.class);
        verify(measurementRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(42L);
        assertThat(captor.getValue().getMeasuredAt()).isEqualTo(today);
    }

    // ─────────────────────────── 목록 조회 (페이징) ───────────────────────────

    @Test
    @DisplayName("측정 기록 목록 조회 시 페이지네이션 결과 반환")
    void listMeasurements_returnsPaginatedResults() {
        Long userId = 1L;
        Pageable pageable = PageRequest.of(0, 20, Sort.by("measuredAt").descending());
        BodyMeasurement m1 = buildMeasurement(1L, userId, LocalDate.now(), 75.0, 18.0);
        BodyMeasurement m2 = buildMeasurement(2L, userId, LocalDate.now().minusDays(7), 76.0, 18.5);
        Page<BodyMeasurement> page = new PageImpl<>(List.of(m1, m2), pageable, 2);
        given(measurementRepository.findByUserId(userId, pageable)).willReturn(page);

        MeasurementListResponse response = measurementService.listMeasurements(userId, pageable);

        assertThat(response.content()).hasSize(2);
        assertThat(response.totalElements()).isEqualTo(2);
        assertThat(response.first()).isTrue();
        assertThat(response.last()).isTrue();
    }

    @Test
    @DisplayName("측정 기록이 없는 사용자 조회 시 빈 목록 반환")
    void listMeasurements_noData_returnsEmptyList() {
        Long userId = 1L;
        Pageable pageable = PageRequest.of(0, 20);
        Page<BodyMeasurement> emptyPage = new PageImpl<>(List.of(), pageable, 0);
        given(measurementRepository.findByUserId(userId, pageable)).willReturn(emptyPage);

        MeasurementListResponse response = measurementService.listMeasurements(userId, pageable);

        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isZero();
    }

    // ─────────────────────────── 날짜 범위 조회 ───────────────────────────

    @Test
    @DisplayName("날짜 범위 조회 시 해당 기간 측정 기록만 반환")
    void listMeasurementsByDateRange_returnsFilteredResults() {
        Long userId = 1L;
        LocalDate from = LocalDate.now().minusDays(30);
        LocalDate to = LocalDate.now();
        BodyMeasurement m = buildMeasurement(1L, userId, LocalDate.now().minusDays(15), 74.0, 17.5);
        given(measurementRepository.findByUserIdAndDateRange(userId, from, to)).willReturn(List.of(m));

        List<MeasurementResponse> response = measurementService.listMeasurementsByDateRange(userId, from, to);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).getWeightKg()).isEqualTo(74.0);
        verify(measurementRepository).findByUserIdAndDateRange(eq(userId), eq(from), eq(to));
    }

    @Test
    @DisplayName("날짜 범위에 해당하는 기록 없으면 빈 목록 반환")
    void listMeasurementsByDateRange_noData_returnsEmptyList() {
        Long userId = 1L;
        LocalDate from = LocalDate.of(2023, 1, 1);
        LocalDate to = LocalDate.of(2023, 1, 31);
        given(measurementRepository.findByUserIdAndDateRange(userId, from, to)).willReturn(List.of());

        List<MeasurementResponse> response = measurementService.listMeasurementsByDateRange(userId, from, to);

        assertThat(response).isEmpty();
    }

    // ─────────────────────────── 최근 기록 조회 ───────────────────────────

    @Test
    @DisplayName("최근 측정 기록 조회 성공")
    void getLatestMeasurement_success_returnsMostRecentRecord() {
        Long userId = 1L;
        BodyMeasurement latest = buildMeasurement(5L, userId, LocalDate.now(), 73.0, 17.0);
        given(measurementRepository.findFirstByUserIdOrderByMeasuredAtDesc(userId))
                .willReturn(Optional.of(latest));

        MeasurementResponse response = measurementService.getLatestMeasurement(userId);

        assertThat(response.getId()).isEqualTo(5L);
        assertThat(response.getWeightKg()).isEqualTo(73.0);
        assertThat(response.getMeasuredAt()).isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName("측정 기록이 없을 때 최근 기록 조회 시 ResourceNotFoundException 발생")
    void getLatestMeasurement_noData_throwsResourceNotFoundException() {
        Long userId = 1L;
        given(measurementRepository.findFirstByUserIdOrderByMeasuredAtDesc(userId))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> measurementService.getLatestMeasurement(userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─────────────────────────── 단건 조회 ───────────────────────────

    @Test
    @DisplayName("본인 측정 기록 단건 조회 성공")
    void getMeasurementById_ownRecord_returnsResponse() {
        Long userId = 1L;
        Long measurementId = 10L;
        BodyMeasurement m = buildMeasurement(measurementId, userId, LocalDate.now(), 75.0, 18.0);
        given(measurementRepository.findById(measurementId)).willReturn(Optional.of(m));

        MeasurementResponse response = measurementService.getMeasurementById(userId, measurementId);

        assertThat(response.getId()).isEqualTo(measurementId);
    }

    @Test
    @DisplayName("존재하지 않는 측정 기록 조회 시 ResourceNotFoundException 발생")
    void getMeasurementById_notFound_throwsResourceNotFoundException() {
        given(measurementRepository.findById(9999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> measurementService.getMeasurementById(1L, 9999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("다른 사용자의 측정 기록 조회 시 UnauthorizedException 발생")
    void getMeasurementById_otherUserRecord_throwsUnauthorizedException() {
        Long measurementId = 10L;
        BodyMeasurement m = buildMeasurement(measurementId, 99L, LocalDate.now(), 75.0, 18.0);
        given(measurementRepository.findById(measurementId)).willReturn(Optional.of(m));

        assertThatThrownBy(() -> measurementService.getMeasurementById(1L, measurementId))
                .isInstanceOf(UnauthorizedException.class);
    }

    // ─────────────────────────── 수정 ───────────────────────────

    @Test
    @DisplayName("본인 측정 기록 체중 수정 성공")
    void updateMeasurement_ownRecord_updatesAndReturns() {
        Long userId = 1L;
        Long measurementId = 10L;
        BodyMeasurement m = buildMeasurement(measurementId, userId, LocalDate.now(), 75.0, 18.0);
        UpdateMeasurementRequest request = buildUpdateRequest(72.5, null);
        given(measurementRepository.findById(measurementId)).willReturn(Optional.of(m));
        given(measurementRepository.save(any(BodyMeasurement.class))).willReturn(
                buildMeasurement(measurementId, userId, LocalDate.now(), 72.5, 18.0));

        MeasurementResponse response = measurementService.updateMeasurement(userId, measurementId, request);

        assertThat(response.getWeightKg()).isEqualTo(72.5);
        verify(measurementRepository).save(any(BodyMeasurement.class));
    }

    @Test
    @DisplayName("수정 시 null 필드는 기존 값 유지")
    void updateMeasurement_nullFields_preservesExistingValues() {
        Long userId = 1L;
        Long measurementId = 10L;
        BodyMeasurement m = buildMeasurement(measurementId, userId, LocalDate.now(), 75.0, 18.0);
        UpdateMeasurementRequest request = buildUpdateRequest(null, 17.5);
        given(measurementRepository.findById(measurementId)).willReturn(Optional.of(m));
        given(measurementRepository.save(any(BodyMeasurement.class))).willReturn(
                buildMeasurement(measurementId, userId, LocalDate.now(), 75.0, 17.5));

        MeasurementResponse response = measurementService.updateMeasurement(userId, measurementId, request);

        assertThat(response.getWeightKg()).isEqualTo(75.0);
        assertThat(response.getBodyFatPct()).isEqualTo(17.5);
    }

    @Test
    @DisplayName("다른 사용자의 측정 기록 수정 시 UnauthorizedException 발생")
    void updateMeasurement_otherUserRecord_throwsUnauthorizedException() {
        Long measurementId = 10L;
        BodyMeasurement m = buildMeasurement(measurementId, 99L, LocalDate.now(), 75.0, 18.0);
        given(measurementRepository.findById(measurementId)).willReturn(Optional.of(m));

        assertThatThrownBy(() -> measurementService.updateMeasurement(1L, measurementId,
                buildUpdateRequest(70.0, null)))
                .isInstanceOf(UnauthorizedException.class);
    }

    // ─────────────────────────── 삭제 ───────────────────────────

    @Test
    @DisplayName("본인 측정 기록 soft delete 성공 — deletedAt 설정 후 저장")
    void deleteMeasurement_ownRecord_setsDeletedAt() {
        Long userId = 1L;
        Long measurementId = 10L;
        BodyMeasurement m = buildMeasurement(measurementId, userId, LocalDate.now(), 75.0, 18.0);
        given(measurementRepository.findById(measurementId)).willReturn(Optional.of(m));
        given(measurementRepository.save(any(BodyMeasurement.class))).willReturn(m);

        measurementService.deleteMeasurement(userId, measurementId);

        ArgumentCaptor<BodyMeasurement> captor = ArgumentCaptor.forClass(BodyMeasurement.class);
        verify(measurementRepository).save(captor.capture());
        assertThat(captor.getValue().getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("존재하지 않는 측정 기록 삭제 시 ResourceNotFoundException 발생")
    void deleteMeasurement_notFound_throwsResourceNotFoundException() {
        given(measurementRepository.findById(9999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> measurementService.deleteMeasurement(1L, 9999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("다른 사용자의 측정 기록 삭제 시 UnauthorizedException 발생")
    void deleteMeasurement_otherUserRecord_throwsUnauthorizedException() {
        Long measurementId = 10L;
        BodyMeasurement m = buildMeasurement(measurementId, 99L, LocalDate.now(), 75.0, 18.0);
        given(measurementRepository.findById(measurementId)).willReturn(Optional.of(m));

        assertThatThrownBy(() -> measurementService.deleteMeasurement(1L, measurementId))
                .isInstanceOf(UnauthorizedException.class);
    }

    // ─────────────────────────── 헬퍼 ───────────────────────────

    private BodyMeasurement buildMeasurement(Long id, Long userId, LocalDate measuredAt,
                                              Double weightKg, Double bodyFatPct) {
        return BodyMeasurement.builder()
                .id(id)
                .userId(userId)
                .measuredAt(measuredAt)
                .weightKg(weightKg)
                .bodyFatPct(bodyFatPct)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    private CreateMeasurementRequest buildCreateRequest(LocalDate measuredAt,
                                                         Double weightKg, Double bodyFatPct,
                                                         String notes) {
        CreateMeasurementRequest request = new CreateMeasurementRequest();
        setField(request, "measuredAt", measuredAt);
        setField(request, "weightKg", weightKg);
        setField(request, "bodyFatPct", bodyFatPct);
        setField(request, "notes", notes);
        return request;
    }

    private UpdateMeasurementRequest buildUpdateRequest(Double weightKg, Double bodyFatPct) {
        UpdateMeasurementRequest request = new UpdateMeasurementRequest();
        setField(request, "weightKg", weightKg);
        setField(request, "bodyFatPct", bodyFatPct);
        return request;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field: " + fieldName, e);
        }
    }
}
