package com.healthcare.domain.diet.admin;

import com.healthcare.domain.diet.allergen.repository.FoodAllergenTagRepository;
import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.entity.FoodCatalogSource;
import com.healthcare.domain.diet.entity.RecommendationStatus;
import com.healthcare.domain.diet.repository.FoodCatalogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
@DisplayName("VerificationPriorityService 단위 테스트")
class VerificationPriorityServiceTest {

    @Mock private FoodCatalogRepository foodCatalogRepository;
    @Mock private FoodAllergenTagRepository foodAllergenTagRepository;

    private VerificationPriorityService service;

    @BeforeEach
    void setUp() {
        service = new VerificationPriorityService(foodCatalogRepository, foodAllergenTagRepository);
    }

    @Test
    @DisplayName("macro가 불완전한 인기 식품이 완전한 식품보다 우선순위가 높다")
    @SuppressWarnings("unchecked")
    void topPriorities_macroIncomplete_rankedHigher() {
        FoodCatalog incomplete = food(1L, "불완전", FoodCategory.GRAIN, 100L, false);
        FoodCatalog complete = food(2L, "완전", FoodCategory.GRAIN, 100L, true);
        given(foodCatalogRepository.findAll(any(Specification.class), any(Sort.class)))
                .willReturn(List.of(complete, incomplete));
        given(foodAllergenTagRepository.findFoodIdsWithAnyAllergenTag()).willReturn(List.of());

        List<VerificationPriorityEntry> result = service.topPriorities(10);

        assertThat(result.get(0).foodCatalogId()).isEqualTo(1L); // incomplete 먼저
    }

    @Test
    @DisplayName("allergenTag가 없는 인기 식품이 태그 있는 식품보다 우선순위가 높다")
    @SuppressWarnings("unchecked")
    void topPriorities_untagged_rankedHigher() {
        FoodCatalog untagged = food(1L, "미태깅", FoodCategory.GRAIN, 100L, true);
        FoodCatalog tagged = food(2L, "태깅됨", FoodCategory.GRAIN, 100L, true);
        given(foodCatalogRepository.findAll(any(Specification.class), any(Sort.class)))
                .willReturn(List.of(tagged, untagged));
        given(foodAllergenTagRepository.findFoodIdsWithAnyAllergenTag())
                .willReturn(List.of(2L)); // tagged만 태그 있음

        List<VerificationPriorityEntry> result = service.topPriorities(10);

        assertThat(result.get(0).foodCatalogId()).isEqualTo(1L); // untagged 먼저
    }

    @Test
    @DisplayName("후보가 부족한 카테고리의 식품이 같은 조건의 풍부한 카테고리 식품보다 우선순위가 높다")
    @SuppressWarnings("unchecked")
    void topPriorities_underrepresentedCategory_rankedHigher() {
        // FRUIT 후보 1개(부족) vs GRAIN 후보 4개(충분). 나머지 조건은 동일.
        FoodCatalog scarceFruit = food(10L, "사과", FoodCategory.FRUIT, 100L, true);
        List<FoodCatalog> foods = List.of(
                food(1L, "쌀밥", FoodCategory.GRAIN, 100L, true),
                food(2L, "현미밥", FoodCategory.GRAIN, 100L, true),
                food(3L, "보리밥", FoodCategory.GRAIN, 100L, true),
                food(4L, "잡곡밥", FoodCategory.GRAIN, 100L, true),
                scarceFruit
        );
        given(foodCatalogRepository.findAll(any(Specification.class), any(Sort.class)))
                .willReturn(foods);
        given(foodAllergenTagRepository.findFoodIdsWithAnyAllergenTag()).willReturn(List.of());

        List<VerificationPriorityEntry> result = service.topPriorities(10);

        assertThat(result.get(0).foodCatalogId()).isEqualTo(10L); // 부족한 FRUIT 먼저
    }

    @Test
    @DisplayName("limit 파라미터로 결과 수를 제한한다")
    @SuppressWarnings("unchecked")
    void topPriorities_limitsResult() {
        given(foodCatalogRepository.findAll(any(Specification.class), any(Sort.class)))
                .willReturn(List.of(
                        food(1L, "A", FoodCategory.GRAIN, 50L, false),
                        food(2L, "B", FoodCategory.GRAIN, 40L, false),
                        food(3L, "C", FoodCategory.GRAIN, 30L, false)
                ));
        given(foodAllergenTagRepository.findFoodIdsWithAnyAllergenTag()).willReturn(List.of());

        List<VerificationPriorityEntry> result = service.topPriorities(2);

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("우선순위 항목에 식품 이름·카테고리·점수가 포함된다")
    @SuppressWarnings("unchecked")
    void topPriorities_entryContainsExpectedFields() {
        given(foodCatalogRepository.findAll(any(Specification.class), any(Sort.class)))
                .willReturn(List.of(food(1L, "닭가슴살", FoodCategory.PROTEIN_SOURCE, 200L, false)));
        given(foodAllergenTagRepository.findFoodIdsWithAnyAllergenTag()).willReturn(List.of());

        List<VerificationPriorityEntry> result = service.topPriorities(10);

        assertThat(result).singleElement().satisfies(entry -> {
            assertThat(entry.foodCatalogId()).isEqualTo(1L);
            assertThat(entry.name()).isEqualTo("닭가슴살");
            assertThat(entry.category()).isEqualTo(FoodCategory.PROTEIN_SOURCE);
            assertThat(entry.priorityScore()).isPositive();
        });
    }

    private FoodCatalog food(Long id, String name, FoodCategory category,
                             long usageCount, boolean macroComplete) {
        FoodCatalog.FoodCatalogBuilder builder = FoodCatalog.builder()
                .id(id).name(name).category(category)
                .caloriesPer100g(100.0)
                .source(FoodCatalogSource.SEED)
                .recommendationStatus(RecommendationStatus.RECOMMENDABLE)
                .isCustom(false).usageCount(usageCount);
        if (macroComplete) {
            builder.proteinPer100g(10.0).carbsPer100g(20.0).fatPer100g(3.0);
        }
        return builder.build();
    }
}
