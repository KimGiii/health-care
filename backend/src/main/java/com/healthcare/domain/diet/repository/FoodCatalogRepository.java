package com.healthcare.domain.diet.repository;

import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface FoodCatalogRepository extends JpaRepository<FoodCatalog, Long> {

    /**
     * 모든 사용자가 접근 가능한 식품 카탈로그를 인기순(usageCount DESC)으로 조회한다.
     * - 글로벌 식품 (is_custom = false)
     * - 모든 사용자 등록 커스텀 식품 (is_custom = true, 공개 공용 카탈로그)
     */
    @Query("""
            SELECT f FROM FoodCatalog f
            WHERE (:category IS NULL OR f.category = :category)
              AND (:customOnly = FALSE OR f.isCustom = TRUE)
              AND (
                    :query IS NULL
                    OR LOWER(f.name)   LIKE LOWER(CONCAT('%', CAST(:query AS string), '%'))
                    OR LOWER(f.nameKo) LIKE LOWER(CONCAT('%', CAST(:query AS string), '%'))
                    OR LOWER(FUNCTION('replace', COALESCE(f.name, ''), ' ', ''))
                        LIKE LOWER(CONCAT('%', FUNCTION('replace', CAST(:query AS string), ' ', ''), '%'))
                    OR LOWER(FUNCTION('replace', COALESCE(f.nameKo, ''), ' ', ''))
                        LIKE LOWER(CONCAT('%', FUNCTION('replace', CAST(:query AS string), ' ', ''), '%'))
                  )
            ORDER BY
              CASE
                WHEN :query IS NULL THEN 0
                WHEN LOWER(f.nameKo) LIKE LOWER(CONCAT(CAST(:query AS string), '%')) THEN 0
                WHEN LOWER(f.name) LIKE LOWER(CONCAT(CAST(:query AS string), '%')) THEN 0
                WHEN LOWER(FUNCTION('replace', COALESCE(f.nameKo, ''), ' ', ''))
                    LIKE LOWER(CONCAT(FUNCTION('replace', CAST(:query AS string), ' ', ''), '%')) THEN 1
                WHEN LOWER(FUNCTION('replace', COALESCE(f.name, ''), ' ', ''))
                    LIKE LOWER(CONCAT(FUNCTION('replace', CAST(:query AS string), ' ', ''), '%')) THEN 1
                ELSE 2
              END,
              f.usageCount DESC,
              COALESCE(f.nameKo, f.name) ASC
            """)
    List<FoodCatalog> searchAll(
            @Param("query")      String query,
            @Param("category")   FoodCategory category,
            @Param("customOnly") boolean customOnly
    );

    /** 커스텀 식품 중복 검사: 같은 이름(대소문자 무시) + 카테고리 조합 */
    @Query("""
            SELECT f FROM FoodCatalog f
            WHERE f.isCustom = TRUE
              AND LOWER(f.nameKo) = LOWER(:nameKo)
              AND f.category = :category
            """)
    Optional<FoodCatalog> findCustomByNameKoAndCategory(
            @Param("nameKo")   String nameKo,
            @Param("category") FoodCategory category
    );

    /** 동시성 안전 사용 횟수 증가 (원자적 UPDATE) */
    @Modifying
    @Transactional
    @Query("UPDATE FoodCatalog f SET f.usageCount = f.usageCount + 1 WHERE f.id = :id")
    void incrementUsageCount(@Param("id") Long id);

    /** 동시성 안전 사용 횟수 감소 (최소 0 보장) */
    @Modifying
    @Transactional
    @Query("UPDATE FoodCatalog f SET f.usageCount = CASE WHEN f.usageCount > 0 THEN f.usageCount - 1 ELSE 0 END WHERE f.id = :id")
    void decrementUsageCount(@Param("id") Long id);
}
