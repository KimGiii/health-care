package com.healthcare.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 운영성 admin 작업 진입부에서 별도 operation token을 검증한다.
 * 일반 사용자 JWT 인증과 분리해, 대량 데이터 변경 작업을 명시적인 seam 뒤에 둔다.
 */
@Component
public class AdminOperationGuard {

    public static final String HEADER_NAME = "X-Admin-Token";

    private final String operationToken;

    public AdminOperationGuard(@Value("${app.admin.operation-token:}") String operationToken) {
        this.operationToken = operationToken;
    }

    public void assertAllowed(String providedToken) {
        if (!StringUtils.hasText(operationToken) || !StringUtils.hasText(providedToken)
                || !constantTimeEquals(operationToken, providedToken)) {
            throw new AccessDeniedException("관리자 작업 권한이 없습니다.");
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = actual.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, actualBytes);
    }
}
