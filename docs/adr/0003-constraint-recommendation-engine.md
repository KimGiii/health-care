# 0003. 식단 추천 제약 최적화를 순수 Java 결정적 탐색으로 구현한다

## Status

Accepted

## Context

ADR-0002는 검증된 후보로 목표별 남은 영양량을 제약 최적화하기로 정했고, 실행 계획(`docs/exec-plans/DIET_RECOMMENDATION_OPTIMIZATION.md`) Phase 1~2에서 정책 계약·후보 자격·benchmark 기반을 갖췄다. 그러나 운영 추천 엔진(`DietRecommendationEngine`)은 여전히 "현재" 상태인 카테고리 greedy 선택이었다.

greedy 엔진은 끼니마다 독립적으로 후보를 정렬·선택한다. 끼니 간 동일 식품 회피는 `sortByScore`의 soft 정렬(우선순위 뒤로 밀기)일 뿐 hard 제약이 아니다. 그래서 후보 풀이 작거나 단백질 후보가 빈약하면 같은 식품(예: 달걀·돼지고기 안심)이 아침·점심·저녁에 반복됐다. 하루 영양 검증도 엔진 외부의 `±10%` 사후 점검(`checkTargets`)에 머물러 목표별 비대칭 hard constraint(`GoalAwareNutritionPolicy`)와 분리돼 있었다.

실행 계획 §8.1은 솔버 제품 선택을 별도 ADR과 prototype으로 결정하도록 미뤄 두었다.

## Decision

### 1. 솔버: 순수 Java 결정적 beam search

OR-Tools(CP-SAT)·외부 MILP 솔버를 도입하지 않고, 의존성 없는 순수 Java 결정적 탐색으로 구현한다(`ConstraintRecommendationEngine`).

근거:

- 후보 풀은 자격 hard filter·canonical 대표·제공량 전개를 거치면 수백 개 수준이라, beam search로 응답시간 예산(~150ms) 안에 충분히 탐색할 수 있다.
- benchmark 배포 차단 지표에 **동일 조건 재현성**이 있다. 결정적 탐색은 tie-break(rotationKey·stableKey)만 고정하면 재현성을 자연히 만족한다. 네이티브 솔버는 재현성 보장에 추가 작업이 필요하다.
- 네이티브 의존성(OR-Tools)은 빌드·배포·플랫폼 복잡도를 키운다. 현재 규모에 과하다(KISS/YAGNI).

향후 후보 규모나 제약이 커져 beam search가 품질·시간 한계에 부딪히면, 같은 입력 계약(`RemainingNutritionBudget` + 후보 + 실패 계약)을 유지한 채 솔버를 교체하는 새 ADR을 연다.

### 2. 입력은 정책 적용 후 차감한 남은 예산

엔진은 `NutritionTargets remaining`이 아니라 **`RemainingNutritionBudget`**을 받는다. 정책은 전체 일일 목표에 먼저 적용하고(`GoalAwareNutritionPolicy.resolve`) 확정 섭취를 차감한다(`RemainingNutritionCalculator`). 정책을 잔여 목표에 직접 적용하지 않는다(예: 감량 2,000kcal·600 섭취 → 상한 `1,960-600=1,360`, ✗ `1,400×0.98`).

### 3. hard constraint와 실패 계약

hard constraint: 목표별 열량 상·하한과 단백질·탄수화물 하한(budget), 영양 완전성, **같은 foodId 하루 1회(끼니 간 중복 금지)**, 선택 끼니별 최소 1개 식품.

반환하는 모든 해는 budget을 충족한다. 근접 식단을 성공처럼 노출하지 않는다. feasible 해가 없으면 구조화된 실패(§8.3)를 반환한다:

- 엔진 책임: `CALORIE_PROTEIN_CONFLICT`, `SERVING_OPTIONS_INFEASIBLE`, `REMAINING_MEALS_INFEASIBLE`, `SEARCH_LIMIT_REACHED`.
- use case 책임(후보 풀 진단으로 판정): `TARGET_PROFILE_MISSING`, `ALLERGEN_VERIFIED_POOL_INSUFFICIENT`, `NUTRIENT_DATA_INCOMPLETE`.

beam search는 "해 없음"을 증명하지 못하므로, 빔 폭·후보 수를 단계적으로 키우는 fallback(`BEAM_WIDTH_LADDER`, `VARIATIONS_LADDER`) 후에도 탐색 한도(`MAX_EXPANDED_STATES`)에 도달하면 `REMAINING_MEALS_INFEASIBLE`이 아니라 **`SEARCH_LIMIT_REACHED`**로 거짓 실패를 구분한다.

### 4. soft objective와 다양성

soft: 목표 중심값 거리, 끼니별 칼로리 분산, 단백질 소스 점유 penalty, 최근 섭취(`UserRepetitionPolicy`)·온라인 선호(`OnlinePreferencePolicy`, seam). 다양성은 feasible 해 안에서만 적용해 primary와 카테고리가 다른 해를 대안 상위로 회전한다.

각 해는 자신의 근거(`RecommendationRationale`, §10)를 보유한다. 대안을 상단으로 교체하면 합계와 근거도 함께 바뀐다.

### 5. 끼니 칼로리 배분

선택 끼니 비율을 합이 1이 되도록 정규화해 끼니 목표 합이 aim과 같아지게 한다(칼로리 하한 충족). 한 끼니 안에서는 칼로리 밀도 오름차순으로 순차 배분해, 저밀도 식품이 제공량 상한에 막혀 남긴 칼로리를 고밀도 식품이 흡수하도록 한다.

## Consequences

- 같은 식품이 끼니 간 반복되지 않는다(달걀·돼지고기 회귀 해소).
- 추천은 목표별 hard constraint를 충족하거나 명시적으로 실패한다. 근접 식단 fallback이 사라진다.
- greedy 엔진(`DietRecommendationEngine`)은 박제된 baseline 재현용으로 유지하고, 새 엔진은 `ConstraintEngineBenchmarkTest`로 검증한다.
- beam search는 exact가 아니므로 까다로운 joint 제약(예: 좁은 칼로리 밴드 + 높은 매크로 하한 + 빈약한 풀)에서는 구조화 실패를 반환할 수 있다. 이는 안전 계약상 허용되며, 후보 풀·정책·탐색 파라미터로 개선한다.
- 나트륨·당류·포화지방 soft 가중과 온라인 선호 실측 튜닝은 데이터 확보 후 후속(seam 유지).
