package com.healthcare.domain.diet.repository;

import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FoodCatalogRepository extends JpaRepository<FoodCatalog, Long> {

    /**
     * 사용자에게 접근 가능한 식품 카탈로그를 조회한다.
     * - 글로벌 식품 (created_by_user_id IS NULL)
     * - 해당 사용자의 커스텀 식품 (created_by_user_id = userId)
     * 선택적 필터: name 검색, category, customOnly
     */
    @Query("""
            SELECT f FROM FoodCatalog f
            WHERE (f.createdByUserId IS NULL OR f.createdByUserId = :userId)
              AND (:category IS NULL OR f.category = :category)
              AND (:customOnly = FALSE OR f.isCustom = TRUE)
              AND (
                    :query IS NULL
                    OR LOWER(f.name)   LIKE LOWER(CONCAT('%', CAST(:query AS string), '%'))
                    OR LOWER(f.nameKo) LIKE LOWER(CONCAT('%', CAST(:query AS string), '%'))
                  )
            ORDER BY f.isCustom ASC, f.name ASC
            """)
    List<FoodCatalog> findAccessibleToUser(
            @Param("userId")     Long userId,
            @Param("query")      String query,
            @Param("category")   FoodCategory category,
            @Param("customOnly") boolean customOnly
    );
}
