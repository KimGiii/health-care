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
@DisplayName("MfdsFoodNutrientDbImporter")
class MfdsFoodNutrientDbImporterTest {

    @Mock
    private FoodCatalogRepository foodCatalogRepository;

    private MfdsFoodNutrientDbImporter importer;

    @BeforeEach
    void setUp() {
        importer = new MfdsFoodNutrientDbImporter(new FoodCatalogIngestService(foodCatalogRepository));
    }

    @Test
    @DisplayName("FoodNtrCpntDbInfo02 행의 핵심 영양소와 제공량 메타데이터를 적재한다")
    void importRows_createsFoodNutrientDbCatalogItem() {
        MfdsFoodNutrientDbImportRow row = MfdsFoodNutrientDbImportRow.builder()
                .foodCode("N001")
                .foodNameKr("와퍼")
                .foodCategoryName("가공식품")
                .sellerManufacturerName("버거킹")
                .makerName("비케이알")
                .servingSize("278g")
                .foodWeight("278")
                .nutritionAmountServing("1개")
                .amountNum1("619")
                .amountNum3("29")
                .amountNum4("33")
                .amountNum6("49")
                .amountNum7("11")
                .amountNum8("4")
                .amountNum13("1050")
                .updateDate("2025-12-05")
                .build();

        given(foodCatalogRepository.findBySourceAndFoodCode(FoodCatalogSource.MFDS_FOOD_NUTRIENT_DB, "N001"))
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

        assertThat(saved.getSource()).isEqualTo(FoodCatalogSource.MFDS_FOOD_NUTRIENT_DB);
        assertThat(saved.getSourceDetail()).isEqualTo("FoodNtrCpntDbInfo02");
        assertThat(saved.getFoodCode()).isEqualTo("N001");
        assertThat(saved.getNameKo()).isEqualTo("와퍼");
        assertThat(saved.getBrandName()).isEqualTo("버거킹");
        assertThat(saved.getMaker()).isEqualTo("비케이알");
        assertThat(saved.getCategory()).isEqualTo(FoodCategory.PROCESSED);
        assertThat(saved.getServingReference()).isEqualTo("1개");
        assertThat(saved.getServingSizeG()).isEqualTo(278.0);
        assertThat(saved.getCaloriesPer100g()).isEqualTo(619.0);
        assertThat(saved.getProteinPer100g()).isEqualTo(29.0);
        assertThat(saved.getFatPer100g()).isEqualTo(33.0);
        assertThat(saved.getCarbsPer100g()).isEqualTo(49.0);
        assertThat(saved.getSugarsPer100g()).isEqualTo(11.0);
        assertThat(saved.getDietaryFiberPer100g()).isEqualTo(4.0);
        assertThat(saved.getSodiumPer100gMg()).isEqualTo(1050.0);
        assertThat(saved.getRecommendationStatus()).isEqualTo(RecommendationStatus.SEARCH_ONLY);
        assertThat(saved.getDataVersion()).isEqualTo("2025-12-05");
        assertThat(saved.getLastVerifiedAt()).isEqualTo(
                LocalDate.of(2025, 12, 5).atStartOfDay().atOffset(ZoneOffset.ofHours(9)));
    }

    @Test
    @DisplayName("기존 식품영양성분DB 항목 갱신 시 영양 사실이 유의미하게 바뀌면 추천 자격을 회수한다")
    void importRows_updatesSourceFactsButPreservesRecommendationCuration() {
        FoodCatalog existing = FoodCatalog.builder()
                .id(10L)
                .foodCode("N001")
                .source(FoodCatalogSource.MFDS_FOOD_NUTRIENT_DB)
                .sourceDetail("FoodNtrCpntDbInfo02")
                .name("이전 와퍼")
                .nameKo("이전 와퍼")
                .category(FoodCategory.PROCESSED)
                .caloriesPer100g(500.0)
                .recommendationStatus(RecommendationStatus.RECOMMENDABLE_WITH_CAUTION)
                .recommendationReason("나트륨 주의")
                .isCustom(false)
                .build();
        MfdsFoodNutrientDbImportRow row = MfdsFoodNutrientDbImportRow.builder()
                .foodCode("N001")
                .foodNameKr("와퍼")
                .foodCategoryName("가공식품")
                .amountNum1("619")
                .amountNum3("29")
                .amountNum4("33")
                .amountNum6("49")
                .amountNum13("1050")
                .updateDate("2025-12-05")
                .build();
        given(foodCatalogRepository.findBySourceAndFoodCode(FoodCatalogSource.MFDS_FOOD_NUTRIENT_DB, "N001"))
                .willReturn(Optional.of(existing));
        given(foodCatalogRepository.save(any(FoodCatalog.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        FoodCatalogImportResult result = importer.importRows(List.of(row));

        assertThat(result.updatedCount()).isEqualTo(1);
        ArgumentCaptor<FoodCatalog> foodCaptor = ArgumentCaptor.forClass(FoodCatalog.class);
        verify(foodCatalogRepository).save(foodCaptor.capture());
        FoodCatalog saved = foodCaptor.getValue();
        assertThat(saved.getNameKo()).isEqualTo("와퍼");
        assertThat(saved.getCaloriesPer100g()).isEqualTo(619.0);
        assertThat(saved.getRecommendationStatus()).isEqualTo(RecommendationStatus.SEARCH_ONLY);
        assertThat(saved.getRecommendationReason()).isEqualTo(FoodCatalog.STALE_FACTS_REVALIDATION_REASON);
    }
}
