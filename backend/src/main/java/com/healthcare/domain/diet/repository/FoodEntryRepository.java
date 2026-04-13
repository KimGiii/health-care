package com.healthcare.domain.diet.repository;

import com.healthcare.domain.diet.entity.FoodEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FoodEntryRepository extends JpaRepository<FoodEntry, Long> {

    List<FoodEntry> findByDietLogIdOrderById(Long dietLogId);
}
