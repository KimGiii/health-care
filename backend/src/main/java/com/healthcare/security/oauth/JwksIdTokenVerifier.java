package com.healthcare.security.oauth;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkException;
import com.auth0.jwk.JwkProvider;
import com.healthcare.common.exception.UnauthorizedException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Header;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Locator;
import io.jsonwebtoken.ProtectedHeader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.security.Key;
import java.util.Set;

/**
 * JWKS 기반 OAuth ID 토큰 검증기 공통 구현.
 *
 * <p>검증 항목:
 * <ul>
 *   <li>서명 (JWKS 의 kid 매칭된 공개키로 검증)</li>
 *   <li>iss = expectedIssuers 중 하나</li>
 *   <li>aud = expectedAudience</li>
 *   <li>exp (JJWT 가 자동 검증)</li>
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
public abstract class JwksIdTokenVerifier implements OAuthIdTokenVerifier {

    private final JwkProvider jwkProvider;
    private final Set<String> expectedIssuers;
    private final String expectedAudience;

    @Override
    public OAuthUserInfo verify(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            throw new UnauthorizedException("ID 토큰이 비어있습니다.");
        }

        final Jws<Claims> jws;
        try {
            jws = Jwts.parser()
                    .keyLocator(keyLocator())
                    .requireAudience(expectedAudience)
                    .build()
                    .parseSignedClaims(idToken);
        } catch (JwtException ex) {
            log.warn("OAuth ID 토큰 검증 실패: {}", ex.getMessage());
            throw new UnauthorizedException("유효하지 않은 ID 토큰입니다.");
        }

        Claims claims = jws.getPayload();
        String issuer = claims.getIssuer();
        if (issuer == null || !expectedIssuers.contains(issuer)) {
            throw new UnauthorizedException("허용되지 않은 issuer 입니다.");
        }

        String subject = claims.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new UnauthorizedException("ID 토큰에 subject 가 없습니다.");
        }

        return new OAuthUserInfo(
                subject,
                claims.get("email", String.class),
                parseEmailVerified(claims.get("email_verified")),
                claims.get("name", String.class)
        );
    }

    private Locator<Key> keyLocator() {
        return (Header header) -> {
            if (!(header instanceof ProtectedHeader protectedHeader)) {
                throw new UnauthorizedException("보호되지 않은 JWT 헤더입니다.");
            }
            String kid = protectedHeader.getKeyId();
            if (kid == null) {
                throw new UnauthorizedException("ID 토큰에 kid 헤더가 없습니다.");
            }
            try {
                Jwk jwk = jwkProvider.get(kid);
                return jwk.getPublicKey();
            } catch (JwkException ex) {
                log.warn("JWKS 공개키 조회 실패: kid={} err={}", kid, ex.getMessage());
                throw new UnauthorizedException("ID 토큰 서명을 검증할 수 없습니다.");
            }
        };
    }

    /**
     * email_verified 클레임은 boolean 또는 "true"/"false" 문자열로 올 수 있다 (Apple).
     */
    private static boolean parseEmailVerified(Object raw) {
        return switch (raw) {
            case Boolean b -> b;
            case String s -> Boolean.parseBoolean(s);
            case null, default -> false;
        };
    }
}
