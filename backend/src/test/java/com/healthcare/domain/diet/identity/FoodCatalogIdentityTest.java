package com.healthcare.domain.diet.identity;

import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.entity.FoodCatalogSource;
import com.healthcare.domain.diet.entity.RecommendationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("FoodCatalogIdentity")
class FoodCatalogIdentityTest {

    @Test
    @DisplayName("브랜드 공식 메뉴 food_code는 브랜드명과 메뉴명을 source identity로 조합한다")
    void brandOfficialFoodCode_combinesBrandAndMenu() {
        assertThat(FoodCatalogIdentity.brandOfficialFoodCode("버거킹", "통새우 와퍼"))
                .isEqualTo("버거킹:통새우_와퍼");
    }

    @Test
    @DisplayName("브랜드 공식 메뉴 food_code 입력 중 빈 값은 예외")
    void brandOfficialFoodCode_blankPart_throws() {
        assertThatThrownBy(() -> FoodCatalogIdentity.brandOfficialFoodCode("버거킹", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("duplicateNameKey는 공백과 표기 기호를 제거해 동일성 키를 만든다")
    void duplicateNameKey_stripsPresentationCharacters() {
        assertThat(FoodCatalogIdentity.duplicateNameKey("닭 가슴살(구운)"))
                .isEqualTo("닭가슴살구운");
    }

    @Test
    @DisplayName("duplicateKey는 브랜드명이 있으면 브랜드를 포함한다")
    void duplicateKey_includesBrandWhenPresent() {
        FoodCatalog food = catalog("와퍼", "버거킹");

        assertThat(FoodCatalogIdentity.duplicateKey(food)).isEqualTo("버거킹:와퍼");
    }

    @Test
    @DisplayName("normalizeDisplayName은 NFC와 공백 축약을 적용한다")
    void normalizeDisplayName_appliesNfcAndWhitespaceCollapse() {
        assertThat(FoodCatalogIdentity.normalizeDisplayName("  닭   가슴살  "))
                .isEqualTo("닭 가슴살");
    }

    private FoodCatalog catalog(String nameKo, String brandName) {
        return FoodCatalog.builder()
                .name(nameKo)
                .nameKo(nameKo)
                .brandName(brandName)
                .source(FoodCatalogSource.BRAND_OFFICIAL)
                .category(FoodCategory.PROTEIN_SOURCE)
                .caloriesPer100g(165.0)
                .isCustom(false)
                .recommendationStatus(RecommendationStatus.RECOMMENDABLE)
                .build();
    }
}
