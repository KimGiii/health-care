package com.healthcare.domain.diet.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 추천 큐레이션 운영 검수에서 사용하는 1회 제공량 기준 주의 판정.
 * 런타임 추천 엔진은 이미 저장된 recommendation_status를 사용한다.
 */
public final class RecommendationCautionPolicy {

    /** 1회 제공량이 WHO 성인 나트륨 권고 상한(2000mg)의 30% 이상이면 주의 표시한다. */
    public static final double SODIUM_CAUTION_THRESHOLD_MG = 600.0;
    /** 1회 제공량이 2000kcal 기준 free sugars 10% 상한(50g)의 30% 이상이면 주의 표시한다. */
    public static final double SUGARS_CAUTION_THRESHOLD_G = 15.0;
    /** 1회 제공량이 국내 브랜드 영양표의 포화지방 일일 기준치 15g의 30% 이상이면 주의 표시한다. */
    public static final double SATURATED_FAT_CAUTION_THRESHOLD_G = 4.5;

    private RecommendationCautionPolicy() {
    }

    public static Assessment assess(Double sodiumMg, Double sugarsG, Double saturatedFatG) {
        ArrayList<CautionNutrient> nutrients = new ArrayList<>();
        if (sodiumMg != null && sodiumMg >= SODIUM_CAUTION_THRESHOLD_MG) {
            nutrients.add(CautionNutrient.SODIUM);
        }
        if (sugarsG != null && sugarsG >= SUGARS_CAUTION_THRESHOLD_G) {
            nutrients.add(CautionNutrient.SUGARS);
        }
        if (saturatedFatG != null && saturatedFatG >= SATURATED_FAT_CAUTION_THRESHOLD_G) {
            nutrients.add(CautionNutrient.SATURATED_FAT);
        }
        return new Assessment(List.copyOf(nutrients));
    }

    public enum CautionNutrient {
        SODIUM("나트륨"),
        SUGARS("당류"),
        SATURATED_FAT("포화지방");

        private final String displayName;

        CautionNutrient(String displayName) {
            this.displayName = displayName;
        }
    }

    public record Assessment(List<CautionNutrient> nutrients) {
        public boolean requiresCaution() {
            return !nutrients.isEmpty();
        }

        public String defaultReason() {
            if (!requiresCaution()) {
                return null;
            }
            return nutrients.stream()
                    .map(nutrient -> nutrient.displayName)
                    .collect(Collectors.joining("/"))
                    + " 주의";
        }
    }
}
