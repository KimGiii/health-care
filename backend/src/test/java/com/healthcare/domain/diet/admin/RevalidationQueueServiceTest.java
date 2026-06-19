package com.healthcare.domain.diet.admin;

import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.entity.FoodCatalogSource;
import com.healthcare.domain.diet.entity.RecommendationStatus;
import com.healthcare.domain.diet.policy.DataFreshnessPolicy;
import com.healthcare.domain.diet.repository.FoodCatalogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("RevalidationQueueService")
class RevalidationQueueServiceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-06-19T00:00:00Z");

    @Mock
    private FoodCatalogRepository foodCatalogRepository;

    private RevalidationQueueService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-06-19T00:00:00Z"), ZoneOffset.UTC);
        service = new RevalidationQueueService(foodCatalogRepository, new DataFreshnessPolicy(clock));
    }

    @Test
    @DisplayName("자격이 회수된 항목은 REVOKED_STALE_FACTS 사유로 큐에 포함된다")
    void queue_includesRevokedItems() {
        FoodCatalog revoked = food(1L, "회수된 메뉴", FoodCatalogSource.BRAND_OFFICIAL,
                RecommendationStatus.SEARCH_ONLY, FoodCatalog.STALE_FACTS_REVALIDATION_REASON, NOW, 30L);
        given(foodCatalogRepository.findByRecommendationReason(FoodCatalog.STALE_FACTS_REVALIDATION_REASON))
                .willReturn(List.of(revoked));
        given(foodCatalogRepository.findAll(any(Specification.class), any(Sort.class)))
                .willReturn(List.of());

        List<RevalidationQueueEntry> queue = service.queue(50);

        assertThat(queue).hasSize(1);
        assertThat(queue.get(0).foodCatalogId()).isEqualTo(1L);
        assertThat(queue.get(0).reason()).isEqualTo(RevalidationReason.REVOKED_STALE_FACTS);
    }

    @Test
    @DisplayName("신선도가 만료된 추천 후보는 DATA_EXPIRED 사유로 큐에 포함되고, 신선한 후보는 제외된다")
    void queue_includesExpiredCandidatesOnly() {
        FoodCatalog expired = food(2L, "오래된 브랜드 메뉴", FoodCatalogSource.BRAND_OFFICIAL,
                RecommendationStatus.RECOMMENDABLE, null, NOW.minusYears(2), 10L); // 1년 만료창 초과
        FoodCatalog fresh = food(3L, "최근 검수 메뉴", FoodCatalogSource.BRAND_OFFICIAL,
                RecommendationStatus.RECOMMENDABLE, null, NOW.minusDays(10), 99L);
        given(foodCatalogRepository.findByRecommendationReason(FoodCatalog.STALE_FACTS_REVALIDATION_REASON))
                .willReturn(List.of());
        given(foodCatalogRepository.findAll(any(Specification.class), any(Sort.class)))
                .willReturn(List.of(expired, fresh));

        List<RevalidationQueueEntry> queue = service.queue(50);

        assertThat(queue).hasSize(1);
        assertThat(queue.get(0).foodCatalogId()).isEqualTo(2L);
        assertThat(queue.get(0).reason()).isEqualTo(RevalidationReason.DATA_EXPIRED);
    }

    @Test
    @DisplayName("회수 항목이 만료 항목보다 먼저 정렬되고, 같은 사유 안에서는 usageCount 내림차순이다")
    void queue_sortsRevokedFirstThenByUsage() {
        FoodCatalog revoked = food(1L, "회수", FoodCatalogSource.BRAND_OFFICIAL,
                RecommendationStatus.SEARCH_ONLY, FoodCatalog.STALE_FACTS_REVALIDATION_REASON, NOW, 5L);
        FoodCatalog expiredHigh = food(2L, "만료-인기", FoodCatalogSource.MFDS_STANDARD_DISH,
                RecommendationStatus.RECOMMENDABLE, null, NOW.minusYears(3), 80L);
        FoodCatalog expiredLow = food(3L, "만료-비인기", FoodCatalogSource.MFDS_STANDARD_DISH,
                RecommendationStatus.RECOMMENDABLE, null, NOW.minusYears(3), 20L);
        given(foodCatalogRepository.findByRecommendationReason(FoodCatalog.STALE_FACTS_REVALIDATION_REASON))
                .willReturn(List.of(revoked));
        given(foodCatalogRepository.findAll(any(Specification.class), any(Sort.class)))
                .willReturn(List.of(expiredLow, expiredHigh));

        List<RevalidationQueueEntry> queue = service.queue(50);

        assertThat(queue).extracting(RevalidationQueueEntry::foodCatalogId)
                .containsExactly(1L, 2L, 3L);
    }

    @Test
    @DisplayName("limit으로 큐 길이를 제한한다")
    void queue_respectsLimit() {
        FoodCatalog revoked = food(1L, "회수", FoodCatalogSource.BRAND_OFFICIAL,
                RecommendationStatus.SEARCH_ONLY, FoodCatalog.STALE_FACTS_REVALIDATION_REASON, NOW, 5L);
        FoodCatalog expired = food(2L, "만료", FoodCatalogSource.MFDS_STANDARD_DISH,
                RecommendationStatus.RECOMMENDABLE, null, NOW.minusYears(3), 80L);
        given(foodCatalogRepository.findByRecommendationReason(FoodCatalog.STALE_FACTS_REVALIDATION_REASON))
                .willReturn(List.of(revoked));
        given(foodCatalogRepository.findAll(any(Specification.class), any(Sort.class)))
                .willReturn(List.of(expired));

        List<RevalidationQueueEntry> queue = service.queue(1);

        assertThat(queue).hasSize(1);
        assertThat(queue.get(0).foodCatalogId()).isEqualTo(1L);
    }

    private FoodCatalog food(Long id, String name, FoodCatalogSource source,
                             RecommendationStatus status, String reason,
                             OffsetDateTime lastVerifiedAt, long usageCount) {
        return FoodCatalog.builder()
                .id(id)
                .name(name)
                .nameKo(name)
                .category(FoodCategory.PROTEIN_SOURCE)
                .caloriesPer100g(165.0)
                .source(source)
                .recommendationStatus(status)
                .recommendationReason(reason)
                .lastVerifiedAt(lastVerifiedAt)
                .usageCount(usageCount)
                .isCustom(false)
                .build();
    }
}
