package com.healthcare.domain.diet.admin;

import com.healthcare.domain.diet.allergen.repository.FoodAllergenTagRepository;
import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.entity.FoodCatalogSource;
import com.healthcare.domain.diet.entity.FoodServingOption;
import com.healthcare.domain.diet.entity.RecommendationStatus;
import com.healthcare.domain.diet.repository.FoodCatalogRepository;
import com.healthcare.domain.diet.repository.FoodCatalogSpecs;
import com.healthcare.domain.diet.repository.FoodServingOptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * SEARCH_ONLY 카탈로그 중 추천 후보로 승격할 가치가 큰 row를 보여주는 운영 큐.
 * 자동 승격은 하지 않고, 큐레이션 CSV 작성 대상을 좁히는 데만 사용한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendationCurationQueueService {

    private final FoodCatalogRepository foodCatalogRepository;
    private final FoodServingOptionRepository servingOptionRepository;
    private final FoodAllergenTagRepository allergenTagRepository;

    public List<RecommendationCurationQueueEntry> queue(int limit) {
        List<FoodCatalog> foods = foodCatalogRepository.findAll(
                FoodCatalogSpecs.hasRecommendationStatus(RecommendationStatus.SEARCH_ONLY)
                        .and(FoodCatalogSpecs.hasCalories())
                        .and(FoodCatalogSpecs.isCanonicalCandidate()),
                Sort.unsorted()
        ).stream()
                .filter(food -> food.getSource() != FoodCatalogSource.USER_CUSTOM)
                .toList();

        List<Long> foodIds = foods.stream()
                .map(FoodCatalog::getId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, List<FoodServingOption>> optionsByFoodId = servingOptionRepository.findByFoodCatalogIdIn(foodIds)
                .stream()
                .collect(Collectors.groupingBy(FoodServingOption::getFoodCatalogId));
        Set<Long> taggedFoodIds = Set.copyOf(allergenTagRepository.findFoodIdsWithAnyAllergenTag());

        return foods.stream()
                .map(food -> toEntry(food, optionsByFoodId, taggedFoodIds))
                .sorted((a, b) -> Double.compare(b.priorityScore(), a.priorityScore()))
                .limit(limit)
                .toList();
    }

    private RecommendationCurationQueueEntry toEntry(
            FoodCatalog food,
            Map<Long, List<FoodServingOption>> optionsByFoodId,
            Set<Long> taggedFoodIds
    ) {
        boolean macroComplete = isMacroComplete(food);
        boolean hasVerifiedServingOption = optionsByFoodId.getOrDefault(food.getId(), List.of())
                .stream()
                .anyMatch(FoodServingOption::isVerified);
        boolean hasAllergenTags = food.getId() != null && taggedFoodIds.contains(food.getId());
        double score = priorityScore(food, macroComplete, hasVerifiedServingOption, hasAllergenTags);
        return new RecommendationCurationQueueEntry(
                food.getId(),
                food.getSource(),
                food.getFoodCode(),
                food.getNameKo() != null ? food.getNameKo() : food.getName(),
                food.getCategory(),
                food.getUsageCount() != null ? food.getUsageCount() : 0L,
                macroComplete,
                hasVerifiedServingOption,
                hasAllergenTags,
                score
        );
    }

    private double priorityScore(
            FoodCatalog food,
            boolean macroComplete,
            boolean hasVerifiedServingOption,
            boolean hasAllergenTags
    ) {
        long usageCount = food.getUsageCount() != null ? food.getUsageCount() : 0L;
        double readiness = 0.0;
        if (macroComplete && hasVerifiedServingOption) {
            readiness += 10_000.0;
        } else {
            if (macroComplete) readiness += 1_000.0;
            if (hasVerifiedServingOption) readiness += 1_000.0;
        }
        if (!hasAllergenTags) readiness += 200.0;
        return readiness + usageCount;
    }

    private boolean isMacroComplete(FoodCatalog food) {
        return food.getCaloriesPer100g() != null
                && food.getProteinPer100g() != null
                && food.getCarbsPer100g() != null
                && food.getFatPer100g() != null;
    }
}
