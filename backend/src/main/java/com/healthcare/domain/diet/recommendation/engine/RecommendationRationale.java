package com.healthcare.domain.diet.recommendation.engine;

/**
 * 추천 근거 (추천 최적화 계획 §10). raw solver 점수 대신 사용자가 이해할 수 있는 근거를 제공한다.
 *
 * <p>하루 단위 근거로 한정한다. 식품 단위 근거는 범위 밖(계획 §13 비범위).
 * 나트륨·당류·포화지방 근거는 후보 데이터 부재로 현재 제외하며, 데이터 확보 후 확장한다.
 *
 * @param filledCalories 추천이 채운 열량(kcal)
 * @param filledProteinG 추천이 채운 단백질(g)
 * @param filledCarbsG   추천이 채운 탄수화물(g)
 * @param calorieHeadroom 목표 열량 상한과의 여유(양수면 상한 아래, 음수면 초과). 상한이 없으면 null.
 * @param proteinGap     목표 단백질 하한과의 차이(양수면 하한 이상 달성). 하한이 0이면 null.
 * @param policyVersion  적용한 영양 정책 버전
 * @param note           한국어 요약 문구
 */
public record RecommendationRationale(
        double filledCalories,
        double filledProteinG,
        double filledCarbsG,
        Double calorieHeadroom,
        Double proteinGap,
        String policyVersion,
        String note
) {
}
