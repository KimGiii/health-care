package com.healthcare.domain.diet.external;

import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.external.dto.ExternalFoodResult;
import com.healthcare.domain.diet.external.dto.ExternalFoodResult.FoodDataSource;
import com.healthcare.domain.diet.external.dto.ImportFoodRequest;
import com.healthcare.domain.diet.external.service.FoodImportService;
import com.healthcare.domain.diet.repository.FoodCatalogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * RED: FoodImportService, ImportFoodRequest DTO 없으므로 컴파일 실패 상태.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FoodImportService 단위 테스트")
class FoodImportServiceTest {

    @Mock
    private FoodCatalogRepository foodCatalogRepository;

    @InjectMocks
    private FoodImportService foodImportService;

    // ─────────────────────────── 외부 식품 가져오기 ───────────────────────────

    @Test
    @DisplayName("USDA 식품 가져오기 시 isCustom=true, createdByUserId=현재사용자로 저장된다")
    void importFood_fromUsda_savesAsUserCustomFood() {
        // given
        Long userId = 1L;
        ImportFoodRequest request = ImportFoodRequest.builder()
                .source(FoodDataSource.USDA)
                .externalId("171705")
                .name("Chicken Breast")
                .nameKo("닭가슴살")
                .category(FoodCategory.PROTEIN_SOURCE)
                .caloriesPer100g(165.0)
                .proteinPer100g(31.0)
                .carbsPer100g(0.0)
                .fatPer100g(3.6)
                .build();

        FoodCatalog saved = FoodCatalog.builder()
                .id(999L).name("Chicken Breast").nameKo("닭가슴살")
                .category(FoodCategory.PROTEIN_SOURCE)
                .caloriesPer100g(165.0).proteinPer100g(31.0)
                .carbsPer100g(0.0).fatPer100g(3.6)
                .isCustom(true).createdByUserId(userId)
                .build();

        given(foodCatalogRepository.save(any(FoodCatalog.class))).willReturn(saved);

        // when
        var result = foodImportService.importFood(userId, request);

        // then
        assertThat(result.getId()).isEqualTo(999L);
        assertThat(result.getName()).isEqualTo("Chicken Breast");
        assertThat(result.isCustom()).isTrue();
        assertThat(result.getCreatedByUserId()).isEqualTo(userId);

        // 저장 엔티티 검증
        ArgumentCaptor<FoodCatalog> captor = ArgumentCaptor.forClass(FoodCatalog.class);
        verify(foodCatalogRepository).save(captor.capture());
        FoodCatalog capturedEntity = captor.getValue();
        assertThat(capturedEntity.getIsCustom()).isTrue();
        assertThat(capturedEntity.getCreatedByUserId()).isEqualTo(userId);
        assertThat(capturedEntity.getCaloriesPer100g()).isEqualTo(165.0);
        assertThat(capturedEntity.getProteinPer100g()).isEqualTo(31.0);
    }

    @Test
    @DisplayName("Open Food Facts 식품 가져오기 시 올바른 영양소와 카테고리로 저장된다")
    void importFood_fromOpenFoodFacts_savesCorrectNutrients() {
        // given
        Long userId = 2L;
        ImportFoodRequest request = ImportFoodRequest.builder()
                .source(FoodDataSource.OPEN_FOOD_FACTS)
                .externalId("3017620425400")
                .name("Nutella")
                .category(FoodCategory.PROCESSED)
                .caloriesPer100g(541.0)
                .proteinPer100g(6.3)
                .carbsPer100g(57.5)
                .fatPer100g(30.9)
                .build();

        FoodCatalog saved = FoodCatalog.builder()
                .id(888L).name("Nutella").category(FoodCategory.PROCESSED)
                .caloriesPer100g(541.0).proteinPer100g(6.3)
                .carbsPer100g(57.5).fatPer100g(30.9)
                .isCustom(true).createdByUserId(userId)
                .build();

        given(foodCatalogRepository.save(any(FoodCatalog.class))).willReturn(saved);

        // when
        var result = foodImportService.importFood(userId, request);

        // then
        assertThat(result.getCaloriesPer100g()).isEqualTo(541.0);
        assertThat(result.getCategory()).isEqualTo(FoodCategory.PROCESSED);

        ArgumentCaptor<FoodCatalog> captor = ArgumentCaptor.forClass(FoodCatalog.class);
        verify(foodCatalogRepository).save(captor.capture());
        assertThat(captor.getValue().getCreatedByUserId()).isEqualTo(userId);
        assertThat(captor.getValue().getFatPer100g()).isEqualTo(30.9);
    }

    @Test
    @DisplayName("nameKo 미제공 시 null로 저장된다")
    void importFood_withoutNameKo_savesNullNameKo() {
        // given
        Long userId = 1L;
        ImportFoodRequest request = ImportFoodRequest.builder()
                .source(FoodDataSource.USDA)
                .externalId("12345")
                .name("Oatmeal")
                .category(FoodCategory.GRAIN)
                .caloriesPer100g(389.0)
                .proteinPer100g(17.0)
                .carbsPer100g(66.0)
                .fatPer100g(7.0)
                .build();

        FoodCatalog saved = FoodCatalog.builder()
                .id(777L).name("Oatmeal").nameKo(null).category(FoodCategory.GRAIN)
                .caloriesPer100g(389.0).proteinPer100g(17.0)
                .carbsPer100g(66.0).fatPer100g(7.0)
                .isCustom(true).createdByUserId(userId)
                .build();
        given(foodCatalogRepository.save(any(FoodCatalog.class))).willReturn(saved);

        // when
        var result = foodImportService.importFood(userId, request);

        // then
        ArgumentCaptor<FoodCatalog> captor = ArgumentCaptor.forClass(FoodCatalog.class);
        verify(foodCatalogRepository).save(captor.capture());
        assertThat(captor.getValue().getNameKo()).isNull();
    }
}
