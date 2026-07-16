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

    /** SWAPPED 이벤트에서 선택된 대안의 인덱스(V41, #85 판정 계측). 그 외 이벤트는 null. */
    @Column(name = "alternative_index")
    private Integer alternativeIndex;

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

    /**
     * 대안 swap 계측 이벤트(#85 선행). 대안 식품 목록이 스냅샷에 없어 food fan-out은
     * 불가능하므로 스냅샷 단위 1행만 남긴다. swap 빈도·선택 인덱스 분포가 후보 5
     * 잔여분(A/B/C 설계)의 판정 데이터가 된다.
     */
    static RecommendationEvent swapped(Long snapshotId, Long userId, int alternativeIndex) {
        return RecommendationEvent.builder()
                .snapshotId(snapshotId)
                .userId(userId)
                .eventType(EventType.SWAPPED)
                .alternativeIndex(alternativeIndex)
                .createdAt(OffsetDateTime.now())
                .build();
    }

    public enum EventType {
        GENERATED, EXPOSED, REFRESHED, RECORDED, SWAPPED
    }
}
