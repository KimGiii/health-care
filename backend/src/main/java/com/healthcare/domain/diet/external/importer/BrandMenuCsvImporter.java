package com.healthcare.domain.diet.external.importer;

import com.healthcare.common.exception.ValidationException;
import com.healthcare.domain.diet.allergen.AllergenConfidenceLevel;
import com.healthcare.domain.diet.allergen.AllergenDataSource;
import com.healthcare.domain.diet.allergen.AllergenTag;
import com.healthcare.domain.diet.allergen.entity.FoodAllergenTag;
import com.healthcare.domain.diet.allergen.repository.FoodAllergenTagRepository;
import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.entity.FoodCatalogSource;
import com.healthcare.domain.diet.entity.RecommendationCautionPolicy;
import com.healthcare.domain.diet.entity.RecommendationCuration;
import com.healthcare.domain.diet.entity.RecommendationStatus;
import com.healthcare.domain.diet.identity.FoodCatalogIdentity;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class BrandMenuCsvImporter {

    private static final ZoneOffset KOREA_OFFSET = ZoneOffset.ofHours(9);
    private static final Pattern ALLERGEN_DELIMITER = Pattern.compile("[,|/]+");
    private static final Map<String, AllergenTag> KOREAN_ALLERGEN_LABELS = Map.ofEntries(
            Map.entry("난류", AllergenTag.EGG),
            Map.entry("계란", AllergenTag.EGG),
            Map.entry("달걀", AllergenTag.EGG),
            Map.entry("우유", AllergenTag.MILK),
            Map.entry("메밀", AllergenTag.BUCKWHEAT),
            Map.entry("땅콩", AllergenTag.PEANUT),
            Map.entry("대두", AllergenTag.SOY),
            Map.entry("콩", AllergenTag.SOY),
            Map.entry("밀", AllergenTag.WHEAT),
            Map.entry("고등어", AllergenTag.MACKEREL),
            Map.entry("게", AllergenTag.CRAB),
            Map.entry("새우", AllergenTag.SHRIMP),
            Map.entry("잣", AllergenTag.PINE_NUT),
            Map.entry("돼지고기", AllergenTag.PORK),
            Map.entry("복숭아", AllergenTag.PEACH),
            Map.entry("토마토", AllergenTag.TOMATO),
            Map.entry("아황산류", AllergenTag.SULFITE),
            Map.entry("호두", AllergenTag.WALNUT),
            Map.entry("닭고기", AllergenTag.CHICKEN),
            Map.entry("쇠고기", AllergenTag.BEEF),
            Map.entry("소고기", AllergenTag.BEEF),
            Map.entry("오징어", AllergenTag.SQUID),
            Map.entry("조개류", AllergenTag.SHELLFISH),
            Map.entry("굴", AllergenTag.SHELLFISH),
            Map.entry("전복", AllergenTag.SHELLFISH),
            Map.entry("홍합", AllergenTag.SHELLFISH),
            Map.entry("글루텐", AllergenTag.GLUTEN)
    );
    private static final int FOOD_CODE_MAX_LENGTH = 60;
    private static final int NAME_MAX_LENGTH = 150;
    private static final int SOURCE_DETAIL_MAX_LENGTH = 120;
    private static final int SERVING_REFERENCE_MAX_LENGTH = 80;

    private final FoodCatalogIngestService ingestService;
    private final FoodAllergenTagRepository allergenTagRepository;

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
                        .allergenTags(record.get("allergen_tags"))
                        .allergenProfileVerified(record.get("allergen_profile_verified"))
                        .build());
            }
        }
        return rows;
    }

    @Transactional
    public FoodCatalogImportResult importRows(List<BrandMenuCsvRow> rows) {
        List<PreparedBrandMenuRow> preparedRows = rows.stream()
                .map(this::prepareRow)
                .toList();
        FoodCatalogImportResult result = ingestService.ingest(
                preparedRows.stream().map(PreparedBrandMenuRow::candidate).toList(),
                FoodCatalogIngestCurationMode.REPLACE_FROM_IMPORT
        );
        preparedRows.stream()
                .filter(row -> row.candidate().accepted())
                .filter(PreparedBrandMenuRow::hasAllergenReview)
                .forEach(this::replaceBrandOfficialAllergenTags);
        return result;
    }

    private PreparedBrandMenuRow prepareRow(BrandMenuCsvRow row) {
        AllergenParseResult allergens = parseAllergenTags(row.allergenTags());
        if (allergens.rejectionReason() != null) {
            return new PreparedBrandMenuRow(
                    rejected(row, "allergen_tags", allergens.rejectionReason()),
                    List.of(),
                    false,
                    false
            );
        }
        BooleanParseResult allergenProfileVerified = parseAllergenProfileVerified(row.allergenProfileVerified());
        if (allergenProfileVerified.rejectionReason() != null) {
            return new PreparedBrandMenuRow(
                    rejected(row, "allergen_profile_verified", allergenProfileVerified.rejectionReason()),
                    List.of(),
                    false,
                    false
            );
        }
        if (allergenProfileVerified.value() && allergens.tags().isEmpty()) {
            return new PreparedBrandMenuRow(
                    rejected(row, "allergen_tags", "포함 알러젠 태그 없이 완결 프로필 검토를 표시할 수 없습니다."),
                    List.of(),
                    false,
                    false
            );
        }
        return new PreparedBrandMenuRow(
                toFoodCatalog(row),
                allergens.tags(),
                allergenProfileVerified.value(),
                hasAllergenReview(row)
        );
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
                servingReference
        );
        if (lengthFailure != null) {
            return rejected(row, lengthFailure.field(), lengthFailure.reason());
        }

        RecommendationCuration.ImportResult curationResult = parseCuration(row, nutritionBasis, servingSizeG);
        if (curationResult.rejected()) {
            return rejected(row, curationResult.rejectionField(), curationResult.rejectionReason());
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
                .recommendationReason(curation.reasonForStorage())
                .lastVerifiedAt(parseDate(row.lastVerifiedAt()))
                .isCustom(false)
                .build());
    }

    private void replaceBrandOfficialAllergenTags(PreparedBrandMenuRow preparedRow) {
        FoodCatalog imported = preparedRow.candidate().food();
        foodBySourceAndCode(imported)
                .ifPresent(food -> allergenTagRepository.replaceBySource(
                        food.getId(),
                        AllergenDataSource.BRAND_OFFICIAL,
                        preparedRow.allergenTags().stream()
                                .map(tag -> FoodAllergenTag.builder()
                                        .foodCatalogId(food.getId())
                                        .allergenTag(tag)
                                        .confidenceLevel(AllergenConfidenceLevel.LABEL_DERIVED)
                                        .source(AllergenDataSource.BRAND_OFFICIAL)
                                        .allergenProfileVerified(preparedRow.allergenProfileVerified())
                                        .reviewedAt(imported.getLastVerifiedAt())
                                        .build())
                                .toList()
                ));
    }

    private Optional<FoodCatalog> foodBySourceAndCode(FoodCatalog imported) {
        if (imported.getSource() == null || imported.getFoodCode() == null) {
            return Optional.empty();
        }
        return ingestService.findBySourceAndFoodCode(imported.getSource(), imported.getFoodCode());
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

    private RecommendationCuration.ImportResult parseCuration(
            BrandMenuCsvRow row,
            BrandMenuNutritionBasis nutritionBasis,
            Double servingSizeG) {
        RecommendationStatus status = parseRecommendationStatus(row.recommendationStatus());
        String reason = normalize(row.recommendationReason());
        RecommendationCuration.ImportResult result = RecommendationCuration.fromImport(status, reason);
        if (result.rejected() || status != RecommendationStatus.RECOMMENDABLE) {
            return result;
        }

        RecommendationCautionPolicy.Assessment cautionAssessment = RecommendationCautionPolicy.assess(
                toNutrientPerServing(parseDouble(row.sodium()), servingSizeG, nutritionBasis),
                toNutrientPerServing(parseDouble(row.sugar()), servingSizeG, nutritionBasis),
                toNutrientPerServing(parseDouble(row.saturatedFat()), servingSizeG, nutritionBasis)
        );
        if (!cautionAssessment.requiresCaution()) {
            return result;
        }
        return RecommendationCuration.ImportResult.rejected(
                "recommendation_status",
                "주의 기준 초과 항목은 RECOMMENDABLE_WITH_CAUTION으로 검수해야 합니다: "
                        + cautionAssessment.defaultReason());
    }

    private Double toNutrientPerServing(
            Double inputValue,
            Double servingSizeG,
            BrandMenuNutritionBasis nutritionBasis) {
        if (inputValue == null) {
            return null;
        }
        if (nutritionBasis == BrandMenuNutritionBasis.PER_SERVING || servingSizeG == null) {
            return inputValue;
        }
        return round((inputValue / 100.0) * servingSizeG);
    }

    private AllergenParseResult parseAllergenTags(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return new AllergenParseResult(List.of(), null);
        }

        Set<AllergenTag> tags = new LinkedHashSet<>();
        for (String token : ALLERGEN_DELIMITER.split(normalized)) {
            String label = normalize(token);
            if (label == null) {
                continue;
            }
            AllergenTag tag = parseAllergenTag(label);
            if (tag == null) {
                return new AllergenParseResult(List.of(), "알 수 없는 알러젠 태그입니다: " + label);
            }
            tags.add(tag);
        }
        return new AllergenParseResult(List.copyOf(tags), null);
    }

    private AllergenTag parseAllergenTag(String label) {
        AllergenTag koreanTag = KOREAN_ALLERGEN_LABELS.get(label);
        if (koreanTag != null) {
            return koreanTag;
        }
        try {
            return AllergenTag.valueOf(label.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private BooleanParseResult parseAllergenProfileVerified(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return new BooleanParseResult(false, null);
        }
        if ("true".equalsIgnoreCase(normalized)) {
            return new BooleanParseResult(true, null);
        }
        if ("false".equalsIgnoreCase(normalized)) {
            return new BooleanParseResult(false, null);
        }
        return new BooleanParseResult(false, "true 또는 false를 입력해야 합니다.");
    }

    private boolean hasAllergenReview(BrandMenuCsvRow row) {
        return normalize(row.allergenTags()) != null || normalize(row.allergenProfileVerified()) != null;
    }

    private ValidationFailure firstFieldEnvelopeFailure(
            String brandName,
            String menuName,
            String foodCode,
            String sourceDetail,
            String servingReference) {
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

    private record ValidationFailure(String field, String reason) {
    }

    private record AllergenParseResult(List<AllergenTag> tags, String rejectionReason) {
    }

    private record BooleanParseResult(boolean value, String rejectionReason) {
    }

    private record PreparedBrandMenuRow(
            FoodCatalogIngestCandidate candidate,
            List<AllergenTag> allergenTags,
            boolean allergenProfileVerified,
            boolean hasAllergenReview
    ) {
    }
}
