package com.healthcare.domain.diet.recommendation.snapshot;

import com.healthcare.domain.goals.entity.Goal;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "recommendation_snapshots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecommendationSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "meals_json", nullable = false, columnDefinition = "TEXT")
    private String mealsJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "goal_type", length = 30)
    private Goal.GoalType goalType;

    @Column(name = "strict_allergy_mode", nullable = false)
    private boolean strictAllergyMode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Transient
    private final List<RecommendationEvent> events = new ArrayList<>();

    public static RecommendationSnapshot create(Long userId, LocalDate snapshotDate,
                                                String mealsJson, Goal.GoalType goalType,
                                                boolean strictAllergyMode) {
        RecommendationSnapshot snapshot = new RecommendationSnapshot();
        snapshot.userId = userId;
        snapshot.snapshotDate = snapshotDate;
        snapshot.mealsJson = mealsJson;
        snapshot.goalType = goalType;
        snapshot.strictAllergyMode = strictAllergyMode;
        snapshot.createdAt = OffsetDateTime.now();
        snapshot.events.add(RecommendationEvent.generated(null, userId));
        return snapshot;
    }

    public void recordExposed() {
        events.add(RecommendationEvent.exposed(id, userId));
    }

    public void recordRefreshed(RecommendationFeedbackReason reason) {
        events.add(RecommendationEvent.refreshed(id, userId, reason));
    }

    public void recordConverted(Long dietLogId) {
        events.add(RecommendationEvent.recorded(id, userId, dietLogId));
    }

    public List<RecommendationEvent> getEvents() {
        return Collections.unmodifiableList(events);
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
