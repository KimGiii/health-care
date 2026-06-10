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

@DisplayName("StandardFoodApiPageFetcher")
class StandardFoodApiPageFetcherTest {

    @Test
    @DisplayName("표준데이터 API 페이지를 row로 변환하고 다음 페이지 여부를 계산한다")
    void fetch_convertsStandardFoodItemsAndDetectsHasNext() {
        ExternalApiProperties properties = new ExternalApiProperties();
        properties.setPublicApiKey("test-key");
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.example.test/standard-foods");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.build();
        server.expect(requestTo(startsWith("https://api.example.test/standard-foods")))
                .andExpect(queryParam("serviceKey", "test-key"))
                .andExpect(queryParam("type", "json"))
                .andExpect(queryParam("pageNo", "2"))
                .andExpect(queryParam("numOfRows", "2"))
                .andRespond(withSuccess("""
                        {
                          "response": {
                            "body": {
                              "items": [{
                                "foodCd": "P001",
                                "foodNm": "닭가슴살 샐러드",
                                "mfrNm": "테스트푸드",
                                "restNm": "테스트식당",
                                "foodLv3Nm": "가공",
                                "nutConSrtrQua": "100g",
                                "servSize": "1팩",
                                "foodSize": "210g",
                                "enerc": "210",
                                "prot": "28",
                                "chocdf": "12",
                                "fatce": "6",
                                "sugar": "3",
                                "fibtg": "4",
                                "fasat": "1.2",
                                "fatrn": "0",
                                "chole": "35",
                                "nat": "430",
                                "crtrYmd": "2026-06-05"
                              }],
                              "totalCount": "5",
                              "numOfRows": "2",
                              "pageNo": "2"
                            }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));
        StandardFoodApiPageFetcher fetcher = new StandardFoodApiPageFetcher(client, properties);

        FoodCatalogImportPage<StandardFoodImportRow> page = fetcher.fetch(2, 2);

        assertThat(page.hasNext()).isTrue();
        assertThat(page.rows()).hasSize(1);
        StandardFoodImportRow row = page.rows().get(0);
        assertThat(row.getFoodCode()).isEqualTo("P001");
        assertThat(row.getFoodName()).isEqualTo("닭가슴살 샐러드");
        assertThat(row.getManufacturerName()).isEqualTo("테스트푸드");
        assertThat(row.getRestaurantName()).isEqualTo("테스트식당");
        assertThat(row.getNutritionServingSize()).isEqualTo("1팩");
        assertThat(row.getFoodSize()).isEqualTo("210g");
        assertThat(row.getCalories()).isEqualTo("210");
        assertThat(row.getDataVersion()).isEqualTo("2026-06-05");
        assertThat(row.getLastVerifiedDate()).isEqualTo("2026-06-05");
        server.verify();
    }
}
