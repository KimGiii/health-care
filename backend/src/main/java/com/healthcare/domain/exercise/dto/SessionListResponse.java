package com.healthcare.domain.exercise.dto;

import com.healthcare.domain.exercise.entity.ExerciseSession;
import com.healthcare.domain.exercise.entity.ExerciseSession.CalorieEstimateMethod;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class SessionListResponse {

    private final List<SessionSummary> content;
    private final int pageNumber;
    private final int pageSize;
    private final long totalElements;
    private final int totalPages;
    private final boolean first;
    private final boolean last;

    public static SessionListResponse from(Page<ExerciseSession> page) {
        List<SessionSummary> summaries = page.getContent().stream()
                .map(SessionSummary::from)
                .toList();
        return SessionListResponse.builder()
                .content(summaries)
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }

    @Getter
    @Builder
    public static class SessionSummary {
        private final Long sessionId;
        private final LocalDate sessionDate;
        private final Integer durationMinutes;
        private final Double totalVolumeKg;
        private final Double caloriesBurned;
        private final CalorieEstimateMethod calorieEstimateMethod;
        private final String notes;

        public static SessionSummary from(ExerciseSession session) {
            return SessionSummary.builder()
                    .sessionId(session.getId())
                    .sessionDate(session.getSessionDate())
                    .durationMinutes(session.getDurationMinutes())
                    .totalVolumeKg(session.getTotalVolumeKg())
                    .caloriesBurned(session.getCaloriesBurned())
                    .calorieEstimateMethod(session.getCalorieEstimateMethod())
                    .notes(session.getNotes())
                    .build();
        }
    }
}
