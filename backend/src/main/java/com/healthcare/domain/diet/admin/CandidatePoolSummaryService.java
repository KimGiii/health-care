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

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CandidatePoolSummaryService {

    private final FoodCatalogRepository foodCatalogRepository;
    private final FoodAllergenTagRepository foodAllergenTagRepository;

    public CandidatePoolSummary summarize() {
        List<FoodCatalog> candidates = foodCatalogRepository.findAll(
                FoodCatalogSpecs.hasRecommendationCandidateStatus()
                        .and(FoodCatalogSpecs.isCanonicalCandidate()),
                Sort.unsorted()
        );

        Set<Long> taggedFoodIds = Set.copyOf(foodAllergenTagRepository.findFoodIdsWithAnyAllergenTag());

        Map<FoodCategory, Long> countByCategory = candidates.stream()
                .collect(Collectors.groupingBy(FoodCatalog::getCategory, Collectors.counting()));

        long macroCompleteTotal = candidates.stream()
                .filter(this::isMacroComplete)
                .count();

        long untaggedTotal = candidates.stream()
                .filter(f -> f.getId() != null && !taggedFoodIds.contains(f.getId()))
                .count();

        List<FoodCategory> underrepresented = countByCategory.entrySet().stream()
                .filter(e -> e.getValue() < CandidatePoolSummary.UNDERREPRESENTED_THRESHOLD)
                .map(Map.Entry::getKey)
                .sorted(Comparator.comparing(Enum::name))
                .toList();

        return new CandidatePoolSummary(
                candidates.size(),
                macroCompleteTotal,
                untaggedTotal,
                Map.copyOf(countByCategory),
                underrepresented
        );
    }

    private boolean isMacroComplete(FoodCatalog food) {
        return food.getCaloriesPer100g() != null
                && food.getProteinPer100g() != null
                && food.getCarbsPer100g() != null
                && food.getFatPer100g() != null;
    }
}
