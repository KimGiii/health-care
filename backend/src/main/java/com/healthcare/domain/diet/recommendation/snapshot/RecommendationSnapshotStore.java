package com.healthcare.domain.diet.recommendation.snapshot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.domain.diet.recommendation.dto.RecommendedMeal;
import com.healthcare.domain.goals.entity.Goal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RecommendationSnapshotStore {

    private final RecommendationSnapshotRepository snapshotRepository;
    private final ObjectMapper objectMapper;

    public Long save(Long userId, LocalDate date, String mealsJson,
                     Goal.GoalType goalType, boolean strictAllergyMode) {
        RecommendationSnapshot snapshot = RecommendationSnapshot.create(
                userId, date, mealsJson, goalType, strictAllergyMode);
        RecommendationSnapshot saved = snapshotRepository.save(snapshot);
        return saved.getId();
    }

    public String serializeMeals(List<RecommendedMeal> meals) {
        try {
            return objectMapper.writeValueAsString(meals);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }
}
