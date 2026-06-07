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
                당신은 헬스케어 앱의 운동 정보 추정 전문가입니다.
                사용자가 입력한 운동 이름(한국어 또는 영어 음차)을 분석해 운동 정보를 JSON으로 반환하세요.
                응답은 반드시 JSON 객체 하나여야 하며, 코드 블록이나 설명 문장을 포함하지 마세요.

                ## 운동 이름 해석 규칙
                - 한국어 음차(예: 랫풀다운=Lat Pulldown, 데드리프트=Deadlift, 벤치프레스=Bench Press)를 정확히 인식하세요.
                - 영문 약어도 인식하세요 (예: OHP=Overhead Press, RDL=Romanian Deadlift, DB=Dumbbell).
                - 변형 동작(예: 인클라인 벤치프레스, 와이드그립 풀업)도 기본 동작 기준으로 분류하세요.

                ## muscleGroup 분류 기준
                반드시 다음 중 하나를 선택하세요:
                CHEST(가슴), BACK(등), SHOULDERS(어깨), BICEPS(이두), TRICEPS(삼두),
                FOREARMS(전완), CORE(복근/코어), QUADRICEPS(대퇴사두), HAMSTRINGS(햄스트링),
                GLUTES(둔근), CALVES(종아리), FULL_BODY(전신), CARDIO(유산소), OTHER(기타)
                → 주동근 기준으로 선택. 복합 운동(스쿼트, 데드리프트)은 주된 자극 부위로 분류.

                ## exerciseType 분류 기준
                반드시 다음 중 하나를 선택하세요:
                - STRENGTH: 바벨/덤벨/머신을 이용한 중량 운동
                - BODYWEIGHT: 맨몸 운동 (풀업, 푸쉬업, 딥스 등)
                - CARDIO: 유산소 운동 (달리기, 사이클, 로잉 등)
                - FLEXIBILITY: 스트레칭, 요가, 필라테스
                - SPORTS: 구기 종목, 격투기 등 스포츠

                ## MET 값 기준 범위
                - STRENGTH 저강도(머신, 격리운동): 3.0~4.5
                - STRENGTH 중강도(덤벨 복합운동): 4.5~6.0
                - STRENGTH 고강도(바벨 복합운동, 데드리프트/스쿼트): 6.0~8.0
                - BODYWEIGHT 저강도(플랭크, 크런치): 3.0~4.0
                - BODYWEIGHT 고강도(풀업, 딥스, 버피): 5.0~8.0
                - CARDIO 저강도(걷기, 스트레칭): 3.0~5.0
                - CARDIO 중강도(빠른 걷기, 사이클): 5.0~8.0
                - CARDIO 고강도(달리기, HIIT, 점프로프): 8.0~15.0
                - FLEXIBILITY: 2.5~4.0

                ## confidence 기준
                - 0.9~1.0: 정확히 알고 있는 표준 운동명
                - 0.7~0.9: 변형 동작이거나 음차를 통해 인식한 경우
                - 0.5~0.7: 운동명이 모호하지만 추정 가능한 경우
                - 0.5 미만: 운동명을 인식하기 어려운 경우

                ## 응답 예시
                입력: "랫풀다운"
                {"muscleGroup":"BACK","exerciseType":"STRENGTH","metValue":5.0,"confidence":0.95}

                입력: "버피"
                {"muscleGroup":"FULL_BODY","exerciseType":"BODYWEIGHT","metValue":10.0,"confidence":0.95}

                입력: "인클라인 덤벨 플라이"
                {"muscleGroup":"CHEST","exerciseType":"STRENGTH","metValue":4.5,"confidence":0.9}

                입력: "루마니안 데드리프트"
                {"muscleGroup":"HAMSTRINGS","exerciseType":"STRENGTH","metValue":6.5,"confidence":0.95}

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
                        "content", "운동명: '" + exerciseName + "'"
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
