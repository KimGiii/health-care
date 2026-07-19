package com.healthcare.domain.diet.external.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PublicFoodApiClientImpl 정적 헬퍼 단위 테스트")
class PublicFoodApiClientImplTest {

    // ─────────────────────────── normalizeServiceKey ───────────────────────────

    @Test
    @DisplayName("디코딩 키('+', '=' 포함)는 그대로 반환한다")
    void normalizeServiceKey_decodedKey_returnsAsIs() {
        String decoded = "abc+def/ghi==";
        assertThat(PublicFoodApiClientImpl.normalizeServiceKey(decoded)).isEqualTo(decoded);
    }

    @Test
    @DisplayName("인코딩 키('%' 포함)는 한 번 디코딩해 반환한다")
    void normalizeServiceKey_encodedKey_decodesOnce() {
        assertThat(PublicFoodApiClientImpl.normalizeServiceKey("abc%2Bdef%2Fghi%3D%3D"))
                .isEqualTo("abc+def/ghi==");
    }

    @Test
    @DisplayName("앞뒤 공백은 제거한다")
    void normalizeServiceKey_trimsWhitespace() {
        assertThat(PublicFoodApiClientImpl.normalizeServiceKey("  mykey  ")).isEqualTo("mykey");
    }

    @Test
    @DisplayName("null 또는 빈 키는 빈 문자열을 반환한다")
    void normalizeServiceKey_nullOrBlank_returnsEmpty() {
        assertThat(PublicFoodApiClientImpl.normalizeServiceKey(null)).isEmpty();
        assertThat(PublicFoodApiClientImpl.normalizeServiceKey("   ")).isEmpty();
    }

    @Test
    @DisplayName("잘못된 퍼센트 시퀀스는 디코딩 실패 시 원본을 반환한다")
    void normalizeServiceKey_invalidPercentSequence_returnsOriginal() {
        assertThat(PublicFoodApiClientImpl.normalizeServiceKey("abc%zzdef")).isEqualTo("abc%zzdef");
    }

    // ─────────────────────────── extractXmlError ───────────────────────────

    @Test
    @DisplayName("공공데이터포털 인증 오류 XML에서 원인 필드를 추출한다")
    void extractXmlError_authError_extractsFields() {
        String xml = """
                <OpenAPI_ServiceResponse>
                  <cmmMsgHeader>
                    <errMsg>SERVICE ERROR</errMsg>
                    <returnAuthMsg>SERVICE_KEY_IS_NOT_REGISTERED_ERROR</returnAuthMsg>
                    <returnReasonCode>30</returnReasonCode>
                  </cmmMsgHeader>
                </OpenAPI_ServiceResponse>
                """;

        String result = PublicFoodApiClientImpl.extractXmlError(xml);

        assertThat(result)
                .contains("errMsg=SERVICE ERROR")
                .contains("returnAuthMsg=SERVICE_KEY_IS_NOT_REGISTERED_ERROR")
                .contains("returnReasonCode=30");
    }

    @Test
    @DisplayName("알려진 오류 필드가 없으면 본문 스니펫을 반환한다")
    void extractXmlError_unknownFormat_returnsSnippet() {
        String xml = "<unexpected>something went wrong</unexpected>";
        assertThat(PublicFoodApiClientImpl.extractXmlError(xml)).contains("something went wrong");
    }
}
