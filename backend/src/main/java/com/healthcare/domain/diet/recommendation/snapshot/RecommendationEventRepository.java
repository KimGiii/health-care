package com.healthcare.domain.diet.recommendation.snapshot;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

public interface RecommendationEventRepository extends JpaRepository<RecommendationEvent, Long> {

    List<RecommendationEvent> findBySnapshotId(Long snapshotId);

    @Modifying
    @Query("DELETE FROM RecommendationEvent e WHERE e.snapshotId IN :snapshotIds")
    void deleteAllBySnapshotIdIn(@Param("snapshotIds") List<Long> snapshotIds);

    /**
     * 온라인 선호 신호용 식품별 참여 집계(R1 food 매핑).
     * RECORDED는 전부, REFRESHED는 음식 기인 거절 사유({@code dislikeReasons})만 산입한다
     * — 저장은 전 사유를 남기고 해석은 이 쿼리가 필터한다(저장은 사실·해석은 정책).
     */
    @Query("""
            SELECT new com.healthcare.domain.diet.recommendation.snapshot.FoodEngagementRow(
                e.foodCatalogId, e.eventType, COUNT(e))
            FROM RecommendationEvent e
            WHERE e.userId = :userId
              AND e.createdAt >= :since
              AND e.foodCatalogId IS NOT NULL
              AND (e.eventType = com.healthcare.domain.diet.recommendation.snapshot.RecommendationEvent.EventType.RECORDED
                   OR (e.eventType = com.healthcare.domain.diet.recommendation.snapshot.RecommendationEvent.EventType.REFRESHED
                       AND e.feedbackReason IN :dislikeReasons))
            GROUP BY e.foodCatalogId, e.eventType
            """)
    List<FoodEngagementRow> aggregateFoodEngagement(
            @Param("userId") Long userId,
            @Param("since") OffsetDateTime since,
            @Param("dislikeReasons") Collection<RecommendationFeedbackReason> dislikeReasons);
}
