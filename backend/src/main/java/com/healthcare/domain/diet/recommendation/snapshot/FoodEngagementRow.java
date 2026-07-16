package com.healthcare.domain.diet.recommendation.snapshot;

/** 사용자·기간 창 내 식품별 이벤트 집계 행(JPQL 생성자 프로젝션). */
public record FoodEngagementRow(
        Long foodCatalogId,
        RecommendationEvent.EventType eventType,
        long count
) {}
