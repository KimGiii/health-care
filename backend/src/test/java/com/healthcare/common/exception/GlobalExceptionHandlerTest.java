package com.healthcare.common.exception;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.healthcare.common.response.ErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;

import java.io.IOException;
import java.lang.reflect.Method;

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

    @Test
    @DisplayName("클라이언트 연결 중단 예외는 미처리 500 예외 핸들러로 라우팅하지 않는다")
    void asyncRequestNotUsable_resolvesToClientAbortHandler() {
        ExceptionHandlerMethodResolver resolver = new ExceptionHandlerMethodResolver(GlobalExceptionHandler.class);

        Method method = resolver.resolveMethod(new AsyncRequestNotUsableException(
                "ServletOutputStream failed to flush: java.io.IOException: Broken pipe",
                new IOException("Broken pipe")));

        assertThat(method).isNotNull();
        assertThat(method.getName()).isEqualTo("handleAsyncRequestNotUsable");
    }

    @Test
    @DisplayName("클라이언트 연결 중단 예외는 ERROR 로그를 남기지 않는다")
    void handleAsyncRequestNotUsable_doesNotLogError() {
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        Level originalLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);

        try {
            handler.handleAsyncRequestNotUsable(new AsyncRequestNotUsableException(
                    "ServletOutputStream failed to flush: java.io.IOException: Broken pipe",
                    new IOException("Broken pipe")));
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(originalLevel);
        }

        assertThat(appender.list)
                .noneMatch(event -> event.getLevel().isGreaterOrEqual(Level.ERROR));
        assertThat(appender.list)
                .anyMatch(event -> event.getFormattedMessage().contains("Client disconnected before response completed"));
    }
}
