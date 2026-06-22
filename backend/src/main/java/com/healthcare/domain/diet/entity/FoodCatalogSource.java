package com.healthcare.domain.diet.entity;

public enum FoodCatalogSource {
    SEED,
    MFDS_STANDARD_PROCESSED,
    MFDS_STANDARD_DISH,
    MFDS_FOOD_NUTRIENT_DB,
    BRAND_OFFICIAL,
    USER_CUSTOM;

    /**
     * 출처 간 dedup canonical 채택 우선순위. 높을수록 대표(canonical)로 채택된다.
     * SEED는 수기 큐레이션이라 공공데이터에 밀리지 않도록 최상위. USER_CUSTOM은 dedup 비대상이라 0.
     * 정책을 코드에 고정해 버전관리·테스트가 가능하게 한다(설계 §4).
     */
    public int dedupPriority() {
        return switch (this) {
            case SEED -> 500;
            case BRAND_OFFICIAL -> 400;
            case MFDS_STANDARD_PROCESSED -> 300;
            case MFDS_FOOD_NUTRIENT_DB -> 200;
            case MFDS_STANDARD_DISH -> 100;
            case USER_CUSTOM -> 0;
        };
    }

    /** USER_CUSTOM(사용자별 데이터)은 출처 간 dedup 대상이 아니다. 기존 경로를 유지한다. */
    public boolean isDedupEligible() {
        return this != USER_CUSTOM;
    }
}
