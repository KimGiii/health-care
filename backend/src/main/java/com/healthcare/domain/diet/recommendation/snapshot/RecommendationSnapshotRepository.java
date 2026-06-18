package com.healthcare.domain.diet.recommendation.snapshot;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface RecommendationSnapshotRepository extends JpaRepository<RecommendationSnapshot, Long> {

    Optional<RecommendationSnapshot> findByIdAndUserId(Long id, Long userId);

    @Query("SELECT s.id FROM RecommendationSnapshot s WHERE s.createdAt < :cutoff")
    List<Long> findIdsByCreatedAtBefore(@Param("cutoff") OffsetDateTime cutoff);

    @Modifying
    @Query("DELETE FROM RecommendationSnapshot s WHERE s.userId = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
