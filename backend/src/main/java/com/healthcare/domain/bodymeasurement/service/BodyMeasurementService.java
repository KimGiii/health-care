package com.healthcare.domain.bodymeasurement.service;

import com.healthcare.common.exception.ResourceNotFoundException;
import com.healthcare.common.exception.UnauthorizedException;
import com.healthcare.domain.bodymeasurement.dto.*;
import com.healthcare.domain.bodymeasurement.entity.BodyMeasurement;
import com.healthcare.domain.bodymeasurement.repository.BodyMeasurementRepository;
import lombok.RequiredArgsConstructor;
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

    // ─────────────────────────── 측정 기록 생성 ───────────────────────────

    @Transactional
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

        return MeasurementResponse.from(measurementRepository.save(measurement));
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
    public MeasurementResponse updateMeasurement(Long userId, Long measurementId, UpdateMeasurementRequest request) {
        BodyMeasurement measurement = findAndVerifyOwnership(userId, measurementId);
        measurement.update(
                request.getWeightKg(), request.getBodyFatPct(), request.getMuscleMassKg(), request.getBmi(),
                request.getChestCm(), request.getWaistCm(), request.getHipCm(),
                request.getThighCm(), request.getArmCm(), request.getNotes()
        );
        return MeasurementResponse.from(measurementRepository.save(measurement));
    }

    // ─────────────────────────── 측정 기록 삭제 (soft delete) ───────────────────────────

    @Transactional
    public void deleteMeasurement(Long userId, Long measurementId) {
        BodyMeasurement measurement = findAndVerifyOwnership(userId, measurementId);
        measurement.delete();
        measurementRepository.save(measurement);
    }

    // ─────────────────────────── 내부 헬퍼 ───────────────────────────

    private BodyMeasurement findAndVerifyOwnership(Long userId, Long measurementId) {
        BodyMeasurement measurement = measurementRepository.findById(measurementId)
                .orElseThrow(() -> new ResourceNotFoundException("BodyMeasurement", measurementId));
        if (!measurement.isOwnedBy(userId)) {
            throw new UnauthorizedException("다른 사용자의 신체 측정 기록에 접근할 수 없습니다.");
        }
        return measurement;
    }
}
