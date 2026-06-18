package com.healthcare.domain.diet.allergen.repository;

import com.healthcare.domain.diet.allergen.AllergenDataSource;
import com.healthcare.domain.diet.allergen.AllergenTag;
import com.healthcare.domain.diet.allergen.entity.FoodAllergenTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface FoodAllergenTagRepository extends JpaRepository<FoodAllergenTag, Long> {

    @Query("SELECT f FROM FoodAllergenTag f WHERE f.foodCatalogId IN :ids")
    List<FoodAllergenTag> findByFoodCatalogIdIn(@Param("ids") Collection<Long> ids);

    @Query("SELECT f FROM FoodAllergenTag f WHERE f.foodCatalogId IN :ids AND f.allergenTag IN :tags")
    List<FoodAllergenTag> findByFoodCatalogIdInAndAllergenTagIn(
            @Param("ids") Collection<Long> ids,
            @Param("tags") Collection<AllergenTag> tags
    );

    List<FoodAllergenTag> findByFoodCatalogId(Long foodCatalogId);

    boolean existsByFoodCatalogIdAndAllergenTag(Long foodCatalogId, AllergenTag allergenTag);

    void deleteByFoodCatalogId(Long foodCatalogId);

    void deleteByFoodCatalogIdAndSource(Long foodCatalogId, AllergenDataSource source);

    @Query("SELECT DISTINCT f.foodCatalogId FROM FoodAllergenTag f")
    List<Long> findFoodIdsWithAnyAllergenTag();

    default void replaceBySource(Long foodCatalogId, AllergenDataSource source, List<FoodAllergenTag> tags) {
        deleteByFoodCatalogIdAndSource(foodCatalogId, source);
        flush();
        if (!tags.isEmpty()) {
            saveAll(tags);
        }
    }
}
