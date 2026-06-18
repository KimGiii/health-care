package com.healthcare.domain.diet.repository;

import com.healthcare.domain.diet.entity.FoodServingOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface FoodServingOptionRepository extends JpaRepository<FoodServingOption, Long> {

    List<FoodServingOption> findByFoodCatalogIdIn(Collection<Long> foodCatalogIds);

    List<FoodServingOption> findByFoodCatalogIdOrderBySortOrderAsc(Long foodCatalogId);
}
