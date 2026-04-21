package com.healthcare.domain.diet.mealphoto.dto;

import com.healthcare.domain.diet.dto.CreateDietLogResponse;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ConfirmMealPhotoAnalysisResponse {
    private Long analysisId;
    private String status;
    private CreateDietLogResponse dietLog;
}
