package com.healthcare.domain.exercise.repository;

import com.healthcare.domain.exercise.entity.ExerciseCatalog;
import com.healthcare.domain.exercise.entity.ExerciseCatalog.ExerciseType;
import com.healthcare.domain.exercise.entity.ExerciseCatalog.MuscleGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ExerciseCatalogRepository extends JpaRepository<ExerciseCatalog, Long> {

    /**
     * 사용자에게 접근 가능한 운동 카탈로그를 조회한다.
     * - 글로벌 운동 (created_by_user_id IS NULL)
     * - 해당 사용자의 커스텀 운동 (created_by_user_id = userId)
     * 선택적 필터: name 검색, exerciseType, muscleGroup, customOnly
     */
    @Query("""
            SELECT c FROM ExerciseCatalog c
            WHERE (c.createdByUserId IS NULL OR c.createdByUserId = :userId)
              AND (:exerciseType IS NULL OR c.exerciseType = :exerciseType)
              AND (:muscleGroup  IS NULL OR c.muscleGroup  = :muscleGroup)
              AND (:customOnly = FALSE   OR c.isCustom = TRUE)
              AND (
                    :query IS NULL
                    OR LOWER(c.name)   LIKE LOWER(CONCAT('%', CAST(:query AS string), '%'))
                    OR LOWER(c.nameKo) LIKE LOWER(CONCAT('%', CAST(:query AS string), '%'))
                  )
            ORDER BY c.isCustom ASC, c.name ASC
            """)
    List<ExerciseCatalog> findAccessibleToUser(
            @Param("userId")       Long userId,
            @Param("query")        String query,
            @Param("exerciseType") ExerciseType exerciseType,
            @Param("muscleGroup")  MuscleGroup muscleGroup,
            @Param("customOnly")   boolean customOnly
    );
}
