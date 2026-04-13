package com.healthcare.domain.diet.external.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.external.config.ExternalApiProperties;
import com.healthcare.domain.diet.external.dto.ExternalFoodResult;
import com.healthcare.domain.diet.external.dto.ExternalFoodResult.FoodDataSource;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class UsdaFoodDataClientImpl implements UsdaFoodDataClient {

    // USDA FoodData Central 주요 영양소 ID
    private static final int NUTRIENT_ENERGY   = 1008; // kcal
    private static final int NUTRIENT_PROTEIN  = 1003; // g
    private static final int NUTRIENT_FAT      = 1004; // g
    private static final int NUTRIENT_CARBS    = 1005; // g

    @Qualifier("usdaRestClient")
    private final RestClient usdaRestClient;
    private final ExternalApiProperties props;

    @Override
    public List<ExternalFoodResult> search(String query, int page, int size) {
        UsdaSearchResponse response = usdaRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/foods/search")
                        .queryParam("query", query)
                        .queryParam("api_key", props.getUsdaApiKey())
                        .queryParam("pageNumber", page + 1) // USDA는 1-based
                        .queryParam("pageSize", size)
                        .queryParam("dataType", "Foundation,SR Legacy")
                        .build())
                .retrieve()
                .body(UsdaSearchResponse.class);

        if (response == null || response.getFoods() == null) {
            return List.of();
        }

        return response.getFoods().stream()
                .map(this::toExternalResult)
                .filter(Objects::nonNull)
                .toList();
    }

    private ExternalFoodResult toExternalResult(UsdaFood food) {
        if (food.getCaloriesPer100g() == null) return null;

        return ExternalFoodResult.builder()
                .source(FoodDataSource.USDA)
                .externalId(String.valueOf(food.getFdcId()))
                .name(food.getDescription())
                .brand(food.getBrandOwner())
                .category(mapCategory(food.getFoodCategory()))
                .caloriesPer100g(food.getCaloriesPer100g())
                .proteinPer100g(food.getNutrientValue(NUTRIENT_PROTEIN))
                .carbsPer100g(food.getNutrientValue(NUTRIENT_CARBS))
                .fatPer100g(food.getNutrientValue(NUTRIENT_FAT))
                .build();
    }

    private FoodCategory mapCategory(String usdaCategory) {
        if (usdaCategory == null) return FoodCategory.OTHER;
        String cat = usdaCategory.toUpperCase();
        if (cat.contains("POULTRY") || cat.contains("BEEF") || cat.contains("PORK")
                || cat.contains("FISH") || cat.contains("SEAFOOD") || cat.contains("EGG")
                || cat.contains("LEGUME") || cat.contains("NUT") || cat.contains("SEED")) {
            return FoodCategory.PROTEIN_SOURCE;
        }
        if (cat.contains("GRAIN") || cat.contains("CEREAL") || cat.contains("BREAD")
                || cat.contains("RICE") || cat.contains("PASTA")) {
            return FoodCategory.GRAIN;
        }
        if (cat.contains("VEGETABLE")) return FoodCategory.VEGETABLE;
        if (cat.contains("FRUIT"))     return FoodCategory.FRUIT;
        if (cat.contains("DAIRY") || cat.contains("MILK") || cat.contains("CHEESE")) {
            return FoodCategory.DAIRY;
        }
        if (cat.contains("FAT") || cat.contains("OIL"))  return FoodCategory.FAT;
        if (cat.contains("BEVERAGE") || cat.contains("DRINK")) return FoodCategory.BEVERAGE;
        return FoodCategory.OTHER;
    }

    // ─────────────────────────── 내부 응답 DTO ───────────────────────────

    @Getter @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class UsdaSearchResponse {
        private List<UsdaFood> foods;
    }

    @Getter @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class UsdaFood {
        private long fdcId;
        private String description;
        private String brandOwner;
        private String foodCategory;
        private List<UsdaNutrient> foodNutrients;

        Double getCaloriesPer100g() {
            return getNutrientValue(NUTRIENT_ENERGY);
        }

        Double getNutrientValue(int nutrientId) {
            if (foodNutrients == null) return null;
            return foodNutrients.stream()
                    .filter(n -> n.getNutrientId() == nutrientId && n.getValue() != null)
                    .findFirst()
                    .map(UsdaNutrient::getValue)
                    .orElse(null);
        }
    }

    @Getter @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class UsdaNutrient {
        private int nutrientId;
        private Double value;
    }
}
