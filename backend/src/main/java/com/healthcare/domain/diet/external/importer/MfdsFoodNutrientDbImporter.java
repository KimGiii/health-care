package com.healthcare.domain.diet.external.importer;

import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.entity.FoodCatalogSource;
import com.healthcare.domain.diet.entity.RecommendationStatus;
import com.healthcare.domain.diet.repository.FoodCatalogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MfdsFoodNutrientDbImporter implements FoodCatalogPageImporter<MfdsFoodNutrientDbImportRow> {

    private static final String SOURCE_DETAIL = "FoodNtrCpntDbInfo02";
    private static final ZoneOffset KOREA_OFFSET = ZoneOffset.ofHours(9);
    private static final Map<String, FoodCategory> CATEGORY_MAPPING = Map.ofEntries(
            Map.entry("곡류", FoodCategory.GRAIN),
            Map.entry("서류", FoodCategory.GRAIN),
            Map.entry("당류", FoodCategory.PROCESSED),
            Map.entry("두류", FoodCategory.PROTEIN_SOURCE),
            Map.entry("견과류", FoodCategory.FAT),
            Map.entry("채소류", FoodCategory.VEGETABLE),
            Map.entry("과일류", FoodCategory.FRUIT),
            Map.entry("버섯류", FoodCategory.VEGETABLE),
            Map.entry("육류", FoodCategory.PROTEIN_SOURCE),
            Map.entry("가금류", FoodCategory.PROTEIN_SOURCE),
            Map.entry("난류", FoodCategory.PROTEIN_SOURCE),
            Map.entry("어패류", FoodCategory.PROTEIN_SOURCE),
            Map.entry("해조류", FoodCategory.VEGETABLE),
            Map.entry("우유류", FoodCategory.DAIRY),
            Map.entry("유제품류", FoodCategory.DAIRY),
            Map.entry("유지류", FoodCategory.FAT),
            Map.entry("음료류", FoodCategory.BEVERAGE),
            Map.entry("주류", FoodCategory.BEVERAGE),
            Map.entry("가공", FoodCategory.PROCESSED)
    );

    private final FoodCatalogRepository foodCatalogRepository;

    @Transactional
    public FoodCatalogImportResult importRows(List<MfdsFoodNutrientDbImportRow> rows) {
        int created = 0;
        int updated = 0;
        int skipped = 0;

        for (MfdsFoodNutrientDbImportRow row : rows) {
            Optional<FoodCatalog> food = toFoodCatalog(row);
            if (food.isEmpty()) {
                skipped++;
                continue;
            }

            Optional<FoodCatalog> existing = foodCatalogRepository
                    .findBySourceAndFoodCode(FoodCatalogSource.MFDS_FOOD_NUTRIENT_DB, food.get().getFoodCode());
            if (existing.isPresent()) {
                existing.get().updateSourceFactsFromImportedCatalog(food.get());
                foodCatalogRepository.save(existing.get());
                updated++;
                continue;
            }

            foodCatalogRepository.save(food.get());
            created++;
        }

        return new FoodCatalogImportResult(created, updated, skipped);
    }

    private Optional<FoodCatalog> toFoodCatalog(MfdsFoodNutrientDbImportRow row) {
        String foodCode = normalize(row.getFoodCode());
        String foodName = normalize(row.getFoodNameKr());
        Double calories = parseDouble(row.getAmountNum1());
        if (foodCode == null || foodName == null || calories == null) {
            return Optional.empty();
        }

        return Optional.of(FoodCatalog.builder()
                .foodCode(foodCode)
                .source(FoodCatalogSource.MFDS_FOOD_NUTRIENT_DB)
                .sourceDetail(SOURCE_DETAIL)
                .name(foodName)
                .nameKo(foodName)
                .brandName(normalize(row.getSellerManufacturerName()))
                .maker(firstNonBlank(row.getMakerName(), row.getImportedManufacturerName(), row.getSellerManufacturerName()))
                .category(mapCategory(row.getFoodCategoryName()))
                .servingSizeG(servingSizeG(row))
                .servingReference(firstNonBlank(row.getNutritionAmountServing(), row.getServingSize()))
                .caloriesPer100g(calories)
                .proteinPer100g(parseDouble(row.getAmountNum3()))
                .fatPer100g(parseDouble(row.getAmountNum4()))
                .carbsPer100g(parseDouble(row.getAmountNum6()))
                .sugarsPer100g(parseDouble(row.getAmountNum7()))
                .dietaryFiberPer100g(parseDouble(row.getAmountNum8()))
                .sodiumPer100gMg(parseDouble(row.getAmountNum13()))
                .recommendationStatus(RecommendationStatus.SEARCH_ONLY)
                .dataVersion(normalize(row.getUpdateDate()))
                .lastVerifiedAt(parseDate(row.getUpdateDate()))
                .isCustom(false)
                .build());
    }

    private Double servingSizeG(MfdsFoodNutrientDbImportRow row) {
        Double foodWeight = parseDouble(row.getFoodWeight());
        if (foodWeight != null) {
            return foodWeight;
        }
        return parseServingSizeG(row.getServingSize());
    }

    private FoodCategory mapCategory(String categoryName) {
        String normalized = normalize(categoryName);
        if (normalized == null) {
            return FoodCategory.OTHER;
        }
        return CATEGORY_MAPPING.entrySet().stream()
                .filter(entry -> normalized.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(FoodCategory.OTHER);
    }

    private OffsetDateTime parseDate(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        try {
            return LocalDate.parse(normalized).atStartOfDay().atOffset(KOREA_OFFSET);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private Double parseServingSizeG(String value) {
        String normalized = normalize(value);
        if (normalized == null || !normalized.toLowerCase().contains("g")) {
            return null;
        }
        return parseDouble(normalized.replaceAll("[^0-9.]", ""));
    }

    private Double parseDouble(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        try {
            return Double.parseDouble(normalized.replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = normalize(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.isBlank() ? null : normalized;
    }
}
