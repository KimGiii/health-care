package com.healthcare.domain.diet.repository;

import com.healthcare.domain.diet.entity.DietLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface DietLogRepository extends JpaRepository<DietLog, Long> {

    /**
     * 날짜 범위로 식사 기록을 페이징 조회한다.
     * from / to는 null이 아니어야 함 (서비스 레이어에서 기본값 제공).
     * PostgreSQL은 nullable 파라미터의 타입을 추론하지 못하므로 non-null로 유지한다.
     */
    @Query("""
            SELECT d FROM DietLog d
            WHERE d.userId = :userId
              AND d.logDate >= :from
              AND d.logDate <= :to
            ORDER BY d.logDate DESC, d.mealType ASC
            """)
    Page<DietLog> findByUserIdAndDateRange(
            @Param("userId") Long userId,
            @Param("from")   LocalDate from,
            @Param("to")     LocalDate to,
            Pageable pageable
    );
}
