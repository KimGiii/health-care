package com.healthcare.domain.exercise.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.domain.exercise.ai.dto.AiExerciseEstimateResponse;
import com.healthcare.domain.exercise.entity.ExerciseCatalog.ExerciseType;
import com.healthcare.domain.exercise.entity.ExerciseCatalog.MuscleGroup;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnExpression("'${app.ai.meal.openai-api-key:}' != ''")
public class AiExerciseEstimationService {

    private static final String DISCLAIMER =
            "AI 추정값이며 실제 소모 칼로리와 다를 수 있습니다. 수정 후 저장하세요.";

    private final ObjectMapper objectMapper;

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

    public AiExerciseEstimateResponse estimate(String exerciseName) {
        String instructions = """
                당신은 한국 헬스케어 앱의 운동 정보 추정기입니다.
                사용자가 한국어 운동 이름을 입력하면 운동 정보를 추정해 JSON으로 반환하세요.
                응답은 반드시 JSON 객체 하나여야 하며, 설명 문장은 포함하지 마세요.
                MET(신진대사 당량) 값은 해당 운동의 평균 강도를 기준으로 추정하세요.
                muscleGroup은 반드시 다음 중 하나: CHEST, BACK, SHOULDERS, BICEPS, TRICEPS, FOREARMS, CORE, QUADRICEPS, HAMSTRINGS, GLUTES, CALVES, FULL_BODY, CARDIO, OTHER
                exerciseType은 반드시 다음 중 하나: STRENGTH, CARDIO, BODYWEIGHT, FLEXIBILITY, SPORTS
                JSON schema:
                {
                  "muscleGroup": "string",
                  "exerciseType": "string",
                  "metValue": number,
                  "confidence": number
                }
                """;

        Map<String, Object> body = Map.of(
                "model", model,
                "instructions", instructions,
                "input", List.of(Map.of(
                        "role", "user",
                        "content", "'" + exerciseName + "' 운동의 정보(muscleGroup, exerciseType, MET값)를 JSON으로 추정해 주세요."
                ))
        );

        try {
            JsonNode response = client.post()
                    .uri("/v1/responses")
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            String outputText = extractOutputText(response);
            JsonNode parsed = objectMapper.readTree(outputText);

            MuscleGroup muscleGroup = parseMuscleGroup(parsed.path("muscleGroup").asText("OTHER"));
            ExerciseType exerciseType = parseExerciseType(parsed.path("exerciseType").asText("STRENGTH"));

            return AiExerciseEstimateResponse.builder()
                    .exerciseName(exerciseName)
                    .muscleGroup(muscleGroup)
                    .exerciseType(exerciseType)
                    .metValue(parsed.path("metValue").asDouble(4.0))
                    .confidence(parsed.path("confidence").asDouble(0.5))
                    .disclaimer(DISCLAIMER)
                    .isAiEstimated(true)
                    .build();

        } catch (Exception e) {
            log.warn("AI 운동 정보 추정 실패: exerciseName={}, error={}", exerciseName, e.getMessage());
            return AiExerciseEstimateResponse.builder()
                    .exerciseName(exerciseName)
                    .muscleGroup(MuscleGroup.OTHER)
                    .exerciseType(ExerciseType.STRENGTH)
                    .metValue(4.0)
                    .confidence(0.0)
                    .disclaimer(DISCLAIMER)
                    .isAiEstimated(true)
                    .build();
        }
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

    private MuscleGroup parseMuscleGroup(String raw) {
        try {
            return MuscleGroup.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            return MuscleGroup.OTHER;
        }
    }

    private ExerciseType parseExerciseType(String raw) {
        try {
            return ExerciseType.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ExerciseType.STRENGTH;
        }
    }
}
