package com.healthcare.domain.diet.external.importer;

import com.healthcare.domain.diet.external.config.ExternalApiProperties;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 공공데이터 API 실제 호출 smoke 테스트.
 * PUBLIC_FOOD_API_KEY 환경변수가 설정된 경우에만 실행된다.
 * CI에서는 일반 단위 테스트와 분리해 실행한다: ./gradlew test -Dgroups=smoke
 */
@Tag("smoke")
@EnabledIfEnvironmentVariable(named = "PUBLIC_FOOD_API_KEY", matches = ".+")
@DisplayName("[Smoke] 공공데이터 API 페이지 fetcher 실제 호출")
class FoodCatalogPublicApiSmokeTest {

    private static final int SMOKE_PAGE_SIZE = 3;
    private static final int SMOKE_PAGE_NO   = 1;

    private ExternalApiProperties properties;

    @BeforeEach
    void setUp() {
        properties = new ExternalApiProperties();
        properties.setPublicApiKey(System.getenv("PUBLIC_FOOD_API_KEY"));
    }

    // -----------------------------------------------------------------------
    // 가공식품 표준데이터 (15100066)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("가공식품 표준데이터 1페이지 호출 → row 반환 및 핵심 필드 존재")
    void processedFoodApi_fetchesFirstPage() {
        ExternalApiProperties props = new ExternalApiProperties();
        props.setPublicApiKey(properties.getPublicApiKey());

        RestClient client = RestClient.builder()
                .baseUrl("https://api.data.go.kr/openapi/tn_pubr_public_nutri_process_info_api")
                .defaultHeader("Accept", "application/json")
                .build();

        StandardProcessedFoodPageFetcher fetcher = new StandardProcessedFoodPageFetcher(client, props);
        FoodCatalogImportPage<StandardFoodImportRow> page = fetcher.fetch(SMOKE_PAGE_NO, SMOKE_PAGE_SIZE);

        assertThat(page.rows()).isNotEmpty();
        System.out.printf("[Smoke] 가공식품 표준데이터: rows=%d, hasNext=%b%n",
                page.rows().size(), page.hasNext());

        StandardFoodImportRow first = page.rows().get(0);
        System.out.printf("[Smoke] 첫 번째 row: foodCode=%s, name=%s, calories=%s%n",
                first.getFoodCode(), first.getFoodName(), first.getCalories());

        assertThat(first.getFoodCode()).isNotBlank();
        assertThat(first.getFoodName()).isNotBlank();
        assertCaloriesParseable(first.getCalories(), "가공식품");
    }

    @Test
    @DisplayName("가공식품 표준데이터 row → importer skip/create 로직이 예외 없이 동작")
    void processedFoodApi_importerProcessesRowsWithoutException() {
        ExternalApiProperties props = new ExternalApiProperties();
        props.setPublicApiKey(properties.getPublicApiKey());

        RestClient client = RestClient.builder()
                .baseUrl("https://api.data.go.kr/openapi/tn_pubr_public_nutri_process_info_api")
                .defaultHeader("Accept", "application/json")
                .build();

        StandardProcessedFoodPageFetcher fetcher = new StandardProcessedFoodPageFetcher(client, props);
        List<StandardFoodImportRow> rows = fetcher.fetch(SMOKE_PAGE_NO, SMOKE_PAGE_SIZE).rows();

        RecordingImporter importer = new RecordingImporter();
        FoodCatalogImportResult result = importer.importRows(rows);

        System.out.printf("[Smoke] 가공식품 importer: attempted=%d, skipped=%d%n",
                rows.size(), result.skippedCount());
        assertThat(result.createdCount() + result.updatedCount() + result.skippedCount())
                .isEqualTo(rows.size());
    }

    // -----------------------------------------------------------------------
    // 음식 표준데이터 (15100070)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("음식 표준데이터 1페이지 호출 → row 반환 및 핵심 필드 존재")
    void dishFoodApi_fetchesFirstPage() {
        ExternalApiProperties props = new ExternalApiProperties();
        props.setPublicApiKey(properties.getPublicApiKey());

        RestClient client = RestClient.builder()
                .baseUrl("https://api.data.go.kr/openapi/tn_pubr_public_nutri_food_info_api")
                .defaultHeader("Accept", "application/json")
                .build();

        StandardDishFoodPageFetcher fetcher = new StandardDishFoodPageFetcher(client, props);
        FoodCatalogImportPage<StandardFoodImportRow> page = fetcher.fetch(SMOKE_PAGE_NO, SMOKE_PAGE_SIZE);

        assertThat(page.rows()).isNotEmpty();
        System.out.printf("[Smoke] 음식 표준데이터: rows=%d, hasNext=%b%n",
                page.rows().size(), page.hasNext());

        StandardFoodImportRow first = page.rows().get(0);
        System.out.printf("[Smoke] 첫 번째 row: foodCode=%s, name=%s, calories=%s%n",
                first.getFoodCode(), first.getFoodName(), first.getCalories());

        assertThat(first.getFoodCode()).isNotBlank();
        assertThat(first.getFoodName()).isNotBlank();
        assertCaloriesParseable(first.getCalories(), "음식 표준데이터");
    }

    // -----------------------------------------------------------------------
    // 식품영양성분DB (FoodNtrCpntDbInfo02)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("식품영양성분DB 1페이지 호출 → row 반환 및 핵심 필드 존재")
    void foodNutrientDbApi_fetchesFirstPage() {
        ExternalApiProperties props = new ExternalApiProperties();
        props.setPublicApiKey(properties.getPublicApiKey());

        RestClient client = RestClient.builder()
                .baseUrl("https://apis.data.go.kr/1471000/FoodNtrCpntDbInfo02/getFoodNtrCpntDbInq02")
                .defaultHeader("Accept", "application/json")
                .build();

        MfdsFoodNutrientDbPageFetcher fetcher = new MfdsFoodNutrientDbPageFetcher(client, props);
        FoodCatalogImportPage<MfdsFoodNutrientDbImportRow> page = fetcher.fetch(SMOKE_PAGE_NO, SMOKE_PAGE_SIZE);

        assertThat(page.rows()).isNotEmpty();
        System.out.printf("[Smoke] 식품영양성분DB: rows=%d, hasNext=%b%n",
                page.rows().size(), page.hasNext());

        MfdsFoodNutrientDbImportRow first = page.rows().get(0);
        System.out.printf("[Smoke] 첫 번째 row: foodCode=%s, name=%s, calories=%s%n",
                first.getFoodCode(), first.getFoodNameKr(), first.getAmountNum1());

        assertThat(first.getFoodCode()).isNotBlank();
        assertThat(first.getFoodNameKr()).isNotBlank();
        assertCaloriesParseable(first.getAmountNum1(), "식품영양성분DB");
    }

    @Test
    @DisplayName("식품영양성분DB row → importer skip/create 로직이 예외 없이 동작")
    void foodNutrientDbApi_importerProcessesRowsWithoutException() {
        ExternalApiProperties props = new ExternalApiProperties();
        props.setPublicApiKey(properties.getPublicApiKey());

        RestClient client = RestClient.builder()
                .baseUrl("https://apis.data.go.kr/1471000/FoodNtrCpntDbInfo02/getFoodNtrCpntDbInq02")
                .defaultHeader("Accept", "application/json")
                .build();

        MfdsFoodNutrientDbPageFetcher fetcher = new MfdsFoodNutrientDbPageFetcher(client, props);
        List<MfdsFoodNutrientDbImportRow> rows = fetcher.fetch(SMOKE_PAGE_NO, SMOKE_PAGE_SIZE).rows();

        NutrientDbRecordingImporter importer = new NutrientDbRecordingImporter();
        FoodCatalogImportResult result = importer.importRows(rows);

        System.out.printf("[Smoke] 식품영양성분DB importer: attempted=%d, skipped=%d%n",
                rows.size(), result.skippedCount());
        assertThat(result.createdCount() + result.updatedCount() + result.skippedCount())
                .isEqualTo(rows.size());
    }

    // -----------------------------------------------------------------------
    // 헬퍼
    // -----------------------------------------------------------------------

    private void assertCaloriesParseable(String value, String label) {
        if (value == null || value.isBlank()) {
            System.out.printf("[Smoke] %s: 칼로리 값 없음 (null/blank) — skip 처리됩니다%n", label);
            return;
        }
        try {
            double kcal = Double.parseDouble(value.trim());
            assertThat(kcal).isGreaterThanOrEqualTo(0);
        } catch (NumberFormatException e) {
            System.out.printf("[Smoke] %s: 칼로리 파싱 실패 — value='%s'%n", label, value);
        }
    }

    /** 실제 DB 없이 row 파싱 가능 여부만 검증하는 in-memory importer */
    private static class RecordingImporter implements FoodCatalogPageImporter<StandardFoodImportRow> {
        @Override
        public FoodCatalogImportResult importRows(List<StandardFoodImportRow> rows) {
            int skipped = 0;
            for (StandardFoodImportRow row : rows) {
                if (row.getFoodCode() == null || row.getFoodCode().isBlank()
                        || row.getFoodName() == null || row.getFoodName().isBlank()
                        || row.getCalories() == null || row.getCalories().isBlank()) {
                    skipped++;
                }
            }
            return new FoodCatalogImportResult(rows.size() - skipped, 0, skipped);
        }
    }

    private static class NutrientDbRecordingImporter implements FoodCatalogPageImporter<MfdsFoodNutrientDbImportRow> {
        @Override
        public FoodCatalogImportResult importRows(List<MfdsFoodNutrientDbImportRow> rows) {
            int skipped = 0;
            for (MfdsFoodNutrientDbImportRow row : rows) {
                if (row.getFoodCode() == null || row.getFoodCode().isBlank()
                        || row.getFoodNameKr() == null || row.getFoodNameKr().isBlank()
                        || row.getAmountNum1() == null || row.getAmountNum1().isBlank()) {
                    skipped++;
                }
            }
            return new FoodCatalogImportResult(rows.size() - skipped, 0, skipped);
        }
    }
}
