package com.healthcare.domain.diet.recommendation.engine;

/**
 * 추천 실패의 구조화된 원인 (추천 최적화 계획 §8.3).
 *
 * <p>근접 식단을 hard constraint 충족 식단처럼 노출하지 않고, 사용자가 조정할 수 있는 선택지만
 * 안내한다. 알러지·목표별 절대 제약의 완화는 권하지 않는다.
 *
 * <p>책임 경계:
 * <ul>
 *   <li>{@code TARGET_PROFILE_MISSING}, {@code ALLERGEN_VERIFIED_POOL_INSUFFICIENT},
 *       {@code NUTRIENT_DATA_INCOMPLETE}는 use case가 후보 풀 진단으로 판정한다.</li>
 *   <li>{@code CALORIE_PROTEIN_CONFLICT}, {@code SERVING_OPTIONS_INFEASIBLE},
 *       {@code REMAINING_MEALS_INFEASIBLE}, {@code SEARCH_LIMIT_REACHED}는 엔진이 판정한다.</li>
 * </ul>
 */
public enum RecommendationFailureReason {

    /** 추천에 필요한 사용자 프로필(성별/생년월일/키/체중/활동 수준)이 부족하다. */
    TARGET_PROFILE_MISSING(
            "추천에 필요한 프로필 정보가 부족합니다. 성별·생년월일·키·체중·활동 수준을 모두 입력해 주세요."),

    /** 알러지 등록 사용자에게 제공할 검증된 후보가 부족하다. */
    ALLERGEN_VERIFIED_POOL_INSUFFICIENT(
            "등록한 알러지 조건에서 안전이 검증된 추천 식품이 부족합니다. 직접 식단을 기록하거나 잠시 후 다시 시도해 주세요."),

    /** 후보의 핵심 영양 데이터(열량·단백질·탄수화물·지방)가 결측이라 hard constraint를 판정할 수 없다. */
    NUTRIENT_DATA_INCOMPLETE(
            "추천 가능한 식품의 영양 정보가 충분하지 않습니다. 직접 식단을 기록해 주세요."),

    /** 목표 열량 상한과 단백질 하한을 동시에 만족하는 조합이 없다. */
    CALORIE_PROTEIN_CONFLICT(
            "현재 남은 목표에서는 열량과 단백질 조건을 동시에 맞추기 어렵습니다. 끼니 수를 조정하거나 이미 기록한 식단을 확인해 주세요."),

    /** 검증된 제공량 옵션으로는 목표 범위에 도달할 수 없다. */
    SERVING_OPTIONS_INFEASIBLE(
            "추천 식품의 제공량 옵션만으로는 목표에 맞는 식단을 구성하기 어렵습니다. 끼니 수를 조정해 주세요."),

    /** 선택한 끼니 구성으로 남은 목표를 채우는 식단 조합이 존재하지 않는다. */
    REMAINING_MEALS_INFEASIBLE(
            "선택한 끼니 구성으로는 남은 목표에 맞는 추천 식단을 만들기 어렵습니다. 끼니 수를 조정하거나 제한 조건을 줄여 주세요."),

    /** 탐색 한도 내에서 feasible 해를 찾지도 불가능을 증명하지도 못했다(거짓 실패 방지). */
    SEARCH_LIMIT_REACHED(
            "추천 식단을 계산하는 데 시간이 더 필요합니다. 잠시 후 다시 시도하거나 끼니 수를 조정해 주세요.");

    private final String userMessage;

    RecommendationFailureReason(String userMessage) {
        this.userMessage = userMessage;
    }

    public String userMessage() {
        return userMessage;
    }
}
