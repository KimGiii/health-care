package com.healthcare.domain.diet.mealphoto.repository;

import com.healthcare.domain.diet.mealphoto.entity.MealPhotoAnalysisItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MealPhotoAnalysisItemRepository extends JpaRepository<MealPhotoAnalysisItem, Long> {
    List<MealPhotoAnalysisItem> findByAnalysisIdOrderByItemOrderAsc(Long analysisId);
    void deleteByAnalysisId(Long analysisId);
}
