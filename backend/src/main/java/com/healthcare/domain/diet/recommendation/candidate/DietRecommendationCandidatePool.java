package com.healthcare.domain.diet.recommendation.candidate;

import com.healthcare.domain.diet.allergen.AllergenContext;
import com.healthcare.domain.diet.allergen.AllergenSafetyGate;
import com.healthcare.domain.diet.allergen.AllergenTag;
import com.healthcare.domain.diet.allergen.SafetyDecision;
import com.healthcare.domain.diet.allergen.entity.FoodAllergenProfile;
import com.healthcare.domain.diet.allergen.entity.FoodAllergenTag;
import com.healthcare.domain.diet.allergen.repository.FoodAllergenProfileRepository;
import com.healthcare.domain.diet.allergen.repository.FoodAllergenTagRepository;
import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.entity.FoodServingOption;
import com.healthcare.domain.diet.policy.DataFreshnessPolicy;
import com.healthcare.domain.diet.repository.FoodCatalogRepository;
import com.healthcare.domain.diet.repository.FoodCatalogSpecs;
import com.healthcare.domain.diet.repository.FoodServingOptionRepository;
import com.healthcare.domain.diet.restriction.entity.DietRestriction;
import com.healthcare.domain.diet.restriction.entity.DietRestriction.TargetType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 식단 추천 후보 풀 Module.
 * 추천 적합성 상태, 제한 조건, 알러젠 안전 게이트를 통과한 식품 카탈로그 후보를 제공한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DietRecommendationCandidatePool {

    private final FoodCatalogRepository foodCatalogRepository;
    private final FoodAllergenTagRepository foodAllergenTagRepository;
    private final FoodAllergenProfileRepository foodAllergenProfileRepository;
    private final FoodServingOptionRepository foodServingOptionRepository;
    private final AllergenSafetyGate allergenSafetyGate;
    private final DataFreshnessPolicy dataFreshnessPolicy;

    public DietRecommendationCandidates load(
            List<DietRestriction> restrictions,
            boolean strictAllergyMode
    ) {
        CandidateRestrictions parsed = CandidateRestrictions.from(restrictions, strictAllergyMode);
        List<FoodCatalog> catalogCandidates = loadCatalogCandidates(parsed);
        Map<Long, List<FoodAllergenTag>> tagsByFoodId = loadTags(catalogCandidates);
        Map<Long, FoodAllergenProfile> profilesByFoodId = loadProfiles(catalogCandidates, parsed);
        Map<Long, List<FoodServingOption>> optionsByFoodId = loadServingOptions(catalogCandidates);

        // DB Spec이 foodIds·categories·canonical을 먼저 제거하고, 아래는 Mock 환경 안전망 겸
        // 실패 분류용 단계별 진단 집계다(계획 리뷰 P0-3).
        // keywords·allergen·freshness는 DB Spec 대응이 없어 인메모리만 적용한다.
        List<DietRecommendationCandidate> candidates = new java.util.ArrayList<>();
        int restrictionFiltered = 0;
        int freshnessExpired = 0;
        int incompleteMacro = 0;
        int allergenRejected = 0;

        for (FoodCatalog food : catalogCandidates) {
            if (food.getCaloriesPer100g() == null) {
                incompleteMacro++;
                continue;
            }
            if (food.getCanonicalGroupId() != null) {
                // canonical 비대표 후보는 dedup 대상이라 사유로 집계하지 않는다.
                continue;
            }
            if (parsed.foodIds().contains(food.getId())
                    || parsed.categories().contains(food.getCategory())
                    || matchesKeyword(food, parsed.keywords())) {
                restrictionFiltered++;
                continue;
            }
            if (!dataFreshnessPolicy.isCurrent(food)) {
                freshnessExpired++;
                continue;
            }

            AllergenContext ctx = new AllergenContext(
                    food.getId(),
                    parsed.allergenTags(),
                    tagsByFoodId.getOrDefault(food.getId(), List.of()),
                    profilesByFoodId.get(food.getId()),
                    parsed.strictAllergyMode()
            );
            SafetyDecision decision = allergenSafetyGate.decide(ctx);
            if (!decision.passes()) {
                allergenRejected++;
                continue;
            }

            DietRecommendationCandidate candidate = DietRecommendationCandidate.from(
                    food,
                    decision.confidence(),
                    optionsByFoodId.getOrDefault(food.getId(), List.of()));
            // 핵심 매크로 결측 후보는 풀에 포함하되(엔진이 hard filter), 진단에는 결측으로 집계한다.
            if (!candidate.macroDataComplete()) {
                incompleteMacro++;
            }
            candidates.add(candidate);
        }

        CandidatePoolDiagnostics diagnostics = new CandidatePoolDiagnostics(
                candidates.size(), restrictionFiltered, freshnessExpired, incompleteMacro, allergenRejected);
        return new DietRecommendationCandidates(List.copyOf(candidates), diagnostics);
    }

    private List<FoodCatalog> loadCatalogCandidates(CandidateRestrictions restrictions) {
        Specification<FoodCatalog> spec = FoodCatalogSpecs.hasCalories()
                .and(FoodCatalogSpecs.hasRecommendationCandidateStatus())
                .and(FoodCatalogSpecs.isCanonicalCandidate());
        if (!restrictions.foodIds().isEmpty()) {
            spec = spec.and(FoodCatalogSpecs.idNotIn(restrictions.foodIds()));
        }
        if (!restrictions.categories().isEmpty()) {
            spec = spec.and(FoodCatalogSpecs.categoryNotIn(restrictions.categories()));
        }
        return foodCatalogRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "usageCount"));
    }

    private Map<Long, List<FoodAllergenTag>> loadTags(List<FoodCatalog> catalogCandidates) {
        List<Long> foodIds = catalogCandidates.stream()
                .map(FoodCatalog::getId)
                .filter(Objects::nonNull)
                .toList();
        if (foodIds.isEmpty()) {
            return Map.of();
        }
        return foodAllergenTagRepository.findByFoodCatalogIdIn(foodIds)
                .stream()
                .collect(Collectors.groupingBy(FoodAllergenTag::getFoodCatalogId));
    }

    private Map<Long, FoodAllergenProfile> loadProfiles(
            List<FoodCatalog> catalogCandidates,
            CandidateRestrictions restrictions
    ) {
        if (restrictions.allergenTags().isEmpty()) {
            return Map.of();
        }
        List<Long> foodIds = catalogCandidates.stream()
                .map(FoodCatalog::getId)
                .filter(Objects::nonNull)
                .toList();
        if (foodIds.isEmpty()) {
            return Map.of();
        }
        return foodAllergenProfileRepository.findByFoodCatalogIdIn(foodIds).stream()
                .collect(Collectors.toMap(FoodAllergenProfile::getFoodCatalogId, profile -> profile));
    }

    private Map<Long, List<FoodServingOption>> loadServingOptions(List<FoodCatalog> catalogCandidates) {
        List<Long> foodIds = catalogCandidates.stream()
                .map(FoodCatalog::getId)
                .filter(Objects::nonNull)
                .toList();
        if (foodIds.isEmpty()) {
            return Map.of();
        }
        return foodServingOptionRepository.findByFoodCatalogIdIn(foodIds)
                .stream()
                .collect(Collectors.groupingBy(FoodServingOption::getFoodCatalogId));
    }

    private boolean matchesKeyword(FoodCatalog food, List<String> keywords) {
        if (keywords.isEmpty()) {
            return false;
        }
        String nameLower = lower(food.getName());
        String nameKoLower = lower(food.getNameKo());
        return keywords.stream().anyMatch(keyword ->
                nameLower.contains(keyword) || nameKoLower.contains(keyword));
    }

    private String lower(String value) {
        return value != null ? value.toLowerCase(Locale.ROOT) : "";
    }

    private record CandidateRestrictions(
            Set<Long> foodIds,
            Set<FoodCategory> categories,
            List<String> keywords,
            Set<AllergenTag> allergenTags,
            boolean strictAllergyMode
    ) {
        static CandidateRestrictions from(List<DietRestriction> restrictions, boolean strictAllergyMode) {
            return new CandidateRestrictions(
                    restrictions.stream()
                            .filter(restriction -> restriction.getTargetType() == TargetType.FOOD)
                            .map(DietRestriction::getFoodCatalogId)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toUnmodifiableSet()),
                    restrictions.stream()
                            .filter(restriction -> restriction.getTargetType() == TargetType.CATEGORY)
                            .map(DietRestriction::getCategory)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toUnmodifiableSet()),
                    restrictions.stream()
                            .filter(restriction -> restriction.getTargetType() == TargetType.KEYWORD)
                            .map(DietRestriction::getKeyword)
                            .filter(Objects::nonNull)
                            .map(keyword -> keyword.toLowerCase(Locale.ROOT))
                            .toList(),
                    restrictions.stream()
                            .filter(restriction -> restriction.getTargetType() == TargetType.ALLERGEN_TAG)
                            .map(DietRestriction::getAllergenTag)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toUnmodifiableSet()),
                    strictAllergyMode
            );
        }
    }
}
