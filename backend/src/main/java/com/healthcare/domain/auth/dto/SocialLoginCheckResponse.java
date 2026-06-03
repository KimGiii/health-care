package com.healthcare.domain.auth.dto;

/**
 * 소셜로그인 2-step 흐름의 1단계 응답.
 * <ul>
 *   <li>{@code isNew = false}: 기존 사용자 → tokens 즉시 발급 (commit 호출 불필요)</li>
 *   <li>{@code isNew = true}: 신규 사용자 → 클라이언트는 약관 동의 UI 노출 후 commit 호출</li>
 * </ul>
 */
public record SocialLoginCheckResponse(boolean newUser, TokenResponse tokens) {

    public static SocialLoginCheckResponse existing(TokenResponse tokens) {
        return new SocialLoginCheckResponse(false, tokens);
    }

    public static SocialLoginCheckResponse pendingConsent() {
        return new SocialLoginCheckResponse(true, null);
    }
}
