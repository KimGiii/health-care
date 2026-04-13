package com.healthcare.domain.exercise.repository;

import com.healthcare.domain.exercise.entity.ExerciseSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ExerciseSessionRepository extends JpaRepository<ExerciseSession, Long> {

    /**
     * 사용자의 세션 목록을 날짜 범위로 페이징 조회한다.
     * from / to 가 null 이면 전체 기간 조회.
     */
    @Query("""
            SELECT s FROM ExerciseSession s
            WHERE s.userId = :userId
              AND (:from IS NULL OR s.sessionDate >= :from)
              AND (:to   IS NULL OR s.sessionDate <= :to)
            ORDER BY s.sessionDate DESC, s.createdAt DESC
            """)
    Page<ExerciseSession> findByUserIdAndDateRange(
            @Param("userId") Long userId,
            @Param("from")   LocalDate from,
            @Param("to")     LocalDate to,
            Pageable pageable
    );

    /**
     * 특정 날짜의 사용자 세션 목록 조회 (일별 요약 캐시 eviction 용)
     */
    @Query("""
            SELECT s FROM ExerciseSession s
            WHERE s.userId = :userId AND s.sessionDate = :date
            """)
    List<ExerciseSession> findByUserIdAndSessionDate(
            @Param("userId") Long userId,
            @Param("date")   LocalDate date
    );
}
