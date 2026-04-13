package com.healthcare.domain.diet.dto;

import com.healthcare.domain.diet.entity.DietLog.MealType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateDietLogRequest {

    @NotNull
    private LocalDate logDate;

    @NotNull
    private MealType mealType;

    @NotEmpty
    @Valid
    private List<CreateFoodEntryRequest> entries;

    private String notes;
}
