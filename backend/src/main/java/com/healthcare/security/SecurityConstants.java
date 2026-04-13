package com.healthcare.security;

public final class SecurityConstants {

    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";
    public static final long ACCESS_TOKEN_EXPIRY_MS = 24 * 60 * 60 * 1000L;    // 24h
    public static final long REFRESH_TOKEN_EXPIRY_MS = 30L * 24 * 60 * 60 * 1000L; // 30d

    private SecurityConstants() {}
}
