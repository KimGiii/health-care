package com.healthcare.domain.diet.admin;

import com.healthcare.domain.diet.entity.FoodCatalog.FoodCategory;
import com.healthcare.domain.diet.entity.FoodCatalogSource;

import java.time.OffsetDateTime;

/**
 * 재검증이 필요한 식품 카탈로그 항목. 운영자가 검수 우선순위를 판단하는 데 쓰인다.
 */
public record RevalidationQueueEntry(
        Long foodCatalogId,
        String name,
        FoodCategory category,
        FoodCatalogSource source,
        RevalidationReason reason,
        OffsetDateTime lastVerifiedAt,
        long usageCount
) {}
