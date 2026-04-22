package com.healthcare.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.domain.user.dto.UserProfileResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.jackson.JsonComponentModule;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RedisConfigTest {

    private final RedisConfig redisConfig = new RedisConfig();

    @Test
    @DisplayName("Redis serializer는 Java Time 필드를 포함한 사용자 프로필을 직렬화/역직렬화할 수 있다")
    void redisTemplateSerializer_supportsJavaTimeTypes() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        objectMapper.registerModule(new JsonComponentModule());
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);

        RedisTemplate<String, Object> redisTemplate = redisConfig.redisTemplate(connectionFactory, objectMapper);

        UserProfileResponse response = UserProfileResponse.builder()
            .id(10L)
            .email("test@example.com")
            .displayName("테스터")
            .dateOfBirth(LocalDate.of(1995, 5, 10))
            .createdAt(OffsetDateTime.parse("2026-04-22T22:37:50+09:00"))
            .onboardingCompleted(true)
            .build();

        @SuppressWarnings("unchecked")
        RedisSerializer<Object> serializer = (RedisSerializer<Object>) redisTemplate.getValueSerializer();

        byte[] serialized = serializer.serialize(response);

        assertThat(serialized).isNotNull();
        assertThat(serializer.deserialize(serialized))
            .isInstanceOf(UserProfileResponse.class)
            .usingRecursiveComparison()
            .isEqualTo(response);
    }
}
