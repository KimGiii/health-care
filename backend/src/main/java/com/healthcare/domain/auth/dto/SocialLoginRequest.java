package com.healthcare.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SocialLoginRequest {

    @NotBlank(message = "ID 토큰은 필수입니다.")
    private String idToken;

    /**
     * 클라이언트가 인증 요청 시 생성한 원본 nonce. 서버는 제공자 규칙에 맞게 변환해
     * ID 토큰의 nonce 클레임과 비교한다(재생 공격 방지). 구버전 클라이언트는 보내지 않을 수 있다.
     */
    private String nonce;

    public SocialLoginRequest(String idToken) {
        this.idToken = idToken;
    }

    public SocialLoginRequest(String idToken, String nonce) {
        this.idToken = idToken;
        this.nonce = nonce;
    }
}
