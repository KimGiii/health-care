package com.healthcare.domain.diet.repository;

import com.healthcare.domain.diet.entity.FoodEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public interface FoodEntryRepository extends JpaRepository<FoodEntry, Long> {

    List<FoodEntry> findByDietLogIdOrderById(Long dietLogId);

    @Query("""
            SELECT DISTINCT e.foodCatalogId FROM FoodEntry e
            JOIN DietLog d ON e.dietLogId = d.id
            WHERE d.userId = :userId
              AND d.logDate >= :since
            """)
    Set<Long> findRecentFoodCatalogIds(@Param("userId") Long userId,
                                       @Param("since") LocalDate since);
}
