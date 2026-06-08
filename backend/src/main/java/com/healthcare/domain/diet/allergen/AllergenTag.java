package com.healthcare.domain.diet.allergen;

/**
 * 식약처 식품 표시기준 의무표시 알레르기 유발물질 태그 (19종).
 * GLUTEN은 의무표시 외 합성 식이 제한 태그로 별도 분류.
 */
public enum AllergenTag {
    EGG,
    MILK,
    BUCKWHEAT,
    PEANUT,
    SOY,
    WHEAT,
    MACKEREL,
    CRAB,
    SHRIMP,
    PINE_NUT,
    PORK,
    PEACH,
    TOMATO,
    SULFITE,
    WALNUT,
    CHICKEN,
    BEEF,
    SQUID,
    SHELLFISH,  // 조개류(굴·전복·홍합 포함)
    GLUTEN      // 합성 식이 제한 태그 (밀·호밀·보리·귀리 포함)
}
