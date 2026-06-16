# 0001. 식단 추천 알러젠 회피 모델과 Strict 모드

## Status

Accepted

## Context

알러젠 기반 식단 추천은 백엔드 추천 후보 필터, iOS 추천 화면, 제품 문구가 같은 안전 계약을 공유해야 한다. 초기 PRD는 신뢰 레벨을 "특정 알러젠이 없다"는 주장에 두는 모델을 검토했지만, 현재 운영 데이터는 `food_allergen_tags`에 포함 알러젠만 저장한다.

포함 태그 모델에서 "태그가 없음"을 곧바로 안전으로 해석하면 위험하다. 예를 들어 베이글에 `WHEAT` 태그만 있고 우유/계란 검토가 끝나지 않았다면, 우유 알러지 사용자에게 잘못 추천될 수 있다. 반대로 모든 미검토 식품을 기본 모드에서 제외하면 추천 풀이 지나치게 작아진다.

## Decision

v1은 `food_allergen_tags`를 포함 태그 모델로 확정한다. 레코드가 존재하면 해당 식품에 그 알러젠이 포함된다는 뜻이다. 태그가 없다는 사실만으로 특정 알러젠이 없다고 단정하지 않는다.

`confidence_level`은 포함 태그의 검토 출처 신뢰도를 나타낸다.

- `DIRECT_VERIFIED`: 식약처 분류 등 결정적 단일재료 매핑
- `LABEL_DERIVED`: 라벨/공식 표시 기반
- `RECIPE_DERIVED`: 레시피 재료 분해 기반
- `UNKNOWN`: 검토 불명확

Strict 모드에 필요한 완결성은 `allergen_profile_verified`로 별도 표시한다. 이 값은 해당 식품의 알러젠 집합이 완결된 프로필로 검토되었다는 운영 신호다.

추천 판정은 다음과 같다.

- 기본 모드: 사용자가 제한한 알러젠과 매칭되는 포함 태그가 있으면 제외한다. 매칭 태그가 없으면 통과시키되, 알러젠 정보가 완전하지 않을 수 있음을 안내한다.
- Strict 모드: 매칭 포함 태그가 없어야 하며, `allergen_profile_verified=true`이고 `confidence_level`이 `DIRECT_VERIFIED` 또는 `LABEL_DERIVED`인 검토 레코드가 있어야 통과한다.

v1에서는 `allergen_profile_verified=true`를 `DIRECT_VERIFIED` 또는 `LABEL_DERIVED` 태그에만 허용한다. `RECIPE_DERIVED` 기반 프로필 완결성은 레시피 데이터 라이선스와 누락 위험을 별도로 검토한 뒤 후속 결정으로 연다.

## Consequences

Strict 모드를 켠 사용자는 추천 후보가 크게 줄 수 있다. 이는 의도된 실패이며, 후보 부족은 `BUSINESS_RULE_VIOLATION`으로 안내한다.

브랜드 공식 메뉴나 라벨 기반 데이터 적재 시에는 알러젠 전체 프로필을 검토한 항목만 `allergen_profile_verified=true`로 넣어야 한다.

이 모델은 의료적 안전 보증이 아니다. 교차오염과 매장별 원재료 차이는 보장하지 않으며, iOS와 제품 문구는 이를 단정하지 않는다.

향후 "없음 주장"을 식품-알러젠별로 저장하는 모델이 필요해지면 별도 테이블과 ADR로 확장한다.
