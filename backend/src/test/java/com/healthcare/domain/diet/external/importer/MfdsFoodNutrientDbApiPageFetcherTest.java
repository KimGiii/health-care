package com.healthcare.domain.diet.external.importer;

import com.healthcare.domain.diet.external.config.ExternalApiProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("MfdsFoodNutrientDbApiPageFetcher")
class MfdsFoodNutrientDbApiPageFetcherTest {

    @Test
    @DisplayName("FoodNtr API 페이지를 row로 변환하고 다음 페이지 여부를 계산한다")
    void fetch_convertsFoodNutrientDbItemsAndDetectsHasNext() {
        ExternalApiProperties properties = new ExternalApiProperties();
        properties.setPublicApiKey("test-key");
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.example.test/food-nutrients");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.build();
        server.expect(requestTo(startsWith("https://api.example.test/food-nutrients")))
                .andExpect(queryParam("serviceKey", "test-key"))
                .andExpect(queryParam("type", "json"))
                .andExpect(queryParam("pageNo", "3"))
                .andExpect(queryParam("numOfRows", "50"))
                .andRespond(withSuccess("""
                        {
                          "response": {
                            "body": {
                              "items": [{
                                "FOOD_CD": "N001",
                                "FOOD_NM_KR": "와퍼",
                                "FOOD_CAT1_NM": "가공",
                                "SELLER_MANUFAC_NM": "버거킹",
                                "MAKER_NM": "비케이알",
                                "IMP_MANUFAC_NM": "수입사",
                                "SERVING_SIZE": "1개(278g)",
                                "Z10500": "278",
                                "NUTRI_AMOUNT_SERVING": "1개",
                                "AMT_NUM1": "619",
                                "AMT_NUM3": "29",
                                "AMT_NUM4": "35",
                                "AMT_NUM6": "47",
                                "AMT_NUM7": "11",
                                "AMT_NUM8": "3",
                                "AMT_NUM13": "1051",
                                "UPDATE_DATE": "2025-12-05"
                              }],
                              "totalCount": "101",
                              "numOfRows": "50",
                              "pageNo": "3"
                            }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));
        MfdsFoodNutrientDbApiPageFetcher fetcher =
                new MfdsFoodNutrientDbApiPageFetcher(client, properties);

        FoodCatalogImportPage<MfdsFoodNutrientDbImportRow> page = fetcher.fetch(3, 50);

        assertThat(page.hasNext()).isFalse();
        assertThat(page.rows()).hasSize(1);
        MfdsFoodNutrientDbImportRow row = page.rows().get(0);
        assertThat(row.getFoodCode()).isEqualTo("N001");
        assertThat(row.getFoodNameKr()).isEqualTo("와퍼");
        assertThat(row.getFoodCategoryName()).isEqualTo("가공");
        assertThat(row.getSellerManufacturerName()).isEqualTo("버거킹");
        assertThat(row.getMakerName()).isEqualTo("비케이알");
        assertThat(row.getImportedManufacturerName()).isEqualTo("수입사");
        assertThat(row.getServingSize()).isEqualTo("1개(278g)");
        assertThat(row.getFoodWeight()).isEqualTo("278");
        assertThat(row.getNutritionAmountServing()).isEqualTo("1개");
        assertThat(row.getAmountNum1()).isEqualTo("619");
        assertThat(row.getAmountNum13()).isEqualTo("1051");
        assertThat(row.getUpdateDate()).isEqualTo("2025-12-05");
        server.verify();
    }
}
