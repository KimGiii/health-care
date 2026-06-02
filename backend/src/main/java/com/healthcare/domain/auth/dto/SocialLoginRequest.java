package com.healthcare.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SocialLoginRequest {

    @NotBlank(message = "ID 토큰은 필수입니다.")
    private String idToken;

    public SocialLoginRequest(String idToken) {
        this.idToken = idToken;
    }
}
