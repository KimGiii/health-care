package com.healthcare.domain.goals.repository;

import com.healthcare.domain.goals.entity.Goal;
import com.healthcare.domain.goals.entity.Goal.GoalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface GoalRepository extends JpaRepository<Goal, Long> {

    @Query("SELECT g FROM Goal g WHERE g.userId = :userId AND g.status = 'ACTIVE'")
    Optional<Goal> findActiveGoalByUserId(@Param("userId") Long userId);

    @Query("SELECT g FROM Goal g WHERE g.userId = :userId " +
           "AND (:status IS NULL OR g.status = :status) " +
           "ORDER BY g.createdAt DESC")
    Page<Goal> findByUserIdAndStatus(
            @Param("userId") Long userId,
            @Param("status") GoalStatus status,
            Pageable pageable);
}
