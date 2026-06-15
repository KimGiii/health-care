package com.healthcare.domain.diet.recommendation.candidate;

import com.healthcare.domain.diet.allergen.AllergenConfidenceGate;
import com.healthcare.domain.diet.allergen.AllergenTag;
import com.healthcare.domain.diet.allergen.entity.FoodAllergenTag;
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

/**
 * 식단 추천 후보 풀 Module.
 * 추천 적합성 상태, 제한 조건, 알러젠 신뢰 게이트를 통과한 식품 카탈로그 후보를 제공한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DietRecommendationCandidatePool {

    private final FoodCatalogRepository foodCatalogRepository;
    private final FoodAllergenTagRepository foodAllergenTagRepository;
    private final AllergenConfidenceGate allergenGate;

    public DietRecommendationCandidates load(
            List<DietRestriction> restrictions,
            boolean strictAllergyMode
    ) {
        CandidateRestrictions parsedRestrictions = CandidateRestrictions.from(restrictions);
        List<FoodCatalog> catalogCandidates = loadCatalogCandidates(parsedRestrictions);
        Map<Long, List<FoodAllergenTag>> tagsByFoodId = loadTags(catalogCandidates);
        List<FoodCatalog> filtered = applyRuntimeGate(
                catalogCandidates,
                parsedRestrictions,
                tagsByFoodId,
                strictAllergyMode
        );
        return new DietRecommendationCandidates(toRecommendationCandidates(filtered, tagsByFoodId));
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

    private List<FoodCatalog> applyRuntimeGate(
            List<FoodCatalog> candidates,
            CandidateRestrictions restrictions,
            Map<Long, List<FoodAllergenTag>> tagsByFoodId,
            boolean strictAllergyMode
    ) {
        return candidates.stream()
                .filter(food -> food.getCaloriesPer100g() != null)
                .filter(food -> !restrictions.foodIds().contains(food.getId()))
                .filter(food -> !restrictions.categories().contains(food.getCategory()))
                .filter(food -> !matchesKeyword(food, restrictions.keywords()))
                .filter(food -> !allergenGate.containsAllergen(
                        food.getId(),
                        restrictions.allergenTags(),
                        tagsByFoodId
                ))
                .filter(food -> allergenGate.passesGate(
                        food.getId(),
                        restrictions.allergenTags(),
                        tagsByFoodId,
                        strictAllergyMode
                ))
                .toList();
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

    private List<DietRecommendationCandidate> toRecommendationCandidates(
            List<FoodCatalog> foods,
            Map<Long, List<FoodAllergenTag>> tagsByFoodId
    ) {
        return foods.stream()
                .map(food -> DietRecommendationCandidate.from(
                        food,
                        allergenGate.resolveConfidence(food.getId(), tagsByFoodId)
                ))
                .toList();
    }

    private record CandidateRestrictions(
            Set<Long> foodIds,
            Set<FoodCategory> categories,
            List<String> keywords,
            Set<AllergenTag> allergenTags
    ) {
        static CandidateRestrictions from(List<DietRestriction> restrictions) {
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
                            .collect(Collectors.toUnmodifiableSet())
            );
        }
    }
}
