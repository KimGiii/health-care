package com.healthcare.security.oauth;

/**
 * 외부 OAuth 제공자(Apple, Google)의 ID 토큰(JWT)을 JWKS 로 서명·클레임 검증한다.
 * 실패 시 {@link com.healthcare.common.exception.UnauthorizedException} 을 던진다.
 */
public interface OAuthIdTokenVerifier {
    OAuthUserInfo verify(String idToken);
}
