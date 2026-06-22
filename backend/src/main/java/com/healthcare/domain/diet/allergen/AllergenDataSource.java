package com.healthcare.domain.diet.allergen;

public enum AllergenDataSource {
    /** 식약처 식품분류 매핑 */
    MFDS_CLASS,
    /** 국민건강영양조사 음식별 식품재료량 DB (v1.1+) */
    KHANES_RECIPE,
    /** 푸드QR 라벨 기반 (v2+) */
    FOODQR,
    /** 브랜드 공식 영양·알러젠 고지 */
    BRAND_OFFICIAL,
    /** Open Food Facts */
    OPEN_FOOD_FACTS,
    /** 사용자 직접 입력 */
    USER_CUSTOM
}
