package com.healthcare.domain.diet.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AiNutritionEstimateRequest {

    @NotBlank(message = "음식 이름은 필수입니다.")
    @Size(max = 100, message = "음식 이름은 100자 이하여야 합니다.")
    private String foodName;
}
