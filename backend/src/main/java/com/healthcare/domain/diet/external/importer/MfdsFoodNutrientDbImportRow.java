package com.healthcare.domain.diet.external.importer;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MfdsFoodNutrientDbImportRow {

    private final String foodCode;
    private final String foodNameKr;
    private final String foodCategoryName;
    private final String sellerManufacturerName;
    private final String makerName;
    private final String importedManufacturerName;
    private final String servingSize;
    private final String foodWeight;
    private final String nutritionAmountServing;
    private final String amountNum1;
    private final String amountNum3;
    private final String amountNum4;
    private final String amountNum6;
    private final String amountNum7;
    private final String amountNum8;
    private final String amountNum13;
    private final String updateDate;
}
