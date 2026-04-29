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
            Map.entry("즉석", FoodCategory.PROCESSED),
            Map.entry("가공", FoodCategory.PROCESSED)
    );

    @Override
    public List<ExternalFoodResult> search(String query, int page, int size) {
        List<ExternalFoodResult> processedResults = List.of();
        List<ExternalFoodResult> generalResults = List.of();

        // 1. 가공식품 API 검색
        try {
            processedResults = searchProcessedFood(query, page, size);
            log.debug("가공식품 API 검색 결과: {} 건", processedResults.size());
        } catch (Exception e) {
            log.warn("가공식품 API 검색 실패: {}", e.getMessage());
        }

        // 2. 음식 API 검색
        try {
            generalResults = searchGeneralFood(query, page, size);
            log.debug("음식 API 검색 결과: {} 건", generalResults.size());
        } catch (Exception e) {
            log.warn("음식 API 검색 실패: {}", e.getMessage());
        }

        // 두 소스를 균등 비율로 인터리브한 뒤 limit 적용
        // → 한 API 결과만으로 size 슬롯이 꽉 차는 문제 방지
        return interleave(processedResults, generalResults, size);
    }

    /**
     * 두 리스트를 교대로 섞은 뒤 maxSize 개로 자른다.
     * 한쪽이 소진되면 나머지 소스로 채운다.
     */
    private List<ExternalFoodResult> interleave(
            List<ExternalFoodResult> a,
            List<ExternalFoodResult> b,
            int maxSize) {
        List<ExternalFoodResult> merged = new ArrayList<>(maxSize);
        int ai = 0, bi = 0;
        while (merged.size() < maxSize && (ai < a.size() || bi < b.size())) {
            if (ai < a.size()) merged.add(a.get(ai++));
            if (merged.size() < maxSize && bi < b.size()) merged.add(b.get(bi++));
        }
        return merged;
    }

    /**
     * 가공식품 API 검색
     */
    private List<ExternalFoodResult> searchProcessedFood(String query, int page, int size) {
        ApiResponseWrapper response = processedFoodClient.get()
                .uri(uriBuilder -> uriBuilder
                        .queryParam("serviceKey", properties.getPublicApiKey())
                        .queryParam("type", "json")
                        .queryParam("pageNo", page + 1)
                        .queryParam("numOfRows", size * 2)
                        .queryParam("foodNm", query)
                        .build())
                .retrieve()
                .body(ApiResponseWrapper.class);

        if (response == null || response.getResponse() == null ||
            response.getResponse().getBody() == null ||
            response.getResponse().getBody().getItems() == null) {
            return List.of();
        }

        return response.getResponse().getBody().getItems().stream()
                .map(this::toExternalResult)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 음식 API 검색
     */
    private List<ExternalFoodResult> searchGeneralFood(String query, int page, int size) {
        ApiResponseWrapper response = generalFoodClient.get()
                .uri(uriBuilder -> uriBuilder
                        .queryParam("serviceKey", properties.getPublicApiKey())
                        .queryParam("type", "json")
                        .queryParam("pageNo", page + 1)
                        .queryParam("numOfRows", size * 2)
                        .queryParam("foodNm", query)
                        .build())
                .retrieve()
                .body(ApiResponseWrapper.class);

        if (response == null || response.getResponse() == null ||
            response.getResponse().getBody() == null ||
            response.getResponse().getBody().getItems() == null) {
            return List.of();
        }

        return response.getResponse().getBody().getItems().stream()
                .map(this::toExternalResult)
                .filter(Objects::nonNull)
                .toList();
    }

    private ExternalFoodResult toExternalResult(PublicFoodItem item) {
        // 필수 필드 검증
        if (item.getFoodNm() == null || item.getFoodNm().isBlank()) return null;

        // 칼로리 파싱 (문자열일 수 있음)
        Double calories = parseDouble(item.getEnerc());
        if (calories == null || calories == 0) return null;

        return ExternalFoodResult.builder()
                .source(FoodDataSource.PUBLIC_FOOD_API)
                .externalId(item.getFoodCd())
                .name(item.getFoodNm())
                .nameKo(item.getFoodNm())
                .brand(item.getMfrNm())
                .category(mapCategory(item.getFoodLv3Nm()))
                .caloriesPer100g(calories)
                .proteinPer100g(parseDouble(item.getProt()))
                .carbsPer100g(parseDouble(item.getChocdf()))
                .fatPer100g(parseDouble(item.getFatce()))
                .build();
    }

    private Double parseDouble(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
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
    static class ApiResponseWrapper {
        private ResponseData response;
    }

    @Getter @Setter @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ResponseData {
        private Header header;
        private Body body;
    }

    @Getter @Setter @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Header {
        private String resultCode;
        private String resultMsg;
        private String type;
    }

    @Getter @Setter @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Body {
        private List<PublicFoodItem> items;
        private String totalCount;
        private String numOfRows;
        private String pageNo;
    }

    @Getter @Setter @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class PublicFoodItem {
        private String foodCd;        // 식품코드
        private String foodNm;        // 식품명
        private String dataCd;        // 데이터구분코드
        private String typeNm;        // 데이터구분명
        private String foodLv3Cd;     // 식품대분류코드
        private String foodLv3Nm;     // 식품대분류명
        private String nutConSrtrQua; // 영양성분함량 기준량
        private String enerc;         // 에너지(kcal)
        private String prot;          // 단백질(g)
        private String fatce;         // 지방(g)
        private String chocdf;        // 탄수화물(g)
        private String mfrNm;         // 제조사명
        private String itemMnftrRptNo; // 품목제조보고번호
    }
}
