package com.healthcare.domain.diet.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.domain.diet.ai.dto.AiNutritionEstimateResponse;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
        service = new AiNutritionEstimationService(objectMapper);

        mockClient  = mock(RestClient.class);
        postSpec    = mock(RestClient.RequestBodyUriSpec.class);
        responseSpec = mock(RestClient.ResponseSpec.class);

        when(mockClient.post()).thenReturn(postSpec);
        when(postSpec.uri(anyString())).thenReturn(postSpec);
        when(postSpec.body(any(Object.class))).thenReturn(postSpec);
        when(postSpec.retrieve()).thenReturn(responseSpec);

        ReflectionTestUtils.setField(service, "client", mockClient);
        ReflectionTestUtils.setField(service, "model", "gpt-4.1-mini");
    }

    // ─────────────────────────── 헬퍼 ───────────────────────────

    /**
     * AI 응답 구조: output[].content[].text 에 innerJson 을 담아 반환.
     * innerJson 의 따옴표를 이스케이프해 JSON 문자열 값으로 포함시킨다.
     */
    private JsonNode buildResponse(String innerJson) throws Exception {
        String escaped = innerJson.replace("\\", "\\\\").replace("\"", "\\\"");
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

    // ─────────────────────────── 정상 응답 ───────────────────────────

    @Nested
    @DisplayName("정상 API 응답")
    class HappyPath {

        @Test
        @DisplayName("중첩 output 구조 — 영양성분 파싱")
        void estimate_nestedOutput_parsesAllFields() throws Exception {
            givenApiReturns(buildResponse(
                    "{\"category\":\"PROTEIN_SOURCE\",\"caloriesPer100g\":165.0,"
                    + "\"proteinPer100g\":31.0,\"carbsPer100g\":0.0,"
                    + "\"fatPer100g\":3.6,\"confidence\":0.9}"));

            AiNutritionEstimateResponse result = service.estimate("닭가슴살");

            assertThat(result.getFoodName()).isEqualTo("닭가슴살");
            assertThat(result.getCategory()).isEqualTo(FoodCategory.PROTEIN_SOURCE);
            assertThat(result.getCaloriesPer100g()).isEqualTo(165.0);
            assertThat(result.getProteinPer100g()).isEqualTo(31.0);
            assertThat(result.getCarbsPer100g()).isEqualTo(0.0);
            assertThat(result.getFatPer100g()).isEqualTo(3.6);
            assertThat(result.getConfidence()).isEqualTo(0.9);
        }

        @Test
        @DisplayName("output_text 직접 필드 — 파싱 성공")
        void estimate_outputTextDirectField_parsed() throws Exception {
            // output_text 에 내부 JSON 문자열을 직접 담는 응답 형태
            String inner = "{\\\"category\\\":\\\"GRAIN\\\",\\\"caloriesPer100g\\\":130.0,"
                    + "\\\"proteinPer100g\\\":2.4,\\\"carbsPer100g\\\":28.7,"
                    + "\\\"fatPer100g\\\":0.3,\\\"confidence\\\":0.85}";
            JsonNode root = objectMapper.readTree("{\"output_text\": \"" + inner + "\"}");
            givenApiReturns(root);

            AiNutritionEstimateResponse result = service.estimate("흰쌀밥");

            assertThat(result.getCategory()).isEqualTo(FoodCategory.GRAIN);
            assertThat(result.getCaloriesPer100g()).isEqualTo(130.0);
            assertThat(result.getConfidence()).isEqualTo(0.85);
        }

        @Test
        @DisplayName("isAiEstimated 는 항상 true")
        void estimate_isAiEstimatedAlwaysTrue() throws Exception {
            givenApiReturns(buildResponse(
                    "{\"category\":\"OTHER\",\"caloriesPer100g\":100,"
                    + "\"proteinPer100g\":5,\"carbsPer100g\":10,"
                    + "\"fatPer100g\":3,\"confidence\":0.5}"));

            assertThat(service.estimate("테스트").isAiEstimated()).isTrue();
        }

        @Test
        @DisplayName("disclaimer 필드 비어있지 않음")
        void estimate_disclaimerPresent() throws Exception {
            givenApiReturns(buildResponse(
                    "{\"category\":\"OTHER\",\"caloriesPer100g\":50,"
                    + "\"proteinPer100g\":2,\"carbsPer100g\":8,"
                    + "\"fatPer100g\":1,\"confidence\":0.6}"));

            assertThat(service.estimate("테스트").getDisclaimer()).isNotBlank();
        }
    }

    // ─────────────────────────── 카테고리 매핑 ───────────────────────────

    @Nested
    @DisplayName("카테고리 파싱")
    class CategoryParsing {

        @ParameterizedTest
        @ValueSource(strings = {"GRAIN", "PROTEIN_SOURCE", "VEGETABLE", "FRUIT",
                "DAIRY", "FAT", "BEVERAGE", "PROCESSED", "OTHER"})
        @DisplayName("유효한 카테고리 — 정상 매핑")
        void estimate_validCategory_mapsCorrectly(String category) throws Exception {
            givenApiReturns(buildResponse(
                    "{\"category\":\"" + category + "\","
                    + "\"caloriesPer100g\":100,\"proteinPer100g\":5,"
                    + "\"carbsPer100g\":10,\"fatPer100g\":3,\"confidence\":0.7}"));

            assertThat(service.estimate("음식").getCategory())
                    .isEqualTo(FoodCategory.valueOf(category));
        }

        @ParameterizedTest
        @ValueSource(strings = {"UNKNOWN", "INVALID", "123"})
        @DisplayName("알 수 없는 카테고리 — OTHER 폴백")
        void estimate_unknownCategory_defaultsToOther(String category) throws Exception {
            givenApiReturns(buildResponse(
                    "{\"category\":\"" + category + "\","
                    + "\"caloriesPer100g\":100,\"proteinPer100g\":5,"
                    + "\"carbsPer100g\":10,\"fatPer100g\":3,\"confidence\":0.5}"));

            assertThat(service.estimate("음식").getCategory()).isEqualTo(FoodCategory.OTHER);
        }
    }

    // ─────────────────────────── 오류 처리 ───────────────────────────

    @Nested
    @DisplayName("오류 및 폴백")
    class ErrorHandling {

        @Test
        @DisplayName("API 예외 — 모든 수치 0, confidence 0, isAiEstimated true")
        void estimate_apiThrowsException_returnsFallback() {
            givenApiThrows(new RuntimeException("OpenAI timeout"));

            AiNutritionEstimateResponse result = service.estimate("알 수 없는 음식");

            assertThat(result.getFoodName()).isEqualTo("알 수 없는 음식");
            assertThat(result.getCaloriesPer100g()).isEqualTo(0.0);
            assertThat(result.getProteinPer100g()).isEqualTo(0.0);
            assertThat(result.getCarbsPer100g()).isEqualTo(0.0);
            assertThat(result.getFatPer100g()).isEqualTo(0.0);
            assertThat(result.getConfidence()).isEqualTo(0.0);
            assertThat(result.getCategory()).isEqualTo(FoodCategory.OTHER);
            assertThat(result.isAiEstimated()).isTrue();
            assertThat(result.getDisclaimer()).isNotBlank();
        }

        @Test
        @DisplayName("null 응답 — 기본값 사용 (NPE 없음, confidence=0.5)")
        void estimate_nullResponse_usesDefaults() {
            // null → extractOutputText → "{}" → 기본값 경로 (catch 블록 아님)
            givenApiReturns(null);

            AiNutritionEstimateResponse result = service.estimate("음식");

            assertThat(result.getCaloriesPer100g()).isEqualTo(0.0);
            assertThat(result.getConfidence()).isEqualTo(0.5); // asDouble(0.5) 기본값
        }

        @Test
        @DisplayName("빈 JSON {} — asDouble 기본값 사용")
        void estimate_emptyJson_usesAsDoubleDefaults() throws Exception {
            givenApiReturns(buildResponse("{}"));

            AiNutritionEstimateResponse result = service.estimate("테스트");

            assertThat(result.getCaloriesPer100g()).isEqualTo(0.0);
            assertThat(result.getConfidence()).isEqualTo(0.5);
            assertThat(result.getCategory()).isEqualTo(FoodCategory.OTHER);
        }

        @Test
        @DisplayName("빈 output 배열 — 기본값 사용")
        void estimate_emptyOutputArray_usesDefaults() throws Exception {
            givenApiReturns(objectMapper.readTree("{\"output\":[]}"));

            AiNutritionEstimateResponse result = service.estimate("음식");

            assertThat(result.getCaloriesPer100g()).isEqualTo(0.0);
        }
    }
}
