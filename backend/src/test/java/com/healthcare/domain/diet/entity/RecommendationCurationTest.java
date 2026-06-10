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
}
