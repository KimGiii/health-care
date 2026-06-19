package com.healthcare.domain.diet.admin;

import com.healthcare.domain.diet.allergen.repository.FoodAllergenTagRepository;
import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.repository.FoodCatalogRepository;
import com.healthcare.domain.diet.repository.FoodCatalogSpecs;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 검증 우선순위 리포트.
 * 인기 식품 중 macro/allergen 검증이 부족하고, 후보가 부족한 역할일수록 우선 제시한다.
 *
 * <p>priorityScore = usageCount × macroWeight × allergenWeight × coverageWeight
 * <ul>
 *   <li>macroWeight   : macro 불완전 → 2, 완전 → 1</li>
 *   <li>allergenWeight: allergenTag 없음 → 2, 있음 → 1</li>
 *   <li>coverageWeight: 후보가 부족한 카테고리 → 2, 충분 → 1 (계획 §5.3 incremental feasibility coverage 근사)</li>
 * </ul>
 * 유사 식품을 반복 검증하기보다 추천 후보가 부족한 역할의 검증 실패를 먼저 줄이기 위함이다.
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

        Map<FoodCategory, Long> countByCategory = candidates.stream()
                .collect(Collectors.groupingBy(FoodCatalog::getCategory, Collectors.counting()));

        return candidates.stream()
                .map(food -> toEntry(food, taggedFoodIds, countByCategory))
                .sorted((a, b) -> Double.compare(b.priorityScore(), a.priorityScore()))
                .limit(limit)
                .toList();
    }

    private VerificationPriorityEntry toEntry(
            FoodCatalog food, Set<Long> taggedFoodIds, Map<FoodCategory, Long> countByCategory) {
        double macroWeight = isMacroComplete(food) ? 1.0 : 2.0;
        double allergenWeight = (food.getId() != null && taggedFoodIds.contains(food.getId())) ? 1.0 : 2.0;
        double coverageWeight = isUnderrepresented(food, countByCategory) ? 2.0 : 1.0;
        double score = food.getUsageCount() * macroWeight * allergenWeight * coverageWeight;
        return new VerificationPriorityEntry(
                food.getId(), food.getName(), food.getCategory(), food.getUsageCount(), score);
    }

    private boolean isUnderrepresented(FoodCatalog food, Map<FoodCategory, Long> countByCategory) {
        return countByCategory.getOrDefault(food.getCategory(), 0L)
                < CandidatePoolSummary.UNDERREPRESENTED_THRESHOLD;
    }

    private boolean isMacroComplete(FoodCatalog food) {
        return food.getCaloriesPer100g() != null
                && food.getProteinPer100g() != null
                && food.getCarbsPer100g() != null
                && food.getFatPer100g() != null;
    }
}
