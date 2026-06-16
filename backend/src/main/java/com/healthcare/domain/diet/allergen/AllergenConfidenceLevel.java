package com.healthcare.domain.diet.allergen;

/**
 * 식품 알러젠 포함 태그의 검토 출처 신뢰 수준.
 * 레코드가 존재하면 해당 알러젠이 포함됨을 나타낸다.
 * Strict 모드의 완결성 판단은 confidence_level 단독이 아니라
 * food_allergen_tags.allergen_profile_verified와 함께 해석한다.
 */
public enum AllergenConfidenceLevel {
    /** 식약처 식품분류 매핑으로 결정적 확인된 단일재료 */
    DIRECT_VERIFIED,
    /** 라벨·푸드QR의 의무표시 정보에서 도출 */
    LABEL_DERIVED,
    /** 음식별 식품재료량 DB 재료 분해 합집합 (v1.1+ 구현) */
    RECIPE_DERIVED,
    /** 검토 미완 (사용자 커스텀, 정보 부족) */
    UNKNOWN
}
