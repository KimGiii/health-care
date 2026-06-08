package com.healthcare.domain.diet.allergen;

import com.healthcare.domain.diet.allergen.entity.FoodAllergenTag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AllergenConfidenceGate 단위 테스트")
class AllergenConfidenceGateTest {

    private AllergenConfidenceGate gate;

    @BeforeEach
    void setUp() {
        gate = new AllergenConfidenceGate();
    }

    @Nested
    @DisplayName("containsAllergen — 제한 알러젠 포함 여부")
    class ContainsAllergen {

        @Test
        @DisplayName("제한 알러젠 목록이 비어있으면 항상 false를 반환한다")
        void emptyRestrictedTags_returnsFalse() {
            FoodAllergenTag tag = tag(1L, AllergenTag.MILK, AllergenConfidenceLevel.DIRECT_VERIFIED);
            Map<Long, List<FoodAllergenTag>> tagsByFoodId = Map.of(1L, List.of(tag));

            assertThat(gate.containsAllergen(1L, Set.of(), tagsByFoodId)).isFalse();
        }

        @Test
        @DisplayName("식품에 제한 알러젠이 있으면 true를 반환한다")
        void foodHasRestrictedAllergen_returnsTrue() {
            FoodAllergenTag milkTag = tag(1L, AllergenTag.MILK, AllergenConfidenceLevel.DIRECT_VERIFIED);
            Map<Long, List<FoodAllergenTag>> tagsByFoodId = Map.of(1L, List.of(milkTag));

            assertThat(gate.containsAllergen(1L, Set.of(AllergenTag.MILK), tagsByFoodId)).isTrue();
        }

        @Test
        @DisplayName("식품에 제한 알러젠이 없으면 false를 반환한다")
        void foodLacksRestrictedAllergen_returnsFalse() {
            FoodAllergenTag wheatTag = tag(1L, AllergenTag.WHEAT, AllergenConfidenceLevel.LABEL_DERIVED);
            Map<Long, List<FoodAllergenTag>> tagsByFoodId = Map.of(1L, List.of(wheatTag));

            assertThat(gate.containsAllergen(1L, Set.of(AllergenTag.MILK), tagsByFoodId)).isFalse();
        }

        @Test
        @DisplayName("알러젠 태그 정보가 없는 식품(tagsByFoodId에 없음)은 false를 반환한다")
        void noTagRecord_returnsFalse() {
            assertThat(gate.containsAllergen(99L, Set.of(AllergenTag.EGG), Map.of())).isFalse();
        }
    }

    @Nested
    @DisplayName("passesGate — Strict 모드 신뢰 레벨 게이트")
    class PassesGate {

        @Test
        @DisplayName("제한 알러젠이 없으면 항상 통과한다")
        void noRestrictedTags_alwaysPasses() {
            assertThat(gate.passesGate(1L, Set.of(), Map.of(), true)).isTrue();
            assertThat(gate.passesGate(1L, Set.of(), Map.of(), false)).isTrue();
        }

        @Test
        @DisplayName("기본 모드(strictMode=false)에서는 항상 통과한다")
        void normalMode_alwaysPasses() {
            assertThat(gate.passesGate(1L, Set.of(AllergenTag.MILK), Map.of(), false)).isTrue();
        }

        @Test
        @DisplayName("Strict 모드에서 DIRECT_VERIFIED 레코드가 있으면 통과한다")
        void strictMode_directVerified_passes() {
            FoodAllergenTag t = tag(1L, AllergenTag.WHEAT, AllergenConfidenceLevel.DIRECT_VERIFIED);
            Map<Long, List<FoodAllergenTag>> tagsByFoodId = Map.of(1L, List.of(t));

            assertThat(gate.passesGate(1L, Set.of(AllergenTag.MILK), tagsByFoodId, true)).isTrue();
        }

        @Test
        @DisplayName("Strict 모드에서 LABEL_DERIVED 레코드가 있으면 통과한다")
        void strictMode_labelDerived_passes() {
            FoodAllergenTag t = tag(1L, AllergenTag.WHEAT, AllergenConfidenceLevel.LABEL_DERIVED);
            Map<Long, List<FoodAllergenTag>> tagsByFoodId = Map.of(1L, List.of(t));

            assertThat(gate.passesGate(1L, Set.of(AllergenTag.MILK), tagsByFoodId, true)).isTrue();
        }

        @Test
        @DisplayName("Strict 모드에서 알러젠 레코드가 없으면(UNKNOWN) 차단된다")
        void strictMode_noTags_blocked() {
            assertThat(gate.passesGate(1L, Set.of(AllergenTag.MILK), Map.of(), true)).isFalse();
        }

        @Test
        @DisplayName("Strict 모드에서 RECIPE_DERIVED만 있으면 차단된다")
        void strictMode_recipeDerivedOnly_blocked() {
            FoodAllergenTag t = tag(1L, AllergenTag.WHEAT, AllergenConfidenceLevel.RECIPE_DERIVED);
            Map<Long, List<FoodAllergenTag>> tagsByFoodId = Map.of(1L, List.of(t));

            assertThat(gate.passesGate(1L, Set.of(AllergenTag.MILK), tagsByFoodId, true)).isFalse();
        }
    }

    @Nested
    @DisplayName("resolveConfidence — 식품 신뢰 레벨 결정")
    class ResolveConfidence {

        @Test
        @DisplayName("알러젠 태그가 없으면 UNKNOWN을 반환한다")
        void noTags_returnsUnknown() {
            assertThat(gate.resolveConfidence(1L, Map.of())).isEqualTo(AllergenConfidenceLevel.UNKNOWN);
        }

        @Test
        @DisplayName("단일 레코드가 있으면 해당 신뢰 레벨을 반환한다")
        void singleTag_returnsThatLevel() {
            FoodAllergenTag t = tag(1L, AllergenTag.MILK, AllergenConfidenceLevel.LABEL_DERIVED);
            Map<Long, List<FoodAllergenTag>> tagsByFoodId = Map.of(1L, List.of(t));

            assertThat(gate.resolveConfidence(1L, tagsByFoodId)).isEqualTo(AllergenConfidenceLevel.LABEL_DERIVED);
        }

        @Test
        @DisplayName("여러 레코드 중 가장 높은 신뢰 레벨을 반환한다")
        void multipleTags_returnsHighest() {
            FoodAllergenTag recipe = tag(1L, AllergenTag.WHEAT, AllergenConfidenceLevel.RECIPE_DERIVED);
            FoodAllergenTag direct = tag(1L, AllergenTag.MILK, AllergenConfidenceLevel.DIRECT_VERIFIED);
            Map<Long, List<FoodAllergenTag>> tagsByFoodId = Map.of(1L, List.of(recipe, direct));

            assertThat(gate.resolveConfidence(1L, tagsByFoodId)).isEqualTo(AllergenConfidenceLevel.DIRECT_VERIFIED);
        }
    }

    // ─── 헬퍼 ───

    private FoodAllergenTag tag(Long foodCatalogId, AllergenTag allergenTag, AllergenConfidenceLevel level) {
        return FoodAllergenTag.builder()
                .foodCatalogId(foodCatalogId)
                .allergenTag(allergenTag)
                .confidenceLevel(level)
                .source(AllergenDataSource.MFDS_CLASS)
                .build();
    }
}
