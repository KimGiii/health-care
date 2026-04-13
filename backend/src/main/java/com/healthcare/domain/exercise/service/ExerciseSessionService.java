package com.healthcare.domain.exercise.service;

import com.healthcare.common.exception.ResourceNotFoundException;
import com.healthcare.common.exception.UnauthorizedException;
import com.healthcare.domain.exercise.dto.*;
import com.healthcare.domain.exercise.entity.ExerciseCatalog;
import com.healthcare.domain.exercise.entity.ExerciseSession;
import com.healthcare.domain.exercise.entity.ExerciseSession.CalorieEstimateMethod;
import com.healthcare.domain.exercise.entity.ExerciseSet;
import com.healthcare.domain.exercise.entity.ExerciseSet.SetType;
import com.healthcare.domain.exercise.repository.ExerciseCatalogRepository;
import com.healthcare.domain.exercise.repository.ExerciseSessionRepository;
import com.healthcare.domain.exercise.repository.ExerciseSetRepository;
import com.healthcare.domain.user.entity.User;
import com.healthcare.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExerciseSessionService {

    private final ExerciseSessionRepository sessionRepository;
    private final ExerciseSetRepository setRepository;
    private final ExerciseCatalogRepository catalogRepository;
    private final UserRepository userRepository;

    // ─────────────────────────── 세션 생성 ───────────────────────────

    @Transactional
    public CreateSessionResponse createSession(Long userId, CreateSessionRequest request) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        // 요청된 모든 catalogId 를 한 번에 조회 → 접근 불가 항목 검증
        Map<Long, ExerciseCatalog> catalogMap = loadAndValidateCatalogs(userId, request.getSets());

        // 세션 duration 계산
        Integer durationMinutes = calculateDuration(request.getStartedAt(), request.getEndedAt());

        // 세트별 처리: PR 감지 + 볼륨 집계
        List<ExerciseSet> sets = new ArrayList<>();
        List<PersonalRecordInfo> newPrs = new ArrayList<>();
        double totalVolumeKg = 0.0;

        for (CreateSetRequest setReq : request.getSets()) {
            ExerciseCatalog catalog = catalogMap.get(setReq.getExerciseCatalogId());
            boolean isPr = detectPersonalRecord(userId, setReq, newPrs, catalog);

            ExerciseSet set = ExerciseSet.builder()
                    .sessionId(null) // 세션 저장 후 채움
                    .exerciseCatalogId(setReq.getExerciseCatalogId())
                    .setNumber(setReq.getSetNumber())
                    .setType(setReq.getSetType())
                    .weightKg(setReq.getWeightKg())
                    .reps(setReq.getReps())
                    .durationSeconds(setReq.getDurationSeconds())
                    .distanceM(setReq.getDistanceM())
                    .restSeconds(setReq.getRestSeconds())
                    .notes(setReq.getNotes())
                    .isPersonalRecord(isPr)
                    .build();
            sets.add(set);

            if (SetType.WEIGHTED.equals(setReq.getSetType())
                    && setReq.getWeightKg() != null && setReq.getReps() != null) {
                totalVolumeKg += setReq.getWeightKg() * setReq.getReps();
            }
        }

        // 칼로리 계산 (MET 공식)
        CalorieCalculationResult calorieResult = calculateCalories(
                user, request.getSets(), catalogMap, durationMinutes);

        // 세션 저장
        ExerciseSession session = ExerciseSession.builder()
                .userId(userId)
                .sessionDate(request.getSessionDate())
                .startedAt(request.getStartedAt())
                .endedAt(request.getEndedAt())
                .durationMinutes(durationMinutes)
                .totalVolumeKg(totalVolumeKg > 0 ? totalVolumeKg : null)
                .caloriesBurned(calorieResult.calories())
                .calorieEstimateMethod(calorieResult.method())
                .notes(request.getNotes())
                .build();
        ExerciseSession savedSession = sessionRepository.save(session);

        // 세트에 sessionId 할당 후 저장
        List<ExerciseSet> setsWithSessionId = sets.stream()
                .map(s -> ExerciseSet.builder()
                        .sessionId(savedSession.getId())
                        .exerciseCatalogId(s.getExerciseCatalogId())
                        .setNumber(s.getSetNumber())
                        .setType(s.getSetType())
                        .weightKg(s.getWeightKg())
                        .reps(s.getReps())
                        .durationSeconds(s.getDurationSeconds())
                        .distanceM(s.getDistanceM())
                        .restSeconds(s.getRestSeconds())
                        .notes(s.getNotes())
                        .isPersonalRecord(s.getIsPersonalRecord())
                        .build())
                .toList();
        setRepository.saveAll(setsWithSessionId);

        return CreateSessionResponse.builder()
                .sessionId(savedSession.getId())
                .sessionDate(savedSession.getSessionDate())
                .durationMinutes(savedSession.getDurationMinutes())
                .totalVolumeKg(savedSession.getTotalVolumeKg())
                .caloriesBurned(savedSession.getCaloriesBurned())
                .calorieEstimateMethod(savedSession.getCalorieEstimateMethod())
                .setCount(sets.size())
                .newPersonalRecords(newPrs)
                .build();
    }

    // ─────────────────────────── 세션 단건 조회 ───────────────────────────

    public SessionDetailResponse getSessionById(Long userId, Long sessionId) {
        ExerciseSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("ExerciseSession", sessionId));

        if (!session.isOwnedBy(userId)) {
            throw new UnauthorizedException("다른 사용자의 운동 세션에 접근할 수 없습니다.");
        }

        List<ExerciseSet> sets = setRepository.findBySessionIdOrderBySetNumber(sessionId);
        Map<Long, ExerciseCatalog> catalogMap = sets.stream()
                .map(ExerciseSet::getExerciseCatalogId)
                .distinct()
                .flatMap(cid -> catalogRepository.findById(cid).stream())
                .collect(Collectors.toMap(ExerciseCatalog::getId, c -> c));

        List<SetDetailResponse> setDetails = sets.stream()
                .map(s -> {
                    ExerciseCatalog catalog = catalogMap.get(s.getExerciseCatalogId());
                    String name = catalog != null ? catalog.getName() : null;
                    String nameKo = catalog != null ? catalog.getNameKo() : null;
                    var muscleGroup = catalog != null ? catalog.getMuscleGroup() : null;
                    return SetDetailResponse.from(s, name, nameKo, muscleGroup);
                })
                .toList();

        return SessionDetailResponse.from(session, setDetails);
    }

    // ─────────────────────────── 세션 목록 조회 ───────────────────────────

    public SessionListResponse listSessions(Long userId, LocalDate from, LocalDate to,
            Pageable pageable) {
        Page<ExerciseSession> page = sessionRepository
                .findByUserIdAndDateRange(userId, from, to, pageable);
        return SessionListResponse.from(page);
    }

    // ─────────────────────────── 세션 삭제 (소프트) ───────────────────────────

    @Transactional
    public void deleteSession(Long userId, Long sessionId) {
        ExerciseSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("ExerciseSession", sessionId));

        if (!session.isOwnedBy(userId)) {
            throw new UnauthorizedException("다른 사용자의 운동 세션을 삭제할 수 없습니다.");
        }

        session.softDelete();
        sessionRepository.save(session);
    }

    // ─────────────────────────── 내부 헬퍼 ───────────────────────────

    private Map<Long, ExerciseCatalog> loadAndValidateCatalogs(Long userId,
            List<CreateSetRequest> sets) {
        List<Long> catalogIds = sets.stream()
                .map(CreateSetRequest::getExerciseCatalogId)
                .distinct()
                .toList();

        Map<Long, ExerciseCatalog> catalogMap = new java.util.LinkedHashMap<>();
        for (Long id : catalogIds) {
            ExerciseCatalog catalog = catalogRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("ExerciseCatalog", id));
            // 다른 사용자의 커스텀 운동은 접근 불가
            if (Boolean.TRUE.equals(catalog.getIsCustom())
                    && !userId.equals(catalog.getCreatedByUserId())) {
                throw new ResourceNotFoundException("ExerciseCatalog", id);
            }
            catalogMap.put(id, catalog);
        }
        return catalogMap;
    }

    private boolean detectPersonalRecord(Long userId, CreateSetRequest setReq,
            List<PersonalRecordInfo> newPrs, ExerciseCatalog catalog) {
        if (!SetType.WEIGHTED.equals(setReq.getSetType()) || setReq.getWeightKg() == null) {
            return false;
        }

        Optional<Double> previousMax = setRepository
                .findMaxWeightKgForUserAndExercise(userId, setReq.getExerciseCatalogId());

        boolean isPr = previousMax.isEmpty() || setReq.getWeightKg() > previousMax.get();
        if (isPr) {
            newPrs.add(PersonalRecordInfo.builder()
                    .exerciseCatalogId(catalog.getId())
                    .exerciseName(catalog.getName())
                    .exerciseNameKo(catalog.getNameKo())
                    .weightKg(setReq.getWeightKg())
                    .reps(setReq.getReps())
                    .build());
        }
        return isPr;
    }

    private Integer calculateDuration(OffsetDateTime startedAt, OffsetDateTime endedAt) {
        if (startedAt == null || endedAt == null) return null;
        long minutes = ChronoUnit.MINUTES.between(startedAt, endedAt);
        return minutes > 0 ? (int) minutes : null;
    }

    private CalorieCalculationResult calculateCalories(User user,
            List<CreateSetRequest> sets, Map<Long, ExerciseCatalog> catalogMap,
            Integer durationMinutes) {
        if (durationMinutes == null || user.getWeightKg() == null) {
            return new CalorieCalculationResult(null, CalorieEstimateMethod.NONE);
        }

        // 평균 MET 계산
        double totalMet = sets.stream()
                .mapToDouble(s -> {
                    ExerciseCatalog c = catalogMap.get(s.getExerciseCatalogId());
                    return (c != null && c.getMetValue() != null) ? c.getMetValue() : 0.0;
                })
                .sum();
        long setsWithMet = sets.stream()
                .filter(s -> {
                    ExerciseCatalog c = catalogMap.get(s.getExerciseCatalogId());
                    return c != null && c.getMetValue() != null;
                })
                .count();

        if (setsWithMet == 0) {
            return new CalorieCalculationResult(null, CalorieEstimateMethod.NONE);
        }

        double avgMet = totalMet / setsWithMet;
        double durationHours = durationMinutes / 60.0;
        // MET 공식: calories = MET × weight_kg × duration_hours
        double calories = avgMet * user.getWeightKg() * durationHours;
        return new CalorieCalculationResult(Math.round(calories * 10.0) / 10.0, CalorieEstimateMethod.MET);
    }

    private record CalorieCalculationResult(Double calories, CalorieEstimateMethod method) {}
}
