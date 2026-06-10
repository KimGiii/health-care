package com.healthcare.domain.diet.external.dedup;

import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.entity.FoodCatalogSource;
import com.healthcare.domain.diet.entity.RecommendationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FoodCatalogDuplicateCandidateReporter")
class FoodCatalogDuplicateCandidateReporterTest {

    private final FoodCatalogDuplicateCandidateReporter reporter = new FoodCatalogDuplicateCandidateReporter();

    @Test
    @DisplayName("빈 목록이면 그룹이 없는 리포트를 반환한다")
    void report_emptyInput_returnsEmptyReport() {
        FoodCatalogDuplicateCandidateReport report = reporter.report(List.of());

        assertThat(report.groups()).isEmpty();
        assertThat(report.totalGroups()).isZero();
        assertThat(report.totalCandidates()).isZero();
    }

    @Test
    @DisplayName("단일 항목이면 중복 그룹이 없다")
    void report_singleEntry_noGroups() {
        FoodCatalog single = catalog("닭가슴살", null, FoodCatalogSource.SEED);

        FoodCatalogDuplicateCandidateReport report = reporter.report(List.of(single));

        assertThat(report.groups()).isEmpty();
    }

    @Test
    @DisplayName("정규화한 이름이 동일한 항목 2개는 중복 그룹으로 묶인다")
    void report_sameNormalizedName_formsGroup() {
        FoodCatalog a = catalog("닭 가슴살", null, FoodCatalogSource.SEED);
        FoodCatalog b = catalog("닭가슴살", null, FoodCatalogSource.MFDS_FOOD_NUTRIENT_DB);

        FoodCatalogDuplicateCandidateReport report = reporter.report(List.of(a, b));

        assertThat(report.groups()).hasSize(1);
        assertThat(report.groups().get(0).entries()).containsExactlyInAnyOrder(a, b);
        assertThat(report.totalCandidates()).isEqualTo(2);
    }

    @Test
    @DisplayName("정규화 후 다른 이름이면 그룹화하지 않는다")
    void report_differentNames_noGroups() {
        FoodCatalog a = catalog("닭가슴살", null, FoodCatalogSource.SEED);
        FoodCatalog b = catalog("돼지고기", null, FoodCatalogSource.SEED);

        FoodCatalogDuplicateCandidateReport report = reporter.report(List.of(a, b));

        assertThat(report.groups()).isEmpty();
    }

    @Test
    @DisplayName("동일 이름 항목 3개는 하나의 그룹에 모두 포함된다")
    void report_threeEntriesSameName_allInOneGroup() {
        FoodCatalog a = catalog("현미밥", null, FoodCatalogSource.SEED);
        FoodCatalog b = catalog("현미 밥", null, FoodCatalogSource.MFDS_STANDARD_DISH);
        FoodCatalog c = catalog("현미-밥", null, FoodCatalogSource.MFDS_FOOD_NUTRIENT_DB);

        FoodCatalogDuplicateCandidateReport report = reporter.report(List.of(a, b, c));

        assertThat(report.groups()).hasSize(1);
        assertThat(report.groups().get(0).entries()).hasSize(3);
        assertThat(report.totalCandidates()).isEqualTo(3);
    }

    @Test
    @DisplayName("이름 그룹이 여러 개면 각각 별도 그룹으로 리포트된다")
    void report_multipleNameGroups_separateGroups() {
        FoodCatalog a1 = catalog("사과", null, FoodCatalogSource.SEED);
        FoodCatalog a2 = catalog("사과", null, FoodCatalogSource.MFDS_FOOD_NUTRIENT_DB);
        FoodCatalog b1 = catalog("바나나", null, FoodCatalogSource.SEED);
        FoodCatalog b2 = catalog("바나나", null, FoodCatalogSource.MFDS_STANDARD_DISH);

        FoodCatalogDuplicateCandidateReport report = reporter.report(List.of(a1, a2, b1, b2));

        assertThat(report.groups()).hasSize(2);
        assertThat(report.totalGroups()).isEqualTo(2);
        assertThat(report.totalCandidates()).isEqualTo(4);
    }

    @Test
    @DisplayName("nameKo가 null이면 name 기준으로 정규화한다")
    void report_nullNameKo_usesNameForNormalization() {
        FoodCatalog a = catalogWithName("Chicken Breast", null, FoodCatalogSource.SEED);
        FoodCatalog b = catalogWithName("chicken  breast", null, FoodCatalogSource.MFDS_FOOD_NUTRIENT_DB);

        FoodCatalogDuplicateCandidateReport report = reporter.report(List.of(a, b));

        assertThat(report.groups()).hasSize(1);
        assertThat(report.groups().get(0).entries()).containsExactlyInAnyOrder(a, b);
    }

    @Test
    @DisplayName("그룹의 정규화 키를 확인할 수 있다")
    void report_groupHasNormalizedKey() {
        FoodCatalog a = catalog("닭 가슴살", null, FoodCatalogSource.SEED);
        FoodCatalog b = catalog("닭가슴살", null, FoodCatalogSource.MFDS_FOOD_NUTRIENT_DB);

        FoodCatalogDuplicateCandidateReport report = reporter.report(List.of(a, b));

        assertThat(report.groups().get(0).normalizedKey()).isEqualTo("닭가슴살");
    }

    @Test
    @DisplayName("괄호 표기가 다른 동일 식품을 같은 그룹으로 묶는다")
    void report_parenthesesDifference_normalizedToSameKey() {
        FoodCatalog a = catalog("닭가슴살(구운)", null, FoodCatalogSource.SEED);
        FoodCatalog b = catalog("닭가슴살（구운）", null, FoodCatalogSource.MFDS_FOOD_NUTRIENT_DB);

        FoodCatalogDuplicateCandidateReport report = reporter.report(List.of(a, b));

        assertThat(report.groups()).hasSize(1);
    }

    // ---- 헬퍼 ----

    private FoodCatalog catalog(String nameKo, String brandName, FoodCatalogSource source) {
        return FoodCatalog.builder()
                .name(nameKo)
                .nameKo(nameKo)
                .category(FoodCategory.PROTEIN_SOURCE)
                .caloriesPer100g(165.0)
                .proteinPer100g(31.0)
                .carbsPer100g(0.0)
                .fatPer100g(3.6)
                .source(source)
                .brandName(brandName)
                .isCustom(false)
                .recommendationStatus(RecommendationStatus.RECOMMENDABLE)
                .build();
    }

    private FoodCatalog catalogWithName(String name, String nameKo, FoodCatalogSource source) {
        return FoodCatalog.builder()
                .name(name)
                .nameKo(nameKo)
                .category(FoodCategory.PROTEIN_SOURCE)
                .caloriesPer100g(165.0)
                .source(source)
                .isCustom(false)
                .recommendationStatus(RecommendationStatus.RECOMMENDABLE)
                .build();
    }
}
