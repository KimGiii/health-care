package com.healthcare.domain.diet.repository;

import com.healthcare.domain.diet.entity.FoodCatalog;
import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collection;

/** FoodCatalog 조회용 JPA Specification 팩토리. */
public final class FoodCatalogSpecs {

    private FoodCatalogSpecs() {}

    /** 추천에 쓸 수 있는 식품: 칼로리 정보가 반드시 있어야 함. */
    public static Specification<FoodCatalog> hasCalories() {
        return (root, query, cb) -> cb.isNotNull(root.get("caloriesPer100g"));
    }

    /** 지정한 식품 ID 목록을 제외한다 (FOOD 타입 제한 적용). */
    public static Specification<FoodCatalog> idNotIn(Collection<Long> ids) {
        return (root, query, cb) -> root.get("id").in(ids).not();
    }

    /** 지정한 카테고리를 제외한다 (CATEGORY 타입 제한 적용). */
    public static Specification<FoodCatalog> categoryNotIn(Collection<FoodCategory> categories) {
        return (root, query, cb) -> root.get("category").in(categories).not();
    }
}
