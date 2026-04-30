package com.healthcare.domain.exercise.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.domain.exercise.ai.dto.AiExerciseEstimateResponse;
import com.healthcare.domain.exercise.entity.ExerciseCatalog.ExerciseType;
import com.healthcare.domain.exercise.entity.ExerciseCatalog.MuscleGroup;
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

@DisplayName("AiExerciseEstimationService 단위 테스트")
class AiExerciseEstimationServiceTest {

    private AiExerciseEstimationService service;

    private RestClient mockClient;
    private RestClient.RequestBodyUriSpec postSpec;
    private RestClient.ResponseSpec responseSpec;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new AiExerciseEstimationService(objectMapper);

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
        @DisplayName("유효한 응답 — 운동 정보 모두 파싱")
        void estimate_validResponse_parsesAllFields() throws Exception {
            givenApiReturns(buildResponse(
                    "{\"muscleGroup\":\"CHEST\",\"exerciseType\":\"STRENGTH\","
                    + "\"metValue\":8.0,\"confidence\":0.9}"));

            AiExerciseEstimateResponse result = service.estimate("벤치프레스");

            assertThat(result.getExerciseName()).isEqualTo("벤치프레스");
            assertThat(result.getMuscleGroup()).isEqualTo(MuscleGroup.CHEST);
            assertThat(result.getExerciseType()).isEqualTo(ExerciseType.STRENGTH);
            assertThat(result.getMetValue()).isEqualTo(8.0);
            assertThat(result.getConfidence()).isEqualTo(0.9);
        }

        @Test
        @DisplayName("output_text 직접 필드 — 파싱 성공")
        void estimate_outputTextDirectField_parsed() throws Exception {
            String inner = "{\\\"muscleGroup\\\":\\\"CARDIO\\\",\\\"exerciseType\\\":\\\"CARDIO\\\","
                    + "\\\"metValue\\\":7.0,\\\"confidence\\\":0.8}";
            JsonNode root = objectMapper.readTree("{\"output_text\": \"" + inner + "\"}");
            givenApiReturns(root);

            AiExerciseEstimateResponse result = service.estimate("달리기");

            assertThat(result.getMuscleGroup()).isEqualTo(MuscleGroup.CARDIO);
            assertThat(result.getExerciseType()).isEqualTo(ExerciseType.CARDIO);
            assertThat(result.getMetValue()).isEqualTo(7.0);
        }

        @Test
        @DisplayName("isAiEstimated 는 항상 true")
        void estimate_isAiEstimatedAlwaysTrue() throws Exception {
            givenApiReturns(buildResponse(
                    "{\"muscleGroup\":\"FULL_BODY\",\"exerciseType\":\"BODYWEIGHT\","
                    + "\"metValue\":5.0,\"confidence\":0.7}"));

            assertThat(service.estimate("버피").isAiEstimated()).isTrue();
        }

        @Test
        @DisplayName("disclaimer 필드 비어있지 않음")
        void estimate_disclaimerPresent() throws Exception {
            givenApiReturns(buildResponse(
                    "{\"muscleGroup\":\"CORE\",\"exerciseType\":\"BODYWEIGHT\","
                    + "\"metValue\":4.0,\"confidence\":0.6}"));

            assertThat(service.estimate("플랭크").getDisclaimer()).isNotBlank();
        }
    }

    // ─────────────────────────── 열거형 매핑 ───────────────────────────

    @Nested
    @DisplayName("열거형 파싱")
    class EnumParsing {

        @ParameterizedTest
        @ValueSource(strings = {"CHEST", "BACK", "SHOULDERS", "BICEPS", "TRICEPS",
                "FOREARMS", "CORE", "QUADRICEPS", "HAMSTRINGS", "GLUTES",
                "CALVES", "FULL_BODY", "CARDIO", "OTHER"})
        @DisplayName("유효한 MuscleGroup — 정상 매핑")
        void estimate_validMuscleGroup_mapsCorrectly(String group) throws Exception {
            givenApiReturns(buildResponse(
                    "{\"muscleGroup\":\"" + group + "\","
                    + "\"exerciseType\":\"STRENGTH\","
                    + "\"metValue\":5.0,\"confidence\":0.7}"));

            assertThat(service.estimate("운동").getMuscleGroup())
                    .isEqualTo(MuscleGroup.valueOf(group));
        }

        @ParameterizedTest
        @ValueSource(strings = {"STRENGTH", "CARDIO", "BODYWEIGHT", "FLEXIBILITY", "SPORTS"})
        @DisplayName("유효한 ExerciseType — 정상 매핑")
        void estimate_validExerciseType_mapsCorrectly(String type) throws Exception {
            givenApiReturns(buildResponse(
                    "{\"muscleGroup\":\"OTHER\","
                    + "\"exerciseType\":\"" + type + "\","
                    + "\"metValue\":5.0,\"confidence\":0.7}"));

            assertThat(service.estimate("운동").getExerciseType())
                    .isEqualTo(ExerciseType.valueOf(type));
        }

        @ParameterizedTest
        @ValueSource(strings = {"UNKNOWN", "INVALID", "123"})
        @DisplayName("잘못된 MuscleGroup — OTHER 폴백")
        void estimate_invalidMuscleGroup_defaultsToOther(String group) throws Exception {
            givenApiReturns(buildResponse(
                    "{\"muscleGroup\":\"" + group + "\","
                    + "\"exerciseType\":\"STRENGTH\","
                    + "\"metValue\":5.0,\"confidence\":0.5}"));

            assertThat(service.estimate("운동").getMuscleGroup()).isEqualTo(MuscleGroup.OTHER);
        }

        @ParameterizedTest
        @ValueSource(strings = {"UNKNOWN", "INVALID", "YOGA"})
        @DisplayName("잘못된 ExerciseType — STRENGTH 폴백")
        void estimate_invalidExerciseType_defaultsToStrength(String type) throws Exception {
            givenApiReturns(buildResponse(
                    "{\"muscleGroup\":\"OTHER\","
                    + "\"exerciseType\":\"" + type + "\","
                    + "\"metValue\":5.0,\"confidence\":0.5}"));

            assertThat(service.estimate("운동").getExerciseType()).isEqualTo(ExerciseType.STRENGTH);
        }
    }

    // ─────────────────────────── 오류 처리 ───────────────────────────

    @Nested
    @DisplayName("오류 및 폴백")
    class ErrorHandling {

        @Test
        @DisplayName("API 예외 — MET=4.0, confidence=0.0, isAiEstimated=true")
        void estimate_apiThrowsException_returnsFallback() {
            givenApiThrows(new RuntimeException("network error"));

            AiExerciseEstimateResponse result = service.estimate("알 수 없는 운동");

            assertThat(result.getExerciseName()).isEqualTo("알 수 없는 운동");
            assertThat(result.getMuscleGroup()).isEqualTo(MuscleGroup.OTHER);
            assertThat(result.getExerciseType()).isEqualTo(ExerciseType.STRENGTH);
            assertThat(result.getMetValue()).isEqualTo(4.0);
            assertThat(result.getConfidence()).isEqualTo(0.0);
            assertThat(result.isAiEstimated()).isTrue();
            assertThat(result.getDisclaimer()).isNotBlank();
        }

        @Test
        @DisplayName("null 응답 — MET 기본값 4.0, confidence 기본값 0.5")
        void estimate_nullResponse_usesDefaults() {
            // null → extractOutputText → "{}" → 기본값 경로 (catch 블록 아님)
            givenApiReturns(null);

            AiExerciseEstimateResponse result = service.estimate("운동");

            assertThat(result.getMetValue()).isEqualTo(4.0);
            assertThat(result.getConfidence()).isEqualTo(0.5);
        }

        @Test
        @DisplayName("빈 JSON {} — asDouble 기본값 사용")
        void estimate_emptyJson_usesAsDoubleDefaults() throws Exception {
            givenApiReturns(buildResponse("{}"));

            AiExerciseEstimateResponse result = service.estimate("테스트");

            assertThat(result.getMetValue()).isEqualTo(4.0);
            assertThat(result.getConfidence()).isEqualTo(0.5);
            assertThat(result.getMuscleGroup()).isEqualTo(MuscleGroup.OTHER);
            assertThat(result.getExerciseType()).isEqualTo(ExerciseType.STRENGTH);
        }

        @Test
        @DisplayName("빈 output 배열 — 기본값 사용")
        void estimate_emptyOutputArray_usesDefaults() throws Exception {
            givenApiReturns(objectMapper.readTree("{\"output\":[]}"));

            AiExerciseEstimateResponse result = service.estimate("운동");

            assertThat(result.getMetValue()).isEqualTo(4.0);
        }
    }
}
