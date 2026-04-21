package com.healthcare.domain.bodymeasurement.repository;

import com.healthcare.domain.bodymeasurement.entity.ProgressPhoto;
import com.healthcare.domain.bodymeasurement.entity.ProgressPhoto.PhotoType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface ProgressPhotoRepository extends JpaRepository<ProgressPhoto, Long> {

    @Query("""
            SELECT p FROM ProgressPhoto p
            WHERE p.userId = :userId
              AND p.capturedAt BETWEEN :from AND :to
            ORDER BY p.capturedAt DESC
            """)
    Page<ProgressPhoto> findByUserIdAndCapturedAtRange(
            @Param("userId") Long userId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            Pageable pageable);

    @Query("""
            SELECT p FROM ProgressPhoto p
            WHERE p.userId = :userId
              AND p.photoType = :photoType
              AND p.capturedAt BETWEEN :from AND :to
            ORDER BY p.capturedAt DESC
            """)
    Page<ProgressPhoto> findByUserIdAndPhotoTypeAndCapturedAtRange(
            @Param("userId") Long userId,
            @Param("photoType") PhotoType photoType,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            Pageable pageable);

    Optional<ProgressPhoto> findByStorageKeyAndUserId(String storageKey, Long userId);

    Optional<ProgressPhoto> findByUserIdAndPhotoTypeAndIsBaselineTrue(Long userId, PhotoType photoType);
}
