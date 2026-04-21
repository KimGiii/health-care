package com.healthcare.domain.diet.mealphoto.dto;

import com.healthcare.domain.diet.mealphoto.entity.MealPhotoAnalysis;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Builder
public class MealPhotoAnalysisResponse {
    private Long analysisId;
    private String status;
    private String provider;
    private String analysisVersion;
    private String previewUrl;
    private OffsetDateTime capturedAt;
    private boolean needsReview;
    private List<String> analysisWarnings;
    private List<MealPhotoAnalysisItemResponse> detectedItems;

    public static MealPhotoAnalysisResponse from(
            MealPhotoAnalysis analysis,
            String previewUrl,
            List<String> warnings,
            List<MealPhotoAnalysisItemResponse> items
    ) {
        return MealPhotoAnalysisResponse.builder()
                .analysisId(analysis.getId())
                .status(analysis.getStatus().name())
                .provider(analysis.getProvider())
                .analysisVersion(analysis.getAnalysisVersion())
                .previewUrl(previewUrl)
                .capturedAt(analysis.getCapturedAt())
                .needsReview(items.stream().anyMatch(MealPhotoAnalysisItemResponse::isNeedsReview))
                .analysisWarnings(warnings)
                .detectedItems(items)
                .build();
    }
}
