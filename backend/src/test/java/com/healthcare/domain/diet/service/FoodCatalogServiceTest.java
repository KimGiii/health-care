package com.healthcare.domain.diet.service;

import com.healthcare.domain.diet.dto.CreateCustomFoodRequest;
import com.healthcare.domain.diet.dto.FoodCatalogResponse;
import com.healthcare.domain.diet.dto.FoodSearchParams;
import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.repository.FoodCatalogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * RED: FoodCatalogService, FoodCatalogRepository, DTO 클래스가 없으므로
 *      이 테스트들은 컴파일 실패 상태입니다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FoodCatalogService 단위 테스트")
class FoodCatalogServiceTest {

    @Mock
    private FoodCatalogRepository foodCatalogRepository;

    @InjectMocks
    private FoodCatalogService foodCatalogService;

    // ─────────────────────────── 식품 카탈로그 조회 ───────────────────────────

    @Test
    @DisplayName("필터 없이 조회 시 글로벌 + 사용자 커스텀 식품을 반환한다")
    void searchFoods_noFilter_returnsGlobalAndUserCustomFoods() {
        // given
        Long userId = 1L;
        FoodCatalog rice = buildGlobalFood(1L, "White Rice", "흰쌀밥",
                FoodCategory.GRAIN, 130.0, 2.4, 28.7, 0.3);
        FoodCatalog customFood = buildCustomFood(2L, "My Protein Shake", FoodCategory.PROTEIN_SOURCE, userId);

        given(foodCatalogRepository.findAccessibleToUser(userId, null, null, false))
                .willReturn(List.of(rice, customFood));

        // when
        List<FoodCatalogResponse> result = foodCatalogService.searchFoods(
                userId, FoodSearchParams.of(null, null, false));

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("White Rice");
        assertThat(result.get(0).isCustom()).isFalse();
        assertThat(result.get(1).getName()).isEqualTo("My Protein Shake");
        assertThat(result.get(1).isCustom()).isTrue();
    }

    @Test
    @DisplayName("category 필터로 조회 시 해당 카테고리만 반환한다")
    void searchFoods_withCategoryFilter_returnsOnlyMatchingCategory() {
        // given
        Long userId = 1L;
        FoodCatalog chicken = buildGlobalFood(3L, "Chicken Breast", "닭가슴살",
                FoodCategory.PROTEIN_SOURCE, 165.0, 31.0, 0.0, 3.6);

        given(foodCatalogRepository.findAccessibleToUser(userId, null, FoodCategory.PROTEIN_SOURCE, false))
                .willReturn(List.of(chicken));

        // when
        List<FoodCatalogResponse> result = foodCatalogService.searchFoods(
                userId, FoodSearchParams.of(null, FoodCategory.PROTEIN_SOURCE, false));

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCategory()).isEqualTo(FoodCategory.PROTEIN_SOURCE);
    }

    @Test
    @DisplayName("customOnly=true 조회 시 해당 사용자의 커스텀 식품만 반환한다")
    void searchFoods_customOnly_returnsOnlyUserCustomFoods() {
        // given
        Long userId = 1L;
        FoodCatalog customFood = buildCustomFood(10L, "My Salad", FoodCategory.VEGETABLE, userId);

        given(foodCatalogRepository.findAccessibleToUser(userId, null, null, true))
                .willReturn(List.of(customFood));

        // when
        List<FoodCatalogResponse> result = foodCatalogService.searchFoods(
                userId, FoodSearchParams.of(null, null, true));

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).isCustom()).isTrue();
        assertThat(result.get(0).getCreatedByUserId()).isEqualTo(userId);
    }

    @Test
    @DisplayName("검색어는 trim 후 repository에 전달한다")
    void searchFoods_trimsQueryBeforeRepositoryCall() {
        // given
        Long userId = 1L;
        FoodCatalog chicken = buildGlobalFood(3L, "Chicken Breast", "닭가슴살",
                FoodCategory.PROTEIN_SOURCE, 165.0, 31.0, 0.0, 3.6);

        given(foodCatalogRepository.findAccessibleToUser(userId, "닭가슴살", null, false))
                .willReturn(List.of(chicken));

        // when
        List<FoodCatalogResponse> result = foodCatalogService.searchFoods(
                userId, FoodSearchParams.of("  닭가슴살  ", null, false));

        // then
        assertThat(result).hasSize(1);
        verify(foodCatalogRepository).findAccessibleToUser(userId, "닭가슴살", null, false);
    }

    // ─────────────────────────── 커스텀 식품 생성 ───────────────────────────

    @Test
    @DisplayName("커스텀 식품 생성 성공 시 저장된 식품을 반환한다")
    void createCustomFood_success_returnsCreatedFood() {
        // given
        Long userId = 1L;
        CreateCustomFoodRequest request = CreateCustomFoodRequest.builder()
                .name("Greek Yogurt")
                .nameKo("그릭 요거트")
                .category(FoodCategory.DAIRY)
                .caloriesPer100g(97.0)
                .proteinPer100g(9.0)
                .carbsPer100g(3.6)
                .fatPer100g(5.0)
                .build();

        FoodCatalog saved = buildCustomFood(99L, "Greek Yogurt", FoodCategory.DAIRY, userId);
        given(foodCatalogRepository.save(any(FoodCatalog.class))).willReturn(saved);

        // when
        FoodCatalogResponse result = foodCatalogService.createCustomFood(userId, request);

        // then
        assertThat(result.getId()).isEqualTo(99L);
        assertThat(result.getName()).isEqualTo("Greek Yogurt");
        assertThat(result.isCustom()).isTrue();
        assertThat(result.getCreatedByUserId()).isEqualTo(userId);

        // 저장되는 엔티티가 isCustom=true, createdByUserId=userId 인지 확인
        ArgumentCaptor<FoodCatalog> captor = ArgumentCaptor.forClass(FoodCatalog.class);
        verify(foodCatalogRepository).save(captor.capture());
        FoodCatalog capturedEntity = captor.getValue();
        assertThat(capturedEntity.getIsCustom()).isTrue();
        assertThat(capturedEntity.getCreatedByUserId()).isEqualTo(userId);
    }

    @Test
    @DisplayName("커스텀 식품 생성 시 소유자는 항상 현재 사용자로 저장된다")
    void createCustomFood_alwaysAssignsOwnerToCurrentUser() {
        // given
        Long userId = 42L;
        CreateCustomFoodRequest request = CreateCustomFoodRequest.builder()
                .name("My Mix")
                .category(FoodCategory.OTHER)
                .caloriesPer100g(200.0)
                .proteinPer100g(10.0)
                .carbsPer100g(20.0)
                .fatPer100g(8.0)
                .build();

        FoodCatalog saved = buildCustomFood(200L, "My Mix", FoodCategory.OTHER, userId);
        given(foodCatalogRepository.save(any(FoodCatalog.class))).willReturn(saved);

        // when
        foodCatalogService.createCustomFood(userId, request);

        // then — 저장 시 createdByUserId = 현재 userId 로 고정
        ArgumentCaptor<FoodCatalog> captor = ArgumentCaptor.forClass(FoodCatalog.class);
        verify(foodCatalogRepository).save(captor.capture());
        assertThat(captor.getValue().getCreatedByUserId()).isEqualTo(userId);
    }

    // ─────────────────────────── 헬퍼 ───────────────────────────

    private FoodCatalog buildGlobalFood(Long id, String name, String nameKo,
            FoodCategory category, Double caloriesPer100g,
            Double proteinPer100g, Double carbsPer100g, Double fatPer100g) {
        return FoodCatalog.builder()
                .id(id).name(name).nameKo(nameKo).category(category)
                .caloriesPer100g(caloriesPer100g).proteinPer100g(proteinPer100g)
                .carbsPer100g(carbsPer100g).fatPer100g(fatPer100g)
                .isCustom(false).createdByUserId(null)
                .build();
    }

    private FoodCatalog buildCustomFood(Long id, String name, FoodCategory category, Long createdByUserId) {
        return FoodCatalog.builder()
                .id(id).name(name).category(category)
                .caloriesPer100g(100.0).proteinPer100g(10.0)
                .carbsPer100g(10.0).fatPer100g(3.0)
                .isCustom(true).createdByUserId(createdByUserId)
                .build();
    }
}
