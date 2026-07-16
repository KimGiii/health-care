package com.healthcare.domain.diet.recommendation.snapshot;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "recommendation_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class RecommendationEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "snapshot_id", nullable = false)
    private Long snapshotId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 20)
    private EventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "feedback_reason", length = 50)
    private RecommendationFeedbackReason feedbackReason;

    @Column(name = "diet_log_id")
    private Long dietLogId;

    /** 이벤트가 귀속되는 식품(V40, food별 fan-out 행). 스냅샷 단위 이벤트는 null. */
    @Column(name = "food_catalog_id")
    private Long foodCatalogId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    static RecommendationEvent generated(Long snapshotId, Long userId, Long foodCatalogId) {
        return RecommendationEvent.builder()
                .snapshotId(snapshotId)
                .userId(userId)
                .eventType(EventType.GENERATED)
                .foodCatalogId(foodCatalogId)
                .createdAt(OffsetDateTime.now())
                .build();
    }

    static RecommendationEvent refreshed(Long snapshotId, Long userId,
                                         RecommendationFeedbackReason reason, Long foodCatalogId) {
        return RecommendationEvent.builder()
                .snapshotId(snapshotId)
                .userId(userId)
                .eventType(EventType.REFRESHED)
                .feedbackReason(reason)
                .foodCatalogId(foodCatalogId)
                .createdAt(OffsetDateTime.now())
                .build();
    }

    static RecommendationEvent recorded(Long snapshotId, Long userId, Long dietLogId,
                                        Long foodCatalogId) {
        return RecommendationEvent.builder()
                .snapshotId(snapshotId)
                .userId(userId)
                .eventType(EventType.RECORDED)
                .dietLogId(dietLogId)
                .foodCatalogId(foodCatalogId)
                .createdAt(OffsetDateTime.now())
                .build();
    }

    public enum EventType {
        GENERATED, EXPOSED, REFRESHED, RECORDED
    }
}
