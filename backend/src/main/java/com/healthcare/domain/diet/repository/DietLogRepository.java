package com.healthcare.domain.diet.repository;

import com.healthcare.domain.diet.entity.DietLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface DietLogRepository extends JpaRepository<DietLog, Long> {

    @Query("""
            SELECT d FROM DietLog d
            WHERE d.userId = :userId
              AND (:from IS NULL OR d.logDate >= :from)
              AND (:to   IS NULL OR d.logDate <= :to)
            ORDER BY d.logDate DESC, d.mealType ASC
            """)
    Page<DietLog> findByUserIdAndDateRange(
            @Param("userId") Long userId,
            @Param("from")   LocalDate from,
            @Param("to")     LocalDate to,
            Pageable pageable
    );
}
