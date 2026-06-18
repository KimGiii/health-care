package com.healthcare.domain.diet.admin;

import com.healthcare.domain.diet.allergen.repository.FoodAllergenTagRepository;
import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.repository.FoodCatalogRepository;
import com.healthcare.domain.diet.repository.FoodCatalogSpecs;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * 검증 우선순위 리포트.
 * 인기 식품 중 macro/allergen 검증이 부족한 순으로 운영자에게 제시한다.
 *
 * priorityScore = usageCount × macroWeight × allergenWeight
 *   macroWeight   : macro 불완전 → 2, 완전 → 1
 *   allergenWeight: allergenTag 없음 → 2, 있음 → 1
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VerificationPriorityService {

    private final FoodCatalogRepository foodCatalogRepository;
    private final FoodAllergenTagRepository foodAllergenTagRepository;

    public List<VerificationPriorityEntry> topPriorities(int limit) {
        List<FoodCatalog> candidates = foodCatalogRepository.findAll(
                FoodCatalogSpecs.hasRecommendationCandidateStatus()
                        .and(FoodCatalogSpecs.isCanonicalCandidate()),
                Sort.unsorted()
        );

        Set<Long> taggedFoodIds = Set.copyOf(foodAllergenTagRepository.findFoodIdsWithAnyAllergenTag());

        return candidates.stream()
                .map(food -> toEntry(food, taggedFoodIds))
                .sorted((a, b) -> Double.compare(b.priorityScore(), a.priorityScore()))
                .limit(limit)
                .toList();
    }

    private VerificationPriorityEntry toEntry(FoodCatalog food, Set<Long> taggedFoodIds) {
        double macroWeight = isMacroComplete(food) ? 1.0 : 2.0;
        double allergenWeight = (food.getId() != null && taggedFoodIds.contains(food.getId())) ? 1.0 : 2.0;
        double score = food.getUsageCount() * macroWeight * allergenWeight;
        return new VerificationPriorityEntry(
                food.getId(), food.getName(), food.getCategory(), food.getUsageCount(), score);
    }

    private boolean isMacroComplete(FoodCatalog food) {
        return food.getCaloriesPer100g() != null
                && food.getProteinPer100g() != null
                && food.getCarbsPer100g() != null
                && food.getFatPer100g() != null;
    }
}
