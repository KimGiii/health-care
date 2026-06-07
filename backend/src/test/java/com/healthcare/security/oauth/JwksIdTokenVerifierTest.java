package com.healthcare.security.oauth;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkException;
import com.auth0.jwk.JwkProvider;
import com.healthcare.common.exception.UnauthorizedException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link JwksIdTokenVerifier} 공통 검증 로직 단위 테스트.
 *
 * <p>실제 외부 호출 없이, {@link JwkProvider} 를 모킹하고 테스트용 RSA 키페어로
 * JWT 를 직접 서명하여 검증 로직을 격리한다.
 */
class JwksIdTokenVerifierTest {

    private static final String ISSUER = "https://test.issuer";
    private static final String AUDIENCE = "com.healthcare.test";
    private static final String KID = "test-kid-1";

    private KeyPair keyPair;
    private JwkProvider jwkProvider;
    private JwksIdTokenVerifier verifier;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        keyPair = kpg.generateKeyPair();

        jwkProvider = mock(JwkProvider.class);
        Jwk jwk = mock(Jwk.class);
        when(jwk.getPublicKey()).thenReturn(keyPair.getPublic());
        when(jwkProvider.get(eq(KID))).thenReturn(jwk);

        verifier = new TestVerifier(jwkProvider, Set.of(ISSUER), AUDIENCE);
    }

    @Test
    @DisplayName("유효한 토큰은 OAuthUserInfo 로 디코딩된다")
    void verify_validToken_returnsUserInfo() {
        String token = buildToken(ISSUER, AUDIENCE, "sub-123", "user@example.com", true, "홍길동",
                Instant.now().plusSeconds(60));

        OAuthUserInfo info = verifier.verify(token, null);

        assertThat(info.subject()).isEqualTo("sub-123");
        assertThat(info.email()).isEqualTo("user@example.com");
        assertThat(info.emailVerified()).isTrue();
        assertThat(info.name()).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("email_verified 가 문자열 'true' 로 와도 boolean true 로 파싱된다 (Apple)")
    void verify_emailVerifiedAsString_parsedCorrectly() {
        String token = buildToken(ISSUER, AUDIENCE, "sub-1", "u@e.com", "true", null,
                Instant.now().plusSeconds(60));

        OAuthUserInfo info = verifier.verify(token, null);

        assertThat(info.emailVerified()).isTrue();
    }

    @Test
    @DisplayName("issuer 가 허용 목록에 없으면 401")
    void verify_wrongIssuer_throwsUnauthorized() {
        String token = buildToken("https://evil.com", AUDIENCE, "sub-1", "u@e.com", true, null,
                Instant.now().plusSeconds(60));

        assertThatThrownBy(() -> verifier.verify(token, null))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("issuer");
    }

    @Test
    @DisplayName("audience 가 다르면 401")
    void verify_wrongAudience_throwsUnauthorized() {
        String token = buildToken(ISSUER, "other.app", "sub-1", "u@e.com", true, null,
                Instant.now().plusSeconds(60));

        assertThatThrownBy(() -> verifier.verify(token, null))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("만료된 토큰은 401")
    void verify_expiredToken_throwsUnauthorized() {
        String token = buildToken(ISSUER, AUDIENCE, "sub-1", "u@e.com", true, null,
                Instant.now().minusSeconds(60));

        assertThatThrownBy(() -> verifier.verify(token, null))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("kid 헤더가 JWKS 에 없으면 401")
    void verify_unknownKid_throwsUnauthorized() throws JwkException {
        when(jwkProvider.get(eq("unknown-kid"))).thenThrow(new JwkException("not found"));

        String token = Jwts.builder()
                .header().keyId("unknown-kid").and()
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .subject("sub-1")
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith((RSAPrivateKey) keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();

        assertThatThrownBy(() -> verifier.verify(token, null))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("빈/null 토큰은 401")
    void verify_blankToken_throwsUnauthorized() {
        assertThatThrownBy(() -> verifier.verify("", null))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> verifier.verify(null, null))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("expectedNonce 와 토큰 nonce 가 일치하면 통과")
    void verify_matchingNonce_ok() {
        String token = buildTokenWithNonce("sub-1", "nonce-abc");

        OAuthUserInfo info = verifier.verify(token, "nonce-abc");

        assertThat(info.subject()).isEqualTo("sub-1");
    }

    @Test
    @DisplayName("expectedNonce 가 토큰 nonce 와 다르면 401")
    void verify_nonceMismatch_throwsUnauthorized() {
        String token = buildTokenWithNonce("sub-1", "nonce-abc");

        assertThatThrownBy(() -> verifier.verify(token, "nonce-xyz"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("nonce");
    }

    @Test
    @DisplayName("expectedNonce 를 요구하는데 토큰에 nonce 가 없으면 401")
    void verify_missingNonceWhenExpected_throwsUnauthorized() {
        String token = buildToken(ISSUER, AUDIENCE, "sub-1", "u@e.com", true, null,
                Instant.now().plusSeconds(60));

        assertThatThrownBy(() -> verifier.verify(token, "nonce-abc"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("nonce");
    }

    @Test
    @DisplayName("expectedNonce 가 null 이면 nonce 검증을 건너뛴다 (하위호환)")
    void verify_nullExpectedNonce_skipsCheck() {
        String token = buildTokenWithNonce("sub-1", "nonce-abc");

        OAuthUserInfo info = verifier.verify(token, null);

        assertThat(info.subject()).isEqualTo("sub-1");
    }

    @Test
    @DisplayName("다른 키로 서명된 토큰은 서명 검증 실패로 401")
    void verify_tamperedSignature_throwsUnauthorized() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair attacker = kpg.generateKeyPair();

        String token = Jwts.builder()
                .header().keyId(KID).and()
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .subject("sub-1")
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith((RSAPrivateKey) attacker.getPrivate(), Jwts.SIG.RS256)
                .compact();

        assertThatThrownBy(() -> verifier.verify(token, null))
                .isInstanceOf(UnauthorizedException.class);
    }

    // ---- helpers ----

    private String buildToken(
            String issuer,
            String audience,
            String subject,
            String email,
            Object emailVerified,
            String name,
            Instant expiry
    ) {
        var builder = Jwts.builder()
                .header().keyId(KID).and()
                .issuer(issuer)
                .audience().add(audience).and()
                .subject(subject)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(expiry));
        if (email != null) {
            builder.claims(Map.of(
                    "email", email,
                    "email_verified", emailVerified
            ));
        }
        if (name != null) {
            builder.claim("name", name);
        }
        return builder.signWith((RSAPrivateKey) keyPair.getPrivate(), Jwts.SIG.RS256).compact();
    }

    private String buildTokenWithNonce(String subject, String nonce) {
        return Jwts.builder()
                .header().keyId(KID).and()
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .subject(subject)
                .claim("nonce", nonce)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith((RSAPrivateKey) keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    @SuppressWarnings("unused")
    private RSAPublicKey publicKey() {
        return (RSAPublicKey) keyPair.getPublic();
    }

    /**
     * 추상 클래스 인스턴스화를 위한 테스트 전용 서브클래스.
     */
    private static final class TestVerifier extends JwksIdTokenVerifier {
        TestVerifier(JwkProvider provider, Set<String> issuers, String audience) {
            super(provider, issuers, audience);
        }
    }
}
