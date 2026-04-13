package com.healthcare.domain.diet.dto;

import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCustomFoodRequest {

    @NotBlank
    private String name;

    private String nameKo;

    @NotNull
    private FoodCategory category;

    @NotNull
    @Positive
    private Double caloriesPer100g;

    private Double proteinPer100g;

    private Double carbsPer100g;

    private Double fatPer100g;
}
