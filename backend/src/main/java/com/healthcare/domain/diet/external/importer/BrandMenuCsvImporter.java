package com.healthcare.domain.diet.external.importer;

import com.healthcare.common.exception.ValidationException;
import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.entity.FoodCatalogSource;
import com.healthcare.domain.diet.entity.RecommendationCuration;
import com.healthcare.domain.diet.entity.RecommendationStatus;
import com.healthcare.domain.diet.identity.FoodCatalogIdentity;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;

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

@Service
@RequiredArgsConstructor
public class BrandMenuCsvImporter {

    private static final ZoneOffset KOREA_OFFSET = ZoneOffset.ofHours(9);
    private static final int FOOD_CODE_MAX_LENGTH = 60;
    private static final int NAME_MAX_LENGTH = 150;
    private static final int SOURCE_DETAIL_MAX_LENGTH = 120;
    private static final int SERVING_REFERENCE_MAX_LENGTH = 80;
    private static final int RECOMMENDATION_REASON_MAX_LENGTH = 255;

    private final FoodCatalogIngestService ingestService;

    /**
     * CSV 파일을 파싱해서 BRAND_OFFICIAL 출처로 upsert한다.
     * nutrition_basis로 입력 영양값의 기준을 명시하고 내부 저장은 100g당 값으로 정규화한다.
     */
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

    public FoodCatalogImportResult importRows(List<BrandMenuCsvRow> rows) {
        List<FoodCatalogIngestCandidate> candidates = rows.stream()
                .map(this::toFoodCatalog)
                .toList();
        return ingestService.ingest(candidates, FoodCatalogIngestCurationMode.REPLACE_FROM_IMPORT);
    }

    private FoodCatalogIngestCandidate toFoodCatalog(BrandMenuCsvRow row) {
        String brandName = normalize(row.brandName());
        String menuName = normalize(row.menuName());
        BrandMenuNutritionBasis nutritionBasis = parseNutritionBasis(row.nutritionBasis());
        Double servingSizeG = parseDouble(row.servingSizeG());
        Double caloriesPerServing = parseDouble(row.calories());

        if (brandName == null) {
            return rejected(row, "brand_name", "브랜드명은 필수입니다.");
        }
        if (menuName == null) {
            return rejected(row, "menu_name", "메뉴명은 필수입니다.");
        }
        if (nutritionBasis == null) {
            return rejected(row, "nutrition_basis", "PER_SERVING 또는 PER_100G를 입력해야 합니다.");
        }
        if (caloriesPerServing == null) {
            return rejected(row, "calories", "칼로리는 필수 숫자 값입니다.");
        }

        String invalidNumberField = firstInvalidNumberField(row);
        if (invalidNumberField != null) {
            return rejected(row, invalidNumberField, "숫자 형식이어야 합니다.");
        }
        String negativeNumberField = firstNegativeNumberField(row);
        if (negativeNumberField != null) {
            return rejected(row, negativeNumberField, "0 이상이어야 합니다.");
        }

        if (nutritionBasis == BrandMenuNutritionBasis.PER_SERVING && (servingSizeG == null || servingSizeG <= 0)) {
            return rejected(row, "serving_size_g", "PER_SERVING은 0보다 큰 제공량이 필요합니다.");
        }
        if (nutritionBasis == BrandMenuNutritionBasis.PER_100G && servingSizeG != null && servingSizeG <= 0) {
            return rejected(row, "serving_size_g", "제공량을 입력할 때는 0보다 커야 합니다.");
        }

        Double caloriesPer100g = toNutrientPer100g(caloriesPerServing, servingSizeG, nutritionBasis);

        String foodCode = FoodCatalogIdentity.brandOfficialFoodCode(brandName, menuName);
        String sourceDetail = normalize(row.sourceUrl());
        String servingReference = servingSizeG != null ? formatGrams(servingSizeG) : "100g";

        ValidationFailure lengthFailure = firstFieldEnvelopeFailure(
                brandName,
                menuName,
                foodCode,
                sourceDetail,
                servingReference,
                row
        );
        if (lengthFailure != null) {
            return rejected(row, lengthFailure.field(), lengthFailure.reason());
        }

        CurationResult curationResult = parseCuration(row);
        if (curationResult.rejected()) {
            return rejected(row, curationResult.rejection().field(), curationResult.rejection().reason());
        }
        RecommendationCuration curation = curationResult.curation();

        return FoodCatalogIngestCandidate.accepted(FoodCatalog.builder()
                .foodCode(foodCode)
                .source(FoodCatalogSource.BRAND_OFFICIAL)
                .sourceDetail(sourceDetail)
                .name(menuName)
                .nameKo(menuName)
                .brandName(brandName)
                .category(parseCategory(row.category()))
                .servingSizeG(servingSizeG)
                .servingReference(servingReference)
                .caloriesPer100g(round(caloriesPer100g))
                .proteinPer100g(toNutrientPer100g(parseDouble(row.protein()), servingSizeG, nutritionBasis))
                .carbsPer100g(toNutrientPer100g(parseDouble(row.carbs()), servingSizeG, nutritionBasis))
                .fatPer100g(toNutrientPer100g(parseDouble(row.fat()), servingSizeG, nutritionBasis))
                .sodiumPer100gMg(toNutrientPer100g(parseDouble(row.sodium()), servingSizeG, nutritionBasis))
                .sugarsPer100g(toNutrientPer100g(parseDouble(row.sugar()), servingSizeG, nutritionBasis))
                .saturatedFatPer100g(toNutrientPer100g(parseDouble(row.saturatedFat()), servingSizeG, nutritionBasis))
                .recommendationStatus(curation.status())
                .recommendationReason(recommendationReason(curation))
                .lastVerifiedAt(parseDate(row.lastVerifiedAt()))
                .isCustom(false)
                .build());
    }

    private FoodCatalogIngestCandidate rejected(BrandMenuCsvRow row, String field, String reason) {
        return FoodCatalogIngestCandidate.rejected(row.rowNumber(), field, reason);
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

    private String firstNegativeNumberField(BrandMenuCsvRow row) {
        if (isNegativeNumber(row.calories())) return "calories";
        if (isNegativeNumber(row.protein())) return "protein";
        if (isNegativeNumber(row.carbs())) return "carbs";
        if (isNegativeNumber(row.fat())) return "fat";
        if (isNegativeNumber(row.sodium())) return "sodium";
        if (isNegativeNumber(row.sugar())) return "sugar";
        if (isNegativeNumber(row.saturatedFat())) return "saturated_fat";
        return null;
    }

    private boolean isNegativeNumber(String value) {
        Double parsed = parseDouble(value);
        return parsed != null && parsed < 0;
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

    private CurationResult parseCuration(BrandMenuCsvRow row) {
        RecommendationStatus status = parseRecommendationStatus(row.recommendationStatus());
        String reason = normalize(row.recommendationReason());
        if (status == RecommendationStatus.RECOMMENDABLE_WITH_CAUTION) {
            if (reason == null) {
                return CurationResult.rejected(row, "recommendation_reason", "주의 추천 상태는 사유가 필요합니다.");
            }
            if (reason.length() > RECOMMENDATION_REASON_MAX_LENGTH) {
                return CurationResult.rejected(row, "recommendation_reason", "255자 이하여야 합니다.");
            }
            return CurationResult.accepted(new RecommendationCuration.WithCaution(reason));
        }
        return CurationResult.accepted(switch (status) {
            case RECOMMENDABLE -> new RecommendationCuration.Recommendable();
            case SEARCH_ONLY -> new RecommendationCuration.SearchOnly();
            case DISABLED -> new RecommendationCuration.Disabled();
            case RECOMMENDABLE_WITH_CAUTION -> throw new IllegalStateException("handled above");
        });
    }

    private String recommendationReason(RecommendationCuration curation) {
        return curation instanceof RecommendationCuration.WithCaution c ? c.reason() : null;
    }

    private ValidationFailure firstFieldEnvelopeFailure(
            String brandName,
            String menuName,
            String foodCode,
            String sourceDetail,
            String servingReference,
            BrandMenuCsvRow row) {
        if (brandName.length() > NAME_MAX_LENGTH) {
            return new ValidationFailure("brand_name", "150자 이하여야 합니다.");
        }
        if (menuName.length() > NAME_MAX_LENGTH) {
            return new ValidationFailure("menu_name", "150자 이하여야 합니다.");
        }
        if (foodCode.length() > FOOD_CODE_MAX_LENGTH) {
            return new ValidationFailure("food_code", "브랜드명과 메뉴명으로 만든 food_code는 60자 이하여야 합니다.");
        }
        if (sourceDetail != null && sourceDetail.length() > SOURCE_DETAIL_MAX_LENGTH) {
            return new ValidationFailure("source_url", "120자 이하여야 합니다.");
        }
        if (servingReference != null && servingReference.length() > SERVING_REFERENCE_MAX_LENGTH) {
            return new ValidationFailure("serving_size_g", "제공량 표기는 80자 이하여야 합니다.");
        }
        String recommendationReason = normalize(row.recommendationReason());
        if (recommendationReason != null && recommendationReason.length() > RECOMMENDATION_REASON_MAX_LENGTH) {
            return new ValidationFailure("recommendation_reason", "255자 이하여야 합니다.");
        }
        return null;
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
            double parsed = Double.parseDouble(normalized.replace(",", ""));
            return Double.isFinite(parsed) ? parsed : null;
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

    private record CurationResult(
            RecommendationCuration curation,
            FoodCatalogImportRejectedRow rejection
    ) {
        static CurationResult accepted(RecommendationCuration curation) {
            return new CurationResult(curation, null);
        }

        static CurationResult rejected(BrandMenuCsvRow row, String field, String reason) {
            return new CurationResult(
                    null,
                    new FoodCatalogImportRejectedRow(row.rowNumber(), field, reason)
            );
        }

        boolean rejected() {
            return rejection != null;
        }
    }

    private record ValidationFailure(String field, String reason) {
    }
}
