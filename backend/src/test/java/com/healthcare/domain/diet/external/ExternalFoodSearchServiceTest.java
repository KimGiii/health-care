package com.healthcare.domain.diet.external;

import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.external.client.PublicFoodApiClient;
import com.healthcare.domain.diet.external.dto.ExternalFoodResult;
import com.healthcare.domain.diet.external.dto.ExternalFoodResult.FoodDataSource;
import com.healthcare.domain.diet.external.service.ExternalFoodSearchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExternalFoodSearchService 단위 테스트")
class ExternalFoodSearchServiceTest {

    @Mock
    private PublicFoodApiClient publicFoodApiClient;

    @InjectMocks
    private ExternalFoodSearchService searchService;

    @Test
    @DisplayName("source=PUBLIC_FOOD_API 검색 시 공공데이터 API 결과를 반환한다")
    void search_publicFoodApi_returnsResults() {
        // given
        ExternalFoodResult rice = buildPublicFoodResult("F001", "백미",
                130.0, 2.4, 28.7, 0.3, FoodCategory.GRAIN);

        given(publicFoodApiClient.search("백미", 0, 20))
                .willReturn(List.of(rice));

        // when
        List<ExternalFoodResult> result = searchService.search("백미", FoodDataSource.PUBLIC_FOOD_API, 0, 20);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSource()).isEqualTo(FoodDataSource.PUBLIC_FOOD_API);
        assertThat(result.get(0).getName()).isEqualTo("백미");
        assertThat(result.get(0).getCaloriesPer100g()).isEqualTo(130.0);
    }

    @Test
    @DisplayName("source=ALL 검색 시 공공데이터 API 결과를 반환한다")
    void search_all_returnsPublicFoodApiResults() {
        // given
        ExternalFoodResult chicken = buildPublicFoodResult("F002", "닭가슴살",
                165.0, 31.0, 0.0, 3.6, FoodCategory.PROTEIN_SOURCE);

        given(publicFoodApiClient.search("닭가슴살", 0, 10))
                .willReturn(List.of(chicken));

        // when
        List<ExternalFoodResult> result = searchService.search("닭가슴살", FoodDataSource.ALL, 0, 10);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSource()).isEqualTo(FoodDataSource.PUBLIC_FOOD_API);
    }

    @Test
    @DisplayName("API 호출 실패 시 빈 리스트를 반환한다 (graceful degradation)")
    void search_apiFailure_returnsEmptyList() {
        // given
        given(publicFoodApiClient.search("오류", 0, 20))
                .willThrow(new RuntimeException("API timeout"));

        // when
        List<ExternalFoodResult> result = searchService.search("오류", FoodDataSource.ALL, 0, 20);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("검색 결과가 없으면 빈 리스트를 반환한다")
    void search_noResults_returnsEmptyList() {
        // given
        given(publicFoodApiClient.search("존재하지않는식품", 0, 20))
                .willReturn(List.of());

        // when
        List<ExternalFoodResult> result = searchService.search("존재하지않는식품",
                FoodDataSource.PUBLIC_FOOD_API, 0, 20);

        // then
        assertThat(result).isEmpty();
    }

    // ─────────────────────────── 헬퍼 ───────────────────────────

    private ExternalFoodResult buildPublicFoodResult(String externalId, String name,
            Double calories, Double protein, Double carbs, Double fat, FoodCategory category) {
        return ExternalFoodResult.builder()
                .source(FoodDataSource.PUBLIC_FOOD_API)
                .externalId(externalId)
                .name(name)
                .nameKo(name)
                .category(category)
                .caloriesPer100g(calories)
                .proteinPer100g(protein)
                .carbsPer100g(carbs)
                .fatPer100g(fat)
                .build();
    }
}
