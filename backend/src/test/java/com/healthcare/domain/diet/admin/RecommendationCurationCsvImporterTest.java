package com.healthcare.domain.diet.admin;

import com.healthcare.domain.diet.allergen.AllergenConfidenceLevel;
import com.healthcare.domain.diet.allergen.AllergenDataSource;
import com.healthcare.domain.diet.allergen.AllergenTag;
import com.healthcare.domain.diet.allergen.entity.FoodAllergenProfile;
import com.healthcare.domain.diet.allergen.entity.FoodAllergenTag;
import com.healthcare.domain.diet.allergen.repository.FoodAllergenProfileRepository;
import com.healthcare.domain.diet.allergen.repository.FoodAllergenTagRepository;
import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.entity.FoodCatalogSource;
import com.healthcare.domain.diet.entity.FoodServingOption;
import com.healthcare.domain.diet.entity.FoodServingOption.ServingType;
import com.healthcare.domain.diet.entity.RecommendationStatus;
import com.healthcare.domain.diet.external.importer.FoodCatalogImportRejectedRow;
import com.healthcare.domain.diet.external.importer.FoodCatalogImportResult;
import com.healthcare.domain.diet.repository.FoodCatalogRepository;
import com.healthcare.domain.diet.repository.FoodServingOptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RecommendationCurationCsvImporter")
class RecommendationCurationCsvImporterTest {

    @Mock private FoodCatalogRepository foodCatalogRepository;
    @Mock private FoodServingOptionRepository servingOptionRepository;
    @Mock private FoodAllergenTagRepository allergenTagRepository;
    @Mock private FoodAllergenProfileRepository allergenProfileRepository;

    private RecommendationCurationCsvImporter importer;

    @BeforeEach
    void setUp() {
        importer = new RecommendationCurationCsvImporter(
                foodCatalogRepository,
                servingOptionRepository,
                allergenTagRepository,
                allergenProfileRepository);
    }

    @Test
    @DisplayName("알러젠 근거 확장 헤더를 포함한 CSV를 파싱한다")
    void parseCsv_acceptsOptionalEvidenceHeaders() throws Exception {
        String csv = """
                source,food_code,recommendation_status,recommendation_reason,last_verified_at,review_source_url,allergen_tags,allergen_profile_verified,allergen_data_source,allergen_confidence_level
                MFDS_FOOD_NUTRIENT_DB,D202-001,RECOMMENDABLE,,2026-06-20,https://brand.example/allergy,우유,true,BRAND_OFFICIAL,LABEL_DERIVED
                """;

        List<RecommendationCurationCsvRow> rows = importer.parseCsv(
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.allergenDataSource()).isEqualTo("BRAND_OFFICIAL");
            assertThat(row.allergenConfidenceLevel()).isEqualTo("LABEL_DERIVED");
        });
    }

    @Test
    @DisplayName("macro·제공량·알러젠 프로필 조건을 통과하면 기존 row를 RECOMMENDABLE로 승격한다")
    void importRows_promotesExistingRowWhenVerifiedPackageIsComplete() {
        FoodCatalog food = lowCautionFood(10L, FoodCatalogSource.BRAND_OFFICIAL, RecommendationStatus.SEARCH_ONLY);
        given(foodCatalogRepository.findBySourceAndFoodCode(FoodCatalogSource.BRAND_OFFICIAL, "brand:food"))
                .willReturn(Optional.of(food));
        given(servingOptionRepository.findByFoodCatalogIdOrderBySortOrderAsc(10L))
                .willReturn(List.of(servingOption(10L, 100.0)));

        FoodCatalogImportResult result = importer.importRows(List.of(row(
                "BRAND_OFFICIAL",
                "brand:food",
                "RECOMMENDABLE",
                "",
                "2026-06-20",
                "https://example.com/source",
                "",
                "true"
        )));

        assertThat(result.updatedCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isZero();
        assertThat(food.getRecommendationStatus()).isEqualTo(RecommendationStatus.RECOMMENDABLE);
        assertThat(food.getSourceDetail()).isEqualTo("https://example.com/source");
        assertThat(food.getLastVerifiedAt()).isEqualTo(OffsetDateTime.parse("2026-06-20T00:00:00+09:00"));
        verify(allergenTagRepository).replaceBySource(10L, AllergenDataSource.BRAND_OFFICIAL, List.of());
        verify(allergenProfileRepository).save(argThat(profile ->
                profile.getFoodCatalogId().equals(10L)
                        && profile.getConfidenceLevel() == AllergenConfidenceLevel.LABEL_DERIVED
                        && profile.getSource() == AllergenDataSource.BRAND_OFFICIAL
                        && profile.getSourceReference().equals("https://example.com/source")
        ));
    }

    @Test
    @DisplayName("알 수 없는 알러젠 태그가 있으면 row를 거절한다")
    void importRows_rejectsUnknownAllergenTag() {
        FoodCatalog food = food(10L, FoodCatalogSource.BRAND_OFFICIAL, RecommendationStatus.SEARCH_ONLY, true);
        given(foodCatalogRepository.findBySourceAndFoodCode(FoodCatalogSource.BRAND_OFFICIAL, "brand:food"))
                .willReturn(Optional.of(food));

        FoodCatalogImportResult result = importer.importRows(List.of(row(
                "BRAND_OFFICIAL",
                "brand:food",
                "SEARCH_ONLY",
                "",
                "",
                "",
                "없는태그",
                "false"
        )));

        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(result.rejectedRows()).extracting(FoodCatalogImportRejectedRow::field)
                .containsExactly("allergen_tags");
        verify(foodCatalogRepository, never()).save(any());
    }

    @Test
    @DisplayName("추천 후보 승격 시 macro가 불완전하면 row를 거절한다")
    void importRows_rejectsRecommendableWhenMacroIncomplete() {
        FoodCatalog food = food(10L, FoodCatalogSource.BRAND_OFFICIAL, RecommendationStatus.SEARCH_ONLY, false);
        given(foodCatalogRepository.findBySourceAndFoodCode(FoodCatalogSource.BRAND_OFFICIAL, "brand:food"))
                .willReturn(Optional.of(food));
        given(servingOptionRepository.findByFoodCatalogIdOrderBySortOrderAsc(10L))
                .willReturn(List.of(servingOption(10L, 100.0)));

        FoodCatalogImportResult result = importer.importRows(List.of(row(
                "BRAND_OFFICIAL",
                "brand:food",
                "RECOMMENDABLE",
                "",
                "2026-06-20",
                "https://example.com/source",
                "우유",
                "true"
        )));

        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(result.rejectedRows()).extracting(FoodCatalogImportRejectedRow::reason)
                .containsExactly("추천 후보는 4대 매크로가 모두 필요합니다.");
        verify(foodCatalogRepository, never()).save(any());
    }

    @Test
    @DisplayName("주의 기준 초과 식품을 RECOMMENDABLE로 승격하면 row를 거절한다")
    void importRows_rejectsRecommendableWhenCautionThresholdExceeded() {
        FoodCatalog food = food(10L, FoodCatalogSource.BRAND_OFFICIAL, RecommendationStatus.SEARCH_ONLY, true);
        given(foodCatalogRepository.findBySourceAndFoodCode(FoodCatalogSource.BRAND_OFFICIAL, "brand:food"))
                .willReturn(Optional.of(food));
        given(servingOptionRepository.findByFoodCatalogIdOrderBySortOrderAsc(10L))
                .willReturn(List.of(servingOption(10L, 100.0)));

        FoodCatalogImportResult result = importer.importRows(List.of(row(
                "BRAND_OFFICIAL",
                "brand:food",
                "RECOMMENDABLE",
                "",
                "2026-06-20",
                "https://example.com/source",
                "우유",
                "true"
        )));

        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(result.rejectedRows()).containsExactly(
                new FoodCatalogImportRejectedRow(
                        1,
                        "recommendation_status",
                        "주의 기준 초과 항목은 RECOMMENDABLE_WITH_CAUTION으로 검수해야 합니다: 나트륨/포화지방 주의")
        );
        verify(foodCatalogRepository, never()).save(any());
    }

    @Test
    @DisplayName("non-canonical row는 큐레이션 CSV에서 거절한다")
    void importRows_rejectsNonCanonicalRow() {
        FoodCatalog food = food(10L, FoodCatalogSource.BRAND_OFFICIAL, RecommendationStatus.SEARCH_ONLY, true);
        food.supersedeBy(FoodCatalog.builder().id(99L).build());
        given(foodCatalogRepository.findBySourceAndFoodCode(FoodCatalogSource.BRAND_OFFICIAL, "brand:food"))
                .willReturn(Optional.of(food));

        FoodCatalogImportResult result = importer.importRows(List.of(row(
                "BRAND_OFFICIAL",
                "brand:food",
                "SEARCH_ONLY",
                "",
                "",
                "",
                "",
                "false"
        )));

        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(result.rejectedRows()).extracting(FoodCatalogImportRejectedRow::field)
                .containsExactly("food_code");
        verify(foodCatalogRepository, never()).save(any());
    }

    @Test
    @DisplayName("알러젠 태그가 있으면 source별 근거로 교체한다")
    void importRows_replacesAllergenTagsByEvidenceSource() {
        FoodCatalog food = food(10L, FoodCatalogSource.SEED, RecommendationStatus.SEARCH_ONLY, true);
        given(foodCatalogRepository.findBySourceAndFoodCode(FoodCatalogSource.SEED, "seed:egg"))
                .willReturn(Optional.of(food));
        given(servingOptionRepository.findByFoodCatalogIdOrderBySortOrderAsc(10L))
                .willReturn(List.of(servingOption(10L, 50.0)));

        importer.importRows(List.of(row(
                "SEED",
                "seed:egg",
                "RECOMMENDABLE",
                "",
                "2026-06-20",
                "https://example.com/source",
                "난류",
                "true"
        )));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FoodAllergenTag>> captor = ArgumentCaptor.forClass(List.class);
        verify(allergenTagRepository).replaceBySource(
                eq(10L),
                eq(AllergenDataSource.MFDS_CLASS),
                captor.capture());
        assertThat(captor.getValue()).singleElement().satisfies(tag -> {
            assertThat(tag.getAllergenTag()).isEqualTo(AllergenTag.EGG);
            assertThat(tag.getConfidenceLevel()).isEqualTo(AllergenConfidenceLevel.DIRECT_VERIFIED);
            assertThat(tag.isAllergenProfileVerified()).isTrue();
        });
    }

    @Test
    @DisplayName("알러젠 근거 source를 명시하면 food source와 독립적으로 저장한다")
    void importRows_usesExplicitAllergenEvidencePolicy() {
        FoodCatalog food = lowCautionFood(10L, FoodCatalogSource.MFDS_FOOD_NUTRIENT_DB, RecommendationStatus.SEARCH_ONLY);
        given(foodCatalogRepository.findBySourceAndFoodCode(FoodCatalogSource.MFDS_FOOD_NUTRIENT_DB, "brand:food"))
                .willReturn(Optional.of(food));
        given(servingOptionRepository.findByFoodCatalogIdOrderBySortOrderAsc(10L))
                .willReturn(List.of(servingOption(10L, 100.0)));

        FoodCatalogImportResult result = importer.importRows(List.of(row(
                "MFDS_FOOD_NUTRIENT_DB",
                "brand:food",
                "RECOMMENDABLE",
                "",
                "2026-06-20",
                "https://brand.example/allergy",
                "우유",
                "true",
                "BRAND_OFFICIAL",
                "LABEL_DERIVED"
        )));

        assertThat(result.updatedCount()).isEqualTo(1);
        verify(allergenTagRepository).replaceBySource(
                eq(10L),
                eq(AllergenDataSource.BRAND_OFFICIAL),
                argThat(tags -> tags.size() == 1
                        && tags.get(0).getAllergenTag() == AllergenTag.MILK
                        && tags.get(0).getConfidenceLevel() == AllergenConfidenceLevel.LABEL_DERIVED
                        && tags.get(0).getSource() == AllergenDataSource.BRAND_OFFICIAL)
        );
        verify(allergenProfileRepository).save(argThat(profile ->
                profile.getFoodCatalogId().equals(10L)
                        && profile.getSource() == AllergenDataSource.BRAND_OFFICIAL
                        && profile.getConfidenceLevel() == AllergenConfidenceLevel.LABEL_DERIVED
        ));
    }

    private RecommendationCurationCsvRow row(
            String source,
            String foodCode,
            String status,
            String reason,
            String lastVerifiedAt,
            String reviewSourceUrl,
            String allergenTags,
            String allergenProfileVerified
    ) {
        return RecommendationCurationCsvRow.builder()
                .rowNumber(1)
                .source(source)
                .foodCode(foodCode)
                .recommendationStatus(status)
                .recommendationReason(reason)
                .lastVerifiedAt(lastVerifiedAt)
                .reviewSourceUrl(reviewSourceUrl)
                .allergenTags(allergenTags)
                .allergenProfileVerified(allergenProfileVerified)
                .build();
    }

    private RecommendationCurationCsvRow row(
            String source,
            String foodCode,
            String status,
            String reason,
            String lastVerifiedAt,
            String reviewSourceUrl,
            String allergenTags,
            String allergenProfileVerified,
            String allergenDataSource,
            String allergenConfidenceLevel
    ) {
        return RecommendationCurationCsvRow.builder()
                .rowNumber(1)
                .source(source)
                .foodCode(foodCode)
                .recommendationStatus(status)
                .recommendationReason(reason)
                .lastVerifiedAt(lastVerifiedAt)
                .reviewSourceUrl(reviewSourceUrl)
                .allergenTags(allergenTags)
                .allergenProfileVerified(allergenProfileVerified)
                .allergenDataSource(allergenDataSource)
                .allergenConfidenceLevel(allergenConfidenceLevel)
                .build();
    }

    private FoodCatalog food(
            Long id,
            FoodCatalogSource source,
            RecommendationStatus status,
            boolean macroComplete
    ) {
        FoodCatalog.FoodCatalogBuilder builder = FoodCatalog.builder()
                .id(id)
                .source(source)
                .foodCode(source == FoodCatalogSource.SEED ? "seed:egg" : "brand:food")
                .name("테스트 식품")
                .nameKo("테스트 식품")
                .category(FoodCategory.PROTEIN_SOURCE)
                .caloriesPer100g(200.0)
                .sodiumPer100gMg(600.0)
                .saturatedFatPer100g(5.0)
                .sugarsPer100g(1.0)
                .recommendationStatus(status)
                .isCustom(false)
                .usageCount(0L);
        if (macroComplete) {
            builder.proteinPer100g(20.0).carbsPer100g(10.0).fatPer100g(8.0);
        }
        return builder.build();
    }

    private FoodCatalog lowCautionFood(Long id, FoodCatalogSource source, RecommendationStatus status) {
        return FoodCatalog.builder()
                .id(id)
                .source(source)
                .foodCode("brand:food")
                .name("테스트 식품")
                .nameKo("테스트 식품")
                .category(FoodCategory.PROTEIN_SOURCE)
                .caloriesPer100g(200.0)
                .proteinPer100g(20.0)
                .carbsPer100g(10.0)
                .fatPer100g(8.0)
                .sodiumPer100gMg(100.0)
                .saturatedFatPer100g(1.0)
                .sugarsPer100g(1.0)
                .recommendationStatus(status)
                .isCustom(false)
                .usageCount(0L)
                .build();
    }

    private FoodServingOption servingOption(Long foodId, double equivalentG) {
        return FoodServingOption.builder()
                .foodCatalogId(foodId)
                .label("1회 제공량")
                .labelKo("1회 제공량")
                .equivalentG(equivalentG)
                .sortOrder(0)
                .servingType(ServingType.OFFICIAL_SERVING)
                .verifiedAt(OffsetDateTime.now().minusDays(1))
                .build();
    }
}
