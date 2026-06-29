package com.healthcare.domain.diet.admin;

import com.healthcare.domain.diet.allergen.repository.FoodAllergenTagRepository;
import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.entity.FoodCatalogSource;
import com.healthcare.domain.diet.entity.FoodServingOption;
import com.healthcare.domain.diet.entity.FoodServingOption.ServingType;
import com.healthcare.domain.diet.entity.RecommendationStatus;
import com.healthcare.domain.diet.repository.FoodCatalogRepository;
import com.healthcare.domain.diet.repository.FoodServingOptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("RecommendationCurationQueueService")
class RecommendationCurationQueueServiceTest {

    @Mock private FoodCatalogRepository foodCatalogRepository;
    @Mock private FoodServingOptionRepository servingOptionRepository;
    @Mock private FoodAllergenTagRepository allergenTagRepository;

    private RecommendationCurationQueueService service;

    @BeforeEach
    void setUp() {
        service = new RecommendationCurationQueueService(
                foodCatalogRepository,
                servingOptionRepository,
                allergenTagRepository);
    }

    @Test
    @DisplayName("SEARCH_ONLY 중 macro complete와 검증 제공량을 갖춘 row가 먼저 노출된다")
    @SuppressWarnings("unchecked")
    void queue_prioritizesEngineReadySearchOnlyRows() {
        FoodCatalog ready = food(1L, "준비됨", FoodCatalogSource.MFDS_FOOD_NUTRIENT_DB, true, 10L);
        FoodCatalog noServing = food(2L, "제공량없음", FoodCatalogSource.MFDS_FOOD_NUTRIENT_DB, true, 500L);
        FoodCatalog incomplete = food(3L, "매크로부족", FoodCatalogSource.MFDS_FOOD_NUTRIENT_DB, false, 900L);
        FoodCatalog custom = food(4L, "커스텀", FoodCatalogSource.USER_CUSTOM, true, 1_000L);
        given(foodCatalogRepository.findAll(any(Specification.class), any(Sort.class)))
                .willReturn(List.of(incomplete, noServing, custom, ready));
        given(servingOptionRepository.findByFoodCatalogIdIn(List.of(3L, 2L, 1L)))
                .willReturn(List.of(servingOption(1L), servingOption(3L)));
        given(allergenTagRepository.findFoodIdsWithAnyAllergenTag())
                .willReturn(List.of(1L));

        List<RecommendationCurationQueueEntry> result = service.queue(10);

        assertThat(result).extracting(RecommendationCurationQueueEntry::foodCatalogId)
                .containsExactly(1L, 3L, 2L);
        assertThat(result.get(0).engineReadyCandidate()).isTrue();
        assertThat(result).noneMatch(entry -> entry.source() == FoodCatalogSource.USER_CUSTOM);
    }

    private FoodCatalog food(
            Long id,
            String name,
            FoodCatalogSource source,
            boolean macroComplete,
            long usageCount
    ) {
        FoodCatalog.FoodCatalogBuilder builder = FoodCatalog.builder()
                .id(id)
                .source(source)
                .foodCode("code-" + id)
                .name(name)
                .nameKo(name)
                .category(FoodCategory.PROTEIN_SOURCE)
                .caloriesPer100g(100.0)
                .recommendationStatus(RecommendationStatus.SEARCH_ONLY)
                .isCustom(source == FoodCatalogSource.USER_CUSTOM)
                .usageCount(usageCount);
        if (macroComplete) {
            builder.proteinPer100g(10.0).carbsPer100g(20.0).fatPer100g(3.0);
        }
        return builder.build();
    }

    private FoodServingOption servingOption(Long foodId) {
        return FoodServingOption.builder()
                .foodCatalogId(foodId)
                .label("1회 제공량")
                .labelKo("1회 제공량")
                .equivalentG(100.0)
                .sortOrder(0)
                .servingType(ServingType.OFFICIAL_SERVING)
                .verifiedAt(OffsetDateTime.now().minusDays(1))
                .build();
    }
}
