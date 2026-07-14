package com.healthcare.domain.diet.recommendation.snapshot;

import com.healthcare.domain.diet.entity.DietLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationConversionService {

    private final RecommendationSnapshotRepository snapshotRepository;
    private final RecommendationEventRepository eventRepository;
    private final SnapshotMealsCodec mealsCodec;

    /**
     * 추천 → 기록 전환을 식품 단위로 귀속시킨다(R1 food 매핑).
     *
     * <p>귀속 규칙: 기록 식품 ∩ 스냅샷 식품 교집합에만 RECORDED 이벤트를 fan-out 저장한다
     * — 추천에 없던 식품이 전환 신호를 받는 오귀속을 막는다. 교집합이 비면 food 없는
     * 스냅샷 단위 1행으로 전환 사실만 보존한다.
     *
     * <p>best-effort 계약: 스냅샷이 없으면(90일 보관 만료·잘못된 id) 예외 대신 warn 로그 후
     * 스킵한다. 부가 기능(이벤트 적재)이 핵심 기능(식단 기록)을 깨뜨리지 않는다.
     */
    @Transactional
    public void recordConversion(Long userId, Long snapshotId, DietLog dietLog,
                                 List<Long> recordedFoodIds) {
        RecommendationSnapshot snapshot = snapshotRepository
                .findByIdAndUserId(snapshotId, userId)
                .orElse(null);
        if (snapshot == null) {
            log.warn("추천 전환 스킵 — 스냅샷 없음 또는 소유자 불일치: userId={}, snapshotId={}",
                    userId, snapshotId);
            return;
        }

        dietLog.linkRecommendationSnapshot(snapshotId);

        Set<Long> snapshotFoodIds = new LinkedHashSet<>(
                mealsCodec.extractFoodCatalogIds(snapshot.getMealsJson()));
        List<Long> convertedFoodIds = recordedFoodIds.stream()
                .distinct()
                .filter(snapshotFoodIds::contains)
                .toList();

        List<RecommendationEvent> events = convertedFoodIds.isEmpty()
                ? List.of(RecommendationEvent.recorded(snapshotId, userId, dietLog.getId(), null))
                : convertedFoodIds.stream()
                        .map(foodId -> RecommendationEvent.recorded(
                                snapshotId, userId, dietLog.getId(), foodId))
                        .toList();
        eventRepository.saveAll(events);
    }
}
