# 0004. 식단 추천 엔진은 후보 값 객체만 입력으로 받는다

## Status

Accepted

## Context

식단 추천 후보 정책을 `DietRecommendationCandidatePool`로 모았지만, `DietRecommendationEngine`의 Interface에는 여전히 `FoodCatalog`, `FoodAllergenTag` 맵, `strictMode`가 남아 있었다.

이 구조에서는 후보 풀을 통과한 결과를 넘기면서도 호출자가 알러젠 태그 맵과 strict mode를 함께 전달해야 하므로, "후보 정책은 후보 풀에 있다"는 결정이 Engine 호출부에서 다시 흐려졌다. 또한 Engine 테스트가 JPA 엔티티 생성 방식과 추천 큐레이션 세부 필드에 묶였다.

## Decision

`DietRecommendationEngine`은 `DietRecommendationCandidate` 목록만 입력으로 받는다.

`DietRecommendationCandidatePool`은 `FoodCatalog`와 알러젠 태그 조회 결과를 바탕으로 `DietRecommendationCandidate`를 만든다. 이 값 객체에는 Engine이 끼니 구성, 제공량 계산, 날짜 기반 rotation, 응답 항목 생성에 필요한 식품 ID, 이름, 카테고리, 영양값, 사용 횟수, 안정적인 key, 알러젠 신뢰도, caution만 담는다.

Engine은 더 이상 `FoodCatalog`, `FoodAllergenTag`, `AllergenConfidenceGate`, `strictMode`를 알지 않는다.

## Consequences

- 알러젠 신뢰도와 caution 해석은 후보 풀과 큐레이션 모듈의 책임으로 남는다.
- Engine 테스트는 JPA 엔티티 생성 없이 후보 값 객체로 끼니 구성 규칙만 검증한다.
- `RecommendedFoodEntry` 생성은 후보 값 객체를 기준으로 하며, persistence 필드나 DB 상태를 다시 조회하지 않는다.
- 후속으로 추천 점수, 후보 부족 진단, 후보 설명이 필요하면 먼저 `DietRecommendationCandidate`의 공개 필드를 확장할지 검토한다.
