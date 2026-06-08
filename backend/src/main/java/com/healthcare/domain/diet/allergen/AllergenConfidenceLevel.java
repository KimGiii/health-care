package com.healthcare.domain.diet.allergen;

/**
 * 식품 알러젠 검토 수준.
 * 레코드가 존재하면 해당 알러젠이 포함됨을 나타내고,
 * confidence_level 은 해당 식품에 대해 알러젠 세트가 얼마나 완결적으로 검토됐는지를 나타낸다.
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
