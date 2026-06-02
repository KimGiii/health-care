package com.healthcare.domain.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 소셜로그인 2-step 흐름의 2단계 요청.
 * 신규 사용자만 호출하며, 약관·개인정보처리방침 동의가 반드시 true 여야 한다.
 */
@Getter
@NoArgsConstructor
public class SocialLoginCommitRequest {

    @NotBlank(message = "ID 토큰은 필수입니다.")
    private String idToken;

    @AssertTrue(message = "이용약관에 동의해야 합니다.")
    private boolean agreedToTerms;

    @AssertTrue(message = "개인정보처리방침에 동의해야 합니다.")
    private boolean agreedToPrivacy;

    public SocialLoginCommitRequest(String idToken, boolean agreedToTerms, boolean agreedToPrivacy) {
        this.idToken = idToken;
        this.agreedToTerms = agreedToTerms;
        this.agreedToPrivacy = agreedToPrivacy;
    }
}
