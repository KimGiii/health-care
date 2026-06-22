package com.healthcare.domain.diet.allergen.repository;

import com.healthcare.domain.diet.allergen.entity.FoodAllergenProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface FoodAllergenProfileRepository extends JpaRepository<FoodAllergenProfile, Long> {

    @Query("SELECT p FROM FoodAllergenProfile p WHERE p.foodCatalogId IN :ids")
    List<FoodAllergenProfile> findByFoodCatalogIdIn(@Param("ids") Collection<Long> ids);
}
