package com.healthcare.domain.diet.mealphoto.dto;

import com.healthcare.domain.diet.mealphoto.entity.MealPhotoAnalysisItem;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MealPhotoAnalysisItemResponse {
    private Long analysisItemId;
    private String label;
    private Long matchedFoodCatalogId;
    private Double estimatedServingG;
    private Double calories;
    private Double proteinG;
    private Double carbsG;
    private Double fatG;
    private Double confidence;
    private boolean needsReview;
    private String unknownOrUncertain;

    public static MealPhotoAnalysisItemResponse from(MealPhotoAnalysisItem item) {
        return MealPhotoAnalysisItemResponse.builder()
                .analysisItemId(item.getId())
                .label(item.getLabel())
                .matchedFoodCatalogId(item.getMatchedFoodCatalogId())
                .estimatedServingG(item.getEstimatedServingG())
                .calories(item.getCalories())
                .proteinG(item.getProteinG())
                .carbsG(item.getCarbsG())
                .fatG(item.getFatG())
                .confidence(item.getConfidence())
                .needsReview(item.isNeedsReview())
                .unknownOrUncertain(item.getUnknownOrUncertain())
                .build();
    }
}
