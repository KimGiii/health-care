package com.healthcare.domain.diet.external;

import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.external.client.OpenFoodFactsClient;
import com.healthcare.domain.diet.external.client.UsdaFoodDataClient;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * RED: ExternalFoodSearchService, Client 인터페이스, DTO가 없으므로 컴파일 실패 상태.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ExternalFoodSearchService 단위 테스트")
class ExternalFoodSearchServiceTest {

    @Mock private UsdaFoodDataClient usdaClient;
    @Mock private OpenFoodFactsClient openFoodFactsClient;

    @InjectMocks
    private ExternalFoodSearchService searchService;

    // ─────────────────────────── USDA 검색 ───────────────────────────

    @Test
    @DisplayName("source=USDA 검색 시 USDA 결과만 반환하고 Open Food Facts는 호출하지 않는다")
    void search_usda_returnsUsdaResultsOnly() {
        // given
        ExternalFoodResult chicken = buildUsdaResult("171705", "Chicken Breast",
                165.0, 31.0, 0.0, 3.6, FoodCategory.PROTEIN_SOURCE);

        given(usdaClient.search("chicken", 0, 20)).willReturn(List.of(chicken));

        // when
        List<ExternalFoodResult> result = searchService.search("chicken", FoodDataSource.USDA, 0, 20);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSource()).isEqualTo(FoodDataSource.USDA);
        assertThat(result.get(0).getName()).isEqualTo("Chicken Breast");
        assertThat(result.get(0).getCaloriesPer100g()).isEqualTo(165.0);
        verify(openFoodFactsClient, never()).search(eq("chicken"), anyInt(), anyInt());
    }

    @Test
    @DisplayName("source=OPEN_FOOD_FACTS 검색 시 OFF 결과만 반환하고 USDA는 호출하지 않는다")
    void search_openFoodFacts_returnsOffResultsOnly() {
        // given
        ExternalFoodResult rice = buildOffResult("3017620425400", "White Rice",
                130.0, 2.4, 28.7, 0.3, FoodCategory.GRAIN);

        given(openFoodFactsClient.search("rice", 0, 20)).willReturn(List.of(rice));

        // when
        List<ExternalFoodResult> result = searchService.search("rice", FoodDataSource.OPEN_FOOD_FACTS, 0, 20);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSource()).isEqualTo(FoodDataSource.OPEN_FOOD_FACTS);
        assertThat(result.get(0).getExternalId()).isEqualTo("3017620425400");
        verify(usdaClient, never()).search(eq("rice"), anyInt(), anyInt());
    }

    @Test
    @DisplayName("source=ALL 검색 시 USDA + Open Food Facts 결과를 합산하여 반환한다")
    void search_all_aggregatesFromBothSources() {
        // given
        ExternalFoodResult usdaFood = buildUsdaResult("171705", "Chicken Breast",
                165.0, 31.0, 0.0, 3.6, FoodCategory.PROTEIN_SOURCE);
        ExternalFoodResult offFood = buildOffResult("3017620425400", "Brown Rice",
                111.0, 2.6, 23.0, 0.9, FoodCategory.GRAIN);

        given(usdaClient.search("food", 0, 10)).willReturn(List.of(usdaFood));
        given(openFoodFactsClient.search("food", 0, 10)).willReturn(List.of(offFood));

        // when
        List<ExternalFoodResult> result = searchService.search("food", FoodDataSource.ALL, 0, 10);

        // then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(ExternalFoodResult::getSource)
                .containsExactlyInAnyOrder(FoodDataSource.USDA, FoodDataSource.OPEN_FOOD_FACTS);
    }

    @Test
    @DisplayName("USDA 호출 실패 시 Open Food Facts 결과만 반환한다 (graceful degradation)")
    void search_usdaFailure_returnsOffResultsOnly() {
        // given
        ExternalFoodResult offFood = buildOffResult("code123", "Salmon",
                208.0, 20.0, 0.0, 13.0, FoodCategory.PROTEIN_SOURCE);

        given(usdaClient.search("salmon", 0, 20)).willThrow(new RuntimeException("USDA API timeout"));
        given(openFoodFactsClient.search("salmon", 0, 20)).willReturn(List.of(offFood));

        // when
        List<ExternalFoodResult> result = searchService.search("salmon", FoodDataSource.ALL, 0, 20);

        // then — USDA 실패해도 OFF 결과는 반환
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSource()).isEqualTo(FoodDataSource.OPEN_FOOD_FACTS);
    }

    @Test
    @DisplayName("Open Food Facts 호출 실패 시 USDA 결과만 반환한다 (graceful degradation)")
    void search_offFailure_returnsUsdaResultsOnly() {
        // given
        ExternalFoodResult usdaFood = buildUsdaResult("171705", "Chicken Breast",
                165.0, 31.0, 0.0, 3.6, FoodCategory.PROTEIN_SOURCE);

        given(usdaClient.search("chicken", 0, 20)).willReturn(List.of(usdaFood));
        given(openFoodFactsClient.search("chicken", 0, 20)).willThrow(new RuntimeException("OFF unavailable"));

        // when
        List<ExternalFoodResult> result = searchService.search("chicken", FoodDataSource.ALL, 0, 20);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSource()).isEqualTo(FoodDataSource.USDA);
    }

    @Test
    @DisplayName("양쪽 API 모두 실패하면 빈 리스트를 반환한다")
    void search_bothFailure_returnsEmptyList() {
        // given
        given(usdaClient.search("xyz", 0, 20)).willThrow(new RuntimeException("USDA down"));
        given(openFoodFactsClient.search("xyz", 0, 20)).willThrow(new RuntimeException("OFF down"));

        // when
        List<ExternalFoodResult> result = searchService.search("xyz", FoodDataSource.ALL, 0, 20);

        // then
        assertThat(result).isEmpty();
    }

    // ─────────────────────────── 바코드 조회 ───────────────────────────

    @Test
    @DisplayName("바코드로 Open Food Facts 식품 조회 성공")
    void findByBarcode_found_returnsResult() {
        // given
        ExternalFoodResult offFood = buildOffResult("3017620425400", "Nutella",
                541.0, 6.3, 57.5, 30.9, FoodCategory.PROCESSED);

        given(openFoodFactsClient.findByBarcode("3017620425400"))
                .willReturn(Optional.of(offFood));

        // when
        Optional<ExternalFoodResult> result = searchService.findByBarcode("3017620425400");

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getExternalId()).isEqualTo("3017620425400");
        assertThat(result.get().getName()).isEqualTo("Nutella");
    }

    @Test
    @DisplayName("바코드 미등록 식품 조회 시 빈 Optional 반환")
    void findByBarcode_notFound_returnsEmpty() {
        // given
        given(openFoodFactsClient.findByBarcode("0000000000000"))
                .willReturn(Optional.empty());

        // when
        Optional<ExternalFoodResult> result = searchService.findByBarcode("0000000000000");

        // then
        assertThat(result).isEmpty();
    }

    // ─────────────────────────── 헬퍼 ───────────────────────────

    private ExternalFoodResult buildUsdaResult(String externalId, String name,
            Double calories, Double protein, Double carbs, Double fat, FoodCategory category) {
        return ExternalFoodResult.builder()
                .source(FoodDataSource.USDA)
                .externalId(externalId)
                .name(name)
                .category(category)
                .caloriesPer100g(calories)
                .proteinPer100g(protein)
                .carbsPer100g(carbs)
                .fatPer100g(fat)
                .build();
    }

    private ExternalFoodResult buildOffResult(String externalId, String name,
            Double calories, Double protein, Double carbs, Double fat, FoodCategory category) {
        return ExternalFoodResult.builder()
                .source(FoodDataSource.OPEN_FOOD_FACTS)
                .externalId(externalId)
                .name(name)
                .category(category)
                .caloriesPer100g(calories)
                .proteinPer100g(protein)
                .carbsPer100g(carbs)
                .fatPer100g(fat)
                .build();
    }
}
