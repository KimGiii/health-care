package com.healthcare.security.oauth;

/**
 * 외부 OAuth 제공자(Apple, Google)의 ID 토큰에서 추출한 사용자 정보.
 *
 * @param subject       제공자별 고유 사용자 ID (Apple/Google JWT 의 sub 클레임)
 * @param email         사용자 이메일 (Apple 의 private relay 가능)
 * @param emailVerified 이메일 검증 여부 (검증되지 않은 이메일은 자동 연결에서 제외해야 함)
 * @param name          표시 이름 (없을 수 있음)
 */
public record OAuthUserInfo(
        String subject,
        String email,
        boolean emailVerified,
        String name
) {
}
