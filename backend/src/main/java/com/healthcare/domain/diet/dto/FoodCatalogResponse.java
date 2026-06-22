package com.healthcare.domain.diet.dto;

import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.entity.FoodCatalogSource;
import com.healthcare.domain.diet.entity.FoodServingOption;
import com.healthcare.domain.diet.entity.RecommendationStatus;
import com.healthcare.domain.diet.entity.ServingOptionSnapshot;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Builder
public class FoodCatalogResponse {

    private final Long id;
    private final String name;
    private final String nameKo;
    private final FoodCategory category;
    private final Double caloriesPer100g;
    private final Double proteinPer100g;
    private final Double carbsPer100g;
    private final Double fatPer100g;
    private final Double sugarsPer100g;
    private final Double dietaryFiberPer100g;
    private final Double saturatedFatPer100g;
    private final Double transFatPer100g;
    private final Double cholesterolPer100gMg;
    private final Double sodiumPer100gMg;
    private final String foodCode;
    private final FoodCatalogSource source;
    private final String sourceDetail;
    private final String brandName;
    private final String maker;
    private final Double servingSizeG;
    private final String servingReference;
    private final RecommendationStatus recommendationStatus;
    private final String recommendationReason;
    private final String dataVersion;
    private final OffsetDateTime lastVerifiedAt;
    private final boolean custom;
    private final long usageCount;
    private final Long createdByUserId;
    /** iOS 프리셋 UX용 제공량 옵션 목록. sort_order 오름차순. 없으면 빈 리스트. */
    private final List<ServingOptionSnapshot> servingOptions;

    public static FoodCatalogResponse from(FoodCatalog food) {
        return from(food, List.of());
    }

    public static FoodCatalogResponse from(FoodCatalog food, List<FoodServingOption> options) {
        List<ServingOptionSnapshot> snapshots = options.stream()
                .sorted(java.util.Comparator.comparingInt(FoodServingOption::getSortOrder))
                .map(ServingOptionSnapshot::from)
                .toList();
        return FoodCatalogResponse.builder()
                .id(food.getId())
                .name(food.getName())
                .nameKo(food.getNameKo())
                .category(food.getCategory())
                .caloriesPer100g(food.getCaloriesPer100g())
                .proteinPer100g(food.getProteinPer100g())
                .carbsPer100g(food.getCarbsPer100g())
                .fatPer100g(food.getFatPer100g())
                .sugarsPer100g(food.getSugarsPer100g())
                .dietaryFiberPer100g(food.getDietaryFiberPer100g())
                .saturatedFatPer100g(food.getSaturatedFatPer100g())
                .transFatPer100g(food.getTransFatPer100g())
                .cholesterolPer100gMg(food.getCholesterolPer100gMg())
                .sodiumPer100gMg(food.getSodiumPer100gMg())
                .foodCode(food.getFoodCode())
                .source(food.getSource())
                .sourceDetail(food.getSourceDetail())
                .brandName(food.getBrandName())
                .maker(food.getMaker())
                .servingSizeG(food.getServingSizeG())
                .servingReference(food.getServingReference())
                .recommendationStatus(food.getRecommendationStatus())
                .recommendationReason(food.getRecommendationReason())
                .dataVersion(food.getDataVersion())
                .lastVerifiedAt(food.getLastVerifiedAt())
                .custom(food.getIsCustom())
                .usageCount(food.getUsageCount() != null ? food.getUsageCount() : 0L)
                .createdByUserId(food.getCreatedByUserId())
                .servingOptions(snapshots)
                .build();
    }
}
