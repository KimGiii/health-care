package com.healthcare.domain.diet.external.importer;

import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.entity.FoodCatalogSource;
import com.healthcare.domain.diet.entity.RecommendationStatus;
import com.healthcare.domain.diet.repository.FoodCatalogRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

abstract class StandardFoodCatalogImporter {

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
            Map.entry("즉석", FoodCategory.PROCESSED),
            Map.entry("가공", FoodCategory.PROCESSED)
    );

    private final FoodCatalogRepository foodCatalogRepository;
    private final FoodCatalogSource source;
    private final String sourceDetail;

    protected StandardFoodCatalogImporter(
            FoodCatalogRepository foodCatalogRepository,
            FoodCatalogSource source,
            String sourceDetail) {
        this.foodCatalogRepository = foodCatalogRepository;
        this.source = source;
        this.sourceDetail = sourceDetail;
    }

    @Transactional
    public FoodCatalogImportResult importRows(List<StandardFoodImportRow> rows) {
        int created = 0;
        int updated = 0;
        int skipped = 0;

        for (StandardFoodImportRow row : rows) {
            Optional<FoodCatalog> food = toFoodCatalog(row);
            if (food.isEmpty()) {
                skipped++;
                continue;
            }

            Optional<FoodCatalog> existing = foodCatalogRepository
                    .findBySourceAndFoodCode(source, food.get().getFoodCode());
            if (existing.isPresent()) {
                existing.get().updateFromImportedCatalog(food.get());
                foodCatalogRepository.save(existing.get());
                updated++;
                continue;
            }

            foodCatalogRepository.save(food.get());
            created++;
        }

        return new FoodCatalogImportResult(created, updated, skipped);
    }

    protected String maker(StandardFoodImportRow row) {
        return null;
    }

    protected String brandName(StandardFoodImportRow row) {
        return null;
    }

    protected String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.isBlank() ? null : normalized;
    }

    private Optional<FoodCatalog> toFoodCatalog(StandardFoodImportRow row) {
        String foodCode = normalize(row.getFoodCode());
        String foodName = normalize(row.getFoodName());
        Double calories = parseDouble(row.getCalories());
        if (foodCode == null || foodName == null || calories == null) {
            return Optional.empty();
        }

        return Optional.of(FoodCatalog.builder()
                .foodCode(foodCode)
                .source(source)
                .sourceDetail(sourceDetail)
                .name(foodName)
                .nameKo(foodName)
                .brandName(brandName(row))
                .maker(maker(row))
                .category(mapCategory(row.getCategoryName()))
                .servingSizeG(parseServingSizeG(row.getFoodSize()))
                .servingReference(normalize(row.getNutritionServingSize()))
                .caloriesPer100g(calories)
                .proteinPer100g(parseDouble(row.getProtein()))
                .carbsPer100g(parseDouble(row.getCarbs()))
                .fatPer100g(parseDouble(row.getFat()))
                .sugarsPer100g(parseDouble(row.getSugar()))
                .dietaryFiberPer100g(parseDouble(row.getDietaryFiber()))
                .saturatedFatPer100g(parseDouble(row.getSaturatedFat()))
                .transFatPer100g(parseDouble(row.getTransFat()))
                .cholesterolPer100gMg(parseDouble(row.getCholesterol()))
                .sodiumPer100gMg(parseDouble(row.getSodium()))
                .recommendationStatus(RecommendationStatus.SEARCH_ONLY)
                .dataVersion(normalize(row.getDataVersion()))
                .lastVerifiedAt(parseDate(row.getLastVerifiedDate()))
                .isCustom(false)
                .build());
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
                .orElse(FoodCategory.PROCESSED);
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
}
