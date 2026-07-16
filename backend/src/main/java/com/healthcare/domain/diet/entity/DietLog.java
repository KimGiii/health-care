package com.healthcare.domain.diet.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "diet_logs")
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class DietLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "log_date", nullable = false)
    private LocalDate logDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_type", nullable = false, length = 15)
    private MealType mealType;

    @Column(name = "total_calories")
    private Double totalCalories;

    @Column(name = "total_protein_g")
    private Double totalProteinG;

    @Column(name = "total_carbs_g")
    private Double totalCarbsG;

    @Column(name = "total_fat_g")
    private Double totalFatG;

    @Column(name = "total_sugars_g")
    private Double totalSugarsG;

    @Column(name = "total_dietary_fiber_g")
    private Double totalDietaryFiberG;

    @Column(name = "total_saturated_fat_g")
    private Double totalSaturatedFatG;

    @Column(name = "total_trans_fat_g")
    private Double totalTransFatG;

    @Column(name = "total_cholesterol_mg")
    private Double totalCholesterolMg;

    @Column(name = "total_sodium_mg")
    private Double totalSodiumMg;

    @Column(columnDefinition = "TEXT")
    private String notes;

    /** 이 기록의 출처가 된 추천 스냅샷(V37). 추천 없이 기록하면 null. */
    @Column(name = "recommendation_snapshot_id")
    private Long recommendationSnapshotId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    /** 추천 스냅샷 검증(존재·소유) 통과 후에만 호출한다 — FK 위반 방지. */
    public void linkRecommendationSnapshot(Long snapshotId) {
        this.recommendationSnapshotId = snapshotId;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public void update(MealType mealType, LocalDate logDate, String notes,
                       DietLogNutritionTotals totals) {
        this.mealType            = mealType;
        this.logDate             = logDate;
        this.notes               = notes;
        this.totalCalories       = totals.totalCalories();
        this.totalProteinG       = totals.totalProteinG();
        this.totalCarbsG         = totals.totalCarbsG();
        this.totalFatG           = totals.totalFatG();
        this.totalSugarsG        = totals.totalSugarsG();
        this.totalDietaryFiberG  = totals.totalDietaryFiberG();
        this.totalSaturatedFatG  = totals.totalSaturatedFatG();
        this.totalTransFatG      = totals.totalTransFatG();
        this.totalCholesterolMg  = totals.totalCholesterolMg();
        this.totalSodiumMg       = totals.totalSodiumMg();
    }

    public void softDelete() {
        this.deletedAt = OffsetDateTime.now();
    }

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    public enum MealType {
        BREAKFAST, LUNCH, DINNER, SNACK
    }
}
