package com.healthcare.domain.diet.repository;

import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.entity.FoodCatalogSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FoodCatalogRepository extends JpaRepository<FoodCatalog, Long>, JpaSpecificationExecutor<FoodCatalog> {

    /**
     * 모든 사용자가 접근 가능한 식품 카탈로그를 인기순(usageCount DESC)으로 조회한다.
     * - 글로벌 식품 (is_custom = false)
     * - 모든 사용자 등록 커스텀 식품 (is_custom = true, 공개 공용 카탈로그)
     */
    @Query("""
            SELECT f FROM FoodCatalog f
            WHERE (:category IS NULL OR f.category = :category)
              AND (:customOnly = FALSE OR f.isCustom = TRUE)
              AND (f.isCustom = TRUE OR f.canonicalGroupId IS NULL)
              AND (
                    :query IS NULL
                    OR LOWER(f.name)   LIKE LOWER(CONCAT('%', CAST(:query AS string), '%'))
                    OR LOWER(f.nameKo) LIKE LOWER(CONCAT('%', CAST(:query AS string), '%'))
                    OR LOWER(FUNCTION('replace', COALESCE(f.name, ''), ' ', ''))
                        LIKE LOWER(CONCAT('%', FUNCTION('replace', CAST(:query AS string), ' ', ''), '%'))
                    OR LOWER(FUNCTION('replace', COALESCE(f.nameKo, ''), ' ', ''))
                        LIKE LOWER(CONCAT('%', FUNCTION('replace', CAST(:query AS string), ' ', ''), '%'))
                    OR LOWER(FUNCTION('replace', COALESCE(f.searchAlias, ''), ' ', ''))
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

    /** 외부/배치 적재 시 source + foodCode 기준으로 기존 카탈로그 항목을 찾는다. */
    Optional<FoodCatalog> findBySourceAndFoodCode(FoodCatalogSource source, String foodCode);

    /**
     * dedup 클러스터 (group, nameKey)의 현재 대표(canonical) 행을 찾는다.
     * 대표 = canonical_group_id IS NULL. 부분 유니크 인덱스가 그룹·이름키당 1개를 보장한다.
     */
    @Query("""
            SELECT f FROM FoodCatalog f
            WHERE f.dedupGroup = :group
              AND f.dedupNameKey = :nameKey
              AND f.canonicalGroupId IS NULL
            """)
    Optional<FoodCatalog> findCanonical(
            @Param("group")   String dedupGroup,
            @Param("nameKey") String dedupNameKey
    );

    /** 같은 코드(group)의 모든 대표 행. 이름키가 다른 대표가 둘 이상이면 충돌(검토 대상)이다. */
    @Query("""
            SELECT f FROM FoodCatalog f
            WHERE f.dedupGroup = :group
              AND f.canonicalGroupId IS NULL
            """)
    List<FoodCatalog> findCanonicalsByGroup(@Param("group") String dedupGroup);

    /** 검토 큐: 같은 코드·다른 이름 충돌로 표시된 대표 행을 조회한다(커스텀 제외). */
    List<FoodCatalog> findByDedupStateAndIsCustomFalse(FoodCatalog.DedupState dedupState);

    /** 재검증 큐: 자동 자격 회수 등 특정 사유가 기록된 항목을 조회한다. */
    List<FoodCatalog> findByRecommendationReason(String recommendationReason);

    /** 표시명 수동 오버라이드용: 동일한 원본명(name)을 가진 모든 행을 조회한다(soft-delete 제외). */
    List<FoodCatalog> findByName(String name);

    /**
     * 표시명 재정규화 백필용: 원본 어순({@code 대분류_세분류})이 남아 있는 행을 id 커서 기준으로 조회한다.
     * id 오름차순 + afterId 커서로 매 배치가 전진하므로 재처리/무한루프가 없다.
     */
    @Query(value = """
            SELECT * FROM food_catalog
            WHERE id > :afterId
              AND name LIKE '%\\_%' ESCAPE '\\'
              AND deleted_at IS NULL
            ORDER BY id
            LIMIT :limit
            """, nativeQuery = true)
    List<FoodCatalog> findUnderscoreNamesAfterId(@Param("afterId") long afterId, @Param("limit") int limit);

    /** 중복 후보 리포트용: 사용자 커스텀 식품을 제외한 전체 카탈로그를 반환한다. */
    List<FoodCatalog> findByIsCustomFalse();

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

    /** 여러 식품 사용 횟수 일괄 증가 (N+1 방지) */
    @Modifying
    @Transactional
    @Query("UPDATE FoodCatalog f SET f.usageCount = f.usageCount + 1 WHERE f.id IN :ids")
    void incrementUsageCountBatch(@Param("ids") Collection<Long> ids);

    /** 여러 식품 사용 횟수 일괄 감소 (최소 0 보장, N+1 방지) */
    @Modifying
    @Transactional
    @Query("UPDATE FoodCatalog f SET f.usageCount = CASE WHEN f.usageCount > 0 THEN f.usageCount - 1 ELSE 0 END WHERE f.id IN :ids")
    void decrementUsageCountBatch(@Param("ids") Collection<Long> ids);
}
