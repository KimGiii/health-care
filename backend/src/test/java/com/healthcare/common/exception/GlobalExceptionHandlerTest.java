package com.healthcare.common.exception;

import com.healthcare.common.response.ErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("DataIntegrityViolationException(동시 가입 UNIQUE 충돌)은 500 이 아닌 409 로 변환된다")
    void handleDataIntegrityViolation_returnsConflict() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"uq_user_identities_provider_subject\"");

        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("CONFLICT");
        assertThat(response.getBody().getMessage()).contains("다시 시도");
    }
}
