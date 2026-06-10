package com.healthcare.domain.diet.external.importer;

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
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BrandMenuCsvImporter {

    private static final ZoneOffset KOREA_OFFSET = ZoneOffset.ofHours(9);

    private final FoodCatalogRepository foodCatalogRepository;

    /**
     * CSV 파일을 파싱해서 BRAND_OFFICIAL 출처로 upsert한다.
     * 칼로리·영양소 값은 1회 제공량(serving_size_g) 기준으로 입력받고 100g당 값으로 변환한다.
     */
    @Transactional
    public FoodCatalogImportResult importFromCsv(InputStream csvStream) throws IOException {
        List<BrandMenuCsvRow> rows = parseCsv(csvStream);
        return importRows(rows);
    }

    List<BrandMenuCsvRow> parseCsv(InputStream csvStream) throws IOException {
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader(BrandMenuCsvRow.HEADERS)
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .build();

        List<BrandMenuCsvRow> rows = new ArrayList<>();
        try (CSVParser parser = new CSVParser(
                new InputStreamReader(csvStream, StandardCharsets.UTF_8), format)) {
            for (CSVRecord record : parser) {
                rows.add(BrandMenuCsvRow.builder()
                        .brandName(record.get("brand_name"))
                        .menuName(record.get("menu_name"))
                        .category(record.get("category"))
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

        for (BrandMenuCsvRow row : rows) {
            Optional<FoodCatalog> converted = toFoodCatalog(row);
            if (converted.isEmpty()) {
                skipped++;
                continue;
            }

            FoodCatalog food = converted.get();
            Optional<FoodCatalog> existing = foodCatalogRepository
                    .findBySourceAndFoodCode(FoodCatalogSource.BRAND_OFFICIAL, food.getFoodCode());

            if (existing.isPresent()) {
                existing.get().updateFromImportedCatalog(food);
                foodCatalogRepository.save(existing.get());
                updated++;
            } else {
                foodCatalogRepository.save(food);
                created++;
            }
        }

        return new FoodCatalogImportResult(created, updated, skipped);
    }

    private Optional<FoodCatalog> toFoodCatalog(BrandMenuCsvRow row) {
        String brandName = normalize(row.brandName());
        String menuName = normalize(row.menuName());
        Double servingSizeG = parseDouble(row.servingSizeG());
        Double caloriesPerServing = parseDouble(row.calories());

        if (brandName == null || menuName == null || caloriesPerServing == null) {
            return Optional.empty();
        }

        // serving_size_g가 없거나 0이면 칼로리 값을 100g당으로 그대로 사용
        Double caloriesPer100g;
        if (servingSizeG != null && servingSizeG > 0) {
            caloriesPer100g = (caloriesPerServing / servingSizeG) * 100.0;
        } else {
            caloriesPer100g = caloriesPerServing;
        }

        String foodCode = brandName.toLowerCase().replaceAll("\\s+", "_")
                + ":" + menuName.toLowerCase().replaceAll("\\s+", "_");

        RecommendationStatus status = parseRecommendationStatus(row.recommendationStatus());

        return Optional.of(FoodCatalog.builder()
                .foodCode(foodCode)
                .source(FoodCatalogSource.BRAND_OFFICIAL)
                .sourceDetail(normalize(row.sourceUrl()))
                .name(menuName)
                .nameKo(menuName)
                .brandName(brandName)
                .category(parseCategory(row.category()))
                .servingSizeG(servingSizeG)
                .servingReference(servingSizeG != null ? servingSizeG.intValue() + "g" : null)
                .caloriesPer100g(round(caloriesPer100g))
                .proteinPer100g(toNutrientPer100g(parseDouble(row.protein()), servingSizeG))
                .carbsPer100g(toNutrientPer100g(parseDouble(row.carbs()), servingSizeG))
                .fatPer100g(toNutrientPer100g(parseDouble(row.fat()), servingSizeG))
                .sodiumPer100gMg(toNutrientPer100g(parseDouble(row.sodium()), servingSizeG))
                .sugarsPer100g(toNutrientPer100g(parseDouble(row.sugar()), servingSizeG))
                .saturatedFatPer100g(toNutrientPer100g(parseDouble(row.saturatedFat()), servingSizeG))
                .recommendationStatus(status)
                .recommendationReason(normalize(row.recommendationReason()))
                .lastVerifiedAt(parseDate(row.lastVerifiedAt()))
                .isCustom(false)
                .build());
    }

    private Double toNutrientPer100g(Double perServing, Double servingSizeG) {
        if (perServing == null) {
            return null;
        }
        if (servingSizeG == null || servingSizeG <= 0) {
            return round(perServing);
        }
        return round((perServing / servingSizeG) * 100.0);
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
}
