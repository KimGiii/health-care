package com.healthcare.domain.diet.recommendation.candidate;

import com.healthcare.domain.diet.allergen.AllergenConfidenceLevel;
import com.healthcare.domain.diet.allergen.AllergenDataSource;
import com.healthcare.domain.diet.allergen.AllergenSafetyGate;
import com.healthcare.domain.diet.allergen.AllergenTag;
import com.healthcare.domain.diet.allergen.entity.FoodAllergenProfile;
import com.healthcare.domain.diet.allergen.entity.FoodAllergenTag;
import com.healthcare.domain.diet.allergen.repository.FoodAllergenProfileRepository;
import com.healthcare.domain.diet.allergen.repository.FoodAllergenTagRepository;
import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.entity.FoodCatalogSource;
import com.healthcare.domain.diet.entity.FoodServingOption;
import com.healthcare.domain.diet.entity.RecommendationStatus;
import com.healthcare.domain.diet.policy.DataFreshnessPolicy;
import com.healthcare.domain.diet.repository.FoodCatalogRepository;
import com.healthcare.domain.diet.repository.FoodServingOptionRepository;
import com.healthcare.domain.diet.restriction.entity.DietRestriction;
import com.healthcare.domain.diet.restriction.entity.DietRestriction.RestrictionType;
import com.healthcare.domain.diet.restriction.entity.DietRestriction.TargetType;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collection;
import java.util.List;
import java.time.OffsetDateTime;

import static com.healthcare.domain.diet.entity.RecommendationStatus.RECOMMENDABLE;
import static com.healthcare.domain.diet.entity.RecommendationStatus.RECOMMENDABLE_WITH_CAUTION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("DietRecommendationCandidatePool")
class DietRecommendationCandidatePoolTest {

    @Mock
    private FoodCatalogRepository foodCatalogRepository;

    @Mock
    private FoodAllergenTagRepository foodAllergenTagRepository;

    @Mock
    private FoodAllergenProfileRepository foodAllergenProfileRepository;

    @Mock
    private FoodServingOptionRepository foodServingOptionRepository;

    private DietRecommendationCandidatePool candidatePool;

    @BeforeEach
    void setUp() {
        candidatePool = new DietRecommendationCandidatePool(
                foodCatalogRepository,
                foodAllergenTagRepository,
                foodAllergenProfileRepository,
                foodServingOptionRepository,
                new AllergenSafetyGate(),
                new DataFreshnessPolicy()
        );
    }

    @Test
    @DisplayName("추천 후보 로드 시 recommendation_status 필터가 항상 적용된다")
    void load_alwaysAppliesRecommendationStatusFilter() {
        givenCatalogCandidates(List.of());

        candidatePool.load(List.of(), false);

        Collection<RecommendationStatus> statuses = extractRecommendationStatuses(captureCandidateSpec());
        assertThat(statuses)
                .containsExactlyInAnyOrder(RECOMMENDABLE, RECOMMENDABLE_WITH_CAUTION)
                .doesNotContain(RecommendationStatus.SEARCH_ONLY, RecommendationStatus.DISABLED);
    }

    @Test
    @DisplayName("FOOD 제한 조건의 식품은 후보에서 제외된다")
    void load_foodRestriction_excludesFood() {
        FoodCatalog chicken = food(1L, "닭가슴살", FoodCategory.PROTEIN_SOURCE, 165.0);
        FoodCatalog rice = food(2L, "백미밥", FoodCategory.GRAIN, 130.0);
        givenCatalogCandidates(List.of(chicken, rice));
        given(foodAllergenTagRepository.findByFoodCatalogIdIn(any())).willReturn(List.of());

        DietRestriction restriction = foodRestriction(1L, RestrictionType.AVOID, 1L);
        List<DietRecommendationCandidate> result = candidatePool.load(List.of(restriction), false).foods();

        assertThat(resultFoodIds(result)).doesNotContain(chicken.getId());
        assertThat(resultFoodIds(result)).contains(rice.getId());
    }

    @Test
    @DisplayName("CATEGORY 제한 조건의 카테고리 식품은 모두 제외된다")
    void load_categoryRestriction_excludesAllInCategory() {
        FoodCatalog chicken = food(1L, "닭가슴살", FoodCategory.PROTEIN_SOURCE, 165.0);
        FoodCatalog egg = food(2L, "달걀", FoodCategory.PROTEIN_SOURCE, 155.0);
        FoodCatalog rice = food(3L, "백미밥", FoodCategory.GRAIN, 130.0);
        givenCatalogCandidates(List.of(chicken, egg, rice));
        given(foodAllergenTagRepository.findByFoodCatalogIdIn(any())).willReturn(List.of());

        DietRestriction restriction = categoryRestriction(RestrictionType.AVOID, FoodCategory.PROTEIN_SOURCE);
        List<DietRecommendationCandidate> result = candidatePool.load(List.of(restriction), false).foods();

        assertThat(resultFoodIds(result)).doesNotContain(chicken.getId(), egg.getId());
        assertThat(resultFoodIds(result)).contains(rice.getId());
    }

    @Test
    @DisplayName("KEYWORD 제한 조건과 이름이 매칭되는 식품은 제외된다")
    void load_keywordRestriction_excludesMatchingFoods() {
        FoodCatalog porkBelly = food(1L, "삼겹살", FoodCategory.PROTEIN_SOURCE, 331.0);
        FoodCatalog porkLoin = food(2L, "돼지등심", FoodCategory.PROTEIN_SOURCE, 182.0);
        FoodCatalog chicken = food(3L, "닭가슴살", FoodCategory.PROTEIN_SOURCE, 165.0);
        givenCatalogCandidates(List.of(porkBelly, porkLoin, chicken));
        given(foodAllergenTagRepository.findByFoodCatalogIdIn(any())).willReturn(List.of());

        DietRestriction restriction = keywordRestriction("돼지");
        List<DietRecommendationCandidate> result = candidatePool.load(List.of(restriction), false).foods();

        assertThat(resultFoodIds(result)).doesNotContain(porkLoin.getId());
        assertThat(resultFoodIds(result)).contains(porkBelly.getId(), chicken.getId());
    }

    @Test
    @DisplayName("ALLERGEN_TAG 제한 조건의 알러젠 식품은 후보에서 제외된다")
    void load_allergenTag_excludesFoodsWithAllergen() {
        FoodCatalog milk = food(1L, "우유", FoodCategory.DAIRY, 61.0);
        FoodCatalog chicken = food(2L, "닭가슴살", FoodCategory.PROTEIN_SOURCE, 165.0);
        givenCatalogCandidates(List.of(milk, chicken));
        given(foodAllergenTagRepository.findByFoodCatalogIdIn(any()))
                .willReturn(List.of(allergenTag(1L, AllergenTag.MILK, AllergenConfidenceLevel.DIRECT_VERIFIED)));
        given(foodAllergenProfileRepository.findByFoodCatalogIdIn(any()))
                .willReturn(List.of(allergenProfile(2L)));

        DietRestriction restriction = allergenTagRestriction(RestrictionType.ALLERGY, AllergenTag.MILK);
        List<DietRecommendationCandidate> result = candidatePool.load(List.of(restriction), false).foods();

        assertThat(resultFoodIds(result)).doesNotContain(milk.getId());
        assertThat(resultFoodIds(result)).contains(chicken.getId());
    }

    @Test
    @DisplayName("알러지 제한이 있으면 strict 요청값과 무관하게 별도 프로필 검증 후보만 통과한다")
    void load_allergyRestriction_requiresSeparateVerifiedProfile() {
        FoodCatalog riceNoodle = food(1L, "쌀국수", FoodCategory.GRAIN, 100.0);
        FoodCatalog plainRice = food(2L, "백미밥", FoodCategory.GRAIN, 130.0);
        givenCatalogCandidates(List.of(riceNoodle, plainRice));
        given(foodAllergenTagRepository.findByFoodCatalogIdIn(any())).willReturn(List.of());
        given(foodAllergenProfileRepository.findByFoodCatalogIdIn(any()))
                .willReturn(List.of(allergenProfile(2L)));

        DietRestriction milkRestriction = allergenTagRestriction(RestrictionType.ALLERGY, AllergenTag.MILK);
        List<DietRecommendationCandidate> result = candidatePool.load(List.of(milkRestriction), false).foods();

        assertThat(resultFoodIds(result)).doesNotContain(riceNoodle.getId());
        assertThat(resultFoodIds(result)).contains(plainRice.getId());
        assertThat(result).singleElement()
                .extracting(DietRecommendationCandidate::allergenConfidenceLevel)
                .isEqualTo(AllergenConfidenceLevel.LABEL_DERIVED);
    }

    @Test
    @DisplayName("strict 모드에서 고신뢰 verified 태그가 없는 식품은 제외된다")
    void load_strictMode_excludesFoodWithoutHighConfidenceVerifiedTag() {
        FoodCatalog plainRice = food(1L, "백미밥", FoodCategory.GRAIN, 130.0);
        givenCatalogCandidates(List.of(plainRice));
        // WHEAT 태그가 있지만 allergenProfileVerified=false → strict gate 미통과
        given(foodAllergenTagRepository.findByFoodCatalogIdIn(any()))
                .willReturn(List.of(allergenTag(1L, AllergenTag.WHEAT, AllergenConfidenceLevel.UNKNOWN)));
        given(foodAllergenProfileRepository.findByFoodCatalogIdIn(any()))
                .willReturn(List.of(allergenProfile(1L)));

        DietRestriction milkRestriction = allergenTagRestriction(RestrictionType.ALLERGY, AllergenTag.MILK);
        List<DietRecommendationCandidate> result = candidatePool.load(List.of(milkRestriction), true).foods();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("non-strict 모드에서는 profile 검증만으로 통과한다")
    void load_nonStrictMode_passesWithProfileAloneWithoutHighConfidenceTag() {
        FoodCatalog plainRice = food(1L, "백미밥", FoodCategory.GRAIN, 130.0);
        givenCatalogCandidates(List.of(plainRice));
        given(foodAllergenTagRepository.findByFoodCatalogIdIn(any()))
                .willReturn(List.of(allergenTag(1L, AllergenTag.WHEAT, AllergenConfidenceLevel.UNKNOWN)));
        given(foodAllergenProfileRepository.findByFoodCatalogIdIn(any()))
                .willReturn(List.of(allergenProfile(1L)));

        DietRestriction milkRestriction = allergenTagRestriction(RestrictionType.ALLERGY, AllergenTag.MILK);
        List<DietRecommendationCandidate> result = candidatePool.load(List.of(milkRestriction), false).foods();

        assertThat(resultFoodIds(result)).contains(plainRice.getId());
    }

    @Test
    @DisplayName("MFDS source 식품이 만료된 lastVerifiedAt을 가지면 후보에서 제외된다")
    void load_mfdsFood_staleData_excluded() {
        FoodCatalog staleFood = FoodCatalog.builder()
                .id(1L).name("구식MFDS식품").category(FoodCategory.GRAIN)
                .caloriesPer100g(100.0).proteinPer100g(3.0)
                .carbsPer100g(20.0).fatPer100g(1.0)
                .isCustom(false).usageCount(0L)
                .source(FoodCatalogSource.MFDS_FOOD_NUTRIENT_DB)
                .lastVerifiedAt(OffsetDateTime.now().minusDays(3000)) // 3000일 전 → 2년 초과
                .build();
        FoodCatalog freshFood = food(2L, "신선한식품", FoodCategory.GRAIN, 120.0);
        givenCatalogCandidates(List.of(staleFood, freshFood));
        given(foodAllergenTagRepository.findByFoodCatalogIdIn(any())).willReturn(List.of());

        List<DietRecommendationCandidate> result = candidatePool.load(List.of(), false).foods();

        assertThat(resultFoodIds(result)).doesNotContain(staleFood.getId());
        assertThat(resultFoodIds(result)).contains(freshFood.getId());
    }

    @Test
    @DisplayName("MFDS source 식품이 freshness 범위 내의 lastVerifiedAt을 가지면 후보에 포함된다")
    void load_mfdsFood_freshData_included() {
        FoodCatalog freshMfds = FoodCatalog.builder()
                .id(1L).name("최신MFDS식품").category(FoodCategory.GRAIN)
                .caloriesPer100g(100.0).proteinPer100g(3.0)
                .carbsPer100g(20.0).fatPer100g(1.0)
                .isCustom(false).usageCount(0L)
                .source(FoodCatalogSource.MFDS_STANDARD_PROCESSED)
                .lastVerifiedAt(OffsetDateTime.now().minusDays(30)) // 30일 전 → 유효
                .build();
        givenCatalogCandidates(List.of(freshMfds));
        given(foodAllergenTagRepository.findByFoodCatalogIdIn(any())).willReturn(List.of());

        List<DietRecommendationCandidate> result = candidatePool.load(List.of(), false).foods();

        assertThat(resultFoodIds(result)).contains(freshMfds.getId());
    }

    @Test
    @DisplayName("SEED source 식품은 lastVerifiedAt이 없어도 후보에 포함된다")
    void load_seedFood_nullVerifiedAt_included() {
        // food() 헬퍼가 SEED source(기본값)로 생성하고 lastVerifiedAt=null
        FoodCatalog seedFood = food(1L, "시드식품", FoodCategory.GRAIN, 130.0);
        givenCatalogCandidates(List.of(seedFood));
        given(foodAllergenTagRepository.findByFoodCatalogIdIn(any())).willReturn(List.of());

        List<DietRecommendationCandidate> result = candidatePool.load(List.of(), false).foods();

        assertThat(resultFoodIds(result)).contains(seedFood.getId());
    }

    @Test
    @DisplayName("영양 정보가 없는 식품(calories null)은 안전망에서 제외된다")
    void load_noNutritionInfo_excludedBySafetyGate() {
        FoodCatalog noCalFood = FoodCatalog.builder()
                .id(1L).name("미지정").category(FoodCategory.OTHER)
                .caloriesPer100g(null).isCustom(false)
                .build();
        FoodCatalog normalFood = food(2L, "닭가슴살", FoodCategory.PROTEIN_SOURCE, 165.0);
        givenCatalogCandidates(List.of(noCalFood, normalFood));
        given(foodAllergenTagRepository.findByFoodCatalogIdIn(any())).willReturn(List.of());

        List<DietRecommendationCandidate> result = candidatePool.load(List.of(), false).foods();

        assertThat(resultFoodIds(result)).doesNotContain(noCalFood.getId());
        assertThat(resultFoodIds(result)).contains(normalFood.getId());
    }

    @Test
    @DisplayName("4종 매크로가 모두 있는 식품은 macroDataComplete=true로 변환된다")
    void load_allMacrosPresent_macroDataCompleteIsTrue() {
        FoodCatalog complete = FoodCatalog.builder()
                .id(1L).name("닭가슴살").category(FoodCategory.PROTEIN_SOURCE)
                .caloriesPer100g(165.0).proteinPer100g(31.0)
                .carbsPer100g(0.0).fatPer100g(3.6)
                .isCustom(false).usageCount(10L)
                .build();
        givenCatalogCandidates(List.of(complete));
        given(foodAllergenTagRepository.findByFoodCatalogIdIn(any())).willReturn(List.of());

        List<DietRecommendationCandidate> result = candidatePool.load(List.of(), false).foods();

        assertThat(result).singleElement()
                .extracting(DietRecommendationCandidate::macroDataComplete)
                .isEqualTo(true);
    }

    @Test
    @DisplayName("매크로 중 하나라도 null인 식품은 macroDataComplete=false로 변환된다")
    void load_anyMacroNull_macroDataCompleteIsFalse() {
        FoodCatalog incomplete = FoodCatalog.builder()
                .id(1L).name("미검증식품").category(FoodCategory.OTHER)
                .caloriesPer100g(200.0).proteinPer100g(null)
                .carbsPer100g(null).fatPer100g(null)
                .isCustom(false).usageCount(0L)
                .build();
        givenCatalogCandidates(List.of(incomplete));
        given(foodAllergenTagRepository.findByFoodCatalogIdIn(any())).willReturn(List.of());

        List<DietRecommendationCandidate> result = candidatePool.load(List.of(), false).foods();

        assertThat(result).singleElement()
                .extracting(DietRecommendationCandidate::macroDataComplete)
                .isEqualTo(false);
    }

    @Test
    @DisplayName("제공량 옵션이 있는 식품은 hasVerifiedServingOptions=true가 되고 옵션이 포함된다")
    void load_withVerifiedServingOption_hasVerifiedServingOptionsTrue() {
        FoodCatalog chicken = food(1L, "닭가슴살", FoodCategory.PROTEIN_SOURCE, 165.0);
        givenCatalogCandidates(List.of(chicken));
        given(foodAllergenTagRepository.findByFoodCatalogIdIn(any())).willReturn(List.of());
        FoodServingOption verifiedOption = FoodServingOption.builder()
                .id(1L).foodCatalogId(1L)
                .label("1회 제공량").equivalentG(150.0).sortOrder(0)
                .servingType(FoodServingOption.ServingType.OFFICIAL_SERVING)
                .verifiedAt(java.time.OffsetDateTime.now().minusDays(1))
                .build();
        given(foodServingOptionRepository.findByFoodCatalogIdIn(any())).willReturn(List.of(verifiedOption));

        List<DietRecommendationCandidate> result = candidatePool.load(List.of(), false).foods();

        assertThat(result).singleElement().satisfies(candidate -> {
            assertThat(candidate.hasVerifiedServingOptions()).isTrue();
            assertThat(candidate.servingOptions()).hasSize(1);
            assertThat(candidate.servingOptions().get(0).equivalentG()).isEqualTo(150.0);
        });
    }

    @Test
    @DisplayName("제공량 옵션이 없는 식품은 hasVerifiedServingOptions=false이고 빈 리스트다")
    void load_withoutServingOption_hasVerifiedServingOptionsFalse() {
        FoodCatalog chicken = food(1L, "닭가슴살", FoodCategory.PROTEIN_SOURCE, 165.0);
        givenCatalogCandidates(List.of(chicken));
        given(foodAllergenTagRepository.findByFoodCatalogIdIn(any())).willReturn(List.of());
        given(foodServingOptionRepository.findByFoodCatalogIdIn(any())).willReturn(List.of());

        List<DietRecommendationCandidate> result = candidatePool.load(List.of(), false).foods();

        assertThat(result).singleElement().satisfies(candidate -> {
            assertThat(candidate.hasVerifiedServingOptions()).isFalse();
            assertThat(candidate.servingOptions()).isEmpty();
        });
    }

    @Test
    @DisplayName("canonical 그룹 비대표 식품(canonicalGroupId != null)은 후보에서 제외된다")
    void load_nonCanonicalFood_excluded() {
        FoodCatalog representative = food(1L, "흰쌀밥", FoodCategory.GRAIN, 130.0);
        FoodCatalog nonRepresentative = FoodCatalog.builder()
                .id(2L).name("쌀밥(비대표)").category(FoodCategory.GRAIN)
                .caloriesPer100g(132.0).proteinPer100g(2.5)
                .carbsPer100g(29.0).fatPer100g(0.3)
                .isCustom(false).usageCount(0L)
                .canonicalGroupId(1L) // 그룹 비대표 → 제외 대상
                .build();
        givenCatalogCandidates(List.of(representative, nonRepresentative));
        given(foodAllergenTagRepository.findByFoodCatalogIdIn(any())).willReturn(List.of());

        List<DietRecommendationCandidate> result = candidatePool.load(List.of(), false).foods();

        assertThat(resultFoodIds(result)).contains(representative.getId());
        assertThat(resultFoodIds(result)).doesNotContain(nonRepresentative.getId());
    }

    @Test
    @DisplayName("후보 풀은 엔진에 필요한 알러젠 신뢰도와 caution을 후보 값으로 변환한다")
    void load_mapsCatalogsToEngineCandidates() {
        FoodCatalog cautionFood = FoodCatalog.builder()
                .id(1L).name("와퍼").category(FoodCategory.PROCESSED)
                .caloriesPer100g(300.0).proteinPer100g(15.0)
                .carbsPer100g(25.0).fatPer100g(18.0)
                .recommendationStatus(RECOMMENDABLE_WITH_CAUTION)
                .recommendationReason("고지방·고나트륨")
                .isCustom(false).usageCount(5L)
                .build();
        givenCatalogCandidates(List.of(cautionFood));
        given(foodAllergenTagRepository.findByFoodCatalogIdIn(any()))
                .willReturn(List.of(allergenTag(1L, AllergenTag.MILK, AllergenConfidenceLevel.LABEL_DERIVED)));

        List<DietRecommendationCandidate> result = candidatePool.load(List.of(), false).foods();

        assertThat(result).singleElement().satisfies(candidate -> {
            assertThat(candidate.foodCatalogId()).isEqualTo(1L);
            assertThat(candidate.name()).isEqualTo("와퍼");
            assertThat(candidate.category()).isEqualTo(FoodCategory.PROCESSED);
            assertThat(candidate.allergenConfidenceLevel()).isEqualTo(AllergenConfidenceLevel.LABEL_DERIVED);
            assertThat(candidate.caution()).isEqualTo("고지방·고나트륨");
        });
    }

    @SuppressWarnings("unchecked")
    private void givenCatalogCandidates(List<FoodCatalog> candidates) {
        given(foodCatalogRepository.findAll(any(Specification.class), any(Sort.class)))
                .willReturn(candidates);
    }

    @SuppressWarnings("unchecked")
    private Specification<FoodCatalog> captureCandidateSpec() {
        ArgumentCaptor<Specification<FoodCatalog>> specCaptor = ArgumentCaptor.forClass(Specification.class);
        verify(foodCatalogRepository).findAll(specCaptor.capture(), any(Sort.class));
        return specCaptor.getValue();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Collection<RecommendationStatus> extractRecommendationStatuses(Specification<FoodCatalog> spec) {
        Root root = mock(Root.class);
        CriteriaQuery query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path caloriesPath = mock(Path.class);
        Path statusPath = mock(Path.class);
        Predicate caloriesPredicate = mock(Predicate.class);
        Predicate statusPredicate = mock(Predicate.class);
        Predicate combinedPredicate = mock(Predicate.class);

        given(root.get("caloriesPer100g")).willReturn(caloriesPath);
        given(root.get("recommendationStatus")).willReturn(statusPath);
        given(cb.isNotNull(caloriesPath)).willReturn(caloriesPredicate);
        given(statusPath.in(any(Collection.class))).willReturn(statusPredicate);
        given(cb.and(caloriesPredicate, statusPredicate)).willReturn(combinedPredicate);

        spec.toPredicate(root, query, cb);

        ArgumentCaptor<Collection<RecommendationStatus>> statusCaptor =
                ArgumentCaptor.forClass(Collection.class);
        verify(statusPath).in(statusCaptor.capture());
        return statusCaptor.getValue();
    }

    private FoodCatalog food(Long id, String name, FoodCategory category, double calories) {
        return FoodCatalog.builder()
                .id(id).name(name).category(category)
                .caloriesPer100g(calories).proteinPer100g(20.0)
                .carbsPer100g(10.0).fatPer100g(5.0)
                .isCustom(false).usageCount((long) (11 - id))
                .build();
    }

    private List<Long> resultFoodIds(List<DietRecommendationCandidate> candidates) {
        return candidates.stream()
                .map(DietRecommendationCandidate::foodCatalogId)
                .toList();
    }

    private DietRestriction foodRestriction(Long id, RestrictionType type, Long foodCatalogId) {
        return DietRestriction.builder()
                .id(id).userId(1L).restrictionType(type)
                .targetType(TargetType.FOOD).foodCatalogId(foodCatalogId)
                .build();
    }

    private DietRestriction categoryRestriction(RestrictionType type, FoodCategory category) {
        return DietRestriction.builder()
                .id(1L).userId(1L).restrictionType(type)
                .targetType(TargetType.CATEGORY).category(category)
                .build();
    }

    private DietRestriction keywordRestriction(String keyword) {
        return DietRestriction.builder()
                .id(1L).userId(1L).restrictionType(RestrictionType.AVOID)
                .targetType(TargetType.KEYWORD).keyword(keyword)
                .build();
    }

    private DietRestriction allergenTagRestriction(RestrictionType type, AllergenTag tag) {
        return DietRestriction.builder()
                .id(1L).userId(1L).restrictionType(type)
                .targetType(TargetType.ALLERGEN_TAG).allergenTag(tag)
                .build();
    }

    private FoodAllergenTag allergenTag(
            Long foodCatalogId,
            AllergenTag tag,
            AllergenConfidenceLevel level) {
        return allergenTag(foodCatalogId, tag, level, false);
    }

    private FoodAllergenTag allergenTag(
            Long foodCatalogId,
            AllergenTag tag,
            AllergenConfidenceLevel level,
            boolean allergenProfileVerified) {
        return FoodAllergenTag.builder()
                .foodCatalogId(foodCatalogId)
                .allergenTag(tag)
                .confidenceLevel(level)
                .source(AllergenDataSource.MFDS_CLASS)
                .allergenProfileVerified(allergenProfileVerified)
                .build();
    }

    private FoodAllergenProfile allergenProfile(Long foodCatalogId) {
        return FoodAllergenProfile.builder()
                .foodCatalogId(foodCatalogId)
                .confidenceLevel(AllergenConfidenceLevel.LABEL_DERIVED)
                .source(AllergenDataSource.BRAND_OFFICIAL)
                .standardVersion("KR-22-2026")
                .reviewedAt(OffsetDateTime.now().minusDays(1))
                .validUntil(OffsetDateTime.now().plusDays(1))
                .build();
    }
}
