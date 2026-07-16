package com.healthcare.domain.diet.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * OpenAI Responses API 호출 전담 클라이언트.
 *
 * <p>OpenAI의 일시적 서버 오류(5xx)나 네트워크 타임아웃은 재시도로 흡수한다.
 * 잘못된 요청·인증 오류(4xx)는 재시도하지 않고 즉시 폴백한다.
 * 재시도를 모두 소진하면 {@link #recover}가 {@code null}을 반환하며, 호출 측은
 * 이를 "AI 사용 불가"로 처리한다.
 *
 * <p>{@code @Retryable}은 Spring AOP 프록시로 동작하므로 외부 빈
 * (AiNutritionEstimationService)에서 호출해야 한다. 자기 호출(self-invocation)은
 * 프록시를 우회해 재시도가 적용되지 않는다.
 */
@Slf4j
@Component
@ConditionalOnExpression("'${app.ai.meal.openai-api-key:}' != ''")
public class OpenAiResponsesClient {

    private static final String RESPONSES_PATH = "/v1/responses";

    @Value("${app.ai.meal.openai-api-key:}")
    private String apiKey;

    @Value("${app.ai.meal.openai-base-url:https://api.openai.com}")
    private String baseUrl;

    /** 패키지 가시성: 테스트에서 RestClient 모킹을 위해 주입 가능하게 둔다. */
    RestClient client;

    @PostConstruct
    void initializeClient() {
        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Responses API를 호출하고 원본 JSON 응답을 반환한다.
     * 5xx 또는 타임아웃 시 지수 백오프로 최대 3회까지 재시도한다.
     */
    @Retryable(
            retryFor = {HttpServerErrorException.class, ResourceAccessException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 500, multiplier = 2.0, maxDelay = 4000))
    public JsonNode createResponse(Map<String, Object> requestBody) {
        return client.post()
                .uri(RESPONSES_PATH)
                .body(requestBody)
                .retrieve()
                .body(JsonNode.class);
    }

    /**
     * 재시도 소진(또는 재시도 불가 예외) 시 폴백.
     * 예외를 전파하지 않고 {@code null}을 반환해 호출 측이 graceful degradation 하도록 한다.
     */
    @Recover
    JsonNode recover(Exception e, Map<String, Object> requestBody) {
        log.error("OpenAI Responses API 호출 실패(재시도 종료): error={}", e.getMessage());
        return null;
    }
}
