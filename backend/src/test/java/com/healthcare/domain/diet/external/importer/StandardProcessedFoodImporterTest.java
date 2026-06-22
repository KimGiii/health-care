package com.healthcare.domain.diet.external.importer;

import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.entity.FoodCatalogSource;
import com.healthcare.domain.diet.entity.RecommendationStatus;
import com.healthcare.domain.diet.repository.FoodCatalogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("StandardProcessedFoodImporter")
class StandardProcessedFoodImporterTest {

    @Mock
    private FoodCatalogRepository foodCatalogRepository;

    private StandardProcessedFoodImporter importer;

    @BeforeEach
    void setUp() {
        importer = new StandardProcessedFoodImporter(new FoodCatalogIngestService(foodCatalogRepository));
    }

    @Test
    @DisplayName("가공식품 표준데이터 행을 내부 카탈로그 항목으로 신규 적재한다")
    void importRows_createsProcessedFoodCatalogItem() {
        StandardFoodImportRow row = StandardFoodImportRow.builder()
                .foodCode("P001")
                .foodName("닭가슴살 통조림")
                .manufacturerName("헬스푸드")
                .categoryName("육류")
                .nutritionServingSize("100g")
                .foodSize("100 g")
                .calories("165")
                .protein("31")
                .carbs("0")
                .fat("3.6")
                .sugar("0")
                .dietaryFiber("0")
                .saturatedFat("1")
                .transFat("0")
                .cholesterol("85")
                .sodium("74")
                .dataVersion("2026-06-05")
                .lastVerifiedDate("2026-06-05")
                .build();

        given(foodCatalogRepository.findBySourceAndFoodCode(FoodCatalogSource.MFDS_STANDARD_PROCESSED, "P001"))
                .willReturn(Optional.empty());
        given(foodCatalogRepository.save(any(FoodCatalog.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        FoodCatalogImportResult result = importer.importRows(List.of(row));

        assertThat(result.createdCount()).isEqualTo(1);
        assertThat(result.updatedCount()).isZero();
        assertThat(result.skippedCount()).isZero();

        ArgumentCaptor<FoodCatalog> foodCaptor = ArgumentCaptor.forClass(FoodCatalog.class);
        verify(foodCatalogRepository).save(foodCaptor.capture());
        FoodCatalog saved = foodCaptor.getValue();

        assertThat(saved.getSource()).isEqualTo(FoodCatalogSource.MFDS_STANDARD_PROCESSED);
        assertThat(saved.getSourceDetail()).isEqualTo("15100066");
        assertThat(saved.getFoodCode()).isEqualTo("P001");
        assertThat(saved.getName()).isEqualTo("닭가슴살 통조림");
        assertThat(saved.getNameKo()).isEqualTo("닭가슴살 통조림");
        assertThat(saved.getCategory()).isEqualTo(FoodCategory.PROTEIN_SOURCE);
        assertThat(saved.getMaker()).isEqualTo("헬스푸드");
        assertThat(saved.getServingReference()).isEqualTo("100g");
        assertThat(saved.getServingSizeG()).isEqualTo(100.0);
        assertThat(saved.getCaloriesPer100g()).isEqualTo(165.0);
        assertThat(saved.getProteinPer100g()).isEqualTo(31.0);
        assertThat(saved.getCarbsPer100g()).isEqualTo(0.0);
        assertThat(saved.getFatPer100g()).isEqualTo(3.6);
        assertThat(saved.getSodiumPer100gMg()).isEqualTo(74.0);
        assertThat(saved.getRecommendationStatus()).isEqualTo(RecommendationStatus.SEARCH_ONLY);
        assertThat(saved.getDataVersion()).isEqualTo("2026-06-05");
        assertThat(saved.getLastVerifiedAt()).isEqualTo(
                LocalDate.of(2026, 6, 5).atStartOfDay().atOffset(ZoneOffset.ofHours(9)));
        assertThat(saved.getIsCustom()).isFalse();
    }

    @Test
    @DisplayName("DB 한도를 넘는 제조사명은 150자로 잘라 적재한다")
    void importRows_truncatesLongMakerName() {
        String longMakerName = "A".repeat(163);
        StandardFoodImportRow row = StandardFoodImportRow.builder()
                .foodCode("P-LONG-MAKER")
                .foodName("명태알포")
                .manufacturerName(longMakerName)
                .categoryName("수산가공식품류")
                .nutritionServingSize("100g")
                .foodSize("100 g")
                .calories("300")
                .build();

        given(foodCatalogRepository.findBySourceAndFoodCode(
                FoodCatalogSource.MFDS_STANDARD_PROCESSED, "P-LONG-MAKER"))
                .willReturn(Optional.empty());
        given(foodCatalogRepository.save(any(FoodCatalog.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        FoodCatalogImportResult result = importer.importRows(List.of(row));

        assertThat(result.createdCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isZero();

        ArgumentCaptor<FoodCatalog> foodCaptor = ArgumentCaptor.forClass(FoodCatalog.class);
        verify(foodCatalogRepository).save(foodCaptor.capture());
        FoodCatalog saved = foodCaptor.getValue();

        assertThat(saved.getMaker()).hasSize(150);
        assertThat(saved.getMaker()).isEqualTo("A".repeat(150));
    }

    @Test
    @DisplayName("같은 source와 food_code를 다시 적재하면 기존 항목을 갱신하고, 영양 사실이 유의미하게 바뀌면 추천 자격을 회수한다")
    void importRows_updatesExistingFoodCatalogItem() {
        FoodCatalog existing = FoodCatalog.builder()
                .id(10L)
                .foodCode("P001")
                .source(FoodCatalogSource.MFDS_STANDARD_PROCESSED)
                .sourceDetail("15100066")
                .name("이전 이름")
                .nameKo("이전 이름")
                .category(FoodCategory.OTHER)
                .caloriesPer100g(1.0)
                .recommendationStatus(RecommendationStatus.RECOMMENDABLE)
                .recommendationReason("관리자 검수 완료")
                .isCustom(false)
                .build();

        StandardFoodImportRow row = StandardFoodImportRow.builder()
                .foodCode("P001")
                .foodName("닭가슴살 통조림")
                .manufacturerName("헬스푸드")
                .categoryName("육류")
                .nutritionServingSize("100g")
                .foodSize("100 g")
                .calories("165")
                .protein("31")
                .carbs("0")
                .fat("3.6")
                .sodium("74")
                .dataVersion("2026-06-05")
                .build();

        given(foodCatalogRepository.findBySourceAndFoodCode(FoodCatalogSource.MFDS_STANDARD_PROCESSED, "P001"))
                .willReturn(Optional.of(existing));
        given(foodCatalogRepository.save(any(FoodCatalog.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        FoodCatalogImportResult result = importer.importRows(List.of(row));

        assertThat(result.createdCount()).isZero();
        assertThat(result.updatedCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isZero();

        ArgumentCaptor<FoodCatalog> foodCaptor = ArgumentCaptor.forClass(FoodCatalog.class);
        verify(foodCatalogRepository).save(foodCaptor.capture());
        FoodCatalog saved = foodCaptor.getValue();

        assertThat(saved.getId()).isEqualTo(10L);
        assertThat(saved.getName()).isEqualTo("닭가슴살 통조림");
        assertThat(saved.getCategory()).isEqualTo(FoodCategory.PROTEIN_SOURCE);
        assertThat(saved.getMaker()).isEqualTo("헬스푸드");
        assertThat(saved.getCaloriesPer100g()).isEqualTo(165.0);
        assertThat(saved.getProteinPer100g()).isEqualTo(31.0);
        assertThat(saved.getRecommendationStatus()).isEqualTo(RecommendationStatus.SEARCH_ONLY);
        assertThat(saved.getRecommendationReason()).isEqualTo(FoodCatalog.STALE_FACTS_REVALIDATION_REASON);
    }
}
