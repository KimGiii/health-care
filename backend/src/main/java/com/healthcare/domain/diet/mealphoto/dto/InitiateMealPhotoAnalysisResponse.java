package com.healthcare.domain.diet.mealphoto.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
@Builder
public class InitiateMealPhotoAnalysisResponse {
    private Long analysisId;
    private String storageKey;
    private String uploadUrl;
    private String previewUrl;
    private OffsetDateTime expiresAt;
}
