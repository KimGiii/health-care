package com.healthcare.domain.diet.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class RecommendationCurationTest {

    @Nested
    @DisplayName("WithCaution 생성")
    class WithCautionConstruction {

        @Test
        @DisplayName("null reason 은 예외")
        void withCaution_nullReason_throws() {
            assertThatThrownBy(() -> new RecommendationCuration.WithCaution(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("빈 문자열 reason 은 예외")
        void withCaution_blankReason_throws() {
            assertThatThrownBy(() -> new RecommendationCuration.WithCaution("  "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("유효한 reason 은 보존")
        void withCaution_validReason_preserved() {
            var curation = new RecommendationCuration.WithCaution("고지방·고나트륨");
            assertThat(curation.reason()).isEqualTo("고지방·고나트륨");
        }

        @Test
        @DisplayName("status 는 RECOMMENDABLE_WITH_CAUTION")
        void withCaution_status_isWithCaution() {
            var curation = new RecommendationCuration.WithCaution("고지방");
            assertThat(curation.status()).isEqualTo(RecommendationStatus.RECOMMENDABLE_WITH_CAUTION);
        }

        @Test
        @DisplayName("저장 reason과 응답 caution은 같은 사유를 반환한다")
        void withCaution_reasonForStorageAndCautionForResponse_returnReason() {
            var curation = new RecommendationCuration.WithCaution("고지방");

            assertThat(curation.reasonForStorage()).isEqualTo("고지방");
            assertThat(curation.cautionForResponse()).isEqualTo("고지방");
        }
    }

    @Nested
    @DisplayName("나머지 상태")
    class OtherStatuses {

        @Test
        @DisplayName("Recommendable status 는 RECOMMENDABLE")
        void recommendable_hasCorrectStatus() {
            assertThat(new RecommendationCuration.Recommendable().status())
                    .isEqualTo(RecommendationStatus.RECOMMENDABLE);
        }

        @Test
        @DisplayName("SearchOnly status 는 SEARCH_ONLY")
        void searchOnly_hasCorrectStatus() {
            assertThat(new RecommendationCuration.SearchOnly().status())
                    .isEqualTo(RecommendationStatus.SEARCH_ONLY);
        }

        @Test
        @DisplayName("Disabled status 는 DISABLED")
        void disabled_hasCorrectStatus() {
            assertThat(new RecommendationCuration.Disabled().status())
                    .isEqualTo(RecommendationStatus.DISABLED);
        }

        @Test
        @DisplayName("주의 상태가 아니면 저장 reason과 응답 caution은 null이다")
        void nonCaution_reasonForStorageAndCautionForResponse_areNull() {
            assertThat(new RecommendationCuration.Recommendable().reasonForStorage()).isNull();
            assertThat(new RecommendationCuration.Recommendable().cautionForResponse()).isNull();
            assertThat(new RecommendationCuration.SearchOnly().reasonForStorage()).isNull();
            assertThat(new RecommendationCuration.SearchOnly().cautionForResponse()).isNull();
            assertThat(new RecommendationCuration.Disabled().reasonForStorage()).isNull();
            assertThat(new RecommendationCuration.Disabled().cautionForResponse()).isNull();
        }
    }

    @Nested
    @DisplayName("of() 팩토리 — DB 필드에서 재구성")
    class OfFactory {

        @Test
        @DisplayName("RECOMMENDABLE_WITH_CAUTION + reason → WithCaution")
        void of_withCaution_returnsWithCaution() {
            var curation = RecommendationCuration.of(RecommendationStatus.RECOMMENDABLE_WITH_CAUTION, "고지방");
            assertThat(curation).isInstanceOf(RecommendationCuration.WithCaution.class);
            assertThat(((RecommendationCuration.WithCaution) curation).reason()).isEqualTo("고지방");
        }

        @Test
        @DisplayName("RECOMMENDABLE_WITH_CAUTION + null reason → 예외")
        void of_withCaution_nullReason_throws() {
            assertThatThrownBy(() ->
                    RecommendationCuration.of(RecommendationStatus.RECOMMENDABLE_WITH_CAUTION, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("RECOMMENDABLE → Recommendable")
        void of_recommendable_returnsRecommendable() {
            assertThat(RecommendationCuration.of(RecommendationStatus.RECOMMENDABLE, null))
                    .isInstanceOf(RecommendationCuration.Recommendable.class);
        }

        @Test
        @DisplayName("SEARCH_ONLY → SearchOnly")
        void of_searchOnly_returnsSearchOnly() {
            assertThat(RecommendationCuration.of(RecommendationStatus.SEARCH_ONLY, null))
                    .isInstanceOf(RecommendationCuration.SearchOnly.class);
        }

        @Test
        @DisplayName("DISABLED → Disabled")
        void of_disabled_returnsDisabled() {
            assertThat(RecommendationCuration.of(RecommendationStatus.DISABLED, null))
                    .isInstanceOf(RecommendationCuration.Disabled.class);
        }
    }

    @Nested
    @DisplayName("fromImport() — 운영 입력 검증")
    class FromImport {

        @Test
        @DisplayName("RECOMMENDABLE_WITH_CAUTION + reason → accepted WithCaution")
        void fromImport_withCautionAndReason_returnsAccepted() {
            var result = RecommendationCuration.fromImport(
                    RecommendationStatus.RECOMMENDABLE_WITH_CAUTION,
                    "고나트륨");

            assertThat(result.rejected()).isFalse();
            assertThat(result.curation()).isInstanceOf(RecommendationCuration.WithCaution.class);
            assertThat(result.curation().reasonForStorage()).isEqualTo("고나트륨");
        }

        @Test
        @DisplayName("RECOMMENDABLE_WITH_CAUTION + null reason → rejected")
        void fromImport_withCautionAndNullReason_returnsRejected() {
            var result = RecommendationCuration.fromImport(
                    RecommendationStatus.RECOMMENDABLE_WITH_CAUTION,
                    null);

            assertThat(result.rejected()).isTrue();
            assertThat(result.rejectionReason()).isEqualTo("주의 추천 상태는 사유가 필요합니다.");
        }

        @Test
        @DisplayName("255자를 넘는 reason → rejected")
        void fromImport_reasonLongerThanDbEnvelope_returnsRejected() {
            var result = RecommendationCuration.fromImport(
                    RecommendationStatus.RECOMMENDABLE,
                    "가".repeat(256));

            assertThat(result.rejected()).isTrue();
            assertThat(result.rejectionReason()).isEqualTo("255자 이하여야 합니다.");
        }

        @Test
        @DisplayName("주의 상태가 아닌 입력 reason은 저장하지 않는다")
        void fromImport_nonCautionReason_isCleared() {
            var result = RecommendationCuration.fromImport(
                    RecommendationStatus.SEARCH_ONLY,
                    "추천 제외 사유 메모");

            assertThat(result.rejected()).isFalse();
            assertThat(result.curation()).isInstanceOf(RecommendationCuration.SearchOnly.class);
            assertThat(result.curation().reasonForStorage()).isNull();
        }
    }
}
