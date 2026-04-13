package com.healthcare.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey key;

    public JwtTokenProvider(@Value("${app.jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(Long userId, String email) {
        return buildToken(userId, email, SecurityConstants.ACCESS_TOKEN_EXPIRY_MS);
    }

    public String generateRefreshToken(Long userId, String email) {
        return buildToken(userId, email, SecurityConstants.REFRESH_TOKEN_EXPIRY_MS);
    }

    private String buildToken(Long userId, String email, long expiryMs) {
        Date now = new Date();
        return Jwts.builder()
            .subject(userId.toString())
            .claim("email", email)
            .issuedAt(now)
            .expiration(new Date(now.getTime() + expiryMs))
            .signWith(key)
            .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    public Long getUserId(String token) {
        return Long.parseLong(parseToken(token).getSubject());
    }

    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.debug("Expired JWT token");
        } catch (JwtException e) {
            log.debug("Invalid JWT token: {}", e.getMessage());
        }
        return false;
    }
}
