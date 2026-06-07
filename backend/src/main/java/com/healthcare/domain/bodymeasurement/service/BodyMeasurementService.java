package com.healthcare.domain.bodymeasurement.service;

import com.healthcare.common.exception.ResourceNotFoundException;
import com.healthcare.common.exception.UnauthorizedException;
import com.healthcare.domain.bodymeasurement.dto.*;
import com.healthcare.domain.bodymeasurement.entity.BodyMeasurement;
import com.healthcare.domain.bodymeasurement.repository.BodyMeasurementRepository;
import com.healthcare.domain.user.entity.User;
import com.healthcare.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BodyMeasurementService {

    private final BodyMeasurementRepository measurementRepository;
    private final UserRepository userRepository;

    // ─────────────────────────── 측정 기록 생성 ───────────────────────────

    @Transactional
    @CacheEvict(cacheNames = "userProfile", key = "#userId")
    public MeasurementResponse createMeasurement(Long userId, CreateMeasurementRequest request) {
        BodyMeasurement measurement = BodyMeasurement.builder()
                .userId(userId)
                .measuredAt(request.getMeasuredAt())
                .weightKg(request.getWeightKg())
                .bodyFatPct(request.getBodyFatPct())
                .muscleMassKg(request.getMuscleMassKg())
                .bmi(request.getBmi())
                .chestCm(request.getChestCm())
                .waistCm(request.getWaistCm())
                .hipCm(request.getHipCm())
                .thighCm(request.getThighCm())
                .armCm(request.getArmCm())
                .notes(request.getNotes())
                .build();

        BodyMeasurement saved = measurementRepository.save(measurement);
        syncUserProfileFromLatestMeasurement(userId);
        return MeasurementResponse.from(saved);
    }

    // ─────────────────────────── 측정 기록 목록 조회 (페이징) ───────────────────────────

    public MeasurementListResponse listMeasurements(Long userId, Pageable pageable) {
        Page<MeasurementResponse> page = measurementRepository
                .findByUserId(userId, pageable)
                .map(MeasurementResponse::from);
        return MeasurementListResponse.from(page);
    }

    // ─────────────────────────── 날짜 범위 조회 ───────────────────────────

    public List<MeasurementResponse> listMeasurementsByDateRange(Long userId, LocalDate from, LocalDate to) {
        return measurementRepository
                .findByUserIdAndDateRange(userId, from, to)
                .stream()
                .map(MeasurementResponse::from)
                .toList();
    }

    // ─────────────────────────── 최근 측정 기록 단건 조회 ───────────────────────────

    public MeasurementResponse getLatestMeasurement(Long userId) {
        BodyMeasurement measurement = measurementRepository
                .findFirstByUserIdOrderByMeasuredAtDesc(userId)
                .orElseThrow(() -> new ResourceNotFoundException("BodyMeasurement", 0L));
        return MeasurementResponse.from(measurement);
    }

    // ─────────────────────────── 측정 기록 단건 조회 ───────────────────────────

    public MeasurementResponse getMeasurementById(Long userId, Long measurementId) {
        BodyMeasurement measurement = findAndVerifyOwnership(userId, measurementId);
        return MeasurementResponse.from(measurement);
    }

    // ─────────────────────────── 측정 기록 수정 ───────────────────────────

    @Transactional
    @CacheEvict(cacheNames = "userProfile", key = "#userId")
    public MeasurementResponse updateMeasurement(Long userId, Long measurementId, UpdateMeasurementRequest request) {
        BodyMeasurement measurement = findAndVerifyOwnership(userId, measurementId);
        measurement.update(
                request.getWeightKg(), request.getBodyFatPct(), request.getMuscleMassKg(), request.getBmi(),
                request.getChestCm(), request.getWaistCm(), request.getHipCm(),
                request.getThighCm(), request.getArmCm(), request.getNotes()
        );
        BodyMeasurement saved = measurementRepository.save(measurement);
        syncUserProfileFromLatestMeasurement(userId);
        return MeasurementResponse.from(saved);
    }

    // ─────────────────────────── 측정 기록 삭제 (soft delete) ───────────────────────────

    @Transactional
    @CacheEvict(cacheNames = "userProfile", key = "#userId")
    public void deleteMeasurement(Long userId, Long measurementId) {
        BodyMeasurement measurement = findAndVerifyOwnership(userId, measurementId);
        measurement.delete();
        measurementRepository.save(measurement);
        // 최신 측정이 삭제됐을 수 있으므로 사용자 프로필 재동기화.
        syncUserProfileFromLatestMeasurement(userId);
    }

    // ─────────────────────────── 특정 날짜 기준 직전 기록 조회 ───────────────────────────

    public MeasurementResponse getMeasurementAtOrBefore(Long userId, LocalDate referenceDate) {
        return measurementRepository
                .findFirstByUserIdAndMeasuredAtLessThanEqualOrderByMeasuredAtDesc(userId, referenceDate)
                .map(MeasurementResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("BodyMeasurement", 0L));
    }

    // ─────────────────────────── 내부 헬퍼 ───────────────────────────

    /**
     * 가장 최근(soft delete 제외) 측정 기록의 weightKg를 사용자 프로필에 반영.
     * 측정 기록이 없으면 동기화하지 않는다(기존 프로필 보존).
     * 마이페이지 등 사용자 프로필을 조회하는 화면에서 최신 체중이 자동 반영되도록 한다.
     */
    private void syncUserProfileFromLatestMeasurement(Long userId) {
        measurementRepository.findFirstByUserIdOrderByMeasuredAtDesc(userId)
                .ifPresent(latest -> {
                    if (latest.getWeightKg() == null) {
                        return;
                    }
                    userRepository.findByIdAndDeletedAtIsNull(userId)
                            .ifPresent(user -> {
                                user.updateProfile(
                                        null, null, null,
                                        latest.getWeightKg(),
                                        null, null, null, null);
                                userRepository.save(user);
                            });
                });
    }

    private BodyMeasurement findAndVerifyOwnership(Long userId, Long measurementId) {
        BodyMeasurement measurement = measurementRepository.findById(measurementId)
                .orElseThrow(() -> new ResourceNotFoundException("BodyMeasurement", measurementId));
        if (!measurement.isOwnedBy(userId)) {
            throw new UnauthorizedException("다른 사용자의 신체 측정 기록에 접근할 수 없습니다.");
        }
        return measurement;
    }
}
