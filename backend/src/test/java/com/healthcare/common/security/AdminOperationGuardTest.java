package com.healthcare.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AdminOperationGuard")
class AdminOperationGuardTest {

    @Test
    @DisplayName("설정 토큰과 요청 토큰이 같으면 통과한다")
    void assertAllowed_allowsMatchingToken() {
        AdminOperationGuard guard = new AdminOperationGuard("secret-token");

        assertThatCode(() -> guard.assertAllowed("secret-token"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("요청 토큰이 없으면 거부한다")
    void assertAllowed_rejectsMissingToken() {
        AdminOperationGuard guard = new AdminOperationGuard("secret-token");

        assertThatThrownBy(() -> guard.assertAllowed(null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("요청 토큰이 다르면 거부한다")
    void assertAllowed_rejectsDifferentToken() {
        AdminOperationGuard guard = new AdminOperationGuard("secret-token");

        assertThatThrownBy(() -> guard.assertAllowed("wrong-token"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("설정 토큰이 비어 있으면 fail-closed로 거부한다")
    void assertAllowed_rejectsWhenConfiguredTokenIsBlank() {
        AdminOperationGuard guard = new AdminOperationGuard("");

        assertThatThrownBy(() -> guard.assertAllowed("secret-token"))
                .isInstanceOf(AccessDeniedException.class);
    }
}
