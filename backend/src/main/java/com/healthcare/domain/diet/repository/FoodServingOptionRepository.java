package com.healthcare.domain.diet.repository;

import com.healthcare.domain.diet.entity.FoodServingOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface FoodServingOptionRepository extends JpaRepository<FoodServingOption, Long> {

    List<FoodServingOption> findByFoodCatalogIdIn(Collection<Long> foodCatalogIds);

    List<FoodServingOption> findByFoodCatalogIdOrderBySortOrderAsc(Long foodCatalogId);

    boolean existsByFoodCatalogId(Long foodCatalogId);

    void deleteByFoodCatalogId(Long foodCatalogId);

    /**
     * dedup 백필용: 대표(canonical)가 아닌 비커스텀 행에 매달린 제공량 옵션을 일괄 제거한다.
     * 적재 시 각자 대표로 옵션이 생성됐다가 백필에서 패자로 강등된 구 대표 옵션을 정리해 옵션 폭증을 막는다
     * (설계 §7 — 대표 행에만 옵션 보유). 반환값은 삭제된 옵션 행 수다.
     */
    @Modifying
    @Query("""
            DELETE FROM FoodServingOption o
            WHERE o.foodCatalogId IN (
                SELECT f.id FROM FoodCatalog f
                WHERE f.isCustom = FALSE AND f.canonicalGroupId IS NOT NULL
            )
            """)
    int deleteOptionsForSupersededRows();
}
