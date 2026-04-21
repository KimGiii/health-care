package com.healthcare.domain.diet.mealphoto.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component("openAiMealAnalysisProvider")
@RequiredArgsConstructor
@ConditionalOnExpression("'${app.ai.meal.openai-api-key:}' != ''")
public class OpenAiMealAnalysisProvider implements MealAnalysisProvider {

    private final ObjectMapper objectMapper;

    @Value("${app.ai.meal.openai-api-key}")
    private String apiKey;

    @Value("${app.ai.meal.openai-base-url:https://api.openai.com}")
    private String baseUrl;

    @Value("${app.ai.meal.model:gpt-4.1-mini}")
    private String model;

    @Override
    public AnalysisResult analyze(String imageDataUrl, String contentType) {
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("OpenAI API key is missing.");
        }

        RestClient client = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        String instructions = """
                당신은 한국 식단 기록 서비스를 위한 식사 사진 분석기입니다.
                응답은 반드시 JSON 객체 하나여야 하며, 설명 문장은 포함하지 마세요.
                추정값은 보수적으로 제시하고, 불확실하면 needsReview=true 와 unknown_or_uncertain에 이유를 적으세요.
                소스, 오일, 국물, 숨은 당류, 토핑은 보이지 않으면 단정하지 마세요.
                JSON schema:
                {
                  "warnings": ["string"],
                  "items": [
                    {
                      "label": "string",
                      "estimatedServingG": number,
                      "calories": number,
                      "proteinG": number,
                      "carbsG": number,
                      "fatG": number,
                      "confidence": number,
                      "needsReview": boolean,
                      "unknownOrUncertain": "string|null"
                    }
                  ]
                }
                """;

        Map<String, Object> body = Map.of(
                "model", model,
                "instructions", instructions,
                "input", List.of(Map.of(
                        "role", "user",
                        "content", List.of(
                                Map.of("type", "input_text", "text", "이 식사 사진을 분석해서 식단 기록 초안을 JSON으로 반환하세요."),
                                Map.of("type", "input_image", "image_url", imageDataUrl, "detail", "low")
                        )
                ))
        );

        JsonNode response = client.post()
                .uri("/v1/responses")
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        String rawOutput = response != null ? response.toString() : "{}";
        String outputText = extractOutputText(response);
        try {
            JsonNode parsed = objectMapper.readTree(outputText);
            List<String> warnings = new ArrayList<>();
            JsonNode warningsNode = parsed.path("warnings");
            if (warningsNode.isArray()) {
                warningsNode.forEach(node -> warnings.add(node.asText()));
            }

            List<DetectedItem> items = new ArrayList<>();
            JsonNode itemsNode = parsed.path("items");
            if (itemsNode.isArray()) {
                for (JsonNode item : itemsNode) {
                    items.add(new DetectedItem(
                            item.path("label").asText("알 수 없는 음식"),
                            item.path("estimatedServingG").asDouble(100.0),
                            item.path("calories").asDouble(0.0),
                            item.path("proteinG").asDouble(0.0),
                            item.path("carbsG").asDouble(0.0),
                            item.path("fatG").asDouble(0.0),
                            item.path("confidence").asDouble(0.0),
                            item.path("needsReview").asBoolean(true),
                            item.path("unknownOrUncertain").isMissingNode() || item.path("unknownOrUncertain").isNull()
                                    ? null
                                    : item.path("unknownOrUncertain").asText()
                    ));
                }
            }

            if (items.isEmpty()) {
                warnings.add("AI가 신뢰할 수 있는 항목을 찾지 못했습니다. 수동 수정이 필요합니다.");
                items = List.of(new DetectedItem(
                        "분석 실패",
                        100.0,
                        150.0,
                        5.0,
                        15.0,
                        5.0,
                        0.1,
                        true,
                        "이미지 품질 또는 음식 구성의 복잡성 때문에 정확한 인식이 어렵습니다."
                ));
            }

            return new AnalysisResult("openai", model, rawOutput, warnings, items);
        } catch (Exception e) {
            return new AnalysisResult(
                    "openai",
                    model,
                    rawOutput,
                    List.of("AI 응답을 구조화하지 못해 검토용 초안으로 대체합니다."),
                    List.of(new DetectedItem(
                            "분석 결과 확인 필요",
                            100.0,
                            150.0,
                            5.0,
                            15.0,
                            5.0,
                            0.1,
                            true,
                            "AI 응답 파싱 실패: " + e.getMessage()
                    ))
            );
        }
    }

    private String extractOutputText(JsonNode response) {
        if (response == null) {
            return "{\"warnings\":[],\"items\":[]}";
        }

        JsonNode direct = response.path("output_text");
        if (direct.isTextual()) {
            return direct.asText();
        }

        JsonNode output = response.path("output");
        if (output.isArray()) {
            for (JsonNode item : output) {
                JsonNode content = item.path("content");
                if (!content.isArray()) {
                    continue;
                }
                for (JsonNode contentItem : content) {
                    JsonNode textNode = contentItem.path("text");
                    if (textNode.isTextual()) {
                        return textNode.asText();
                    }
                }
            }
        }

        return "{\"warnings\":[\"AI 응답 텍스트를 찾지 못했습니다.\"],\"items\":[]}";
    }
}
