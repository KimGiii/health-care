package com.healthcare.domain.diet.service;

import com.healthcare.domain.diet.dto.CreateCustomFoodRequest;
import com.healthcare.domain.diet.dto.FoodCatalogResponse;
import com.healthcare.domain.diet.dto.FoodSearchParams;
import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.repository.FoodCatalogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FoodCatalogService {

    private final FoodCatalogRepository foodCatalogRepository;

    public List<FoodCatalogResponse> searchFoods(Long userId, FoodSearchParams params) {
        return foodCatalogRepository
                .findAccessibleToUser(userId, params.getQuery(), params.getCategory(), params.isCustomOnly())
                .stream()
                .map(FoodCatalogResponse::from)
                .toList();
    }

    @Transactional
    public FoodCatalogResponse createCustomFood(Long userId, CreateCustomFoodRequest request) {
        FoodCatalog food = FoodCatalog.builder()
                .name(request.getName())
                .nameKo(request.getNameKo())
                .category(request.getCategory())
                .caloriesPer100g(request.getCaloriesPer100g())
                .proteinPer100g(request.getProteinPer100g())
                .carbsPer100g(request.getCarbsPer100g())
                .fatPer100g(request.getFatPer100g())
                .isCustom(true)
                .createdByUserId(userId)
                .build();

        FoodCatalog saved = foodCatalogRepository.save(food);
        return FoodCatalogResponse.from(saved);
    }
}
