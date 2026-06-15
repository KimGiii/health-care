# 0002. 식단 추천 후보 정책을 후보 풀 모듈에 둔다

## Status

Accepted

## Context

식단 추천은 식품 카탈로그 전체를 후보로 쓰지 않는다. 추천 적합성 상태, 사용자 제한 조건, 알러젠 신뢰 게이트를 통과한 일부 식품만 추천 엔진에 전달해야 한다.

기존 흐름에서는 `DailyDietRecommendationUseCases`가 JPA `Specification`과 `Sort`를 조립하고, `DietRecommendationEngine`이 제한 조건과 알러젠 필터를 한 번 더 적용했다. 이 구조는 추천 후보 정책의 Interface가 UseCase, repository specification, Engine에 나뉘어 있어 Phase 4의 핵심 계약인 "추천 후보는 전체 식품 카탈로그가 아니다"를 한 곳에서 테스트하기 어렵게 만들었다.

## Decision

식단 추천 후보 정책의 테스트 표면을 `DietRecommendationCandidatePool`로 둔다.

`DailyDietRecommendationUseCases`는 사용자, 목표, 제한 조건, 응답 조립을 담당하고, 후보 조회와 후보 필터링은 `DietRecommendationCandidatePool`을 호출한다. `DietRecommendationEngine`은 후보가 이미 추천 가능하다는 전제에서 끼니 구성, 제공량 계산, 날짜 기반 rotation, 응답 항목 생성을 담당한다.

## Consequences

- 추천 적합성 상태, FOOD/CATEGORY 제한의 DB 조건, KEYWORD 제한, 알러젠 신뢰 게이트는 `DietRecommendationCandidatePoolTest`에서 검증한다.
- UseCase 테스트는 repository `Specification` 세부 모킹 대신 후보 풀 Module Interface를 목으로 둔다.
- Engine 테스트는 후보 필터링이 아니라 끼니 구성과 응답 항목 생성에 집중한다.
- 후속으로 추천 후보 조회 성능, 후보 부족 진단, 추천 큐레이션 오버레이가 필요해지면 먼저 `DietRecommendationCandidatePool`의 Interface를 확장할지 검토한다.
