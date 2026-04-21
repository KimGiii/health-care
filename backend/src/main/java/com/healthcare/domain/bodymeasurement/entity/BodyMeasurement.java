package com.healthcare.domain.bodymeasurement.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "body_measurements")
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class BodyMeasurement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "measured_at", nullable = false)
    private LocalDate measuredAt;

    // 기본 측정값
    @Column(name = "weight_kg")
    private Double weightKg;

    @Column(name = "body_fat_pct")
    private Double bodyFatPct;

    @Column(name = "muscle_mass_kg")
    private Double muscleMassKg;

    @Column(name = "bmi")
    private Double bmi;

    // 신체 부위 사이즈 (cm)
    @Column(name = "chest_cm")
    private Double chestCm;

    @Column(name = "waist_cm")
    private Double waistCm;

    @Column(name = "hip_cm")
    private Double hipCm;

    @Column(name = "thigh_cm")
    private Double thighCm;

    @Column(name = "arm_cm")
    private Double armCm;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

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

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    public void update(Double weightKg, Double bodyFatPct, Double muscleMassKg, Double bmi,
                       Double chestCm, Double waistCm, Double hipCm, Double thighCm, Double armCm,
                       String notes) {
        if (weightKg != null) this.weightKg = weightKg;
        if (bodyFatPct != null) this.bodyFatPct = bodyFatPct;
        if (muscleMassKg != null) this.muscleMassKg = muscleMassKg;
        if (bmi != null) this.bmi = bmi;
        if (chestCm != null) this.chestCm = chestCm;
        if (waistCm != null) this.waistCm = waistCm;
        if (hipCm != null) this.hipCm = hipCm;
        if (thighCm != null) this.thighCm = thighCm;
        if (armCm != null) this.armCm = armCm;
        if (notes != null) this.notes = notes;
    }

    public void delete() {
        this.deletedAt = OffsetDateTime.now();
    }
}
