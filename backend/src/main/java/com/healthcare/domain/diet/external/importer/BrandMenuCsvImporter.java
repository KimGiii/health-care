package com.healthcare.domain.diet.external.importer;

import com.healthcare.common.exception.ValidationException;
import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.entity.FoodCatalogSource;
import com.healthcare.domain.diet.entity.RecommendationStatus;
import com.healthcare.domain.diet.repository.FoodCatalogRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BrandMenuCsvImporter {

    private static final ZoneOffset KOREA_OFFSET = ZoneOffset.ofHours(9);

    private final FoodCatalogRepository foodCatalogRepository;

    /**
     * CSV 파일을 파싱해서 BRAND_OFFICIAL 출처로 upsert한다.
     * nutrition_basis로 입력 영양값의 기준을 명시하고 내부 저장은 100g당 값으로 정규화한다.
     */
    @Transactional
    public FoodCatalogImportResult importFromCsv(InputStream csvStream) throws IOException {
        List<BrandMenuCsvRow> rows = parseCsv(csvStream);
        return importRows(rows);
    }

    List<BrandMenuCsvRow> parseCsv(InputStream csvStream) throws IOException {
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .build();

        List<BrandMenuCsvRow> rows = new ArrayList<>();
        try (CSVParser parser = new CSVParser(
                new InputStreamReader(csvStream, StandardCharsets.UTF_8), format)) {
            validateHeaders(new ArrayList<>(parser.getHeaderMap().keySet()));
            for (CSVRecord record : parser) {
                rows.add(BrandMenuCsvRow.builder()
                        .rowNumber(record.getRecordNumber() + 1)
                        .brandName(record.get("brand_name"))
                        .menuName(record.get("menu_name"))
                        .category(record.get("category"))
                        .nutritionBasis(record.get("nutrition_basis"))
                        .servingSizeG(record.get("serving_size_g"))
                        .calories(record.get("calories"))
                        .protein(record.get("protein"))
                        .carbs(record.get("carbs"))
                        .fat(record.get("fat"))
                        .sodium(record.get("sodium"))
                        .sugar(record.get("sugar"))
                        .saturatedFat(record.get("saturated_fat"))
                        .sourceUrl(record.get("source_url"))
                        .lastVerifiedAt(record.get("last_verified_at"))
                        .recommendationStatus(record.get("recommendation_status"))
                        .recommendationReason(record.get("recommendation_reason"))
                        .build());
            }
        }
        return rows;
    }

    @Transactional
    public FoodCatalogImportResult importRows(List<BrandMenuCsvRow> rows) {
        int created = 0;
        int updated = 0;
        int skipped = 0;
        List<FoodCatalogImportRejectedRow> rejectedRows = new ArrayList<>();

        for (BrandMenuCsvRow row : rows) {
            ConversionResult converted = toFoodCatalog(row);
            if (converted.rejected()) {
                skipped++;
                rejectedRows.add(converted.rejection());
                continue;
            }

            FoodCatalog food = converted.food();
            Optional<FoodCatalog> existing = foodCatalogRepository
                    .findBySourceAndFoodCode(FoodCatalogSource.BRAND_OFFICIAL, food.getFoodCode());

            if (existing.isPresent()) {
                FoodCatalog existingFood = existing.get();
                existingFood.updateSourceFactsFromImportedCatalog(food);
                existingFood.updateCuration(food.curation());
                foodCatalogRepository.save(existingFood);
                updated++;
            } else {
                foodCatalogRepository.save(food);
                created++;
            }
        }

        return new FoodCatalogImportResult(created, updated, skipped, rejectedRows);
    }

    private ConversionResult toFoodCatalog(BrandMenuCsvRow row) {
        String brandName = normalize(row.brandName());
        String menuName = normalize(row.menuName());
        BrandMenuNutritionBasis nutritionBasis = parseNutritionBasis(row.nutritionBasis());
        Double servingSizeG = parseDouble(row.servingSizeG());
        Double caloriesPerServing = parseDouble(row.calories());

        if (brandName == null) {
            return ConversionResult.rejected(row, "brand_name", "브랜드명은 필수입니다.");
        }
        if (menuName == null) {
            return ConversionResult.rejected(row, "menu_name", "메뉴명은 필수입니다.");
        }
        if (nutritionBasis == null) {
            return ConversionResult.rejected(row, "nutrition_basis", "PER_SERVING 또는 PER_100G를 입력해야 합니다.");
        }
        if (caloriesPerServing == null) {
            return ConversionResult.rejected(row, "calories", "칼로리는 필수 숫자 값입니다.");
        }

        String invalidNumberField = firstInvalidNumberField(row);
        if (invalidNumberField != null) {
            return ConversionResult.rejected(row, invalidNumberField, "숫자 형식이어야 합니다.");
        }

        if (nutritionBasis == BrandMenuNutritionBasis.PER_SERVING && (servingSizeG == null || servingSizeG <= 0)) {
            return ConversionResult.rejected(row, "serving_size_g", "PER_SERVING은 0보다 큰 제공량이 필요합니다.");
        }
        if (nutritionBasis == BrandMenuNutritionBasis.PER_100G && servingSizeG != null && servingSizeG <= 0) {
            return ConversionResult.rejected(row, "serving_size_g", "제공량을 입력할 때는 0보다 커야 합니다.");
        }

        Double caloriesPer100g = toNutrientPer100g(caloriesPerServing, servingSizeG, nutritionBasis);

        String foodCode = brandName.toLowerCase().replaceAll("\\s+", "_")
                + ":" + menuName.toLowerCase().replaceAll("\\s+", "_");

        RecommendationStatus status = parseRecommendationStatus(row.recommendationStatus());

        return ConversionResult.accepted(FoodCatalog.builder()
                .foodCode(foodCode)
                .source(FoodCatalogSource.BRAND_OFFICIAL)
                .sourceDetail(normalize(row.sourceUrl()))
                .name(menuName)
                .nameKo(menuName)
                .brandName(brandName)
                .category(parseCategory(row.category()))
                .servingSizeG(servingSizeG)
                .servingReference(servingSizeG != null ? formatGrams(servingSizeG) : "100g")
                .caloriesPer100g(round(caloriesPer100g))
                .proteinPer100g(toNutrientPer100g(parseDouble(row.protein()), servingSizeG, nutritionBasis))
                .carbsPer100g(toNutrientPer100g(parseDouble(row.carbs()), servingSizeG, nutritionBasis))
                .fatPer100g(toNutrientPer100g(parseDouble(row.fat()), servingSizeG, nutritionBasis))
                .sodiumPer100gMg(toNutrientPer100g(parseDouble(row.sodium()), servingSizeG, nutritionBasis))
                .sugarsPer100g(toNutrientPer100g(parseDouble(row.sugar()), servingSizeG, nutritionBasis))
                .saturatedFatPer100g(toNutrientPer100g(parseDouble(row.saturatedFat()), servingSizeG, nutritionBasis))
                .recommendationStatus(status)
                .recommendationReason(normalize(row.recommendationReason()))
                .lastVerifiedAt(parseDate(row.lastVerifiedAt()))
                .isCustom(false)
                .build());
    }

    private void validateHeaders(List<String> actualHeaders) {
        List<String> expectedHeaders = Arrays.asList(BrandMenuCsvRow.HEADERS);
        if (!actualHeaders.equals(expectedHeaders)) {
            throw new ValidationException("브랜드 메뉴 CSV 헤더가 템플릿과 일치해야 합니다.");
        }
    }

    private Double toNutrientPer100g(
            Double inputValue,
            Double servingSizeG,
            BrandMenuNutritionBasis nutritionBasis) {
        if (inputValue == null) {
            return null;
        }
        if (nutritionBasis == BrandMenuNutritionBasis.PER_100G) {
            return round(inputValue);
        }
        return round((inputValue / servingSizeG) * 100.0);
    }

    private String firstInvalidNumberField(BrandMenuCsvRow row) {
        if (isInvalidNumber(row.servingSizeG())) return "serving_size_g";
        if (isInvalidNumber(row.calories())) return "calories";
        if (isInvalidNumber(row.protein())) return "protein";
        if (isInvalidNumber(row.carbs())) return "carbs";
        if (isInvalidNumber(row.fat())) return "fat";
        if (isInvalidNumber(row.sodium())) return "sodium";
        if (isInvalidNumber(row.sugar())) return "sugar";
        if (isInvalidNumber(row.saturatedFat())) return "saturated_fat";
        return null;
    }

    private boolean isInvalidNumber(String value) {
        return normalize(value) != null && parseDouble(value) == null;
    }

    private BrandMenuNutritionBasis parseNutritionBasis(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        try {
            return BrandMenuNutritionBasis.valueOf(normalized.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private FoodCategory parseCategory(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return FoodCategory.OTHER;
        }
        try {
            return FoodCategory.valueOf(normalized.toUpperCase());
        } catch (IllegalArgumentException e) {
            return FoodCategory.PROCESSED;
        }
    }

    private RecommendationStatus parseRecommendationStatus(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return RecommendationStatus.SEARCH_ONLY;
        }
        try {
            return RecommendationStatus.valueOf(normalized.toUpperCase());
        } catch (IllegalArgumentException e) {
            return RecommendationStatus.SEARCH_ONLY;
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

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim().replaceAll("\\s+", " ");
        return trimmed.isEmpty() ? null : trimmed;
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private String formatGrams(Double grams) {
        if (grams == null) {
            return null;
        }
        if (grams % 1.0 == 0) {
            return String.format("%.0fg", grams);
        }
        return String.format("%.1fg", grams);
    }

    private record ConversionResult(
            FoodCatalog food,
            FoodCatalogImportRejectedRow rejection
    ) {
        static ConversionResult accepted(FoodCatalog food) {
            return new ConversionResult(food, null);
        }

        static ConversionResult rejected(BrandMenuCsvRow row, String field, String reason) {
            return new ConversionResult(
                    null,
                    new FoodCatalogImportRejectedRow(row.rowNumber(), field, reason)
            );
        }

        boolean rejected() {
            return rejection != null;
        }
    }
}
