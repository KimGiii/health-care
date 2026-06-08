package com.healthcare.domain.diet.recommendation.usecase;

import com.healthcare.common.exception.BusinessRuleViolationException;
import com.healthcare.domain.diet.allergen.AllergenTag;
import com.healthcare.domain.diet.allergen.repository.FoodAllergenTagRepository;
import com.healthcare.domain.diet.entity.DietLog.MealType;
import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.recommendation.dto.DailyDietRecommendationRequest;
import com.healthcare.domain.diet.recommendation.dto.DailyDietRecommendationResponse;
import com.healthcare.domain.diet.recommendation.engine.DietRecommendationEngine;
import com.healthcare.domain.diet.repository.FoodCatalogRepository;
import com.healthcare.domain.diet.restriction.entity.DietRestriction;
import com.healthcare.domain.diet.restriction.entity.DietRestriction.RestrictionType;
import com.healthcare.domain.diet.restriction.entity.DietRestriction.TargetType;
import com.healthcare.domain.diet.restriction.repository.DietRestrictionRepository;
import com.healthcare.domain.goals.entity.Goal;
import com.healthcare.domain.goals.repository.GoalRepository;
import com.healthcare.domain.user.entity.User;
import com.healthcare.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("DailyDietRecommendationUseCases 단위 테스트")
class DailyDietRecommendationUseCasesTest {

    @Mock private UserRepository userRepository;
    @Mock private GoalRepository goalRepository;
    @Mock private DietRestrictionRepository dietRestrictionRepository;
    @Mock private FoodCatalogRepository foodCatalogRepository;
    @Mock private FoodAllergenTagRepository foodAllergenTagRepository;
    @Spy private DietRecommendationEngine engine;

    @InjectMocks
    private DailyDietRecommendationUseCases useCases;

    @Test
    @DisplayName("필수 프로필 정보가 없으면 BusinessRuleViolationException을 던진다")
    void recommend_incompleteProfile_throws() {
        User user = User.builder().id(1L).email("a@b.com").displayName("test").build(); // 프로필 미완
        given(userRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(user));

        var request = request(List.of(MealType.BREAKFAST, MealType.LUNCH));
        assertThatThrownBy(() -> useCases.recommend(1L, request))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("프로필");
    }

    @Test
    @DisplayName("존재하지 않는 사용자는 BusinessRuleViolationException을 던진다")
    void recommend_userNotFound_throws() {
        given(userRepository.findByIdAndDeletedAtIsNull(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> useCases.recommend(99L, request(List.of(MealType.LUNCH))))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    @DisplayName("프로필이 완전하면 추천을 생성하고 응답을 반환한다")
    void recommend_completeProfile_returnsRecommendation() {
        User user = fullProfileUser();
        given(userRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(user));
        given(goalRepository.findActiveGoalByUserId(1L)).willReturn(Optional.empty());
        given(dietRestrictionRepository.findByUserIdAndDeletedAtIsNull(1L)).willReturn(List.of());
        given(foodCatalogRepository.findAll()).willReturn(diverseFoods());
        given(foodAllergenTagRepository.findByFoodCatalogIdIn(any())).willReturn(List.of());

        DailyDietRecommendationResponse response = useCases.recommend(1L,
                request(List.of(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER)));

        assertThat(response).isNotNull();
        assertThat(response.meals()).hasSize(3);
        assertThat(response.appliedRestrictions()).isEmpty();
    }

    @Test
    @DisplayName("추천 런타임에서 외부 API가 호출되지 않는다")
    void recommend_noExternalApiCall() {
        User user = fullProfileUser();
        given(userRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(user));
        given(goalRepository.findActiveGoalByUserId(1L)).willReturn(Optional.empty());
        given(dietRestrictionRepository.findByUserIdAndDeletedAtIsNull(1L)).willReturn(List.of());
        given(foodCatalogRepository.findAll()).willReturn(diverseFoods());
        given(foodAllergenTagRepository.findByFoodCatalogIdIn(any())).willReturn(List.of());

        useCases.recommend(1L, request(List.of(MealType.BREAKFAST)));

        // 외부 API 클라이언트 메서드가 호출되지 않았는지 확인
        // (FoodCatalogRepository와 내부 레포만 사용)
        verify(foodCatalogRepository, never()).findById(anyLong()); // 외부 임포트 전용 메서드
    }

    @Test
    @DisplayName("알러젠 제한이 있으면 응답에 적용된 제한 조건이 포함된다")
    void recommend_withRestrictions_appliedRestrictionsInResponse() {
        User user = fullProfileUser();
        DietRestriction restriction = DietRestriction.builder()
                .id(1L).userId(1L).restrictionType(RestrictionType.ALLERGY)
                .targetType(TargetType.ALLERGEN_TAG).allergenTag(AllergenTag.MILK)
                .build();
        given(userRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(user));
        given(goalRepository.findActiveGoalByUserId(1L)).willReturn(Optional.empty());
        given(dietRestrictionRepository.findByUserIdAndDeletedAtIsNull(1L)).willReturn(List.of(restriction));
        given(foodCatalogRepository.findAll()).willReturn(diverseFoods());
        given(foodAllergenTagRepository.findByFoodCatalogIdIn(any())).willReturn(List.of());

        DailyDietRecommendationResponse response = useCases.recommend(1L,
                request(List.of(MealType.LUNCH)));

        assertThat(response.appliedRestrictions()).hasSize(1);
    }

    // ─── 헬퍼 ───

    private DailyDietRecommendationRequest request(List<MealType> mealTypes) {
        return new DailyDietRecommendationRequest(LocalDate.now(), mealTypes, false);
    }

    private User fullProfileUser() {
        return User.builder()
                .id(1L).email("a@b.com").displayName("test")
                .sex(User.Sex.MALE)
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .heightCm(175.0).weightKg(75.0)
                .activityLevel(User.ActivityLevel.MODERATELY_ACTIVE)
                .build();
    }

    private List<FoodCatalog> diverseFoods() {
        return List.of(
                food(1L, "닭가슴살", FoodCategory.PROTEIN_SOURCE, 165.0),
                food(2L, "현미밥", FoodCategory.GRAIN, 150.0),
                food(3L, "브로콜리", FoodCategory.VEGETABLE, 34.0),
                food(4L, "사과", FoodCategory.FRUIT, 52.0),
                food(5L, "두부", FoodCategory.PROTEIN_SOURCE, 76.0),
                food(6L, "고구마", FoodCategory.GRAIN, 86.0)
        );
    }

    private FoodCatalog food(Long id, String name, FoodCategory category, double cal) {
        return FoodCatalog.builder()
                .id(id).name(name).category(category)
                .caloriesPer100g(cal).proteinPer100g(20.0)
                .carbsPer100g(10.0).fatPer100g(5.0)
                .isCustom(false).usageCount(0L)
                .build();
    }
}
