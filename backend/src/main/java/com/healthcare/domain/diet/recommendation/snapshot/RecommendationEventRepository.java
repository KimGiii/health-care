package com.healthcare.domain.diet.recommendation.snapshot;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RecommendationEventRepository extends JpaRepository<RecommendationEvent, Long> {

    List<RecommendationEvent> findBySnapshotId(Long snapshotId);

    @Modifying
    @Query("DELETE FROM RecommendationEvent e WHERE e.snapshotId IN :snapshotIds")
    void deleteAllBySnapshotIdIn(@Param("snapshotIds") List<Long> snapshotIds);
}
