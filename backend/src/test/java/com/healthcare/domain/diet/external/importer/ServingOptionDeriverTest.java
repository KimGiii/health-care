package com.healthcare.domain.diet.external.importer;

import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.entity.FoodServingOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ServingOptionDeriver — 1회 제공량 옵션 도출")
class ServingOptionDeriverTest {

    private final ServingOptionDeriver deriver = new ServingOptionDeriver();

    @Test
    @DisplayName("servingReference의 깨끗한 단일 g 값을 1회 제공량으로 쓴다")
    void usesCleanSingleGramReference() {
        double base = deriver.resolveBaseServingG(FoodCategory.GRAIN, "210g");
        assertThat(base).isEqualTo(210.0);
    }

    @Test
    @DisplayName("100g/100ml은 영양 기준이므로 무시하고 카테고리 기본값을 쓴다")
    void ignoresNutritionBasisReference() {
        assertThat(deriver.resolveBaseServingG(FoodCategory.FRUIT, "100g")).isEqualTo(150.0);
        assertThat(deriver.resolveBaseServingG(FoodCategory.BEVERAGE, "100ml")).isEqualTo(200.0);
    }

    @Test
    @DisplayName("복수 값·미상 reference는 카테고리 기본값으로 대체한다")
    void fallsBackForMessyReference() {
        assertThat(deriver.resolveBaseServingG(FoodCategory.PROCESSED, "드레싱 15g, 덮밥소스 165g"))
                .isEqualTo(50.0);
        assertThat(deriver.resolveBaseServingG(FoodCategory.VEGETABLE, null)).isEqualTo(75.0);
    }

    @Test
    @DisplayName("비현실적 큰 값은 카테고리 현실 범위로 캡한다")
    void capsUnrealisticValuesToCategoryRange() {
        // 과일 범위 상한 300g
        assertThat(deriver.resolveBaseServingG(FoodCategory.FRUIT, "5000g")).isEqualTo(300.0);
        // 지방 범위 상한 50g
        assertThat(deriver.resolveBaseServingG(FoodCategory.FAT, "1650g")).isEqualTo(50.0);
    }

    @Test
    @DisplayName("도출 옵션은 0.5/1/1.5/2회 검증 옵션이며 1회가 primary(sort 0)다")
    void derivesVerifiedMultiplierOptions() {
        List<FoodServingOption> options = deriver.derive(7L, FoodCategory.PROTEIN_SOURCE, "120g");

        assertThat(options).hasSize(4);
        assertThat(options).allSatisfy(o -> {
            assertThat(o.getFoodCatalogId()).isEqualTo(7L);
            assertThat(o.isVerified()).isTrue();
            assertThat(o.getServingType()).isEqualTo(FoodServingOption.ServingType.OFFICIAL_SERVING);
        });
        FoodServingOption primary = options.get(0);
        assertThat(primary.getSortOrder()).isEqualTo(0);
        assertThat(primary.getEquivalentG()).isEqualTo(120.0);
        assertThat(options.stream().map(FoodServingOption::getEquivalentG))
                .containsExactlyInAnyOrder(60.0, 120.0, 180.0, 240.0);
    }

    @Test
    @DisplayName("낱개 식품(계란)은 COUNT_UNIT 정수배 옵션으로 전개한다 — 분수 개수 없음")
    void derivesDiscreteCountOptionsForCountableFood() {
        List<FoodServingOption> options = deriver.derive(11L, FoodCategory.PROTEIN_SOURCE, "100g", "계란");

        assertThat(options).allSatisfy(o -> {
            assertThat(o.getServingType()).isEqualTo(FoodServingOption.ServingType.COUNT_UNIT);
            assertThat(o.isVerified()).isTrue();
            assertThat(o.getFoodCatalogId()).isEqualTo(11L);
        });
        // 계란 1개=50g 기준의 정수배만. 0.5개(25g) 같은 분수 분량은 없어야 한다.
        assertThat(options.stream().map(FoodServingOption::getEquivalentG))
                .containsExactly(50.0, 100.0, 150.0);
        assertThat(options.stream().map(FoodServingOption::getLabelKo))
                .containsExactly("1개", "2개", "3개");
        assertThat(options.get(0).getSortOrder()).isEqualTo(0);
    }

    @Test
    @DisplayName("낱개가 아닌 식품은 기존 OFFICIAL_SERVING 전개를 유지한다")
    void keepsOfficialServingForNonCountableFood() {
        List<FoodServingOption> options = deriver.derive(7L, FoodCategory.PROTEIN_SOURCE, "120g", "닭가슴살");

        assertThat(options).allSatisfy(o ->
                assertThat(o.getServingType()).isEqualTo(FoodServingOption.ServingType.OFFICIAL_SERVING));
        assertThat(options.stream().map(FoodServingOption::getEquivalentG))
                .containsExactlyInAnyOrder(60.0, 120.0, 180.0, 240.0);
    }

    @Test
    @DisplayName("3-인자 derive는 비낱개로 간주해 기존 동작을 유지한다(하위 호환)")
    void threeArgDeriveStaysOfficial() {
        List<FoodServingOption> options = deriver.derive(7L, FoodCategory.PROTEIN_SOURCE, "120g");

        assertThat(options).allSatisfy(o ->
                assertThat(o.getServingType()).isEqualTo(FoodServingOption.ServingType.OFFICIAL_SERVING));
    }
}
