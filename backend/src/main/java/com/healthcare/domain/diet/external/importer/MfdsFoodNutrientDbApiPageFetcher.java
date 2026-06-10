package com.healthcare.domain.diet.external.importer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.healthcare.domain.diet.external.config.ExternalApiProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.web.client.RestClient;

import java.util.List;

public class MfdsFoodNutrientDbApiPageFetcher implements FoodCatalogImportPageFetcher<MfdsFoodNutrientDbImportRow> {

    private final RestClient client;
    private final ExternalApiProperties properties;

    public MfdsFoodNutrientDbApiPageFetcher(RestClient client, ExternalApiProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public FoodCatalogImportPage<MfdsFoodNutrientDbImportRow> fetch(int pageNo, int pageSize) {
        MfdsFoodNutrientDbApiResponse response = client.get()
                .uri(uriBuilder -> uriBuilder
                        .queryParam("serviceKey", properties.getPublicApiKey())
                        .queryParam("type", "json")
                        .queryParam("pageNo", pageNo)
                        .queryParam("numOfRows", pageSize)
                        .build())
                .retrieve()
                .body(MfdsFoodNutrientDbApiResponse.class);

        MfdsFoodNutrientDbApiBody body = response == null || response.getResponse() == null
                ? null
                : response.getResponse().getBody();
        if (body == null || body.getItems() == null) {
            return new FoodCatalogImportPage<>(List.of(), false);
        }

        List<MfdsFoodNutrientDbImportRow> rows = body.getItems().stream()
                .map(MfdsFoodNutrientDbApiItem::toImportRow)
                .toList();

        return new FoodCatalogImportPage<>(rows, hasNext(body, pageNo, pageSize));
    }

    private boolean hasNext(MfdsFoodNutrientDbApiBody body, int fallbackPageNo, int fallbackPageSize) {
        int totalCount = parseInt(body.getTotalCount(), 0);
        int pageNo = parseInt(body.getPageNo(), fallbackPageNo);
        int pageSize = parseInt(body.getNumOfRows(), fallbackPageSize);
        return pageNo * pageSize < totalCount;
    }

    private int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class MfdsFoodNutrientDbApiResponse {
        private MfdsFoodNutrientDbApiResponseData response;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class MfdsFoodNutrientDbApiResponseData {
        private MfdsFoodNutrientDbApiBody body;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class MfdsFoodNutrientDbApiBody {
        private List<MfdsFoodNutrientDbApiItem> items;
        private String totalCount;
        private String numOfRows;
        private String pageNo;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class MfdsFoodNutrientDbApiItem {
        @JsonProperty("FOOD_CD")
        private String foodCd;

        @JsonProperty("FOOD_NM_KR")
        private String foodNmKr;

        @JsonProperty("FOOD_CAT1_NM")
        private String foodCat1Nm;

        @JsonProperty("SELLER_MANUFAC_NM")
        private String sellerManufactureName;

        @JsonProperty("MAKER_NM")
        private String makerName;

        @JsonProperty("IMP_MANUFAC_NM")
        private String importedManufacturerName;

        @JsonProperty("SERVING_SIZE")
        private String servingSize;

        @JsonProperty("Z10500")
        private String foodWeight;

        @JsonProperty("NUTRI_AMOUNT_SERVING")
        private String nutritionAmountServing;

        @JsonProperty("AMT_NUM1")
        private String amountNum1;

        @JsonProperty("AMT_NUM3")
        private String amountNum3;

        @JsonProperty("AMT_NUM4")
        private String amountNum4;

        @JsonProperty("AMT_NUM6")
        private String amountNum6;

        @JsonProperty("AMT_NUM7")
        private String amountNum7;

        @JsonProperty("AMT_NUM8")
        private String amountNum8;

        @JsonProperty("AMT_NUM13")
        private String amountNum13;

        @JsonProperty("UPDATE_DATE")
        private String updateDate;

        private MfdsFoodNutrientDbImportRow toImportRow() {
            return MfdsFoodNutrientDbImportRow.builder()
                    .foodCode(foodCd)
                    .foodNameKr(foodNmKr)
                    .foodCategoryName(foodCat1Nm)
                    .sellerManufacturerName(sellerManufactureName)
                    .makerName(makerName)
                    .importedManufacturerName(importedManufacturerName)
                    .servingSize(servingSize)
                    .foodWeight(foodWeight)
                    .nutritionAmountServing(nutritionAmountServing)
                    .amountNum1(amountNum1)
                    .amountNum3(amountNum3)
                    .amountNum4(amountNum4)
                    .amountNum6(amountNum6)
                    .amountNum7(amountNum7)
                    .amountNum8(amountNum8)
                    .amountNum13(amountNum13)
                    .updateDate(updateDate)
                    .build();
        }
    }
}
