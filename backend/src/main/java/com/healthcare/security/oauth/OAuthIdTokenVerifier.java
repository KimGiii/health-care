package com.healthcare.security.oauth;

/**
 * 외부 OAuth 제공자(Apple, Google)의 ID 토큰(JWT)을 JWKS 로 서명·클레임 검증한다.
 * 실패 시 {@link com.healthcare.common.exception.UnauthorizedException} 을 던진다.
 */
public interface OAuthIdTokenVerifier {

    /**
     * @param idToken       제공자가 발급한 ID 토큰(JWT)
     * @param expectedNonce 토큰의 {@code nonce} 클레임과 일치해야 하는 값.
     *                      {@code null} 이면 nonce 검증을 건너뛴다(구버전 클라이언트 하위호환).
     *                      값이 있으면 토큰의 nonce 가 없거나 다를 때 {@code UnauthorizedException}.
     */
    OAuthUserInfo verify(String idToken, String expectedNonce);
}
