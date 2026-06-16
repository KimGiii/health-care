package com.healthcare.domain.diet.external.importer;

import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.entity.FoodCatalogSource;
import com.healthcare.domain.diet.entity.RecommendationStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.healthcare.domain.diet.external.importer.FoodCatalogImportText.DATA_VERSION_MAX_LENGTH;
import static com.healthcare.domain.diet.external.importer.FoodCatalogImportText.FOOD_CODE_MAX_LENGTH;
import static com.healthcare.domain.diet.external.importer.FoodCatalogImportText.NAME_MAX_LENGTH;
import static com.healthcare.domain.diet.external.importer.FoodCatalogImportText.ORGANIZATION_NAME_MAX_LENGTH;
import static com.healthcare.domain.diet.external.importer.FoodCatalogImportText.SERVING_REFERENCE_MAX_LENGTH;

abstract class StandardFoodCatalogImporter implements FoodCatalogPageImporter<StandardFoodImportRow> {

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

    private final FoodCatalogIngestService ingestService;
    private final FoodCatalogSource source;
    private final String sourceDetail;

    protected StandardFoodCatalogImporter(
            FoodCatalogIngestService ingestService,
            FoodCatalogSource source,
            String sourceDetail) {
        this.ingestService = ingestService;
        this.source = source;
        this.sourceDetail = sourceDetail;
    }

    public FoodCatalogImportResult importRows(List<StandardFoodImportRow> rows) {
        List<FoodCatalogIngestCandidate> candidates = rows.stream()
                .map(this::toFoodCatalog)
                .map(food -> food
                        .map(FoodCatalogIngestCandidate::accepted)
                        .orElseGet(FoodCatalogIngestCandidate::skipped))
                .toList();
        return ingestService.ingest(candidates, FoodCatalogIngestCurationMode.PRESERVE_EXISTING);
    }

    protected String maker(StandardFoodImportRow row) {
        return null;
    }

    protected String brandName(StandardFoodImportRow row) {
        return null;
    }

    protected String normalize(String value) {
        return FoodCatalogImportText.normalize(value);
    }

    private Optional<FoodCatalog> toFoodCatalog(StandardFoodImportRow row) {
        String foodCode = normalize(row.getFoodCode());
        String foodName = FoodCatalogImportText.normalizeToMaxLength(row.getFoodName(), NAME_MAX_LENGTH);
        Double calories = parseDouble(row.getCalories());
        if (foodCode == null || foodCode.length() > FOOD_CODE_MAX_LENGTH || foodName == null || calories == null) {
            return Optional.empty();
        }

        return Optional.of(FoodCatalog.builder()
                .foodCode(foodCode)
                .source(source)
                .sourceDetail(sourceDetail)
                .name(foodName)
                .nameKo(foodName)
                .brandName(FoodCatalogImportText.normalizeToMaxLength(brandName(row), ORGANIZATION_NAME_MAX_LENGTH))
                .maker(FoodCatalogImportText.normalizeToMaxLength(maker(row), ORGANIZATION_NAME_MAX_LENGTH))
                .category(mapCategory(row.getCategoryName()))
                .servingSizeG(parseServingSizeG(row.getFoodSize()))
                .servingReference(FoodCatalogImportText.normalizeToMaxLength(
                        row.getNutritionServingSize(), SERVING_REFERENCE_MAX_LENGTH))
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
                .dataVersion(FoodCatalogImportText.normalizeToMaxLength(row.getDataVersion(), DATA_VERSION_MAX_LENGTH))
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
