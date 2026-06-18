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
import com.healthcare.domain.diet.repository.FoodCatalogRepository;
import com.healthcare.domain.diet.repository.FoodCatalogSpecs;
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
import java.util.stream.Stream;

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
    private final AllergenSafetyGate allergenSafetyGate;

    public DietRecommendationCandidates load(
            List<DietRestriction> restrictions,
            boolean strictAllergyMode
    ) {
        CandidateRestrictions parsed = CandidateRestrictions.from(restrictions, strictAllergyMode);
        List<FoodCatalog> catalogCandidates = loadCatalogCandidates(parsed);
        Map<Long, List<FoodAllergenTag>> tagsByFoodId = loadTags(catalogCandidates);
        Map<Long, FoodAllergenProfile> profilesByFoodId = loadProfiles(catalogCandidates, parsed);

        List<DietRecommendationCandidate> candidates = catalogCandidates.stream()
                .filter(food -> food.getCaloriesPer100g() != null)
                .filter(food -> !parsed.foodIds().contains(food.getId()))
                .filter(food -> !parsed.categories().contains(food.getCategory()))
                .filter(food -> !matchesKeyword(food, parsed.keywords()))
                .flatMap(food -> {
                    AllergenContext ctx = new AllergenContext(
                            food.getId(),
                            parsed.allergenTags(),
                            tagsByFoodId.getOrDefault(food.getId(), List.of()),
                            profilesByFoodId.get(food.getId()),
                            parsed.strictAllergyMode()
                    );
                    SafetyDecision decision = allergenSafetyGate.decide(ctx);
                    return decision.passes()
                            ? Stream.of(DietRecommendationCandidate.from(food, decision.confidence()))
                            : Stream.empty();
                })
                .toList();

        return new DietRecommendationCandidates(candidates);
    }

    private List<FoodCatalog> loadCatalogCandidates(CandidateRestrictions restrictions) {
        Specification<FoodCatalog> spec = FoodCatalogSpecs.hasCalories()
                .and(FoodCatalogSpecs.hasRecommendationCandidateStatus());
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
