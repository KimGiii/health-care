package com.healthcare.domain.diet.external.dto;

import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.external.dto.ExternalFoodResult.FoodDataSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportFoodRequest {

    @NotNull
    private FoodDataSource source;

    @NotBlank
    private String externalId;

    @NotBlank
    private String name;

    private String nameKo;

    private String brand;

    @NotNull
    private FoodCategory category;

    @NotNull
    @Positive
    private Double caloriesPer100g;

    private Double proteinPer100g;

    private Double carbsPer100g;

    private Double fatPer100g;
}
