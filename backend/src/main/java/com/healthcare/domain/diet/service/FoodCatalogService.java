package com.healthcare.domain.diet.service;

import com.healthcare.domain.diet.dto.CreateCustomFoodRequest;
import com.healthcare.domain.diet.dto.FoodCatalogResponse;
import com.healthcare.domain.diet.dto.FoodSearchParams;
import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.entity.FoodCatalogSource;
import com.healthcare.domain.diet.entity.RecommendationStatus;
import com.healthcare.domain.diet.identity.FoodCatalogIdentity;
import com.healthcare.domain.diet.repository.FoodCatalogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FoodCatalogService {

    private final FoodCatalogRepository foodCatalogRepository;

    public List<FoodCatalogResponse> searchFoods(FoodSearchParams params) {
        String normalizedQuery = normalizeQuery(params.getQuery());
        return foodCatalogRepository
                .searchAll(normalizedQuery, params.getCategory(), params.isCustomOnly())
                .stream()
                .map(FoodCatalogResponse::from)
                .toList();
    }

    @Transactional
    public FoodCatalogResponse createCustomFood(Long userId, CreateCustomFoodRequest request) {
        String normalizedName = normalizeName(request.getNameKo() != null ? request.getNameKo() : request.getName());

        // 중복 검사: 같은 이름+카테고리의 커스텀 식품이 이미 있으면 기존 항목 반환 (idempotent)
        var existing = foodCatalogRepository.findCustomByNameKoAndCategory(normalizedName, request.getCategory());
        if (existing.isPresent()) {
            return FoodCatalogResponse.from(existing.get());
        }

        try {
            FoodCatalog food = FoodCatalog.builder()
                    .name(normalizedName)
                    .nameKo(normalizedName)
                    .category(request.getCategory())
                    .caloriesPer100g(request.getCaloriesPer100g())
                    .proteinPer100g(request.getProteinPer100g())
                    .carbsPer100g(request.getCarbsPer100g())
                    .fatPer100g(request.getFatPer100g())
                    .sugarsPer100g(request.getSugarsPer100g())
                    .dietaryFiberPer100g(request.getDietaryFiberPer100g())
                    .saturatedFatPer100g(request.getSaturatedFatPer100g())
                    .transFatPer100g(request.getTransFatPer100g())
                    .cholesterolPer100gMg(request.getCholesterolPer100gMg())
                    .sodiumPer100gMg(request.getSodiumPer100gMg())
                    .source(FoodCatalogSource.USER_CUSTOM)
                    .recommendationStatus(RecommendationStatus.SEARCH_ONLY)
                    .isCustom(true)
                    .createdByUserId(userId)
                    .build();

            return FoodCatalogResponse.from(foodCatalogRepository.save(food));
        } catch (DataIntegrityViolationException e) {
            // DB 레벨 유니크 충돌 (동시 요청) → 기존 항목 반환
            return foodCatalogRepository
                    .findCustomByNameKoAndCategory(normalizedName, request.getCategory())
                    .map(FoodCatalogResponse::from)
                    .orElseThrow(() -> e);
        }
    }

    private String normalizeQuery(String query) {
        if (query == null) return null;
        String trimmed = query.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** NFC 정규화 + 연속 공백 축약 + 앞뒤 공백 제거 */
    private String normalizeName(String name) {
        return FoodCatalogIdentity.normalizeDisplayName(name);
    }
}
