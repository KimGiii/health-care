package com.healthcare.domain.diet.recommendation.snapshot;

import com.healthcare.domain.goals.entity.Goal;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;

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

    /**
     * 이벤트 생성은 snapshot 패키지 서비스들(Store=GENERATED, Feedback=REFRESHED,
     * Conversion=RECORDED)이 food별 fan-out으로 직접 영속화한다 — 엔티티가 이벤트를
     * 들고 있지 않는다. 과거 @Transient events 리스트는 저장되지 않는 채 쌓여
     * GENERATED 유실 버그를 만들었던 코드라 제거했다(R1 eng review 이슈 1).
     */
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
        return snapshot;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
