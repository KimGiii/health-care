package com.healthcare.domain.diet.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.domain.diet.ai.dto.AiNutritionEstimateResponse;
import com.healthcare.domain.diet.ai.dto.EstimatedItem;
import com.healthcare.domain.diet.ai.dto.NutritionFacts;
import com.healthcare.domain.diet.ai.dto.ServingBasis;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@ConditionalOnExpression("'${app.ai.meal.openai-api-key:}' != ''")
public class AiNutritionEstimationService {

    private static final String INSTRUCTIONS = """
            당신은 한국 식단 기록 앱의 음식 영양성분 추정 API 응답 생성기입니다.
            사용자가 텍스트로 입력한 음식명을 분석하여 영양성분을 추정하고, 반드시 단일 JSON 객체만 반환합니다.

            [핵심 규칙]
            1. 응답은 반드시 JSON 객체 하나만 반환한다. 마크다운/주석/설명 문장 금지.
            2. 모든 수치는 number 타입. 알 수 없는 값도 null 대신 합리적 추정값을 채운다.
            3. 단위 — caloriesKcal=kcal, carbohydrateG·sugarsG·dietaryFiberG·proteinG·fatG·saturatedFatG·transFatG=g, cholesterolMg·sodiumMg=mg.
            4. 음식이 여러 개 입력되면 items 배열에 각각 분리하고 totalNutrition에 합산값을 반환한다.
            5. 입력이 음식이 아니거나 판단 불가하면 isFood=false + error 형식으로만 반환한다.

            [servingBasis 판단]
            - PER_ITEM: 브랜드/프랜차이즈 메뉴, 포장식품, "1개/한 줄/1봉지/1잔/1인분"이 자연스러운 단위 음식.
              예) "맥도날드 빅맥", "신라면", "김밥", "스타벅스 아메리카노 Tall"
            - PER_100G: 일반 식재료·요리명, 무게·개수 정보가 없는 음식.
              예) "닭가슴살", "불고기", "김치찌개", "현미밥", "삶은 계란"
            - CUSTOM_WEIGHT: 사용자가 g·kg 등 무게를 명시한 경우. 해당 무게로 환산.
              예) "닭가슴살 200g", "고구마 150g"

            [category]
            items[].category는 반드시 다음 중 하나:
            GRAIN, PROTEIN_SOURCE, VEGETABLE, FRUIT, DAIRY, FAT, BEVERAGE, PROCESSED, OTHER

            [신뢰도]
            공식 영양성분을 모를 때도 일반 식품 데이터·평균 조리 방식 기준으로 추정하되,
            불확실하면 confidence를 낮게 설정한다. 값: "high" | "medium" | "low".

            [정상 응답 스키마]
            {
              "isFood": true,
              "inputText": "사용자 입력 원문",
              "items": [
                {
                  "name": "음식명",
                  "normalizedName": "표준화된 음식명",
                  "category": "PROTEIN_SOURCE",
                  "servingBasis": "PER_ITEM | PER_100G | CUSTOM_WEIGHT",
                  "servingDescription": "산정 기준 설명",
                  "estimatedWeightG": 0,
                  "nutrition": {
                    "caloriesKcal": 0,
                    "carbohydrateG": 0,
                    "sugarsG": 0,
                    "dietaryFiberG": 0,
                    "proteinG": 0,
                    "fatG": 0,
                    "saturatedFatG": 0,
                    "transFatG": 0,
                    "cholesterolMg": 0,
                    "sodiumMg": 0
                  },
                  "confidence": "high | medium | low",
                  "estimationNote": "추정 근거를 짧게"
                }
              ],
              "totalNutrition": {
                "caloriesKcal": 0, "carbohydrateG": 0, "sugarsG": 0, "dietaryFiberG": 0,
                "proteinG": 0, "fatG": 0, "saturatedFatG": 0, "transFatG": 0,
                "cholesterolMg": 0, "sodiumMg": 0
              }
            }

            [음식 아님 응답 스키마]
            {
              "isFood": false,
              "inputText": "사용자 입력 원문",
              "items": [],
              "totalNutrition": null,
              "error": {
                "code": "NOT_FOOD_OR_UNKNOWN",
                "message": "입력값에서 음식 또는 식품을 판단할 수 없습니다."
              }
            }
            """;

    private final ObjectMapper objectMapper;
    private final Timer analysisTimer;
    private final MeterRegistry meterRegistry;

    public AiNutritionEstimationService(ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.analysisTimer = Timer.builder("healthcare.diet.ai.analysis")
            .description("식단 AI 영양성분 분석 호출 지연")
            .register(meterRegistry);
    }

    @Value("${app.ai.meal.openai-api-key}")
    private String apiKey;

    @Value("${app.ai.meal.openai-base-url:https://api.openai.com}")
    private String baseUrl;

    @Value("${app.ai.meal.model:gpt-4.1-mini}")
    private String model;

    private RestClient client;

    @PostConstruct
    void initializeClient() {
        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public AiNutritionEstimateResponse estimate(String foodName) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            return doEstimate(foodName);
        } finally {
            sample.stop(analysisTimer);
        }
    }

    private AiNutritionEstimateResponse doEstimate(String foodName) {
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "instructions", INSTRUCTIONS,
                "input", List.of(Map.of(
                        "role", "user",
                        "content", "'" + foodName + "'의 영양성분을 위 규칙대로 JSON으로 반환해 주세요."
                ))
        );

        JsonNode rawResponse;
        try {
            rawResponse = client.post()
                    .uri("/v1/responses")
                    .body(requestBody)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (Exception e) {
            log.error("AI API 호출 실패: foodName={}, error={}", foodName, e.getMessage());
            return AiNutritionEstimateResponse.unavailable(foodName);
        }

        String jsonText = stripMarkdownFences(extractOutputText(rawResponse));

        JsonNode parsed;
        try {
            parsed = objectMapper.readTree(jsonText);
        } catch (Exception e) {
            log.error("AI 응답 JSON 파싱 실패: foodName={}, rawText={}, error={}",
                    foodName, jsonText, e.getMessage());
            return AiNutritionEstimateResponse.unavailable(foodName);
        }

        if (!parsed.path("isFood").asBoolean(true)) {
            return AiNutritionEstimateResponse.notFood(foodName);
        }

        List<EstimatedItem> items = parseItems(parsed.path("items"));
        NutritionFacts total = parseNutrition(parsed.path("totalNutrition"));
        if (total == null) {
            total = sumNutrition(items);
        }
        return AiNutritionEstimateResponse.ok(foodName, items, total);
    }

    // ─────────────────────────── 파싱 헬퍼 ───────────────────────────

    private List<EstimatedItem> parseItems(JsonNode itemsNode) {
        if (!itemsNode.isArray()) return List.of();
        List<EstimatedItem> items = new ArrayList<>();
        for (JsonNode node : itemsNode) {
            items.add(new EstimatedItem(
                    node.path("name").asText(""),
                    node.path("normalizedName").asText(""),
                    parseCategory(node.path("category").asText("OTHER")),
                    parseServingBasis(node.path("servingBasis").asText("PER_100G")),
                    node.path("servingDescription").asText(""),
                    node.path("estimatedWeightG").asDouble(0.0),
                    parseNutrition(node.path("nutrition")),
                    parseConfidence(node.path("confidence")),
                    node.path("estimationNote").asText("")
            ));
        }
        return items;
    }

    private NutritionFacts parseNutrition(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        return new NutritionFacts(
                node.path("caloriesKcal").asDouble(0.0),
                node.path("carbohydrateG").asDouble(0.0),
                node.path("sugarsG").asDouble(0.0),
                node.path("dietaryFiberG").asDouble(0.0),
                node.path("proteinG").asDouble(0.0),
                node.path("fatG").asDouble(0.0),
                node.path("saturatedFatG").asDouble(0.0),
                node.path("transFatG").asDouble(0.0),
                node.path("cholesterolMg").asDouble(0.0),
                node.path("sodiumMg").asDouble(0.0)
        );
    }

    /** items[].nutrition을 합산해 totalNutrition을 계산한다 (모델이 누락한 경우의 폴백). */
    private NutritionFacts sumNutrition(List<EstimatedItem> items) {
        double cal = 0, carb = 0, sugar = 0, fiber = 0, prot = 0;
        double fat = 0, sat = 0, trans = 0, chol = 0, sodium = 0;
        for (EstimatedItem item : items) {
            NutritionFacts n = item.nutrition();
            if (n == null) continue;
            cal    += orZero(n.caloriesKcal());
            carb   += orZero(n.carbohydrateG());
            sugar  += orZero(n.sugarsG());
            fiber  += orZero(n.dietaryFiberG());
            prot   += orZero(n.proteinG());
            fat    += orZero(n.fatG());
            sat    += orZero(n.saturatedFatG());
            trans  += orZero(n.transFatG());
            chol   += orZero(n.cholesterolMg());
            sodium += orZero(n.sodiumMg());
        }
        return new NutritionFacts(cal, carb, sugar, fiber, prot, fat, sat, trans, chol, sodium);
    }

    private FoodCategory parseCategory(String raw) {
        try {
            return FoodCategory.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            return FoodCategory.OTHER;
        }
    }

    private ServingBasis parseServingBasis(String raw) {
        try {
            return ServingBasis.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ServingBasis.PER_100G;
        }
    }

    /** 모델이 "high"/"medium"/"low" 문자열 또는 숫자로 반환하는 경우 모두 지원. */
    private double parseConfidence(JsonNode node) {
        if (node.isNumber()) return node.asDouble();
        return switch (node.asText("").toLowerCase()) {
            case "high"   -> 0.9;
            case "medium" -> 0.6;
            case "low"    -> 0.3;
            default       -> 0.5;
        };
    }

    private double orZero(Double value) {
        return value != null ? value : 0.0;
    }

    private String extractOutputText(JsonNode response) {
        if (response == null) return "{}";

        JsonNode direct = response.path("output_text");
        if (direct.isTextual()) return direct.asText();

        JsonNode output = response.path("output");
        if (output.isArray()) {
            for (JsonNode item : output) {
                JsonNode content = item.path("content");
                if (!content.isArray()) continue;
                for (JsonNode contentItem : content) {
                    JsonNode textNode = contentItem.path("text");
                    if (textNode.isTextual()) return textNode.asText();
                }
            }
        }
        return "{}";
    }

    /** OpenAI가 ```json ... ``` 형태로 감싸서 반환할 때 제거 */
    private String stripMarkdownFences(String text) {
        if (text == null) return "{}";
        String stripped = text.strip();
        if (stripped.startsWith("```")) {
            int firstNewline = stripped.indexOf('\n');
            if (firstNewline >= 0) {
                stripped = stripped.substring(firstNewline + 1).strip();
            }
            if (stripped.endsWith("```")) {
                stripped = stripped.substring(0, stripped.lastIndexOf("```")).strip();
            }
        }
        return stripped;
    }
}
