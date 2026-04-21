package com.healthcare.domain.bodymeasurement.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "progress_photos")
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class ProgressPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "photo_type", nullable = false, length = 20)
    private PhotoType photoType;

    @Column(name = "captured_at", nullable = false)
    private OffsetDateTime capturedAt;

    @Column(name = "photo_date", nullable = false)
    private LocalDate photoDate;

    @Column(name = "storage_key", nullable = false, length = 512)
    private String storageKey;

    @Column(name = "thumbnail_key_150", length = 512)
    private String thumbnailKey150;

    @Column(name = "thumbnail_key_400", length = 512)
    private String thumbnailKey400;

    @Column(name = "thumbnail_key_800", length = 512)
    private String thumbnailKey800;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "exif_stripped", nullable = false)
    @Builder.Default
    private boolean exifStripped = false;

    @Column(name = "is_private", nullable = false)
    @Builder.Default
    private boolean isPrivate = true;

    @Column(name = "is_baseline", nullable = false)
    @Builder.Default
    private boolean isBaseline = false;

    @Column(name = "body_weight_kg")
    private Double bodyWeightKg;

    @Column(name = "body_fat_pct")
    private Double bodyFatPct;

    @Column(name = "waist_cm")
    private Double waistCm;

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

    public void markAsBaseline() {
        this.isBaseline = true;
    }

    public void clearBaseline() {
        this.isBaseline = false;
    }

    public void delete() {
        this.deletedAt = OffsetDateTime.now();
    }

    public enum PhotoType {
        FRONT, BACK, SIDE_LEFT, SIDE_RIGHT, DETAIL
    }
}
