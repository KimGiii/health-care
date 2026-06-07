package com.healthcare.domain.diet.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.domain.diet.ai.dto.AiNutritionEstimateResponse;
import com.healthcare.domain.diet.ai.dto.EstimatedItem;
import com.healthcare.domain.diet.ai.dto.NutritionFacts;
import com.healthcare.domain.diet.ai.dto.ServingBasis;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("AiNutritionEstimationService 단위 테스트")
class AiNutritionEstimationServiceTest {

    private AiNutritionEstimationService service;

    private RestClient mockClient;
    private RestClient.RequestBodyUriSpec postSpec;
    private RestClient.ResponseSpec responseSpec;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new AiNutritionEstimationService(objectMapper, new SimpleMeterRegistry());

        mockClient   = mock(RestClient.class);
        postSpec     = mock(RestClient.RequestBodyUriSpec.class);
        responseSpec = mock(RestClient.ResponseSpec.class);

        when(mockClient.post()).thenReturn(postSpec);
        when(postSpec.uri(anyString())).thenReturn(postSpec);
        when(postSpec.body(any(Object.class))).thenReturn(postSpec);
        when(postSpec.retrieve()).thenReturn(responseSpec);

        ReflectionTestUtils.setField(service, "client", mockClient);
        ReflectionTestUtils.setField(service, "model", "gpt-4.1-mini");
    }

    // ─────────────────────────── 헬퍼 ───────────────────────────

    /** OpenAI Responses API의 output[].content[].text 포맷으로 감싼다. */
    private JsonNode wrapInOutputArray(String innerJson) throws Exception {
        String escaped = innerJson
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
        return objectMapper.readTree("""
                {"output":[{"content":[{"text":"%s"}]}]}
                """.formatted(escaped));
    }

    private void givenApiReturns(JsonNode node) {
        when(responseSpec.body(JsonNode.class)).thenReturn(node);
    }

    private void givenApiThrows(RuntimeException ex) {
        when(responseSpec.body(JsonNode.class)).thenThrow(ex);
    }

    private String singleItemJson(String name, String category, String servingBasis,
                                  double calories, double carbs, double protein, double fat,
                                  String confidence) {
        return """
                {
                  "isFood": true,
                  "inputText": "%s",
                  "items": [{
                    "name": "%s",
                    "normalizedName": "%s",
                    "category": "%s",
                    "servingBasis": "%s",
                    "servingDescription": "테스트",
                    "estimatedWeightG": 100,
                    "nutrition": {
                      "caloriesKcal": %s,
                      "carbohydrateG": %s,
                      "sugarsG": 0,
                      "dietaryFiberG": 0,
                      "proteinG": %s,
                      "fatG": %s,
                      "saturatedFatG": 0,
                      "transFatG": 0,
                      "cholesterolMg": 0,
                      "sodiumMg": 0
                    },
                    "confidence": "%s",
                    "estimationNote": ""
                  }],
                  "totalNutrition": {
                    "caloriesKcal": %s, "carbohydrateG": %s, "sugarsG": 0, "dietaryFiberG": 0,
                    "proteinG": %s, "fatG": %s, "saturatedFatG": 0, "transFatG": 0,
                    "cholesterolMg": 0, "sodiumMg": 0
                  }
                }
                """.formatted(name, name, name, category, servingBasis,
                        calories, carbs, protein, fat, confidence,
                        calories, carbs, protein, fat);
    }

    // ─────────────────────────── 정상 응답 ───────────────────────────

    @Nested
    @DisplayName("정상 응답 파싱")
    class HappyPath {

        @Test
        @DisplayName("PER_100G 단일 항목 — 모든 필드 파싱")
        void estimate_per100gSingleItem_parsesAllFields() throws Exception {
            givenApiReturns(wrapInOutputArray(singleItemJson(
                    "닭가슴살", "PROTEIN_SOURCE", "PER_100G",
                    165.0, 0.0, 31.0, 3.6, "high")));

            AiNutritionEstimateResponse r = service.estimate("닭가슴살");

            assertThat(r.isFood()).isTrue();
            assertThat(r.error()).isNull();
            assertThat(r.aiEstimated()).isTrue();
            assertThat(r.disclaimer()).isNotBlank();
            assertThat(r.items()).hasSize(1);

            EstimatedItem item = r.items().get(0);
            assertThat(item.name()).isEqualTo("닭가슴살");
            assertThat(item.category()).isEqualTo(FoodCategory.PROTEIN_SOURCE);
            assertThat(item.servingBasis()).isEqualTo(ServingBasis.PER_100G);
            assertThat(item.confidence()).isEqualTo(0.9); // "high" → 0.9

            NutritionFacts n = item.nutrition();
            assertThat(n.caloriesKcal()).isEqualTo(165.0);
            assertThat(n.proteinG()).isEqualTo(31.0);
            assertThat(n.fatG()).isEqualTo(3.6);

            assertThat(r.totalNutrition().caloriesKcal()).isEqualTo(165.0);
        }

        @Test
        @DisplayName("PER_ITEM 브랜드 메뉴 — servingBasis 매핑")
        void estimate_perItemBrandMenu_servingBasisPerItem() throws Exception {
            givenApiReturns(wrapInOutputArray(singleItemJson(
                    "맥도날드 빅맥", "PROCESSED", "PER_ITEM",
                    550.0, 45.0, 25.0, 30.0, "medium")));

            AiNutritionEstimateResponse r = service.estimate("맥도날드 빅맥");

            assertThat(r.items().get(0).servingBasis()).isEqualTo(ServingBasis.PER_ITEM);
            assertThat(r.items().get(0).confidence()).isEqualTo(0.6); // "medium" → 0.6
        }

        @Test
        @DisplayName("CUSTOM_WEIGHT — 무게 명시 입력")
        void estimate_customWeight_servingBasisCustomWeight() throws Exception {
            givenApiReturns(wrapInOutputArray(singleItemJson(
                    "닭가슴살 200g", "PROTEIN_SOURCE", "CUSTOM_WEIGHT",
                    330.0, 0.0, 62.0, 7.2, "high")));

            AiNutritionEstimateResponse r = service.estimate("닭가슴살 200g");

            assertThat(r.items().get(0).servingBasis()).isEqualTo(ServingBasis.CUSTOM_WEIGHT);
        }

        @Test
        @DisplayName("다중 items + totalNutrition 합산값 그대로 사용")
        void estimate_multipleItems_totalNutritionFromModel() throws Exception {
            String json = """
                    {
                      "isFood": true,
                      "inputText": "쌀밥과 김치찌개",
                      "items": [
                        {"name":"쌀밥","normalizedName":"쌀밥","category":"GRAIN",
                         "servingBasis":"PER_100G","servingDescription":"","estimatedWeightG":100,
                         "nutrition":{"caloriesKcal":130,"carbohydrateG":28,"sugarsG":0,
                          "dietaryFiberG":0,"proteinG":2,"fatG":0,"saturatedFatG":0,
                          "transFatG":0,"cholesterolMg":0,"sodiumMg":0},
                         "confidence":"high","estimationNote":""},
                        {"name":"김치찌개","normalizedName":"김치찌개","category":"OTHER",
                         "servingBasis":"PER_100G","servingDescription":"","estimatedWeightG":100,
                         "nutrition":{"caloriesKcal":50,"carbohydrateG":4,"sugarsG":0,
                          "dietaryFiberG":1,"proteinG":3,"fatG":2,"saturatedFatG":0.5,
                          "transFatG":0,"cholesterolMg":5,"sodiumMg":800},
                         "confidence":"medium","estimationNote":""}
                      ],
                      "totalNutrition": {"caloriesKcal":180,"carbohydrateG":32,"sugarsG":0,
                        "dietaryFiberG":1,"proteinG":5,"fatG":2,"saturatedFatG":0.5,
                        "transFatG":0,"cholesterolMg":5,"sodiumMg":800}
                    }
                    """;
            givenApiReturns(wrapInOutputArray(json));

            AiNutritionEstimateResponse r = service.estimate("쌀밥과 김치찌개");

            assertThat(r.items()).hasSize(2);
            assertThat(r.totalNutrition().caloriesKcal()).isEqualTo(180.0);
            assertThat(r.totalNutrition().sodiumMg()).isEqualTo(800.0);
        }

        @Test
        @DisplayName("totalNutrition 누락 — items[].nutrition 합산으로 폴백")
        void estimate_totalNutritionMissing_sumsFromItems() throws Exception {
            String json = """
                    {
                      "isFood": true,
                      "inputText": "테스트",
                      "items": [
                        {"name":"a","normalizedName":"a","category":"OTHER",
                         "servingBasis":"PER_100G","servingDescription":"","estimatedWeightG":100,
                         "nutrition":{"caloriesKcal":100,"carbohydrateG":10,"sugarsG":0,
                          "dietaryFiberG":0,"proteinG":5,"fatG":2,"saturatedFatG":0,
                          "transFatG":0,"cholesterolMg":0,"sodiumMg":100},
                         "confidence":"low","estimationNote":""},
                        {"name":"b","normalizedName":"b","category":"OTHER",
                         "servingBasis":"PER_100G","servingDescription":"","estimatedWeightG":100,
                         "nutrition":{"caloriesKcal":200,"carbohydrateG":20,"sugarsG":0,
                          "dietaryFiberG":0,"proteinG":10,"fatG":4,"saturatedFatG":0,
                          "transFatG":0,"cholesterolMg":0,"sodiumMg":200},
                         "confidence":"low","estimationNote":""}
                      ]
                    }
                    """;
            givenApiReturns(wrapInOutputArray(json));

            AiNutritionEstimateResponse r = service.estimate("테스트");

            assertThat(r.totalNutrition().caloriesKcal()).isEqualTo(300.0);
            assertThat(r.totalNutrition().sodiumMg()).isEqualTo(300.0);
        }

        @Test
        @DisplayName("output_text 직접 필드 + 마크다운 펜스 — 정상 파싱")
        void estimate_outputTextWithMarkdownFence_parsed() throws Exception {
            String inner = "```json\\n" + singleItemJson(
                    "현미밥", "GRAIN", "PER_100G",
                    111.0, 23.0, 2.6, 0.9, "high").replace("\n", "\\n")
                    .replace("\"", "\\\"") + "\\n```";
            JsonNode root = objectMapper.readTree("{\"output_text\":\"" + inner + "\"}");
            givenApiReturns(root);

            AiNutritionEstimateResponse r = service.estimate("현미밥");

            assertThat(r.isFood()).isTrue();
            assertThat(r.items()).hasSize(1);
            assertThat(r.items().get(0).category()).isEqualTo(FoodCategory.GRAIN);
        }
    }

    // ─────────────────────────── isFood=false ───────────────────────────

    @Nested
    @DisplayName("음식 아님/판단 불가")
    class NotFood {

        @Test
        @DisplayName("isFood=false — items 비어있고 NOT_FOOD_OR_UNKNOWN")
        void estimate_isFoodFalse_returnsNotFoodEnvelope() throws Exception {
            String json = """
                    {
                      "isFood": false,
                      "inputText": "랜덤문자열",
                      "items": [],
                      "totalNutrition": null,
                      "error": {"code":"NOT_FOOD_OR_UNKNOWN","message":"판단 불가"}
                    }
                    """;
            givenApiReturns(wrapInOutputArray(json));

            AiNutritionEstimateResponse r = service.estimate("xkcd123");

            assertThat(r.isFood()).isFalse();
            assertThat(r.items()).isEmpty();
            assertThat(r.totalNutrition()).isNull();
            assertThat(r.error()).isNotNull();
            assertThat(r.error().code()).isEqualTo("NOT_FOOD_OR_UNKNOWN");
        }
    }

    // ─────────────────────────── 오류 처리 ───────────────────────────

    @Nested
    @DisplayName("오류 및 폴백")
    class ErrorHandling {

        @Test
        @DisplayName("API 예외 — AI_UNAVAILABLE 응답")
        void estimate_apiThrows_returnsAiUnavailable() {
            givenApiThrows(new RuntimeException("OpenAI timeout"));

            AiNutritionEstimateResponse r = service.estimate("닭가슴살");

            assertThat(r.isFood()).isFalse();
            assertThat(r.items()).isEmpty();
            assertThat(r.totalNutrition()).isNull();
            assertThat(r.error()).isNotNull();
            assertThat(r.error().code()).isEqualTo("AI_UNAVAILABLE");
            assertThat(r.aiEstimated()).isTrue();
            assertThat(r.disclaimer()).isNotBlank();
        }

        @Test
        @DisplayName("파싱 불가 텍스트 — AI_UNAVAILABLE 응답")
        void estimate_unparseableText_returnsAiUnavailable() throws Exception {
            givenApiReturns(wrapInOutputArray("not a json"));

            AiNutritionEstimateResponse r = service.estimate("음식");

            assertThat(r.isFood()).isFalse();
            assertThat(r.error().code()).isEqualTo("AI_UNAVAILABLE");
        }

        @Test
        @DisplayName("알 수 없는 servingBasis 값 — PER_100G 폴백")
        void estimate_unknownServingBasis_fallsBackToPer100g() throws Exception {
            String json = singleItemJson("음식", "OTHER", "FOO_BAR",
                    100.0, 10.0, 5.0, 2.0, "high");
            givenApiReturns(wrapInOutputArray(json));

            AiNutritionEstimateResponse r = service.estimate("음식");

            assertThat(r.items().get(0).servingBasis()).isEqualTo(ServingBasis.PER_100G);
        }

        @Test
        @DisplayName("알 수 없는 category — OTHER 폴백")
        void estimate_unknownCategory_fallsBackToOther() throws Exception {
            String json = singleItemJson("음식", "INVALID_CAT", "PER_100G",
                    100.0, 10.0, 5.0, 2.0, "medium");
            givenApiReturns(wrapInOutputArray(json));

            AiNutritionEstimateResponse r = service.estimate("음식");

            assertThat(r.items().get(0).category()).isEqualTo(FoodCategory.OTHER);
        }
    }
}
