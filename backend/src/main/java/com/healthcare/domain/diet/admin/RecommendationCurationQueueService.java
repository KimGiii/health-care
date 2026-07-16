package com.healthcare.domain.diet.admin;

import com.healthcare.domain.diet.allergen.entity.FoodAllergenProfile;
import com.healthcare.domain.diet.allergen.repository.FoodAllergenProfileRepository;
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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 추천 후보로 승격할 가치가 큰 SEARCH_ONLY row와, 이미 추천 후보지만 알러젠 프로필이
 * 부족해 런타임에서 탈락할 수 있는 row를 보여주는 운영 큐. 자동 승격은 하지 않고,
 * 큐레이션 CSV 작성 대상을 좁히는 데만 사용한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendationCurationQueueService {

    private static final Set<RecommendationStatus> CURATION_STATUSES = Set.of(
            RecommendationStatus.SEARCH_ONLY,
            RecommendationStatus.RECOMMENDABLE,
            RecommendationStatus.RECOMMENDABLE_WITH_CAUTION
    );

    private final FoodCatalogRepository foodCatalogRepository;
    private final FoodServingOptionRepository servingOptionRepository;
    private final FoodAllergenTagRepository allergenTagRepository;
    private final FoodAllergenProfileRepository allergenProfileRepository;

    public List<RecommendationCurationQueueEntry> queue(int limit) {
        List<FoodCatalog> foods = foodCatalogRepository.findAll(
                FoodCatalogSpecs.hasCalories()
                        .and(FoodCatalogSpecs.hasRecommendationStatusIn(CURATION_STATUSES))
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
        OffsetDateTime now = OffsetDateTime.now();
        Set<Long> verifiedProfileFoodIds = allergenProfileRepository.findByFoodCatalogIdIn(foodIds)
                .stream()
                .filter(profile -> profile.isVerifiedAt(now))
                .map(FoodAllergenProfile::getFoodCatalogId)
                .collect(Collectors.toUnmodifiableSet());

        return foods.stream()
                .map(food -> toEntry(food, optionsByFoodId, taggedFoodIds, verifiedProfileFoodIds))
                .filter(this::needsCuration)
                .sorted((a, b) -> Double.compare(b.priorityScore(), a.priorityScore()))
                .limit(limit)
                .toList();
    }

    private RecommendationCurationQueueEntry toEntry(
            FoodCatalog food,
            Map<Long, List<FoodServingOption>> optionsByFoodId,
            Set<Long> taggedFoodIds,
            Set<Long> verifiedProfileFoodIds
    ) {
        boolean macroComplete = isMacroComplete(food);
        boolean hasVerifiedServingOption = optionsByFoodId.getOrDefault(food.getId(), List.of())
                .stream()
                .anyMatch(FoodServingOption::isVerified);
        boolean hasAllergenTags = food.getId() != null && taggedFoodIds.contains(food.getId());
        boolean hasVerifiedAllergenProfile = food.getId() != null && verifiedProfileFoodIds.contains(food.getId());
        boolean allergenProfileGap = food.getRecommendationStatus() != RecommendationStatus.SEARCH_ONLY
                && !hasVerifiedAllergenProfile;
        double proteinGPer100Kcal = proteinGPer100Kcal(food);
        String curationLane = curationLane(
                food, macroComplete, hasVerifiedServingOption, hasVerifiedAllergenProfile, proteinGPer100Kcal);
        double score = priorityScore(food, macroComplete, hasVerifiedServingOption, hasAllergenTags,
                hasVerifiedAllergenProfile, proteinGPer100Kcal);
        return new RecommendationCurationQueueEntry(
                food.getId(),
                food.getSource(),
                food.getFoodCode(),
                food.getNameKo() != null ? food.getNameKo() : food.getName(),
                food.getCategory(),
                food.getRecommendationStatus(),
                food.getUsageCount() != null ? food.getUsageCount() : 0L,
                food.getCaloriesPer100g(),
                food.getProteinPer100g(),
                round(proteinGPer100Kcal),
                curationLane,
                macroComplete,
                hasVerifiedServingOption,
                hasAllergenTags,
                hasVerifiedAllergenProfile,
                allergenProfileGap,
                score
        );
    }

    private boolean needsCuration(RecommendationCurationQueueEntry entry) {
        return entry.recommendationStatus() == RecommendationStatus.SEARCH_ONLY
                || entry.allergenProfileGap();
    }

    private double priorityScore(
            FoodCatalog food,
            boolean macroComplete,
            boolean hasVerifiedServingOption,
            boolean hasAllergenTags,
            boolean hasVerifiedAllergenProfile,
            double proteinGPer100Kcal
    ) {
        long usageCount = food.getUsageCount() != null ? food.getUsageCount() : 0L;
        double readiness = 0.0;
        boolean recommendationCandidate = food.getRecommendationStatus().isRecommendationCandidate();
        boolean profileGap = recommendationCandidate && !hasVerifiedAllergenProfile;
        if (profileGap && macroComplete && hasVerifiedServingOption) {
            readiness += 2_000_000.0;
        } else if (macroComplete && hasVerifiedServingOption) {
            readiness += 1_000_000.0;
        } else {
            if (macroComplete) readiness += 100_000.0;
            if (hasVerifiedServingOption) readiness += 100_000.0;
        }
        if (!hasAllergenTags) readiness += 200.0;
        double proteinLane = food.getCategory() == FoodCatalog.FoodCategory.PROTEIN_SOURCE ? 4_000.0 : 0.0;
        double proteinAmount = food.getProteinPer100g() != null ? food.getProteinPer100g() * 80.0 : 0.0;
        double proteinDensity = proteinGPer100Kcal * 160.0;
        return readiness + proteinLane + proteinAmount + proteinDensity + usageCount;
    }

    private boolean isMacroComplete(FoodCatalog food) {
        return food.getCaloriesPer100g() != null
                && food.getProteinPer100g() != null
                && food.getCarbsPer100g() != null
                && food.getFatPer100g() != null;
    }

    private double proteinGPer100Kcal(FoodCatalog food) {
        if (food.getCaloriesPer100g() == null || food.getCaloriesPer100g() <= 0
                || food.getProteinPer100g() == null) {
            return 0.0;
        }
        return food.getProteinPer100g() / food.getCaloriesPer100g() * 100.0;
    }

    private String curationLane(
            FoodCatalog food,
            boolean macroComplete,
            boolean hasVerifiedServingOption,
            boolean hasVerifiedAllergenProfile,
            double proteinGPer100Kcal
    ) {
        boolean highProtein = food.getCategory() == FoodCatalog.FoodCategory.PROTEIN_SOURCE
                || (food.getProteinPer100g() != null && food.getProteinPer100g() >= 15.0)
                || proteinGPer100Kcal >= 8.0;
        boolean profileGap = food.getRecommendationStatus().isRecommendationCandidate()
                && !hasVerifiedAllergenProfile;
        if (profileGap && macroComplete && hasVerifiedServingOption && highProtein) {
            return "A_PROFILE_GAP_HIGH_PROTEIN";
        }
        if (profileGap && macroComplete && hasVerifiedServingOption) {
            return "A_PROFILE_GAP";
        }
        if (macroComplete && hasVerifiedServingOption && highProtein) {
            return "A_ENGINE_READY_HIGH_PROTEIN";
        }
        if (macroComplete && hasVerifiedServingOption) {
            return "A_ENGINE_READY";
        }
        if (macroComplete) {
            return "B_MACRO_NEEDS_SERVING";
        }
        if (hasVerifiedServingOption) {
            return "C_SERVING_NEEDS_MACRO";
        }
        return "D_NEEDS_NUTRITION_AND_SERVING";
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
