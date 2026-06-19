package com.healthcare.domain.diet.admin;

import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.policy.DataFreshnessPolicy;
import com.healthcare.domain.diet.repository.FoodCatalogRepository;
import com.healthcare.domain.diet.repository.FoodCatalogSpecs;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 재검증 큐. 추천 자격을 잃었거나 데이터가 만료되어 운영자 재검증이 필요한 항목을 모은다.
 *
 * <ul>
 *   <li>{@link RevalidationReason#REVOKED_STALE_FACTS} — source 사실 변경으로 자격이 회수된 항목 (Phase 5 Unit 1)</li>
 *   <li>{@link RevalidationReason#DATA_EXPIRED} — 신선도 정책상 만료된 추천 후보</li>
 * </ul>
 *
 * <p>정렬은 사유 우선순위(회수 먼저) → 사용 빈도(usageCount) 내림차순이다.
 * 회수 항목은 {@code SEARCH_ONLY}라 추천 후보 만료 집합과 겹치지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RevalidationQueueService {

    private final FoodCatalogRepository foodCatalogRepository;
    private final DataFreshnessPolicy dataFreshnessPolicy;

    public List<RevalidationQueueEntry> queue(int limit) {
        Stream<RevalidationQueueEntry> revoked = foodCatalogRepository
                .findByRecommendationReason(FoodCatalog.STALE_FACTS_REVALIDATION_REASON)
                .stream()
                .map(food -> toEntry(food, RevalidationReason.REVOKED_STALE_FACTS));

        Stream<RevalidationQueueEntry> expired = foodCatalogRepository
                .findAll(FoodCatalogSpecs.hasRecommendationCandidateStatus(), Sort.unsorted())
                .stream()
                .filter(food -> !dataFreshnessPolicy.isCurrent(food))
                .map(food -> toEntry(food, RevalidationReason.DATA_EXPIRED));

        return Stream.concat(revoked, expired)
                .sorted(Comparator
                        .comparing(RevalidationQueueEntry::reason)
                        .thenComparing(Comparator.comparingLong(RevalidationQueueEntry::usageCount).reversed()))
                .limit(limit)
                .toList();
    }

    private RevalidationQueueEntry toEntry(FoodCatalog food, RevalidationReason reason) {
        return new RevalidationQueueEntry(
                food.getId(),
                food.getName(),
                food.getCategory(),
                food.getSource(),
                reason,
                food.getLastVerifiedAt(),
                food.getUsageCount() == null ? 0L : food.getUsageCount());
    }
}
