package com.healthcare.domain.diet.admin;

import com.healthcare.common.exception.ValidationException;
import com.healthcare.domain.diet.allergen.AllergenConfidenceLevel;
import com.healthcare.domain.diet.allergen.AllergenDataSource;
import com.healthcare.domain.diet.allergen.AllergenTag;
import com.healthcare.domain.diet.allergen.entity.FoodAllergenProfile;
import com.healthcare.domain.diet.allergen.entity.FoodAllergenTag;
import com.healthcare.domain.diet.allergen.repository.FoodAllergenProfileRepository;
import com.healthcare.domain.diet.allergen.repository.FoodAllergenTagRepository;
import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.entity.FoodCatalogSource;
import com.healthcare.domain.diet.entity.FoodServingOption;
import com.healthcare.domain.diet.entity.RecommendationCautionPolicy;
import com.healthcare.domain.diet.entity.RecommendationCuration;
import com.healthcare.domain.diet.entity.RecommendationStatus;
import com.healthcare.domain.diet.external.importer.FoodCatalogImportRejectedRow;
import com.healthcare.domain.diet.external.importer.FoodCatalogImportResult;
import com.healthcare.domain.diet.repository.FoodCatalogRepository;
import com.healthcare.domain.diet.repository.FoodServingOptionRepository;
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

/**
 * 기존 카탈로그 row(source + food_code)를 추천 후보로 승격/강등하는 운영 CSV importer.
 * 새 식품 생성은 하지 않으며, 추천 자격과 검수 근거만 갱신한다.
 */
@Service
@RequiredArgsConstructor
public class RecommendationCurationCsvImporter {

    private static final ZoneOffset KOREA_OFFSET = ZoneOffset.ofHours(9);
    private static final Pattern ALLERGEN_DELIMITER = Pattern.compile("[,|/]+");
    private static final int SOURCE_DETAIL_MAX_LENGTH = 120;
    private static final String PROFILE_STANDARD_VERSION = "curation-csv-v1";

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

    private final FoodCatalogRepository foodCatalogRepository;
    private final FoodServingOptionRepository servingOptionRepository;
    private final FoodAllergenTagRepository allergenTagRepository;
    private final FoodAllergenProfileRepository allergenProfileRepository;

    @Transactional
    public FoodCatalogImportResult importFromCsv(InputStream csvStream) throws IOException {
        return importRows(parseCsv(csvStream));
    }

    List<RecommendationCurationCsvRow> parseCsv(InputStream csvStream) throws IOException {
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .build();

        List<RecommendationCurationCsvRow> rows = new ArrayList<>();
        try (CSVParser parser = new CSVParser(
                new InputStreamReader(csvStream, StandardCharsets.UTF_8), format)) {
            validateHeaders(new ArrayList<>(parser.getHeaderMap().keySet()));
            for (CSVRecord record : parser) {
                rows.add(RecommendationCurationCsvRow.builder()
                        .rowNumber(record.getRecordNumber() + 1)
                        .source(record.get("source"))
                        .foodCode(record.get("food_code"))
                        .recommendationStatus(record.get("recommendation_status"))
                        .recommendationReason(record.get("recommendation_reason"))
                        .lastVerifiedAt(record.get("last_verified_at"))
                        .reviewSourceUrl(record.get("review_source_url"))
                        .allergenTags(record.get("allergen_tags"))
                        .allergenProfileVerified(record.get("allergen_profile_verified"))
                        .allergenDataSource(optionalValue(record, "allergen_data_source"))
                        .allergenConfidenceLevel(optionalValue(record, "allergen_confidence_level"))
                        .build());
            }
        }
        return rows;
    }

    @Transactional
    public FoodCatalogImportResult importRows(List<RecommendationCurationCsvRow> rows) {
        int updated = 0;
        int skipped = 0;
        List<FoodCatalogImportRejectedRow> rejectedRows = new ArrayList<>();

        for (RecommendationCurationCsvRow row : rows) {
            RowPreparation preparation = prepare(row);
            if (preparation.rejection() != null) {
                skipped++;
                rejectedRows.add(preparation.rejection());
                continue;
            }
            apply(preparation);
            updated++;
        }

        return new FoodCatalogImportResult(0, updated, skipped, rejectedRows);
    }

    private RowPreparation prepare(RecommendationCurationCsvRow row) {
        FoodCatalogSource source = parseSource(row.source());
        if (source == null) {
            return rejected(row, "source", "알 수 없는 source입니다.");
        }
        if (source == FoodCatalogSource.USER_CUSTOM) {
            return rejected(row, "source", "사용자 커스텀 식품은 큐레이션 CSV 대상이 아닙니다.");
        }

        String foodCode = normalize(row.foodCode());
        if (foodCode == null) {
            return rejected(row, "food_code", "food_code는 필수입니다.");
        }
        Optional<FoodCatalog> foodOptional = foodCatalogRepository.findBySourceAndFoodCode(source, foodCode);
        if (foodOptional.isEmpty()) {
            return rejected(row, "food_code", "source + food_code에 해당하는 식품이 없습니다.");
        }

        FoodCatalog food = foodOptional.get();
        if (!food.isCanonicalRow()) {
            return rejected(row, "food_code", "canonical 대표 행만 큐레이션할 수 있습니다.");
        }

        RecommendationStatus status = parseRecommendationStatus(row.recommendationStatus());
        if (status == null) {
            return rejected(row, "recommendation_status", "알 수 없는 recommendation_status입니다.");
        }
        RecommendationCuration.ImportResult curationResult =
                RecommendationCuration.fromImport(status, normalize(row.recommendationReason()));
        if (curationResult.rejected()) {
            return rejected(row, curationResult.rejectionField(), curationResult.rejectionReason());
        }

        OffsetDateTime lastVerifiedAt = parseDate(row.lastVerifiedAt());
        if (normalize(row.lastVerifiedAt()) != null && lastVerifiedAt == null) {
            return rejected(row, "last_verified_at", "yyyy-MM-dd 형식이어야 합니다.");
        }

        String reviewSourceUrl = normalize(row.reviewSourceUrl());
        if (reviewSourceUrl != null && reviewSourceUrl.length() > SOURCE_DETAIL_MAX_LENGTH) {
            return rejected(row, "review_source_url", SOURCE_DETAIL_MAX_LENGTH + "자 이하여야 합니다.");
        }

        AllergenParseResult allergens = parseAllergenTags(row.allergenTags());
        if (allergens.rejectionReason() != null) {
            return rejected(row, "allergen_tags", allergens.rejectionReason());
        }
        BooleanParseResult profileVerified = parseAllergenProfileVerified(row.allergenProfileVerified());
        if (profileVerified.rejectionReason() != null) {
            return rejected(row, "allergen_profile_verified", profileVerified.rejectionReason());
        }

        List<FoodServingOption> servingOptions =
                servingOptionRepository.findByFoodCatalogIdOrderBySortOrderAsc(food.getId());
        if (status.isRecommendationCandidate()) {
            ValidationFailure failure = firstRecommendationCandidateFailure(
                    row, food, servingOptions, lastVerifiedAt, reviewSourceUrl, profileVerified.value());
            if (failure != null) {
                return rejected(row, failure.field(), failure.reason());
            }
            RecommendationCautionPolicy.Assessment cautionAssessment =
                    assessCaution(food, primaryVerifiedServingOption(servingOptions));
            if (status == RecommendationStatus.RECOMMENDABLE && cautionAssessment.requiresCaution()) {
                return rejected(
                        row,
                        "recommendation_status",
                        "주의 기준 초과 항목은 RECOMMENDABLE_WITH_CAUTION으로 검수해야 합니다: "
                                + cautionAssessment.defaultReason()
                );
            }
        }

        EvidencePolicy explicitEvidencePolicy = parseExplicitEvidencePolicy(row);
        if (explicitEvidencePolicy.rejectionReason() != null) {
            return rejected(row, explicitEvidencePolicy.rejectionField(), explicitEvidencePolicy.rejectionReason());
        }
        EvidencePolicy evidencePolicy = explicitEvidencePolicy.isPresent()
                ? explicitEvidencePolicy
                : evidencePolicyFor(source);
        if (profileVerified.value() && !evidencePolicy.confidenceLevel().isProfileGrade()) {
            return rejected(row, "allergen_profile_verified",
                    "완결 프로필은 DIRECT_VERIFIED 또는 LABEL_DERIVED 근거에서만 만들 수 있습니다.");
        }

        return new RowPreparation(
                row,
                food,
                curationResult.curation(),
                lastVerifiedAt,
                reviewSourceUrl,
                allergens.tags(),
                profileVerified.value(),
                hasAllergenReview(row),
                evidencePolicy,
                null
        );
    }

    private ValidationFailure firstRecommendationCandidateFailure(
            RecommendationCurationCsvRow row,
            FoodCatalog food,
            List<FoodServingOption> servingOptions,
            OffsetDateTime lastVerifiedAt,
            String reviewSourceUrl,
            boolean profileVerified
    ) {
        if (!isMacroComplete(food)) {
            return new ValidationFailure("recommendation_status", "추천 후보는 4대 매크로가 모두 필요합니다.");
        }
        if (primaryVerifiedServingOption(servingOptions) == null) {
            return new ValidationFailure("recommendation_status", "추천 후보는 검증된 제공량 옵션이 필요합니다.");
        }
        if (lastVerifiedAt == null) {
            return new ValidationFailure("last_verified_at", "추천 후보 검수일은 필수입니다.");
        }
        if (reviewSourceUrl == null) {
            return new ValidationFailure("review_source_url", "추천 후보 검수 출처 URL은 필수입니다.");
        }
        if (!profileVerified) {
            return new ValidationFailure("allergen_profile_verified", "추천 후보는 완결 알러젠 프로필 검토가 필요합니다.");
        }
        return null;
    }

    private void apply(RowPreparation preparation) {
        FoodCatalog food = preparation.food();
        food.updateCuration(preparation.curation());
        food.updateCurationReviewMetadata(preparation.reviewSourceUrl(), preparation.lastVerifiedAt());
        foodCatalogRepository.save(food);

        if (preparation.hasAllergenReview()) {
            allergenTagRepository.replaceBySource(
                    food.getId(),
                    preparation.evidencePolicy().source(),
                    preparation.allergenTags().stream()
                            .map(tag -> FoodAllergenTag.builder()
                                    .foodCatalogId(food.getId())
                                    .allergenTag(tag)
                                    .confidenceLevel(preparation.evidencePolicy().confidenceLevel())
                                    .source(preparation.evidencePolicy().source())
                                    .allergenProfileVerified(preparation.allergenProfileVerified())
                                    .reviewedAt(preparation.lastVerifiedAt())
                                    .build())
                            .toList()
            );
            if (preparation.allergenProfileVerified()) {
                allergenProfileRepository.save(FoodAllergenProfile.builder()
                        .foodCatalogId(food.getId())
                        .confidenceLevel(preparation.evidencePolicy().confidenceLevel())
                        .source(preparation.evidencePolicy().source())
                        .standardVersion(PROFILE_STANDARD_VERSION)
                        .sourceReference(preparation.reviewSourceUrl())
                        .reviewedAt(preparation.lastVerifiedAt())
                        .build());
            } else {
                allergenProfileRepository.findById(food.getId()).ifPresent(allergenProfileRepository::delete);
            }
        }
    }

    private RecommendationCautionPolicy.Assessment assessCaution(
            FoodCatalog food,
            FoodServingOption primaryServingOption
    ) {
        if (primaryServingOption == null) {
            return RecommendationCautionPolicy.assess(null, null, null);
        }
        double multiplier = primaryServingOption.getEquivalentG() / 100.0;
        return RecommendationCautionPolicy.assess(
                perServing(food.getSodiumPer100gMg(), multiplier),
                perServing(food.getSugarsPer100g(), multiplier),
                perServing(food.getSaturatedFatPer100g(), multiplier)
        );
    }

    private Double perServing(Double per100g, double multiplier) {
        return per100g == null ? null : per100g * multiplier;
    }

    private FoodServingOption primaryVerifiedServingOption(List<FoodServingOption> servingOptions) {
        return servingOptions.stream()
                .filter(FoodServingOption::isVerified)
                .findFirst()
                .orElse(null);
    }

    private EvidencePolicy evidencePolicyFor(FoodCatalogSource source) {
        return switch (source) {
            case SEED -> new EvidencePolicy(AllergenDataSource.MFDS_CLASS, AllergenConfidenceLevel.DIRECT_VERIFIED);
            case BRAND_OFFICIAL -> new EvidencePolicy(AllergenDataSource.BRAND_OFFICIAL, AllergenConfidenceLevel.LABEL_DERIVED);
            case MFDS_STANDARD_PROCESSED, MFDS_FOOD_NUTRIENT_DB ->
                    new EvidencePolicy(AllergenDataSource.FOODQR, AllergenConfidenceLevel.LABEL_DERIVED);
            case MFDS_STANDARD_DISH ->
                    new EvidencePolicy(AllergenDataSource.KHANES_RECIPE, AllergenConfidenceLevel.RECIPE_DERIVED);
            case USER_CUSTOM -> new EvidencePolicy(AllergenDataSource.USER_CUSTOM, AllergenConfidenceLevel.UNKNOWN);
        };
    }

    private EvidencePolicy parseExplicitEvidencePolicy(RecommendationCurationCsvRow row) {
        String sourceValue = normalize(row.allergenDataSource());
        String confidenceValue = normalize(row.allergenConfidenceLevel());
        if (sourceValue == null && confidenceValue == null) {
            return EvidencePolicy.notPresent();
        }
        if (sourceValue == null || confidenceValue == null) {
            return EvidencePolicy.rejected(
                    "allergen_data_source",
                    "allergen_data_source와 allergen_confidence_level은 함께 입력해야 합니다.");
        }

        AllergenDataSource dataSource = parseAllergenDataSource(sourceValue);
        if (dataSource == null) {
            return EvidencePolicy.rejected("allergen_data_source", "알 수 없는 allergen_data_source입니다.");
        }
        AllergenConfidenceLevel confidenceLevel = parseAllergenConfidenceLevel(confidenceValue);
        if (confidenceLevel == null) {
            return EvidencePolicy.rejected("allergen_confidence_level", "알 수 없는 allergen_confidence_level입니다.");
        }
        return new EvidencePolicy(dataSource, confidenceLevel);
    }

    private AllergenDataSource parseAllergenDataSource(String value) {
        try {
            return AllergenDataSource.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private AllergenConfidenceLevel parseAllergenConfidenceLevel(String value) {
        try {
            return AllergenConfidenceLevel.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private boolean isMacroComplete(FoodCatalog food) {
        return food.getCaloriesPer100g() != null
                && food.getProteinPer100g() != null
                && food.getCarbsPer100g() != null
                && food.getFatPer100g() != null;
    }

    private FoodCatalogSource parseSource(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        try {
            return FoodCatalogSource.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private RecommendationStatus parseRecommendationStatus(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        try {
            return RecommendationStatus.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
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

    private boolean hasAllergenReview(RecommendationCurationCsvRow row) {
        return normalize(row.allergenTags()) != null || normalize(row.allergenProfileVerified()) != null;
    }

    private RowPreparation rejected(RecommendationCurationCsvRow row, String field, String reason) {
        return new RowPreparation(row, null, null, null, null, List.of(), false, false, null,
                new FoodCatalogImportRejectedRow(row.rowNumber(), field, reason));
    }

    private void validateHeaders(List<String> actualHeaders) {
        List<String> expectedHeaders = Arrays.asList(RecommendationCurationCsvRow.HEADERS);
        List<String> expectedHeadersWithEvidence = new ArrayList<>(expectedHeaders);
        expectedHeadersWithEvidence.addAll(Arrays.asList(RecommendationCurationCsvRow.OPTIONAL_HEADERS));
        if (!actualHeaders.equals(expectedHeaders) && !actualHeaders.equals(expectedHeadersWithEvidence)) {
            throw new ValidationException("추천 큐레이션 CSV 헤더가 템플릿과 일치해야 합니다.");
        }
    }

    private String optionalValue(CSVRecord record, String header) {
        return record.isMapped(header) ? record.get(header) : null;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim().replaceAll("\\s+", " ");
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record AllergenParseResult(List<AllergenTag> tags, String rejectionReason) {
    }

    private record BooleanParseResult(boolean value, String rejectionReason) {
    }

    private record ValidationFailure(String field, String reason) {
    }

    private record EvidencePolicy(
            AllergenDataSource source,
            AllergenConfidenceLevel confidenceLevel,
            String rejectionField,
            String rejectionReason
    ) {
        EvidencePolicy(AllergenDataSource source, AllergenConfidenceLevel confidenceLevel) {
            this(source, confidenceLevel, null, null);
        }

        static EvidencePolicy notPresent() {
            return new EvidencePolicy(null, null, null, null);
        }

        static EvidencePolicy rejected(String field, String reason) {
            return new EvidencePolicy(null, null, field, reason);
        }

        boolean isPresent() {
            return source != null && confidenceLevel != null;
        }
    }

    private record RowPreparation(
            RecommendationCurationCsvRow row,
            FoodCatalog food,
            RecommendationCuration curation,
            OffsetDateTime lastVerifiedAt,
            String reviewSourceUrl,
            List<AllergenTag> allergenTags,
            boolean allergenProfileVerified,
            boolean hasAllergenReview,
            EvidencePolicy evidencePolicy,
            FoodCatalogImportRejectedRow rejection
    ) {
    }
}
