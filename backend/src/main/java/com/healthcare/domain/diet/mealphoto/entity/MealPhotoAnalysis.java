package com.healthcare.domain.diet.mealphoto.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.OffsetDateTime;

@Entity
@Table(name = "meal_photo_analyses")
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class MealPhotoAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "storage_key", nullable = false, length = 512)
    private String storageKey;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;

    @Column(name = "captured_at", nullable = false)
    private OffsetDateTime capturedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(length = 50)
    private String provider;

    @Column(name = "analysis_version", length = 50)
    private String analysisVersion;

    @Column(name = "raw_model_output", columnDefinition = "TEXT")
    private String rawModelOutput;

    @Column(name = "analysis_warnings", columnDefinition = "TEXT")
    private String analysisWarnings;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

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

    public void markAnalyzed(String provider, String analysisVersion, String rawModelOutput, String analysisWarnings) {
        this.status = Status.ANALYZED;
        this.provider = provider;
        this.analysisVersion = analysisVersion;
        this.rawModelOutput = rawModelOutput;
        this.analysisWarnings = analysisWarnings;
    }

    public void markFailed(String provider, String analysisVersion, String rawModelOutput, String analysisWarnings) {
        this.status = Status.FAILED;
        this.provider = provider;
        this.analysisVersion = analysisVersion;
        this.rawModelOutput = rawModelOutput;
        this.analysisWarnings = analysisWarnings;
    }

    public void markConfirmed() {
        this.status = Status.CONFIRMED;
        this.confirmedAt = OffsetDateTime.now();
    }

    public enum Status {
        INITIATED, ANALYZED, FAILED, CONFIRMED
    }
}
