package com.healthcare.domain.bodymeasurement.repository;

import com.healthcare.domain.bodymeasurement.entity.BodyMeasurement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BodyMeasurementRepository extends JpaRepository<BodyMeasurement, Long> {

    @Query("SELECT b FROM BodyMeasurement b WHERE b.userId = :userId ORDER BY b.measuredAt DESC")
    Page<BodyMeasurement> findByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT b FROM BodyMeasurement b WHERE b.userId = :userId " +
           "AND b.measuredAt BETWEEN :from AND :to ORDER BY b.measuredAt DESC")
    List<BodyMeasurement> findByUserIdAndDateRange(
            @Param("userId") Long userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    Optional<BodyMeasurement> findFirstByUserIdOrderByMeasuredAtDesc(Long userId);
}
