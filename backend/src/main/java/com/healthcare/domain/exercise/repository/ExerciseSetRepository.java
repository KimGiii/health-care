package com.healthcare.domain.exercise.repository;

import com.healthcare.domain.exercise.entity.ExerciseSet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ExerciseSetRepository extends JpaRepository<ExerciseSet, Long> {

    List<ExerciseSet> findBySessionIdOrderBySetNumber(Long sessionId);

    /**
     * PR 감지: 해당 사용자 + 운동에 대한 역대 최고 중량 조회.
     * exercise_sets → exercise_sessions 조인으로 user_id 필터링.
     */
    @Query("""
            SELECT MAX(es.weightKg)
            FROM ExerciseSet es
            JOIN ExerciseSession sess ON sess.id = es.sessionId
            WHERE sess.userId = :userId
              AND es.exerciseCatalogId = :catalogId
              AND es.setType = com.healthcare.domain.exercise.entity.ExerciseSet.SetType.WEIGHTED
              AND sess.deletedAt IS NULL
            """)
    Optional<Double> findMaxWeightKgForUserAndExercise(
            @Param("userId")    Long userId,
            @Param("catalogId") Long catalogId
    );
}
