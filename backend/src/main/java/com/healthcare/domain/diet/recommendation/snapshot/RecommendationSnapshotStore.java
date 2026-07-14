package com.healthcare.domain.diet.recommendation.snapshot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.domain.diet.recommendation.dto.RecommendedFoodEntry;
import com.healthcare.domain.diet.recommendation.dto.RecommendedMeal;
import com.healthcare.domain.goals.entity.Goal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class RecommendationSnapshotStore {

    private final RecommendationSnapshotRepository snapshotRepository;
    private final RecommendationEventRepository eventRepository;
    private final ObjectMapper objectMapper;

    /**
     * 스냅샷을 저장하고 식품별 GENERATED 이벤트를 fan-out 저장한다(R1 food 매핑).
     * GENERATED fan-out은 식품별 전환율 KPI의 분모가 된다 — 스냅샷 단위 생성 수가
     * 필요하면 {@code COUNT(DISTINCT snapshot_id)}로 복원한다.
     */
    public Long save(Long userId, LocalDate date, List<RecommendedMeal> meals,
                     Goal.GoalType goalType, boolean strictAllergyMode) {
        RecommendationSnapshot snapshot = RecommendationSnapshot.create(
                userId, date, serializeMeals(meals), goalType, strictAllergyMode);
        RecommendationSnapshot saved = snapshotRepository.save(snapshot);

        List<RecommendationEvent> generated = distinctFoodCatalogIds(meals).stream()
                .map(foodId -> RecommendationEvent.generated(saved.getId(), userId, foodId))
                .toList();
        eventRepository.saveAll(generated);
        return saved.getId();
    }

    public String serializeMeals(List<RecommendedMeal> meals) {
        try {
            return objectMapper.writeValueAsString(meals);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private static List<Long> distinctFoodCatalogIds(List<RecommendedMeal> meals) {
        return meals.stream()
                .flatMap(meal -> meal.items().stream())
                .map(RecommendedFoodEntry::foodCatalogId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }
}
