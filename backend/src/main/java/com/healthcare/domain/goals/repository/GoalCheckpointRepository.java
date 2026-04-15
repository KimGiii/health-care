package com.healthcare.domain.goals.repository;

import com.healthcare.domain.goals.entity.GoalCheckpoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface GoalCheckpointRepository extends JpaRepository<GoalCheckpoint, Long> {

    List<GoalCheckpoint> findByGoalIdOrderByCheckpointDate(Long goalId);

    Optional<GoalCheckpoint> findByGoalIdAndCheckpointDate(Long goalId, LocalDate checkpointDate);
}
