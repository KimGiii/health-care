package com.healthcare.domain.diet.entity;

/**
 * 제공량 옵션 스냅샷.
 * 추천 후보({@link com.healthcare.domain.diet.recommendation.candidate.DietRecommendationCandidate})와
 * API 응답({@link com.healthcare.domain.diet.dto.FoodCatalogResponse}) 모두에서 사용한다.
 */
public record ServingOptionSnapshot(
        String label,
        String labelKo,
        double equivalentG,
        int sortOrder,
        FoodServingOption.ServingType servingType,
        boolean verified
) {
    public static ServingOptionSnapshot from(FoodServingOption option) {
        return new ServingOptionSnapshot(
                option.getLabel(),
                option.getLabelKo(),
                option.getEquivalentG(),
                option.getSortOrder(),
                option.getServingType(),
                option.isVerified()
        );
    }
}
