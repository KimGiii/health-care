package com.healthcare.domain.diet.admin;

import com.healthcare.domain.diet.allergen.repository.FoodAllergenTagRepository;
import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.entity.FoodCatalogSource;
import com.healthcare.domain.diet.entity.RecommendationStatus;
import com.healthcare.domain.diet.repository.FoodCatalogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("CandidatePoolSummaryService 단위 테스트")
class CandidatePoolSummaryServiceTest {

    @Mock private FoodCatalogRepository foodCatalogRepository;
    @Mock private FoodAllergenTagRepository foodAllergenTagRepository;

    private CandidatePoolSummaryService service;

    @BeforeEach
    void setUp() {
        service = new CandidatePoolSummaryService(foodCatalogRepository, foodAllergenTagRepository);
    }

    @Nested
    @DisplayName("카테고리별 집계")
    class CategorySummary {

        @Test
        @DisplayName("카테고리별 추천 가능 후보 수가 집계된다")
        @SuppressWarnings("unchecked")
        void summarize_countsByCategory() {
            given(foodCatalogRepository.findAll(any(Specification.class), any(Sort.class)))
                    .willReturn(List.of(
                            food(1L, FoodCategory.GRAIN, true),
                            food(2L, FoodCategory.GRAIN, true),
                            food(3L, FoodCategory.PROTEIN_SOURCE, true)
                    ));
            given(foodAllergenTagRepository.findFoodIdsWithAnyAllergenTag())
                    .willReturn(List.of());

            CandidatePoolSummary summary = service.summarize();

            assertThat(summary.totalCandidates()).isEqualTo(3);
            assertThat(summary.byCategoryCount(FoodCategory.GRAIN)).isEqualTo(2);
            assertThat(summary.byCategoryCount(FoodCategory.PROTEIN_SOURCE)).isEqualTo(1);
        }

        @Test
        @DisplayName("macroDataComplete 식품 수가 집계된다")
        @SuppressWarnings("unchecked")
        void summarize_countsMacroComplete() {
            given(foodCatalogRepository.findAll(any(Specification.class), any(Sort.class)))
                    .willReturn(List.of(
                            food(1L, FoodCategory.GRAIN, true),   // macro complete
                            food(2L, FoodCategory.GRAIN, false),  // macro incomplete
                            food(3L, FoodCategory.PROTEIN_SOURCE, true)
                    ));
            given(foodAllergenTagRepository.findFoodIdsWithAnyAllergenTag())
                    .willReturn(List.of());

            CandidatePoolSummary summary = service.summarize();

            assertThat(summary.macroCompleteTotal()).isEqualTo(2);
        }

        @Test
        @DisplayName("allergenTag가 하나도 없는 식품 수를 집계한다")
        @SuppressWarnings("unchecked")
        void summarize_countsUntaggedFoods() {
            given(foodCatalogRepository.findAll(any(Specification.class), any(Sort.class)))
                    .willReturn(List.of(
                            food(1L, FoodCategory.GRAIN, true),
                            food(2L, FoodCategory.GRAIN, true),
                            food(3L, FoodCategory.PROTEIN_SOURCE, true)
                    ));
            given(foodAllergenTagRepository.findFoodIdsWithAnyAllergenTag())
                    .willReturn(List.of(1L)); // food 1만 태그 있음

            CandidatePoolSummary summary = service.summarize();

            assertThat(summary.untaggedTotal()).isEqualTo(2);
        }

        @Test
        @DisplayName("후보가 임계값 미만인 카테고리는 underrepresented로 표시된다")
        @SuppressWarnings("unchecked")
        void summarize_underrepresentedCategories_belowThreshold() {
            given(foodCatalogRepository.findAll(any(Specification.class), any(Sort.class)))
                    .willReturn(List.of(
                            food(1L, FoodCategory.GRAIN, true),
                            food(2L, FoodCategory.GRAIN, true),
                            food(3L, FoodCategory.GRAIN, true),   // GRAIN 3개 → 임계값(3) 충족
                            food(4L, FoodCategory.FRUIT, true)    // FRUIT 1개 → 임계값 미만
                    ));
            given(foodAllergenTagRepository.findFoodIdsWithAnyAllergenTag())
                    .willReturn(List.of());

            CandidatePoolSummary summary = service.summarize();

            assertThat(summary.underrepresentedCategories()).contains(FoodCategory.FRUIT);
            assertThat(summary.underrepresentedCategories()).doesNotContain(FoodCategory.GRAIN);
        }
    }

    private FoodCatalog food(Long id, FoodCategory category, boolean macroComplete) {
        FoodCatalog.FoodCatalogBuilder builder = FoodCatalog.builder()
                .id(id).name("food-" + id).category(category)
                .caloriesPer100g(100.0)
                .source(FoodCatalogSource.SEED)
                .recommendationStatus(RecommendationStatus.RECOMMENDABLE)
                .isCustom(false).usageCount(10L);
        if (macroComplete) {
            builder.proteinPer100g(10.0).carbsPer100g(20.0).fatPer100g(3.0);
        }
        return builder.build();
    }
}
