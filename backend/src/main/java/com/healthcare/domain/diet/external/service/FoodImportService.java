package com.healthcare.domain.diet.external.service;

import com.healthcare.domain.diet.dto.FoodCatalogResponse;
import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.external.dto.ImportFoodRequest;
import com.healthcare.domain.diet.repository.FoodCatalogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FoodImportService {

    private final FoodCatalogRepository foodCatalogRepository;

    /**
     * 외부 API(USDA / Open Food Facts)에서 가져온 식품을 로컬 카탈로그에 저장한다.
     * 항상 isCustom=true, createdByUserId=userId 로 저장한다.
     */
    @Transactional
    public FoodCatalogResponse importFood(Long userId, ImportFoodRequest request) {
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
