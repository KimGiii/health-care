package com.healthcare.domain.diet.restriction.repository;

import com.healthcare.domain.diet.allergen.AllergenTag;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.restriction.entity.DietRestriction;
import com.healthcare.domain.diet.restriction.entity.DietRestriction.RestrictionType;
import com.healthcare.domain.diet.restriction.entity.DietRestriction.TargetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DietRestrictionRepository extends JpaRepository<DietRestriction, Long> {

    List<DietRestriction> findByUserIdAndDeletedAtIsNull(Long userId);

    @Query("""
            SELECT d FROM DietRestriction d
            WHERE d.userId = :userId
              AND d.restrictionType = :restrictionType
              AND d.targetType = 'FOOD'
              AND d.foodCatalogId = :foodCatalogId
              AND d.deletedAt IS NULL
            """)
    Optional<DietRestriction> findActiveFoodRestriction(
            @Param("userId") Long userId,
            @Param("restrictionType") RestrictionType restrictionType,
            @Param("foodCatalogId") Long foodCatalogId
    );

    @Query("""
            SELECT d FROM DietRestriction d
            WHERE d.userId = :userId
              AND d.restrictionType = :restrictionType
              AND d.targetType = 'CATEGORY'
              AND d.category = :category
              AND d.deletedAt IS NULL
            """)
    Optional<DietRestriction> findActiveCategoryRestriction(
            @Param("userId") Long userId,
            @Param("restrictionType") RestrictionType restrictionType,
            @Param("category") FoodCategory category
    );

    @Query("""
            SELECT d FROM DietRestriction d
            WHERE d.userId = :userId
              AND d.restrictionType = :restrictionType
              AND d.targetType = 'KEYWORD'
              AND d.keyword = :keyword
              AND d.deletedAt IS NULL
            """)
    Optional<DietRestriction> findActiveKeywordRestriction(
            @Param("userId") Long userId,
            @Param("restrictionType") RestrictionType restrictionType,
            @Param("keyword") String keyword
    );

    @Query("""
            SELECT d FROM DietRestriction d
            WHERE d.userId = :userId
              AND d.restrictionType = :restrictionType
              AND d.targetType = 'ALLERGEN_TAG'
              AND d.allergenTag = :allergenTag
              AND d.deletedAt IS NULL
            """)
    Optional<DietRestriction> findActiveAllergenTagRestriction(
            @Param("userId") Long userId,
            @Param("restrictionType") RestrictionType restrictionType,
            @Param("allergenTag") AllergenTag allergenTag
    );
}
