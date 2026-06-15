# 0003. 추천 큐레이션 불변 조건을 값 객체 모듈에 둔다

## Status

Accepted

## Context

식품 카탈로그의 추천 상태는 `recommendation_status`와 `recommendation_reason` 두 DB 필드로 저장된다. 하지만 도메인 규칙은 두 필드가 독립적이지 않다. `RECOMMENDABLE_WITH_CAUTION`은 주의 사유가 반드시 필요하고, 다른 상태의 사유는 저장하거나 추천 응답에 노출하지 않는다.

Phase 4에서 추천 응답에 `caution` 필드를 추가하면서 CSV 적재, DB row 재구성, 추천 응답 생성이 모두 이 규칙을 알아야 했다. 호출자가 직접 `WithCaution`인지 검사하면 추천 큐레이션 오류 모드가 여러 모듈에 흩어진다.

## Decision

추천 큐레이션 불변 조건의 테스트 표면을 `RecommendationCuration` 값 객체 모듈에 둔다.

CSV 운영 입력은 `RecommendationCuration.fromImport()`를 통과해 검증한다. DB 저장에는 `reasonForStorage()`를 사용하고, 추천 응답 노출에는 `cautionForResponse()`를 사용한다. 호출자는 `RECOMMENDABLE_WITH_CAUTION`인지 직접 switch하지 않는다.

## Consequences

- `RECOMMENDABLE_WITH_CAUTION` 사유 필수 조건과 255자 길이 조건은 `RecommendationCurationTest`에서 검증한다.
- `BrandMenuCsvImporter`는 CSV row를 정규화한 뒤 추천 큐레이션 모듈의 검증 결과만 해석한다.
- `RecommendedFoodEntry`는 추천 큐레이션 세부 타입을 알지 않고 응답용 주의 문구만 요청한다.
- 후속으로 큐레이션 상태가 늘어나면 `RecommendationCuration`의 Interface를 먼저 확장하고, CSV Adapter와 추천 응답은 그 Interface를 따라간다.
