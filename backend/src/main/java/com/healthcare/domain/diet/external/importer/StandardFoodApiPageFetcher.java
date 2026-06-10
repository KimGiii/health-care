package com.healthcare.domain.diet.external.importer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.healthcare.domain.diet.external.config.ExternalApiProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.web.client.RestClient;

import java.util.List;

public class StandardFoodApiPageFetcher implements FoodCatalogImportPageFetcher<StandardFoodImportRow> {

    private final RestClient client;
    private final ExternalApiProperties properties;

    public StandardFoodApiPageFetcher(RestClient client, ExternalApiProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public FoodCatalogImportPage<StandardFoodImportRow> fetch(int pageNo, int pageSize) {
        StandardFoodApiResponse response = client.get()
                .uri(uriBuilder -> uriBuilder
                        .queryParam("serviceKey", properties.getPublicApiKey())
                        .queryParam("type", "json")
                        .queryParam("pageNo", pageNo)
                        .queryParam("numOfRows", pageSize)
                        .build())
                .retrieve()
                .body(StandardFoodApiResponse.class);

        StandardFoodApiBody body = response == null || response.getResponse() == null
                ? null
                : response.getResponse().getBody();
        if (body == null || body.getItems() == null) {
            return new FoodCatalogImportPage<>(List.of(), false);
        }

        List<StandardFoodImportRow> rows = body.getItems().stream()
                .map(StandardFoodApiItem::toImportRow)
                .toList();

        return new FoodCatalogImportPage<>(rows, hasNext(body, pageNo, pageSize));
    }

    private boolean hasNext(StandardFoodApiBody body, int fallbackPageNo, int fallbackPageSize) {
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

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class StandardFoodApiResponse {
        private StandardFoodApiResponseData response;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class StandardFoodApiResponseData {
        private StandardFoodApiBody body;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class StandardFoodApiBody {
        private List<StandardFoodApiItem> items;
        private String totalCount;
        private String numOfRows;
        private String pageNo;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class StandardFoodApiItem {
        private String foodCd;
        private String foodNm;
        private String mfrNm;
        private String restNm;
        private String distNm;
        private String imptNm;
        private String foodLv3Nm;
        private String nutConSrtrQua;
        private String servSize;
        private String foodSize;
        private String enerc;
        private String prot;
        private String chocdf;
        private String fatce;
        private String sugar;
        private String fibtg;
        private String fasat;
        private String fatrn;
        private String chole;
        private String nat;
        private String crtrYmd;

        private StandardFoodImportRow toImportRow() {
            return StandardFoodImportRow.builder()
                    .foodCode(foodCd)
                    .foodName(foodNm)
                    .manufacturerName(firstNonBlank(mfrNm, imptNm, distNm))
                    .restaurantName(restNm)
                    .categoryName(foodLv3Nm)
                    .nutritionServingSize(firstNonBlank(servSize, nutConSrtrQua))
                    .foodSize(foodSize)
                    .calories(enerc)
                    .protein(prot)
                    .carbs(chocdf)
                    .fat(fatce)
                    .sugar(sugar)
                    .dietaryFiber(fibtg)
                    .saturatedFat(fasat)
                    .transFat(fatrn)
                    .cholesterol(chole)
                    .sodium(nat)
                    .dataVersion(crtrYmd)
                    .lastVerifiedDate(crtrYmd)
                    .build();
        }
    }
}
