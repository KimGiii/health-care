package com.healthcare.domain.diet.external.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.external.config.ExternalApiProperties;
import com.healthcare.domain.diet.external.dto.ExternalFoodResult;
import com.healthcare.domain.diet.external.dto.ExternalFoodResult.FoodDataSource;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Component
public class PublicFoodApiClientImpl implements PublicFoodApiClient {

    /** 가공식품 API 클라이언트 */
    private final RestClient processedFoodClient;

    /** 음식 API 클라이언트 */
    private final RestClient generalFoodClient;

    /** API 설정 */
    private final ExternalApiProperties properties;

    public PublicFoodApiClientImpl(
            @Qualifier("processedFoodRestClient") RestClient processedFoodClient,
            @Qualifier("generalFoodRestClient") RestClient generalFoodClient,
            ExternalApiProperties properties) {
        this.processedFoodClient = processedFoodClient;
        this.generalFoodClient = generalFoodClient;
        this.properties = properties;
    }

    // 식품 대분류 → FoodCategory 매핑
    private static final Map<String, FoodCategory> CATEGORY_MAPPING = Map.ofEntries(
            Map.entry("곡류", FoodCategory.GRAIN),
            Map.entry("서류", FoodCategory.GRAIN),
            Map.entry("당류", FoodCategory.PROCESSED),
            Map.entry("두류", FoodCategory.PROTEIN_SOURCE),
            Map.entry("견과류", FoodCategory.FAT),
            Map.entry("채소류", FoodCategory.VEGETABLE),
            Map.entry("과일류", FoodCategory.FRUIT),
            Map.entry("버섯류", FoodCategory.VEGETABLE),
            Map.entry("육류", FoodCategory.PROTEIN_SOURCE),
            Map.entry("가금류", FoodCategory.PROTEIN_SOURCE),
            Map.entry("난류", FoodCategory.PROTEIN_SOURCE),
            Map.entry("어패류", FoodCategory.PROTEIN_SOURCE),
            Map.entry("해조류", FoodCategory.VEGETABLE),
            Map.entry("우유류", FoodCategory.DAIRY),
            Map.entry("유제품류", FoodCategory.DAIRY),
            Map.entry("유지류", FoodCategory.FAT),
            Map.entry("음료류", FoodCategory.BEVERAGE),
            Map.entry("주류", FoodCategory.BEVERAGE),
            Map.entry("조리가공식품류", FoodCategory.PROCESSED)
    );

    @Override
    public List<ExternalFoodResult> search(String query, int page, int size) {
        List<ExternalFoodResult> results = new ArrayList<>();

        // 1. 가공식품 API 검색
        try {
            List<ExternalFoodResult> processedResults = searchProcessedFood(query, page, size);
            results.addAll(processedResults);
            log.debug("가공식품 API 검색 결과: {} 건", processedResults.size());
        } catch (Exception e) {
            log.warn("가공식품 API 검색 실패: {}", e.getMessage());
        }

        // 2. 음식 API 검색
        try {
            List<ExternalFoodResult> generalResults = searchGeneralFood(query, page, size);
            results.addAll(generalResults);
            log.debug("음식 API 검색 결과: {} 건", generalResults.size());
        } catch (Exception e) {
            log.warn("음식 API 검색 실패: {}", e.getMessage());
        }

        return results;
    }

    /**
     * 가공식품 API 검색
     */
    private List<ExternalFoodResult> searchProcessedFood(String query, int page, int size) {
        PublicFoodApiResponse response = processedFoodClient.get()
                .uri(uriBuilder -> uriBuilder
                        .queryParam("serviceKey", properties.getPublicApiKey())
                        .queryParam("page", page + 1)  // 1-based
                        .queryParam("perPage", size)
                        .queryParam("cond[PRDLST_NM::LIKE]", query)
                        .build())
                .retrieve()
                .body(PublicFoodApiResponse.class);

        if (response == null || response.getData() == null) {
            return List.of();
        }

        return response.getData().stream()
                .map(this::toExternalResult)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 음식 API 검색
     */
    private List<ExternalFoodResult> searchGeneralFood(String query, int page, int size) {
        PublicFoodApiResponse response = generalFoodClient.get()
                .uri(uriBuilder -> uriBuilder
                        .queryParam("serviceKey", properties.getPublicApiKey())
                        .queryParam("page", page + 1)  // 1-based
                        .queryParam("perPage", size)
                        .queryParam("cond[식품명::LIKE]", query)
                        .build())
                .retrieve()
                .body(PublicFoodApiResponse.class);

        if (response == null || response.getData() == null) {
            return List.of();
        }

        return response.getData().stream()
                .map(this::toExternalResult)
                .filter(Objects::nonNull)
                .toList();
    }

    private ExternalFoodResult toExternalResult(PublicFoodItem item) {
        // 필수 필드 검증
        if (item.getFoodName() == null || item.getFoodName().isBlank()) return null;
        if (item.getCalories() == null) return null;

        return ExternalFoodResult.builder()
                .source(FoodDataSource.PUBLIC_FOOD_API)
                .externalId(item.getFoodCode())
                .name(item.getFoodName())
                .nameKo(item.getFoodName())
                .brand(item.getManufacturer())
                .category(mapCategory(item.getMajorCategory()))
                .caloriesPer100g(item.getCalories())
                .proteinPer100g(item.getProtein())
                .carbsPer100g(item.getCarbohydrate())
                .fatPer100g(item.getFat())
                .build();
    }

    private FoodCategory mapCategory(String majorCategory) {
        if (majorCategory == null || majorCategory.isBlank()) {
            return FoodCategory.OTHER;
        }
        return CATEGORY_MAPPING.entrySet().stream()
                .filter(e -> majorCategory.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(FoodCategory.PROCESSED);
    }

    // ─────────────────────────── 내부 응답 DTO ───────────────────────────

    @Getter @Setter @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class PublicFoodApiResponse {
        private Integer currentCount;
        private List<PublicFoodItem> data;
        private Integer matchCount;
        private Integer page;
        private Integer perPage;
        private Integer totalCount;
    }

    @Getter @Setter @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class PublicFoodItem {
        /** 식품코드 */
        @JsonProperty("식품코드")
        private String foodCode;

        /** 식품명 */
        @JsonProperty("식품명")
        private String foodName;

        /** 식품대분류명 */
        @JsonProperty("식품대분류명")
        private String majorCategory;

        /** 에너지(kcal) */
        @JsonProperty("에너지(kcal)")
        private Double calories;

        /** 단백질(g) */
        @JsonProperty("단백질(g)")
        private Double protein;

        /** 탄수화물(g) */
        @JsonProperty("탄수화물(g)")
        private Double carbohydrate;

        /** 지방(g) */
        @JsonProperty("지방(g)")
        private Double fat;

        /** 제조사명 (가공식품의 경우) */
        @JsonProperty("제조사명")
        private String manufacturer;

        /** 품목제조보고번호 (가공식품 전용 필드) */
        @JsonProperty("품목제조보고번호")
        private String productReportNumber;
    }
}
