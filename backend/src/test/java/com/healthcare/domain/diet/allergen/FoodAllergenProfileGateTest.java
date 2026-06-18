package com.healthcare.domain.diet.allergen;

import com.healthcare.domain.diet.allergen.entity.FoodAllergenProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("완결 알러젠 프로필 게이트")
class FoodAllergenProfileGateTest {

    private static final Instant NOW = Instant.parse("2026-06-18T00:00:00Z");
    private final FoodAllergenProfileGate gate = new FoodAllergenProfileGate(
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    @DisplayName("포함 태그가 0개여도 유효한 고신뢰 프로필은 통과하고 프로필이 없으면 제외한다")
    void requiresSeparateVerifiedProfileForAllergyRestrictions() {
        FoodAllergenProfile verified = FoodAllergenProfile.builder()
                .foodCatalogId(1L)
                .confidenceLevel(AllergenConfidenceLevel.LABEL_DERIVED)
                .source(AllergenDataSource.BRAND_OFFICIAL)
                .standardVersion("KR-22-2026")
                .reviewedAt(OffsetDateTime.ofInstant(NOW.minusSeconds(3600), ZoneOffset.UTC))
                .validUntil(OffsetDateTime.ofInstant(NOW.plusSeconds(3600), ZoneOffset.UTC))
                .build();

        assertThat(gate.passesGate(1L, Set.of(AllergenTag.MILK), Map.of(1L, verified))).isTrue();
        assertThat(gate.passesGate(2L, Set.of(AllergenTag.MILK), Map.of())).isFalse();
    }
}
