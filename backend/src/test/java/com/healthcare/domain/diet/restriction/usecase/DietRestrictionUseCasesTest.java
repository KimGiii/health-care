package com.healthcare.domain.diet.restriction.usecase;

import com.healthcare.common.exception.DuplicateResourceException;
import com.healthcare.common.exception.ResourceNotFoundException;
import com.healthcare.common.exception.UnauthorizedException;
import com.healthcare.domain.diet.allergen.AllergenTag;
import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.repository.FoodCatalogRepository;
import com.healthcare.domain.diet.restriction.dto.CreateDietRestrictionRequest;
import com.healthcare.domain.diet.restriction.dto.DietRestrictionResponse;
import com.healthcare.domain.diet.restriction.entity.DietRestriction;
import com.healthcare.domain.diet.restriction.entity.DietRestriction.RestrictionType;
import com.healthcare.domain.diet.restriction.entity.DietRestriction.TargetType;
import com.healthcare.domain.diet.restriction.repository.DietRestrictionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("DietRestrictionUseCases 단위 테스트")
class DietRestrictionUseCasesTest {

    @Mock private DietRestrictionRepository dietRestrictionRepository;
    @Mock private FoodCatalogRepository foodCatalogRepository;

    @InjectMocks
    private DietRestrictionUseCases dietRestrictionUseCases;

    // ─── 조회 ───

    @Test
    @DisplayName("사용자의 활성 제한 조건 목록을 반환한다")
    void listRestrictions_returnsActiveRestrictions() {
        var restriction = allergenTagRestriction(1L, 1L, RestrictionType.ALLERGY, AllergenTag.MILK);
        given(dietRestrictionRepository.findByUserIdAndDeletedAtIsNull(1L)).willReturn(List.of(restriction));

        List<DietRestrictionResponse> result = dietRestrictionUseCases.listRestrictions(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(1L);
        assertThat(result.get(0).allergenTag()).isEqualTo(AllergenTag.MILK);
    }

    // ─── 알러젠 태그 등록 ───

    @Nested
    @DisplayName("ALLERGEN_TAG 제한 등록")
    class AllergenTagRestriction {

        @Test
        @DisplayName("알러젠 태그 제한을 등록하면 저장된 응답을 반환한다")
        void create_allergenTag_savesAndReturns() {
            var request = CreateDietRestrictionRequest.builder()
                    .restrictionType(RestrictionType.ALLERGY)
                    .targetType(TargetType.ALLERGEN_TAG)
                    .allergenTag(AllergenTag.MILK)
                    .build();
            given(dietRestrictionRepository.findActiveAllergenTagRestriction(1L, RestrictionType.ALLERGY, AllergenTag.MILK))
                    .willReturn(Optional.empty());
            given(dietRestrictionRepository.saveAndFlush(any())).willAnswer(inv -> {
                DietRestriction r = inv.getArgument(0);
                return allergenTagRestriction(10L, 1L, r.getRestrictionType(), r.getAllergenTag());
            });

            DietRestrictionResponse result = dietRestrictionUseCases.createRestriction(1L, request);

            assertThat(result.allergenTag()).isEqualTo(AllergenTag.MILK);
            assertThat(result.targetType()).isEqualTo(TargetType.ALLERGEN_TAG);
        }

        @Test
        @DisplayName("이미 등록된 알러젠 태그 제한은 DuplicateResourceException을 던진다")
        void create_allergenTag_duplicate_throws() {
            var request = CreateDietRestrictionRequest.builder()
                    .restrictionType(RestrictionType.ALLERGY)
                    .targetType(TargetType.ALLERGEN_TAG)
                    .allergenTag(AllergenTag.MILK)
                    .build();
            given(dietRestrictionRepository.findActiveAllergenTagRestriction(1L, RestrictionType.ALLERGY, AllergenTag.MILK))
                    .willReturn(Optional.of(allergenTagRestriction(10L, 1L, RestrictionType.ALLERGY, AllergenTag.MILK)));

            assertThatThrownBy(() -> dietRestrictionUseCases.createRestriction(1L, request))
                    .isInstanceOf(DuplicateResourceException.class);
            verify(dietRestrictionRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("동시 요청으로 DB 중복 제약이 발생해도 DuplicateResourceException으로 변환한다")
        void create_allergenTag_databaseDuplicate_throwsDuplicateResourceException() {
            var request = CreateDietRestrictionRequest.builder()
                    .restrictionType(RestrictionType.ALLERGY)
                    .targetType(TargetType.ALLERGEN_TAG)
                    .allergenTag(AllergenTag.MILK)
                    .build();
            given(dietRestrictionRepository.findActiveAllergenTagRestriction(1L, RestrictionType.ALLERGY, AllergenTag.MILK))
                    .willReturn(Optional.empty());
            given(dietRestrictionRepository.saveAndFlush(any()))
                    .willThrow(new DataIntegrityViolationException("uq_diet_restrictions_active_allergen_tag"));

            assertThatThrownBy(() -> dietRestrictionUseCases.createRestriction(1L, request))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessage("이미 등록된 식단 제한 조건입니다.");
        }
    }

    // ─── 카테고리 등록 ───

    @Nested
    @DisplayName("CATEGORY 제한 등록")
    class CategoryRestriction {

        @Test
        @DisplayName("카테고리 제한을 등록한다")
        void create_category_savesAndReturns() {
            var request = CreateDietRestrictionRequest.builder()
                    .restrictionType(RestrictionType.AVOID)
                    .targetType(TargetType.CATEGORY)
                    .category(FoodCategory.DAIRY)
                    .build();
            given(dietRestrictionRepository.findActiveCategoryRestriction(1L, RestrictionType.AVOID, FoodCategory.DAIRY))
                    .willReturn(Optional.empty());
            given(dietRestrictionRepository.saveAndFlush(any())).willAnswer(inv -> categoryRestriction(20L, 1L, FoodCategory.DAIRY));

            DietRestrictionResponse result = dietRestrictionUseCases.createRestriction(1L, request);

            assertThat(result.category()).isEqualTo(FoodCategory.DAIRY);
        }

        @Test
        @DisplayName("CATEGORY 제한에 category 값이 없으면 예외를 던진다")
        void create_category_withoutCategory_throws() {
            var request = CreateDietRestrictionRequest.builder()
                    .restrictionType(RestrictionType.AVOID)
                    .targetType(TargetType.CATEGORY)
                    .build();

            assertThatThrownBy(() -> dietRestrictionUseCases.createRestriction(1L, request))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ─── 키워드 등록 ───

    @Nested
    @DisplayName("KEYWORD 제한 등록")
    class KeywordRestriction {

        @Test
        @DisplayName("키워드 제한을 등록 시 NFC 정규화를 적용한다")
        void create_keyword_normalizesKeyword() {
            var request = CreateDietRestrictionRequest.builder()
                    .restrictionType(RestrictionType.AVOID)
                    .targetType(TargetType.KEYWORD)
                    .keyword("  돼지  고기  ")
                    .build();
            given(dietRestrictionRepository.findActiveKeywordRestriction(1L, RestrictionType.AVOID, "돼지 고기"))
                    .willReturn(Optional.empty());
            given(dietRestrictionRepository.saveAndFlush(any())).willAnswer(inv -> {
                DietRestriction r = inv.getArgument(0);
                return keywordRestriction(30L, 1L, r.getKeyword());
            });

            DietRestrictionResponse result = dietRestrictionUseCases.createRestriction(1L, request);

            assertThat(result.keyword()).isEqualTo("돼지 고기");
        }

        @Test
        @DisplayName("빈 키워드는 예외를 던진다")
        void create_keyword_blank_throws() {
            var request = CreateDietRestrictionRequest.builder()
                    .restrictionType(RestrictionType.AVOID)
                    .targetType(TargetType.KEYWORD)
                    .keyword("   ")
                    .build();

            assertThatThrownBy(() -> dietRestrictionUseCases.createRestriction(1L, request))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ─── FOOD 등록 ───

    @Nested
    @DisplayName("FOOD 제한 등록")
    class FoodRestriction {

        @Test
        @DisplayName("존재하지 않는 식품 ID는 ResourceNotFoundException을 던진다")
        void create_food_notFound_throws() {
            var request = CreateDietRestrictionRequest.builder()
                    .restrictionType(RestrictionType.AVOID)
                    .targetType(TargetType.FOOD)
                    .foodCatalogId(999L)
                    .build();
            given(foodCatalogRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> dietRestrictionUseCases.createRestriction(1L, request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("FOOD 제한에 foodCatalogId가 없으면 예외를 던진다")
        void create_food_withoutFoodCatalogId_throws() {
            var request = CreateDietRestrictionRequest.builder()
                    .restrictionType(RestrictionType.AVOID)
                    .targetType(TargetType.FOOD)
                    .build();

            assertThatThrownBy(() -> dietRestrictionUseCases.createRestriction(1L, request))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ─── 필드 누락 검증 ───

    @Nested
    @DisplayName("필드 누락 검증")
    class FieldValidation {

        @Test
        @DisplayName("ALLERGEN_TAG 제한에 allergenTag가 없으면 예외를 던진다")
        void create_allergenTag_withoutTag_throws() {
            var request = CreateDietRestrictionRequest.builder()
                    .restrictionType(RestrictionType.ALLERGY)
                    .targetType(TargetType.ALLERGEN_TAG)
                    .build();

            assertThatThrownBy(() -> dietRestrictionUseCases.createRestriction(1L, request))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("KEYWORD가 100자 초과면 예외를 던진다")
        void create_keyword_tooLong_throws() {
            var request = CreateDietRestrictionRequest.builder()
                    .restrictionType(RestrictionType.AVOID)
                    .targetType(TargetType.KEYWORD)
                    .keyword("a".repeat(101))
                    .build();

            assertThatThrownBy(() -> dietRestrictionUseCases.createRestriction(1L, request))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ─── 삭제 ───

    @Test
    @DisplayName("본인의 제한 조건을 삭제한다")
    void deleteRestriction_ownRecord_softDeletes() {
        var restriction = allergenTagRestriction(1L, 1L, RestrictionType.ALLERGY, AllergenTag.MILK);
        given(dietRestrictionRepository.findById(1L)).willReturn(Optional.of(restriction));

        dietRestrictionUseCases.deleteRestriction(1L, 1L);

        verify(dietRestrictionRepository).save(restriction);
        assertThat(restriction.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("다른 사용자의 제한 조건 삭제는 UnauthorizedException을 던진다")
    void deleteRestriction_otherUser_throws() {
        var restriction = allergenTagRestriction(1L, 2L, RestrictionType.ALLERGY, AllergenTag.MILK);
        given(dietRestrictionRepository.findById(1L)).willReturn(Optional.of(restriction));

        assertThatThrownBy(() -> dietRestrictionUseCases.deleteRestriction(1L, 1L))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("존재하지 않는 제한 조건 삭제는 ResourceNotFoundException을 던진다")
    void deleteRestriction_notFound_throws() {
        given(dietRestrictionRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> dietRestrictionUseCases.deleteRestriction(1L, 99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── 헬퍼 ───

    private DietRestriction allergenTagRestriction(Long id, Long userId, RestrictionType type, AllergenTag tag) {
        return DietRestriction.builder()
                .id(id).userId(userId).restrictionType(type)
                .targetType(TargetType.ALLERGEN_TAG).allergenTag(tag)
                .build();
    }

    private DietRestriction categoryRestriction(Long id, Long userId, FoodCategory category) {
        return DietRestriction.builder()
                .id(id).userId(userId).restrictionType(RestrictionType.AVOID)
                .targetType(TargetType.CATEGORY).category(category)
                .build();
    }

    private DietRestriction keywordRestriction(Long id, Long userId, String keyword) {
        return DietRestriction.builder()
                .id(id).userId(userId).restrictionType(RestrictionType.AVOID)
                .targetType(TargetType.KEYWORD).keyword(keyword)
                .build();
    }

    private FoodCatalog food(Long id) {
        return FoodCatalog.builder()
                .id(id).name("test").category(FoodCategory.PROTEIN_SOURCE)
                .caloriesPer100g(200.0).isCustom(false)
                .build();
    }
}
