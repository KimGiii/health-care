package com.healthcare.domain.diet.admin;

import com.healthcare.domain.diet.allergen.entity.FoodAllergenProfile;
import com.healthcare.domain.diet.allergen.repository.FoodAllergenProfileRepository;
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
    @Mock private FoodAllergenProfileRepository allergenProfileRepository;

    private RecommendationCurationQueueService service;

    @BeforeEach
    void setUp() {
        service = new RecommendationCurationQueueService(
                foodCatalogRepository,
                servingOptionRepository,
                allergenTagRepository,
                allergenProfileRepository);
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
        given(allergenProfileRepository.findByFoodCatalogIdIn(List.of(3L, 2L, 1L)))
                .willReturn(List.of());

        List<RecommendationCurationQueueEntry> result = service.queue(10);

        assertThat(result).extracting(RecommendationCurationQueueEntry::foodCatalogId)
                .containsExactly(1L, 2L, 3L);
        assertThat(result.get(0).engineReadyCandidate()).isTrue();
        assertThat(result.get(0).curationLane()).isEqualTo("A_ENGINE_READY_HIGH_PROTEIN");
        assertThat(result).noneMatch(entry -> entry.source() == FoodCatalogSource.USER_CUSTOM);
    }

    @Test
    @DisplayName("engine-ready 후보 안에서는 고단백·단백질 밀도 높은 row를 먼저 노출한다")
    @SuppressWarnings("unchecked")
    void queue_prioritizesHighProteinRowsWithinEngineReadyCandidates() {
        FoodCatalog highProtein = food(
                1L, "닭가슴살", FoodCatalogSource.MFDS_FOOD_NUTRIENT_DB,
                FoodCategory.PROTEIN_SOURCE, 165.0, 31.0, true, 10L);
        FoodCatalog popularSnack = food(
                2L, "인기과자", FoodCatalogSource.MFDS_FOOD_NUTRIENT_DB,
                FoodCategory.PROCESSED, 480.0, 6.0, true, 5_000L);
        given(foodCatalogRepository.findAll(any(Specification.class), any(Sort.class)))
                .willReturn(List.of(popularSnack, highProtein));
        given(servingOptionRepository.findByFoodCatalogIdIn(List.of(2L, 1L)))
                .willReturn(List.of(servingOption(1L), servingOption(2L)));
        given(allergenTagRepository.findFoodIdsWithAnyAllergenTag())
                .willReturn(List.of());
        given(allergenProfileRepository.findByFoodCatalogIdIn(List.of(2L, 1L)))
                .willReturn(List.of());

        List<RecommendationCurationQueueEntry> result = service.queue(10);

        assertThat(result).extracting(RecommendationCurationQueueEntry::foodCatalogId)
                .containsExactly(1L, 2L);
        assertThat(result.get(0).proteinGPer100Kcal()).isEqualTo(18.8);
        assertThat(result.get(0).curationLane()).isEqualTo("A_ENGINE_READY_HIGH_PROTEIN");
        assertThat(result.get(1).curationLane()).isEqualTo("A_ENGINE_READY");
    }

    @Test
    @DisplayName("이미 추천 후보지만 알러젠 프로필이 없는 고단백 row를 SEARCH_ONLY보다 먼저 노출한다")
    @SuppressWarnings("unchecked")
    void queue_prioritizesRecommendableHighProteinProfileGaps() {
        FoodCatalog profileGap = food(
                1L, "닭가슴살", FoodCatalogSource.MFDS_STANDARD_PROCESSED,
                FoodCategory.PROTEIN_SOURCE, 110.0, 24.0, true, 10L,
                RecommendationStatus.RECOMMENDABLE);
        FoodCatalog searchOnlyReady = food(
                2L, "검색전용두부", FoodCatalogSource.MFDS_STANDARD_PROCESSED,
                FoodCategory.PROCESSED, 80.0, 8.0, true, 1_000L,
                RecommendationStatus.SEARCH_ONLY);
        FoodCatalog alreadyProfiled = food(
                3L, "검증완료참치", FoodCatalogSource.MFDS_STANDARD_PROCESSED,
                FoodCategory.PROTEIN_SOURCE, 130.0, 28.0, true, 2_000L,
                RecommendationStatus.RECOMMENDABLE);
        given(foodCatalogRepository.findAll(any(Specification.class), any(Sort.class)))
                .willReturn(List.of(searchOnlyReady, alreadyProfiled, profileGap));
        given(servingOptionRepository.findByFoodCatalogIdIn(List.of(2L, 3L, 1L)))
                .willReturn(List.of(servingOption(1L), servingOption(2L), servingOption(3L)));
        given(allergenTagRepository.findFoodIdsWithAnyAllergenTag())
                .willReturn(List.of());
        given(allergenProfileRepository.findByFoodCatalogIdIn(List.of(2L, 3L, 1L)))
                .willReturn(List.of(verifiedProfile(3L)));

        List<RecommendationCurationQueueEntry> result = service.queue(10);

        assertThat(result).extracting(RecommendationCurationQueueEntry::foodCatalogId)
                .containsExactly(1L, 2L);
        assertThat(result.get(0).curationLane()).isEqualTo("A_PROFILE_GAP_HIGH_PROTEIN");
        assertThat(result.get(0).allergenProfileGap()).isTrue();
        assertThat(result).noneMatch(entry -> entry.foodCatalogId().equals(3L));
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

    private FoodCatalog food(
            Long id,
            String name,
            FoodCatalogSource source,
            FoodCategory category,
            double caloriesPer100g,
            double proteinPer100g,
            boolean macroComplete,
            long usageCount
    ) {
        return food(id, name, source, category, caloriesPer100g, proteinPer100g,
                macroComplete, usageCount, RecommendationStatus.SEARCH_ONLY);
    }

    private FoodCatalog food(
            Long id,
            String name,
            FoodCatalogSource source,
            FoodCategory category,
            double caloriesPer100g,
            double proteinPer100g,
            boolean macroComplete,
            long usageCount,
            RecommendationStatus recommendationStatus
    ) {
        FoodCatalog.FoodCatalogBuilder builder = FoodCatalog.builder()
                .id(id)
                .source(source)
                .foodCode("code-" + id)
                .name(name)
                .nameKo(name)
                .category(category)
                .caloriesPer100g(caloriesPer100g)
                .recommendationStatus(recommendationStatus)
                .isCustom(source == FoodCatalogSource.USER_CUSTOM)
                .usageCount(usageCount);
        if (macroComplete) {
            builder.proteinPer100g(proteinPer100g).carbsPer100g(20.0).fatPer100g(3.0);
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

    private FoodAllergenProfile verifiedProfile(Long foodId) {
        return FoodAllergenProfile.builder()
                .foodCatalogId(foodId)
                .confidenceLevel(com.healthcare.domain.diet.allergen.AllergenConfidenceLevel.LABEL_DERIVED)
                .source(com.healthcare.domain.diet.allergen.AllergenDataSource.FOODQR)
                .standardVersion("test")
                .reviewedAt(OffsetDateTime.now().minusDays(1))
                .build();
    }
}
