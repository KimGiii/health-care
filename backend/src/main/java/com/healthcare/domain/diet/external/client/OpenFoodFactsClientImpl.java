package com.healthcare.domain.diet.external.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.external.dto.ExternalFoodResult;
import com.healthcare.domain.diet.external.dto.ExternalFoodResult.FoodDataSource;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Component
public class OpenFoodFactsClientImpl implements OpenFoodFactsClient {

    /** 바코드 조회용 */
    private final RestClient offRestClient;

    /** 텍스트 검색용 (search.openfoodfacts.org) */
    private final RestClient offSearchRestClient;

    public OpenFoodFactsClientImpl(
            @Qualifier("offRestClient") RestClient offRestClient,
            @Qualifier("offSearchRestClient") RestClient offSearchRestClient) {
        this.offRestClient = offRestClient;
        this.offSearchRestClient = offSearchRestClient;
    }

    // OFF categories → FoodCategory 매핑 키워드
    private static final Map<String, FoodCategory> CATEGORY_KEYWORDS = Map.of(
            "meat",       FoodCategory.PROTEIN_SOURCE,
            "fish",       FoodCategory.PROTEIN_SOURCE,
            "dairy",      FoodCategory.DAIRY,
            "cereals",    FoodCategory.GRAIN,
            "bread",      FoodCategory.GRAIN,
            "vegetables", FoodCategory.VEGETABLE,
            "fruits",     FoodCategory.FRUIT,
            "beverages",  FoodCategory.BEVERAGE,
            "fats",       FoodCategory.FAT,
            "oils",       FoodCategory.FAT
    );

    @Override
    public List<ExternalFoodResult> search(String query, int page, int size) {
        // search.openfoodfacts.org: Elasticsearch 기반 v2 API (rate-limit 없음)
        OffSearchResponse response = offSearchRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/search")
                        .queryParam("q", query)
                        .queryParam("page", page + 1)   // 1-based
                        .queryParam("page_size", size)
                        .queryParam("fields",
                                "code,product_name,brands,categories_tags,nutriments")
                        .build())
                .retrieve()
                .body(OffSearchResponse.class);

        if (response == null || response.getHits() == null) {
            return List.of();
        }

        return response.getHits().stream()
                .map(this::toExternalResult)
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public Optional<ExternalFoodResult> findByBarcode(String barcode) {
        OffProductResponse response = offRestClient.get()
                .uri("/api/v2/product/{barcode}.json", barcode)
                .retrieve()
                .body(OffProductResponse.class);

        if (response == null || response.getStatus() != 1 || response.getProduct() == null) {
            return Optional.empty();
        }

        ExternalFoodResult result = toExternalResult(response.getProduct());
        return Optional.ofNullable(result);
    }

    private ExternalFoodResult toExternalResult(OffProduct product) {
        if (product.getProductName() == null || product.getProductName().isBlank()) return null;
        if (product.getNutriments() == null) return null;

        Double calories = product.getNutriments().getCalories();
        if (calories == null) return null;

        return ExternalFoodResult.builder()
                .source(FoodDataSource.OPEN_FOOD_FACTS)
                .externalId(product.getCode())
                .name(product.getProductName())
                .brand(product.getBrands())
                .category(mapCategory(product.getCategoriesTags()))
                .caloriesPer100g(calories)
                .proteinPer100g(product.getNutriments().getProtein())
                .carbsPer100g(product.getNutriments().getCarbs())
                .fatPer100g(product.getNutriments().getFat())
                .build();
    }

    private FoodCategory mapCategory(List<String> tags) {
        if (tags == null || tags.isEmpty()) return FoodCategory.OTHER;
        String joined = String.join(",", tags).toLowerCase();
        return CATEGORY_KEYWORDS.entrySet().stream()
                .filter(e -> joined.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(FoodCategory.PROCESSED);
    }

    // ─────────────────────────── 내부 응답 DTO ───────────────────────────

    @Getter @Setter @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class OffSearchResponse {
        /** v2 Search API (search.openfoodfacts.org) 는 'hits' 필드 사용 */
        private List<OffProduct> hits;
    }

    @Getter @Setter @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class OffProductResponse {
        private int status;
        private OffProduct product;
    }

    @Getter @Setter @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class OffProduct {
        private String code;

        @JsonProperty("product_name")
        private String productName;

        private String brands;

        @JsonProperty("categories_tags")
        private List<String> categoriesTags;

        private OffNutriments nutriments;
    }

    @Getter @Setter @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class OffNutriments {

        @JsonProperty("energy-kcal_100g")
        private Double calories;

        @JsonProperty("proteins_100g")
        private Double protein;

        @JsonProperty("carbohydrates_100g")
        private Double carbs;

        @JsonProperty("fat_100g")
        private Double fat;
    }
}
