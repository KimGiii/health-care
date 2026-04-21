package com.healthcare.domain.diet.mealphoto.dto;

import com.healthcare.domain.diet.entity.DietLog.MealType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
public class ConfirmMealPhotoAnalysisRequest {

    @NotNull(message = "logDate는 필수입니다.")
    private LocalDate logDate;

    @NotNull(message = "mealType은 필수입니다.")
    private MealType mealType;

    private String notes;

    @NotEmpty(message = "확정할 항목이 최소 1개 이상 필요합니다.")
    @Valid
    private List<ConfirmMealPhotoAnalysisItemRequest> items;

    @Getter
    public static class ConfirmMealPhotoAnalysisItemRequest {
        private Long analysisItemId;

        @NotNull(message = "label은 필수입니다.")
        private String label;

        private Long matchedFoodCatalogId;

        @NotNull(message = "estimatedServingG는 필수입니다.")
        @Positive(message = "estimatedServingG는 0보다 커야 합니다.")
        private Double estimatedServingG;

        @NotNull(message = "calories는 필수입니다.")
        @Positive(message = "calories는 0보다 커야 합니다.")
        private Double calories;

        private Double proteinG;
        private Double carbsG;
        private Double fatG;
        private String notes;
    }
}
