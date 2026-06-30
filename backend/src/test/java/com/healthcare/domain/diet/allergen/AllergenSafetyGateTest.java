package com.healthcare.domain.diet.allergen;

import com.healthcare.domain.diet.allergen.entity.FoodAllergenProfile;
import com.healthcare.domain.diet.allergen.entity.FoodAllergenTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AllergenSafetyGate")
class AllergenSafetyGateTest {

    private static final Instant NOW = Instant.parse("2026-06-18T00:00:00Z");
    private final AllergenSafetyGate gate = new AllergenSafetyGate(Clock.fixed(NOW, ZoneOffset.UTC));

    @Nested
    @DisplayName("제한 알러젠 없음 — 항상 통과")
    class NoRestrictions {

        @Test
        @DisplayName("태그가 있으면 태그 기반 신뢰도로 통과한다")
        void withTags_passesWithTagConfidence() {
            AllergenContext ctx = ctx(Set.of(),
                    List.of(tag(AllergenTag.MILK, AllergenConfidenceLevel.DIRECT_VERIFIED)),
                    null, false);
            SafetyDecision d = gate.decide(ctx);
            assertThat(d.passes()).isTrue();
            assertThat(d.confidence()).isEqualTo(AllergenConfidenceLevel.DIRECT_VERIFIED);
        }

        @Test
        @DisplayName("태그도 없으면 UNKNOWN 신뢰도로 통과한다")
        void noTags_passesWithUnknown() {
            AllergenContext ctx = ctx(Set.of(), List.of(), null, false);
            SafetyDecision d = gate.decide(ctx);
            assertThat(d.passes()).isTrue();
            assertThat(d.confidence()).isEqualTo(AllergenConfidenceLevel.UNKNOWN);
        }
    }

    @Nested
    @DisplayName("제한 알러젠 포함 — 차단")
    class ContainsAllergen {

        @Test
        @DisplayName("제한 알러젠 태그가 있으면 차단한다")
        void restrictedAllergenTag_blocked() {
            AllergenContext ctx = ctx(Set.of(AllergenTag.MILK),
                    List.of(tag(AllergenTag.MILK, AllergenConfidenceLevel.DIRECT_VERIFIED)),
                    null, false);
            assertThat(gate.decide(ctx).passes()).isFalse();
        }

        @Test
        @DisplayName("다른 알러젠 태그만 있으면 차단하지 않는다")
        void differentAllergenTag_notBlocked() {
            AllergenContext ctx = ctx(Set.of(AllergenTag.MILK),
                    List.of(tag(AllergenTag.WHEAT, AllergenConfidenceLevel.DIRECT_VERIFIED, true)),
                    profile(AllergenConfidenceLevel.DIRECT_VERIFIED), false);
            assertThat(gate.decide(ctx).passes()).isTrue();
        }

        @Test
        @DisplayName("GLUTEN 제한은 WHEAT 포함 태그도 차단한다")
        void glutenRestriction_blocksWheatTag() {
            AllergenContext ctx = ctx(Set.of(AllergenTag.GLUTEN),
                    List.of(tag(AllergenTag.WHEAT, AllergenConfidenceLevel.LABEL_DERIVED, true)),
                    profile(AllergenConfidenceLevel.LABEL_DERIVED), true);
            assertThat(gate.decide(ctx).passes()).isFalse();
        }
    }

    @Nested
    @DisplayName("Strict 모드 — 고신뢰 verified 태그 요구")
    class StrictMode {

        @Test
        @DisplayName("DIRECT_VERIFIED + verified=true 태그가 있으면 통과한다")
        void directVerifiedTag_passes() {
            AllergenContext ctx = ctx(Set.of(AllergenTag.MILK),
                    List.of(tag(AllergenTag.WHEAT, AllergenConfidenceLevel.DIRECT_VERIFIED, true)),
                    profile(AllergenConfidenceLevel.DIRECT_VERIFIED), true);
            assertThat(gate.decide(ctx).passes()).isTrue();
        }

        @Test
        @DisplayName("LABEL_DERIVED + verified=true 태그도 통과한다")
        void labelDerivedVerifiedTag_passes() {
            AllergenContext ctx = ctx(Set.of(AllergenTag.MILK),
                    List.of(tag(AllergenTag.WHEAT, AllergenConfidenceLevel.LABEL_DERIVED, true)),
                    profile(AllergenConfidenceLevel.LABEL_DERIVED), true);
            assertThat(gate.decide(ctx).passes()).isTrue();
        }

        @Test
        @DisplayName("완결 프로필이 고신뢰면 개별 태그 verified=false여도 통과한다")
        void profileGradeProfile_passesEvenWhenTagVerificationFlagIsFalse() {
            AllergenContext ctx = ctx(Set.of(AllergenTag.MILK),
                    List.of(tag(AllergenTag.WHEAT, AllergenConfidenceLevel.DIRECT_VERIFIED, false)),
                    profile(AllergenConfidenceLevel.DIRECT_VERIFIED), true);
            assertThat(gate.decide(ctx).passes()).isTrue();
        }

        @Test
        @DisplayName("포함 태그가 없어도 고신뢰 완결 프로필이 있으면 통과한다")
        void noTags_profileGradeProfile_passes() {
            AllergenContext ctx = ctx(Set.of(AllergenTag.MILK), List.of(),
                    profile(AllergenConfidenceLevel.LABEL_DERIVED), true);
            assertThat(gate.decide(ctx).passes()).isTrue();
        }

        @Test
        @DisplayName("RECIPE_DERIVED 완결 프로필은 차단한다")
        void recipeDerivedProfile_blocked() {
            AllergenContext ctx = ctx(Set.of(AllergenTag.MILK),
                    List.of(tag(AllergenTag.WHEAT, AllergenConfidenceLevel.RECIPE_DERIVED, true)),
                    profile(AllergenConfidenceLevel.RECIPE_DERIVED), true);
            assertThat(gate.decide(ctx).passes()).isFalse();
        }

        @Test
        @DisplayName("non-strict 모드에서는 태그 신뢰도 무관하게 프로필 검증만으로 통과한다")
        void nonStrictMode_profileAlone_passes() {
            AllergenContext ctx = ctx(Set.of(AllergenTag.MILK), List.of(),
                    profile(AllergenConfidenceLevel.LABEL_DERIVED), false);
            assertThat(gate.decide(ctx).passes()).isTrue();
        }
    }

    @Nested
    @DisplayName("완결 알러젠 프로필 — 검증 필요")
    class ProfileVerification {

        @Test
        @DisplayName("알러젠 제한이 있는데 프로필이 없으면 차단한다")
        void allergenRestriction_noProfile_blocked() {
            AllergenContext ctx = ctx(Set.of(AllergenTag.MILK), List.of(), null, false);
            assertThat(gate.decide(ctx).passes()).isFalse();
        }

        @Test
        @DisplayName("유효기간이 지난 프로필은 차단한다")
        void expiredProfile_blocked() {
            FoodAllergenProfile expired = FoodAllergenProfile.builder()
                    .foodCatalogId(1L)
                    .confidenceLevel(AllergenConfidenceLevel.LABEL_DERIVED)
                    .source(AllergenDataSource.BRAND_OFFICIAL)
                    .standardVersion("KR-22-2026")
                    .reviewedAt(OffsetDateTime.ofInstant(NOW.minusSeconds(7200), ZoneOffset.UTC))
                    .validUntil(OffsetDateTime.ofInstant(NOW.minusSeconds(1), ZoneOffset.UTC))
                    .build();
            AllergenContext ctx = ctx(Set.of(AllergenTag.MILK), List.of(), expired, false);
            assertThat(gate.decide(ctx).passes()).isFalse();
        }

        @Test
        @DisplayName("포함 태그가 0개여도 유효한 프로필이 있으면 통과한다")
        void noInclusionTags_validProfile_passes() {
            AllergenContext ctx = ctx(Set.of(AllergenTag.MILK), List.of(),
                    profile(AllergenConfidenceLevel.LABEL_DERIVED), false);
            assertThat(gate.decide(ctx).passes()).isTrue();
        }
    }

    @Nested
    @DisplayName("신뢰도 해석")
    class ConfidenceResolution {

        @Test
        @DisplayName("프로필이 있으면 프로필 신뢰도를 반환한다")
        void profilePresent_returnsProfileConfidence() {
            AllergenContext ctx = ctx(Set.of(),
                    List.of(tag(AllergenTag.WHEAT, AllergenConfidenceLevel.RECIPE_DERIVED)),
                    profile(AllergenConfidenceLevel.LABEL_DERIVED), false);
            assertThat(gate.decide(ctx).confidence()).isEqualTo(AllergenConfidenceLevel.LABEL_DERIVED);
        }

        @Test
        @DisplayName("프로필이 없으면 태그 중 가장 높은 신뢰도를 반환한다")
        void noProfile_returnsHighestTagConfidence() {
            AllergenContext ctx = ctx(Set.of(),
                    List.of(
                            tag(AllergenTag.WHEAT, AllergenConfidenceLevel.RECIPE_DERIVED),
                            tag(AllergenTag.MILK, AllergenConfidenceLevel.DIRECT_VERIFIED)
                    ),
                    null, false);
            assertThat(gate.decide(ctx).confidence()).isEqualTo(AllergenConfidenceLevel.DIRECT_VERIFIED);
        }

        @Test
        @DisplayName("태그도 프로필도 없으면 UNKNOWN을 반환한다")
        void noTagsNoProfile_returnsUnknown() {
            AllergenContext ctx = ctx(Set.of(), List.of(), null, false);
            assertThat(gate.decide(ctx).confidence()).isEqualTo(AllergenConfidenceLevel.UNKNOWN);
        }
    }

    // ─── 헬퍼 ───

    private AllergenContext ctx(
            Set<AllergenTag> restricted,
            List<FoodAllergenTag> tags,
            FoodAllergenProfile profile,
            boolean strict
    ) {
        return new AllergenContext(1L, restricted, tags, profile, strict);
    }

    private FoodAllergenTag tag(AllergenTag allergenTag, AllergenConfidenceLevel level) {
        return tag(allergenTag, level, false);
    }

    private FoodAllergenTag tag(AllergenTag allergenTag, AllergenConfidenceLevel level, boolean verified) {
        return FoodAllergenTag.builder()
                .foodCatalogId(1L)
                .allergenTag(allergenTag)
                .confidenceLevel(level)
                .source(AllergenDataSource.MFDS_CLASS)
                .allergenProfileVerified(verified)
                .build();
    }

    private FoodAllergenProfile profile(AllergenConfidenceLevel level) {
        return FoodAllergenProfile.builder()
                .foodCatalogId(1L)
                .confidenceLevel(level)
                .source(AllergenDataSource.BRAND_OFFICIAL)
                .standardVersion("KR-22-2026")
                .reviewedAt(OffsetDateTime.ofInstant(NOW.minusSeconds(3600), ZoneOffset.UTC))
                .validUntil(OffsetDateTime.ofInstant(NOW.plusSeconds(3600), ZoneOffset.UTC))
                .build();
    }
}
