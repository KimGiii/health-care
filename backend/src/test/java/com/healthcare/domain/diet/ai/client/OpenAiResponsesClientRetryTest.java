package com.healthcare.domain.diet.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Spring Retry AOP가 실제로 동작하는 컨텍스트에서 재시도 정책을 검증한다.
 * - 5xx/타임아웃: 최대 3회까지 재시도
 * - 4xx: 재시도 없이 즉시 폴백
 */
@ExtendWith(SpringExtension.class)
@DisplayName("OpenAiResponsesClient 재시도 정책")
class OpenAiResponsesClientRetryTest {

    @Configuration
    @EnableRetry
    static class RetryTestConfig {
        @Bean
        OpenAiResponsesClient openAiResponsesClient() {
            return new OpenAiResponsesClient();
        }
    }

    @Autowired
    private OpenAiResponsesClient client;

    private RestClient restClient;
    private RestClient.RequestBodyUriSpec postSpec;
    private RestClient.ResponseSpec responseSpec;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        restClient   = mock(RestClient.class);
        postSpec     = mock(RestClient.RequestBodyUriSpec.class);
        responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.post()).thenReturn(postSpec);
        when(postSpec.uri(anyString())).thenReturn(postSpec);
        when(postSpec.body(org.mockito.ArgumentMatchers.any(Object.class))).thenReturn(postSpec);
        when(postSpec.retrieve()).thenReturn(responseSpec);

        // @PostConstruct가 만든 실제 RestClient를 모킹된 것으로 교체
        ReflectionTestUtils.setField(client, "client", restClient);
    }

    @Test
    @DisplayName("5xx가 2회 발생해도 3번째 시도에서 성공하면 응답을 반환한다")
    void retriesOn5xx_thenSucceeds() throws Exception {
        JsonNode success = objectMapper.readTree("{\"output_text\":\"ok\"}");
        when(responseSpec.body(JsonNode.class))
                .thenThrow(HttpServerErrorException.create(
                        HttpStatus.INTERNAL_SERVER_ERROR, "server_error", null, null, null))
                .thenThrow(HttpServerErrorException.create(
                        HttpStatus.INTERNAL_SERVER_ERROR, "server_error", null, null, null))
                .thenReturn(success);

        JsonNode result = client.createResponse(Map.of("model", "gpt-4.1-mini"));

        assertThat(result).isNotNull();
        verify(responseSpec, times(3)).body(JsonNode.class);
    }

    @Test
    @DisplayName("타임아웃(ResourceAccessException)도 재시도한다")
    void retriesOnTimeout_thenSucceeds() throws Exception {
        JsonNode success = objectMapper.readTree("{\"output_text\":\"ok\"}");
        when(responseSpec.body(JsonNode.class))
                .thenThrow(new ResourceAccessException("I/O timeout"))
                .thenReturn(success);

        JsonNode result = client.createResponse(Map.of("model", "gpt-4.1-mini"));

        assertThat(result).isNotNull();
        verify(responseSpec, times(2)).body(JsonNode.class);
    }

    @Test
    @DisplayName("재시도를 모두 소진하면 recover가 null을 반환한다")
    void exhaustsRetries_recoversWithNull() {
        when(responseSpec.body(JsonNode.class))
                .thenThrow(HttpServerErrorException.create(
                        HttpStatus.SERVICE_UNAVAILABLE, "unavailable", null, null, null));

        JsonNode result = client.createResponse(Map.of("model", "gpt-4.1-mini"));

        assertThat(result).isNull();
        verify(responseSpec, times(3)).body(JsonNode.class); // maxAttempts=3
    }

    @Test
    @DisplayName("4xx는 재시도하지 않고 즉시 폴백한다")
    void doesNotRetryOn4xx() {
        when(responseSpec.body(JsonNode.class))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.BAD_REQUEST, "bad_request", null, null, null));

        JsonNode result = client.createResponse(Map.of("model", "gpt-4.1-mini"));

        assertThat(result).isNull();
        verify(responseSpec, times(1)).body(JsonNode.class); // 재시도 없음
    }
}
