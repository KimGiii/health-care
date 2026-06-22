package com.healthcare.domain.nutrition.dto;

import com.healthcare.domain.diet.entity.DietLog;

import java.util.List;

/** 특정 날짜에 사용자가 이미 섭취한 영양량 합산. */
public record ConsumedNutrients(
        int calories,
        int proteinG,
        int carbsG,
        int fatG
) {
    public static ConsumedNutrients from(List<DietLog> logs) {
        int cal = 0, protein = 0, carbs = 0, fat = 0;
        for (DietLog log : logs) {
            cal     += nullToZero(log.getTotalCalories());
            protein += nullToZero(log.getTotalProteinG());
            carbs   += nullToZero(log.getTotalCarbsG());
            fat     += nullToZero(log.getTotalFatG());
        }
        return new ConsumedNutrients(cal, protein, carbs, fat);
    }

    public static ConsumedNutrients zero() {
        return new ConsumedNutrients(0, 0, 0, 0);
    }

    private static int nullToZero(Double value) {
        return value == null ? 0 : (int) Math.round(value);
    }
}
