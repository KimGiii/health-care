package com.healthcare.domain.diet.external.importer;

import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.entity.FoodCatalogSource;
import com.healthcare.domain.diet.entity.RecommendationStatus;
import com.healthcare.domain.diet.repository.FoodCatalogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("BrandMenuCsvImporter")
class BrandMenuCsvImporterTest {

    private FoodCatalogRepository repository;
    private BrandMenuCsvImporter importer;

    @BeforeEach
    void setUp() {
        repository = mock(FoodCatalogRepository.class);
        importer = new BrandMenuCsvImporter(new FoodCatalogIngestService(repository));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private InputStream csvStream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    // -----------------------------------------------------------------------
    // CSV 파싱
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("헤더 포함 CSV를 파싱하면 행 수가 일치한다")
    void parseCsv_returnsCorrectRowCount() throws IOException {
        String csv = """
                brand_name,menu_name,category,nutrition_basis,serving_size_g,calories,protein,carbs,fat,sodium,sugar,saturated_fat,source_url,last_verified_at,recommendation_status,recommendation_reason
                서브웨이,로스트치킨 샌드위치,PROTEIN_SOURCE,PER_SERVING,232,320,24,42,5,720,6,1.5,https://subway.com,2026-01-01,RECOMMENDABLE,
                샐러디,닭가슴살 샐러드,PROTEIN_SOURCE,PER_SERVING,300,250,28,15,8,480,5,2,https://saladii.com,2026-01-01,RECOMMENDABLE,
                """;

        List<BrandMenuCsvRow> rows = importer.parseCsv(csvStream(csv));

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).brandName()).isEqualTo("서브웨이");
        assertThat(rows.get(0).menuName()).isEqualTo("로스트치킨 샌드위치");
    }

    @Test
    @DisplayName("빈 행은 파싱 결과에 포함되지 않는다")
    void parseCsv_skipsEmptyLines() throws IOException {
        String csv = """
                brand_name,menu_name,category,nutrition_basis,serving_size_g,calories,protein,carbs,fat,sodium,sugar,saturated_fat,source_url,last_verified_at,recommendation_status,recommendation_reason
                서브웨이,로스트치킨 샌드위치,PROTEIN_SOURCE,PER_SERVING,232,320,24,42,5,720,6,1.5,https://subway.com,2026-01-01,RECOMMENDABLE,

                """;

        List<BrandMenuCsvRow> rows = importer.parseCsv(csvStream(csv));

        assertThat(rows).hasSize(1);
    }

    // -----------------------------------------------------------------------
    // 영양소 변환 (1회 제공량 → 100g당)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("serving_size_g가 있으면 영양소 값을 100g당으로 변환한다")
    void importRows_convertsNutrientsPer100g() {
        BrandMenuCsvRow row = BrandMenuCsvRow.builder()
                .brandName("서브웨이")
                .menuName("로스트치킨 샌드위치")
                .category("PROTEIN_SOURCE")
                .nutritionBasis("PER_SERVING")
                .servingSizeG("200")
                .calories("320")       // 320 / 200 * 100 = 160
                .protein("24")         // 24 / 200 * 100 = 12
                .carbs("40")
                .fat("8")
                .sodium("720")
                .sugar("6")
                .saturatedFat("2")
                .sourceUrl("https://subway.com")
                .lastVerifiedAt("2026-01-01")
                .recommendationStatus("RECOMMENDABLE")
                .recommendationReason("")
                .build();
        when(repository.findBySourceAndFoodCode(eq(FoodCatalogSource.BRAND_OFFICIAL), any()))
                .thenReturn(Optional.empty());

        importer.importRows(List.of(row));

        verify(repository).save(argThat(fc ->
                fc.getCaloriesPer100g() == 160.0 && fc.getProteinPer100g() == 12.0
        ));
    }

    @Test
    @DisplayName("PER_100G는 칼로리 값을 100g당으로 그대로 사용한다")
    void importRows_usesCaloriesDirectlyWhenNutritionBasisIsPer100g() {
        BrandMenuCsvRow row = BrandMenuCsvRow.builder()
                .brandName("테스트브랜드")
                .menuName("테스트메뉴")
                .category("PROCESSED")
                .nutritionBasis("PER_100G")
                .servingSizeG("")
                .calories("300")
                .protein("10")
                .carbs("50")
                .fat("8")
                .sodium("500")
                .sugar("5")
                .saturatedFat("2")
                .sourceUrl("")
                .lastVerifiedAt("")
                .recommendationStatus("SEARCH_ONLY")
                .recommendationReason("")
                .build();
        when(repository.findBySourceAndFoodCode(eq(FoodCatalogSource.BRAND_OFFICIAL), any()))
                .thenReturn(Optional.empty());

        importer.importRows(List.of(row));

        verify(repository).save(argThat(fc -> fc.getCaloriesPer100g() == 300.0));
    }

    @Test
    @DisplayName("PER_100G에 serving_size_g가 있으면 기본 제공량으로 보존하고 영양값은 100g당으로 유지한다")
    void importRows_per100gWithServingSizeKeepsServingSizeAndPer100gNutrition() {
        BrandMenuCsvRow row = BrandMenuCsvRow.builder()
                .brandName("BBQ")
                .menuName("황금올리브치킨")
                .category("PROTEIN_SOURCE")
                .nutritionBasis("PER_100G")
                .servingSizeG("500")
                .calories("253")
                .protein("18")
                .carbs("10")
                .fat("16")
                .sodium("450")
                .sugar("1")
                .saturatedFat("3")
                .sourceUrl("")
                .lastVerifiedAt("")
                .recommendationStatus("SEARCH_ONLY")
                .recommendationReason("")
                .build();
        when(repository.findBySourceAndFoodCode(eq(FoodCatalogSource.BRAND_OFFICIAL), any()))
                .thenReturn(Optional.empty());

        importer.importRows(List.of(row));

        verify(repository).save(argThat(fc ->
                fc.getServingSizeG() == 500.0
                        && fc.getCaloriesPer100g() == 253.0
                        && fc.getProteinPer100g() == 18.0
        ));
    }

    // -----------------------------------------------------------------------
    // upsert 동작
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("동일 food_code가 없으면 신규 생성한다")
    void importRows_createsNewWhenNotExists() {
        BrandMenuCsvRow row = BrandMenuCsvRow.builder()
                .brandName("서브웨이").menuName("베지").category("VEGETABLE")
                .nutritionBasis("PER_SERVING")
                .servingSizeG("200").calories("280").protein("12").carbs("38")
                .fat("6").sodium("600").sugar("4").saturatedFat("1")
                .sourceUrl("").lastVerifiedAt("").recommendationStatus("RECOMMENDABLE")
                .recommendationReason("").build();
        when(repository.findBySourceAndFoodCode(eq(FoodCatalogSource.BRAND_OFFICIAL), any()))
                .thenReturn(Optional.empty());

        FoodCatalogImportResult result = importer.importRows(List.of(row));

        assertThat(result.createdCount()).isEqualTo(1);
        assertThat(result.updatedCount()).isEqualTo(0);
        assertThat(result.skippedCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("동일 food_code가 있으면 기존 항목을 갱신한다")
    void importRows_updatesExistingEntry() {
        FoodCatalog existing = FoodCatalog.builder()
                .foodCode("서브웨이:베지")
                .source(FoodCatalogSource.BRAND_OFFICIAL)
                .name("베지")
                .nameKo("베지")
                .category(FoodCategory.VEGETABLE)
                .caloriesPer100g(100.0)
                .recommendationStatus(RecommendationStatus.SEARCH_ONLY)
                .isCustom(false)
                .build();
        BrandMenuCsvRow row = BrandMenuCsvRow.builder()
                .brandName("서브웨이").menuName("베지").category("VEGETABLE")
                .nutritionBasis("PER_SERVING")
                .servingSizeG("200").calories("280").protein("12").carbs("38")
                .fat("6").sodium("600").sugar("4").saturatedFat("1")
                .sourceUrl("").lastVerifiedAt("2026-06-01")
                .recommendationStatus("RECOMMENDABLE_WITH_CAUTION")
                .recommendationReason("고나트륨").build();
        when(repository.findBySourceAndFoodCode(eq(FoodCatalogSource.BRAND_OFFICIAL), any()))
                .thenReturn(Optional.of(existing));

        FoodCatalogImportResult result = importer.importRows(List.of(row));

        assertThat(result.updatedCount()).isEqualTo(1);
        assertThat(result.createdCount()).isEqualTo(0);
        verify(repository).save(existing);
        assertThat(existing.getCaloriesPer100g()).isEqualTo(140.0);
        assertThat(existing.getRecommendationStatus()).isEqualTo(RecommendationStatus.RECOMMENDABLE_WITH_CAUTION);
        assertThat(existing.getRecommendationReason()).isEqualTo("고나트륨");
    }

    // -----------------------------------------------------------------------
    // skip 조건
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("brand_name 또는 menu_name이 비어있으면 skip한다")
    void importRows_skipsRowWithoutRequiredFields() {
        BrandMenuCsvRow missingBrand = BrandMenuCsvRow.builder()
                .brandName("").menuName("베지").category("VEGETABLE")
                .nutritionBasis("PER_SERVING")
                .servingSizeG("200").calories("280").protein("").carbs("")
                .fat("").sodium("").sugar("").saturatedFat("")
                .sourceUrl("").lastVerifiedAt("").recommendationStatus("")
                .recommendationReason("").build();
        BrandMenuCsvRow missingCalories = BrandMenuCsvRow.builder()
                .brandName("서브웨이").menuName("베지").category("VEGETABLE")
                .nutritionBasis("PER_SERVING")
                .servingSizeG("200").calories("").protein("").carbs("")
                .fat("").sodium("").sugar("").saturatedFat("")
                .sourceUrl("").lastVerifiedAt("").recommendationStatus("")
                .recommendationReason("").build();

        FoodCatalogImportResult result = importer.importRows(List.of(missingBrand, missingCalories));

        assertThat(result.skippedCount()).isEqualTo(2);
        assertThat(result.rejectedRows()).extracting(FoodCatalogImportRejectedRow::field)
                .containsExactly("brand_name", "calories");
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("nutrition_basis가 없으면 row를 거절하고 사유를 반환한다")
    void importRows_rejectsRowWithoutNutritionBasis() {
        BrandMenuCsvRow row = BrandMenuCsvRow.builder()
                .brandName("서브웨이").menuName("베지").category("VEGETABLE")
                .servingSizeG("200").calories("280").protein("").carbs("")
                .fat("").sodium("").sugar("").saturatedFat("")
                .sourceUrl("").lastVerifiedAt("").recommendationStatus("")
                .recommendationReason("").build();

        FoodCatalogImportResult result = importer.importRows(List.of(row));

        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(result.rejectedRows()).hasSize(1);
        assertThat(result.rejectedRows().get(0).field()).isEqualTo("nutrition_basis");
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("PER_SERVING인데 serving_size_g가 없으면 row를 거절한다")
    void importRows_rejectsPerServingWithoutServingSize() {
        BrandMenuCsvRow row = BrandMenuCsvRow.builder()
                .brandName("서브웨이").menuName("베지").category("VEGETABLE")
                .nutritionBasis("PER_SERVING")
                .servingSizeG("").calories("280").protein("").carbs("")
                .fat("").sodium("").sugar("").saturatedFat("")
                .sourceUrl("").lastVerifiedAt("").recommendationStatus("")
                .recommendationReason("").build();

        FoodCatalogImportResult result = importer.importRows(List.of(row));

        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(result.rejectedRows().get(0).field()).isEqualTo("serving_size_g");
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("RECOMMENDABLE_WITH_CAUTION인데 사유가 없으면 row를 거절한다")
    void importRows_rejectsCautionStatusWithoutReason() {
        BrandMenuCsvRow row = BrandMenuCsvRow.builder()
                .brandName("버거킹").menuName("와퍼").category("PROCESSED")
                .nutritionBasis("PER_SERVING")
                .servingSizeG("290").calories("660").protein("28").carbs("49")
                .fat("40").sodium("980").sugar("11").saturatedFat("12")
                .sourceUrl("").lastVerifiedAt("").recommendationStatus("RECOMMENDABLE_WITH_CAUTION")
                .recommendationReason("").build();

        FoodCatalogImportResult result = importer.importRows(List.of(row));

        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(result.rejectedRows()).extracting(FoodCatalogImportRejectedRow::field)
                .containsExactly("recommendation_reason");
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("DB 저장 길이를 넘는 source_url은 row를 거절한다")
    void importRows_rejectsSourceUrlLongerThanDbEnvelope() {
        BrandMenuCsvRow row = BrandMenuCsvRow.builder()
                .brandName("서브웨이").menuName("베지").category("VEGETABLE")
                .nutritionBasis("PER_SERVING")
                .servingSizeG("200").calories("280").protein("12").carbs("38")
                .fat("6").sodium("600").sugar("4").saturatedFat("1")
                .sourceUrl("https://example.com/" + "a".repeat(121))
                .lastVerifiedAt("").recommendationStatus("RECOMMENDABLE")
                .recommendationReason("").build();

        FoodCatalogImportResult result = importer.importRows(List.of(row));

        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(result.rejectedRows().get(0).field()).isEqualTo("source_url");
        verify(repository, never()).save(any());
    }

    // -----------------------------------------------------------------------
    // food_code 생성
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("food_code는 브랜드명:메뉴명 소문자 형식으로 생성된다")
    void importRows_generatesFoodCode() {
        BrandMenuCsvRow row = BrandMenuCsvRow.builder()
                .brandName("서브웨이").menuName("로스트치킨 샌드위치").category("PROTEIN_SOURCE")
                .nutritionBasis("PER_SERVING")
                .servingSizeG("232").calories("320").protein("24").carbs("42")
                .fat("5").sodium("720").sugar("6").saturatedFat("1.5")
                .sourceUrl("").lastVerifiedAt("").recommendationStatus("RECOMMENDABLE")
                .recommendationReason("").build();
        when(repository.findBySourceAndFoodCode(eq(FoodCatalogSource.BRAND_OFFICIAL), any()))
                .thenReturn(Optional.empty());

        importer.importRows(List.of(row));

        verify(repository).findBySourceAndFoodCode(
                eq(FoodCatalogSource.BRAND_OFFICIAL),
                eq("서브웨이:로스트치킨_샌드위치")
        );
    }

    // -----------------------------------------------------------------------
    // 추천 상태 파싱
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("알 수 없는 recommendation_status는 SEARCH_ONLY로 폴백한다")
    void importRows_fallsBackToSearchOnlyForUnknownStatus() {
        BrandMenuCsvRow row = BrandMenuCsvRow.builder()
                .brandName("테스트").menuName("메뉴").category("PROCESSED")
                .nutritionBasis("PER_SERVING")
                .servingSizeG("100").calories("200").protein("5").carbs("30")
                .fat("8").sodium("300").sugar("3").saturatedFat("2")
                .sourceUrl("").lastVerifiedAt("").recommendationStatus("INVALID_STATUS")
                .recommendationReason("").build();
        when(repository.findBySourceAndFoodCode(eq(FoodCatalogSource.BRAND_OFFICIAL), any()))
                .thenReturn(Optional.empty());

        importer.importRows(List.of(row));

        verify(repository).save(argThat(fc ->
                fc.getRecommendationStatus() == RecommendationStatus.SEARCH_ONLY
        ));
    }

    @Test
    @DisplayName("RECOMMENDABLE_WITH_CAUTION이 아닌 상태의 recommendation_reason은 저장하지 않는다")
    void importRows_clearsReasonForNonCautionStatus() {
        BrandMenuCsvRow row = BrandMenuCsvRow.builder()
                .brandName("테스트").menuName("메뉴").category("PROCESSED")
                .nutritionBasis("PER_SERVING")
                .servingSizeG("100").calories("200").protein("5").carbs("30")
                .fat("8").sodium("300").sugar("3").saturatedFat("2")
                .sourceUrl("").lastVerifiedAt("").recommendationStatus("SEARCH_ONLY")
                .recommendationReason("추천 제외 사유 메모").build();
        when(repository.findBySourceAndFoodCode(eq(FoodCatalogSource.BRAND_OFFICIAL), any()))
                .thenReturn(Optional.empty());

        importer.importRows(List.of(row));

        verify(repository).save(argThat(fc ->
                fc.getRecommendationStatus() == RecommendationStatus.SEARCH_ONLY
                        && fc.getRecommendationReason() == null
        ));
    }
}
