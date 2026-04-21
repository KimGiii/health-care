package com.healthcare.domain.diet.mealphoto.repository;

import com.healthcare.domain.diet.mealphoto.entity.MealPhotoAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MealPhotoAnalysisRepository extends JpaRepository<MealPhotoAnalysis, Long> {
    Optional<MealPhotoAnalysis> findByIdAndUserId(Long id, Long userId);
    Optional<MealPhotoAnalysis> findByStorageKeyAndUserId(String storageKey, Long userId);
}
