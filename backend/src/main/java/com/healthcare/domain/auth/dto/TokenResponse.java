package com.healthcare.domain.auth.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TokenResponse {
    private String accessToken;
    private String refreshToken;
    private long expiresIn;  // seconds
    private boolean onboardingCompleted;
}
