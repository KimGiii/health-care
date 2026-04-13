package com.healthcare.domain.exercise.dto;

import com.healthcare.domain.exercise.entity.ExerciseSession;
import com.healthcare.domain.exercise.entity.ExerciseSession.CalorieEstimateMethod;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Builder
public class SessionDetailResponse {

    private final Long sessionId;
    private final LocalDate sessionDate;
    private final OffsetDateTime startedAt;
    private final OffsetDateTime endedAt;
    private final Integer durationMinutes;
    private final Double totalVolumeKg;
    private final Double caloriesBurned;
    private final CalorieEstimateMethod calorieEstimateMethod;
    private final String notes;
    private final List<SetDetailResponse> sets;

    public static SessionDetailResponse from(ExerciseSession session, List<SetDetailResponse> sets) {
        return SessionDetailResponse.builder()
                .sessionId(session.getId())
                .sessionDate(session.getSessionDate())
                .startedAt(session.getStartedAt())
                .endedAt(session.getEndedAt())
                .durationMinutes(session.getDurationMinutes())
                .totalVolumeKg(session.getTotalVolumeKg())
                .caloriesBurned(session.getCaloriesBurned())
                .calorieEstimateMethod(session.getCalorieEstimateMethod())
                .notes(session.getNotes())
                .sets(sets)
                .build();
    }
}
