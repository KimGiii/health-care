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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("StandardDishFoodImporter")
class StandardDishFoodImporterTest {

    @Mock
    private FoodCatalogRepository foodCatalogRepository;

    private StandardDishFoodImporter importer;

    @BeforeEach
    void setUp() {
        importer = new StandardDishFoodImporter(new FoodCatalogIngestService(
                foodCatalogRepository,
                org.mockito.Mockito.mock(com.healthcare.domain.diet.repository.FoodServingOptionRepository.class),
                new ServingOptionDeriver()));
    }

    @Test
    @DisplayName("음식 표준데이터 행을 브랜드/업체명과 함께 내부 카탈로그에 적재한다")
    void importRows_createsDishFoodCatalogItem() {
        StandardFoodImportRow row = StandardFoodImportRow.builder()
                .foodCode("D001")
                .foodName("김치찌개")
                .restaurantName("한국외식")
                .categoryName("육류")
                .nutritionServingSize("1인분")
                .foodSize("250 g")
                .calories("120")
                .protein("8")
                .carbs("10")
                .fat("5")
                .sodium("680")
                .dataVersion("2026-05-06")
                .build();

        given(foodCatalogRepository.findBySourceAndFoodCode(FoodCatalogSource.MFDS_STANDARD_DISH, "D001"))
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

        assertThat(saved.getSource()).isEqualTo(FoodCatalogSource.MFDS_STANDARD_DISH);
        assertThat(saved.getSourceDetail()).isEqualTo("15100070");
        assertThat(saved.getFoodCode()).isEqualTo("D001");
        assertThat(saved.getNameKo()).isEqualTo("김치찌개");
        assertThat(saved.getBrandName()).isEqualTo("한국외식");
        assertThat(saved.getMaker()).isEqualTo("한국외식");
        assertThat(saved.getServingReference()).isEqualTo("1인분");
        assertThat(saved.getServingSizeG()).isEqualTo(250.0);
        assertThat(saved.getCategory()).isEqualTo(FoodCategory.PROTEIN_SOURCE);
        assertThat(saved.getCaloriesPer100g()).isEqualTo(120.0);
        assertThat(saved.getSodiumPer100gMg()).isEqualTo(680.0);
        assertThat(saved.getRecommendationStatus()).isEqualTo(RecommendationStatus.SEARCH_ONLY);
    }
}
