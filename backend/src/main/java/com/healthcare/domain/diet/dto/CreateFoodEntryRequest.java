package com.healthcare.domain.diet.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateFoodEntryRequest {

    @NotNull
    private Long foodCatalogId;

    @NotNull
    @Positive
    private Double servingG;

    private String notes;
}
