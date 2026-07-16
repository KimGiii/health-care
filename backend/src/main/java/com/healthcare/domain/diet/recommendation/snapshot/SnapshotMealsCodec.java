package com.healthcare.domain.diet.recommendation.snapshot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.domain.diet.recommendation.dto.RecommendedFoodEntry;
import com.healthcare.domain.diet.recommendation.dto.RecommendedMeal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * 스냅샷 meals_json의 직렬화/역직렬화를 한 곳에 모은다.
 * GENERATED(Store)·REFRESHED(Feedback)·RECORDED(Conversion) fan-out이 모두
 * 스냅샷의 식품 목록을 필요로 하므로 파싱 규칙을 공유한다.
 */
@Component
@RequiredArgsConstructor
public class SnapshotMealsCodec {

    private final ObjectMapper objectMapper;

    public String serialize(List<RecommendedMeal> meals) {
        try {
            return objectMapper.writeValueAsString(meals);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    /**
     * meals_json에서 중복 제거된 foodCatalogId 목록을 추출한다.
     * 파싱 실패(손상·구버전 포맷) 시 빈 목록을 반환한다 — 호출부는 스냅샷 단위
     * 이벤트로 폴백해 이벤트 자체를 유실하지 않는다.
     */
    public List<Long> extractFoodCatalogIds(String mealsJson) {
        List<RecommendedMeal> meals;
        try {
            meals = objectMapper.readValue(mealsJson, new TypeReference<List<RecommendedMeal>>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
        return meals.stream()
                .flatMap(meal -> meal.items().stream())
                .map(RecommendedFoodEntry::foodCatalogId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }
}
