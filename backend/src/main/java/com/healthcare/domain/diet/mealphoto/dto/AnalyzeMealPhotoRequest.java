package com.healthcare.domain.diet.mealphoto.dto;

import com.healthcare.domain.diet.entity.DietLog.MealType;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class AnalyzeMealPhotoRequest {

    @NotNull(message = "mealType은 필수입니다.")
    private MealType mealType;
}
