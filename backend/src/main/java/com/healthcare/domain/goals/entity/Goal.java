package com.healthcare.domain.goals.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "goals")
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class Goal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "goal_type", nullable = false, length = 30)
    private GoalType goalType;

    @Column(name = "target_value", precision = 7, scale = 2)
    private BigDecimal targetValue;

    @Column(name = "target_unit", length = 20)
    private String targetUnit;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Column(name = "start_value", precision = 7, scale = 2)
    private BigDecimal startValue;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private GoalStatus status;

    @Column(name = "calorie_target")
    private Integer calorieTarget;

    @Column(name = "protein_target_g")
    private Integer proteinTargetG;

    @Column(name = "carb_target_g")
    private Integer carbTargetG;

    @Column(name = "fat_target_g")
    private Integer fatTargetG;

    @Column(name = "weekly_rate_target", precision = 4, scale = 2)
    private BigDecimal weeklyRateTarget;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "abandoned_at")
    private OffsetDateTime abandonedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public void abandon() {
        this.status = GoalStatus.ABANDONED;
        this.abandonedAt = OffsetDateTime.now();
    }

    public void complete() {
        this.status = GoalStatus.COMPLETED;
        this.completedAt = OffsetDateTime.now();
    }

    public void updateTarget(BigDecimal targetValue, LocalDate targetDate, BigDecimal weeklyRateTarget) {
        if (targetValue != null) this.targetValue = targetValue;
        if (targetDate != null) this.targetDate = targetDate;
        if (weeklyRateTarget != null) this.weeklyRateTarget = weeklyRateTarget;
    }

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    public boolean isActive() {
        return GoalStatus.ACTIVE.equals(this.status);
    }

    public enum GoalType {
        WEIGHT_LOSS, MUSCLE_GAIN, BODY_RECOMPOSITION, ENDURANCE, GENERAL_HEALTH
    }

    public enum GoalStatus {
        ACTIVE, COMPLETED, ABANDONED
    }
}
