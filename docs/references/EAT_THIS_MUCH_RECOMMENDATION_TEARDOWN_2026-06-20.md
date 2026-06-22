# Eat This Much 추천 알고리즘 역설계 ↔ 우리 추천 엔진 대비

작성일: 2026-06-20
방법: `eatthismuch.com` 무료 데모 생성기의 실제 UI·네트워크 동작을 헤드리스 브라우저로 직접 관찰(요청 페이로드·응답 본문·후속 호출 캡처)
목적: 외부 레퍼런스 동작을 우리 설계 의도와 나란히 두고, 채택 검토 후보와 명시적 비채택을 구분한다
관련 문서: `docs/exec-plans/DIET_RECOMMENDATION_OPTIMIZATION.md`, `docs/exec-plans/DIET_RECOMMENDATION_RESTRICTIONS.md`, `docs/adr/0002-goal-aware-nutrition-optimization.md`

> 경쟁 제품의 공개 UI·네트워크 동작 관찰에 기반한 추정이다. 서버 내부 알고리즘은 비공개이며, 여기 기술한 ETM 동작은 관찰 가능한 입출력에서 역추론한 것이다.

---

## 1. ETM 관찰 결과 (요약)

### 1.1 네트워크 구조 — 단일 엔드포인트 + 2단계 로딩

```
POST /g/generate/day-json/?num_results=10&HTTP_BACKEND_VERSION=16   (~26KB, 2.6~3.8s)
  → {data:{results[10], food_infos}, warnings, meta, success, errors}
GET  /api/v1/food/{food_id}/?HTTP_BACKEND_VERSION=16                (음식 상세 lazy 조회)
```

- `num_results=10`: 한 호출로 **완성된 하루 식단 10개**를 반환. 화면엔 1개만 표시하고 나머지는 "Regenerate Day"용 버퍼 → 재생성이 네트워크 대기 없이 즉시.
- generate 응답은 골격(`food_id` + 양 + 끼니 배치)만 담고, `food` 필드는 객체가 아니라 URI 문자열(`/api/v1/food/{id}/`). 화면에 보이는 후보의 음식 상세만 개별 GET으로 lazy 로드.

### 1.2 입력(요청 페이로드)

```jsonc
{
  "swole_user": { "activity_level":1.2, "goal":"M", "bodyfat":20,
                  "preset_diet":"anything", "use_partial_servings":false,
                  "exclusions":null, "generator_focus":0, "units":"I" },
  "days": [{
    "nutrition_profile": {                  // 목표 = 매크로별 min/max 범위
      "calories":2200,
      "min_carbs":110, "max_carbs":275,     // 2200kcal 기준 각 매크로 약 20%~50%
      "min_fats":48.9, "max_fats":122.2,
      "min_proteins":110, "max_proteins":275,
      "fiber":25, "macro_scheme":"grams" },
    "meals": [{ "meal_type": {              // 끼니별 제약
      "title":"Dinner", "size_slider":125,  // 아침100 : 점심100 : 저녁125 = 칼로리 분배 가중
      "preferred_food_types":1, "max_totaltime":30,
      "num_foods_per_meal":0,               // 0 = 자동
      "complexity":2 } }]
  }]
}
```

핵심: 목표 칼로리·매크로가 **단일 목표가 아니라 min/max 허용 범위**. 알고리즘은 "정확히 맞추기"가 아니라 "범위를 만족하는 조합 탐색"이다(목표 2200 → 실제 2191).

### 1.3 음식 데이터 모델 — 선택 신호

| 범주 | 필드 |
|---|---|
| 랭킹/인기 | `score`(음식별), `curated`, `num_favorites/ratings` |
| 다양성/반복 | `default_frequency`, `average_frequency`, `num_frequency_adjustments` |
| 끼니 적합성 | `is_breakfast/lunch/dinner/snack/dessert`, `preferred_food_types` |
| 끼니 역할 | `main_dish` / `side_dish` |
| 포션 스케일 | `min/max_servings`, `is_discrete`, `minimum_discrete_amount` |
| 실용 필터 | `complexity`, `blatantly_unhealthy`, `perishable`, `keeps_well`, `needs_*`(오븐/스토브/블렌더…) |
| 음식 낭비 | `makes_leftovers_for` / `uses_leftovers_from`(한 끼 조리분을 다른 끼니 재사용) |

포션은 `scaled_amount` 0.25 / 0.5 / 1 / 2 같은 이산값이며, `is_discrete`/`minimum_discrete_amount`로 쪼갤 수 없는 음식(예: 계란)을 처리한다.

---

## 2. 우리 추천 엔진 동작 (현재 구현)

코드 기준: `backend/.../diet/recommendation/`

### 2.1 파이프라인 — `DailyDietRecommendationUseCases.recommend`

1. 사용자 영양 계산 가능 여부 확인(`NutritionCalculator.canCalculate`) → 불가 시 `TARGET_PROFILE_MISSING`
2. `DietRestriction`(영속 알러지·기피) 로드 + `strictAllergyMode`(요청 플래그)
3. Goal 기반 `NutritionTargets` 계산 → 오늘 섭취(`todayLogs`) 차감 → `remainingTargets`
4. 남은 칼로리 ≤ 0이면 `alreadyMet`
5. `candidatePool.load(restrictions, strictAllergyMode)` → 알러젠·제약 필터 후보 풀(탈락 원인은 `CandidatePoolDiagnostics`로 추적)
6. `eligible` = `macroDataComplete` ∧ `hasVerifiedServingOptions`만 통과
7. `RemainingNutritionBudget`(목표 정책에 확정 섭취 차감) 계산
8. `UserRepetitionPolicy`(최근 7일 섭취 음식 penalty) 구성
9. `engine.recommend(...)` → primary + alternatives, 스냅샷 저장

### 2.2 엔진 — `ConstraintRecommendationEngine` (결정적 beam search)

- 외부 솔버 없는 순수 Java beam search(ADR-0003). 남은 하루 전체를 hard constraint(`RemainingNutritionBudget`)에 맞춰 공동 최적화.
- **불변식**: 반환 해는 전부 budget 충족(근접 식단을 성공처럼 노출 안 함) / 같은 `foodCatalogId`는 하루 1회(끼니 간 중복 hard 금지) / 동일 입력 → 동일 결과(`rotationKey`·`stableKey` tie-break).
- 끼니 칼로리 분배 `BASE_RATIOS` = 아침 0.25 : 점심 0.35 : 저녁 0.30 : 간식 0.10.
- 끼니 선호 카테고리 `PREFERRED_CATEGORIES`(아침=곡물/유제품/과일/단백, 점심·저녁=단백/곡물/채소 …).
- 제공량은 `verifiedServingGramOptions`(검증된 1회 제공량: 0.5/1/1.5/2회)에서만 선택 → 비현실적 분량(과일 500g 등) 방지.
- beam ladder(24/48/96, variations 8/12/16): feasible 0이면 단계적으로 확대, truncate되면 `SEARCH_LIMIT_REACHED`.
- soft score = 목표중심거리 + 끼니칼로리분산 + 반복 penalty + 온라인 penalty + 단백질소스 점유 penalty. 다양성은 hard 통과 해 안에서 새 카테고리 많은 대안을 상위로.

### 2.3 학습 신호 (soft, hard 아님)

- `UserRepetitionPolicy`: 최근 7일 섭취 음식에 penalty(반복 완화).
- `OnlinePreferencePolicy`: 추천→기록 전환(우대) − 다시 추천 회피(불이익). **현재 use case는 `noSignals()`로 미연결** — 메커니즘만 완료, 가중치 튜닝은 운영 이벤트 축적 후(계획 §11~§12 Phase 5).

---

## 3. 축별 대비

| 축 | Eat This Much | 우리 앱 |
|---|---|---|
| 결정성 | 확률적(매 호출 다른 10후보) | **결정적**(동일 입력→동일 결과, `rotationKey`) |
| 후보 생성 | `num_results=10` 완성 후보 미리 생성, 클라 버퍼 | `maxSolutions = 1 + alternativeCount`(primary + 대안) |
| 목표 표현 | 매크로 min/max 범위(균일·느슨) | **목표 유형별 비대칭** hard band(`GoalAwareNutritionPolicy`) |
| 남은 영양 | 클라가 day 전체를 보냄(섭취 차감은 세션/클라 측) | **서버 권위**로 섭취 차감 후 남은 목표 계산(`RemainingNutritionCalculator`) |
| 음식 풀 | ~50만 전체 활용 | **검증 후보만**(macro 완전 + 검증 제공량 + 알러젠 검증) |
| 알러지/제외 | `swole_user.exclusions`(클라 단일 필드) | `DietRestriction` 영속 + `strictAllergyMode` + 검증 풀 + 탈락 진단 |
| 제공량 | `scaled_amount` 0.25/0.5/1/2, `is_discrete` | `verifiedServingGramOptions`(0.5/1/1.5/2회) |
| 끼니 분배 | `size_slider`(아침100:점심100:저녁125) | `BASE_RATIOS`(0.25:0.35:0.30:0.10) |
| 끼니 적합 | `is_breakfast` 등 + `preferred_food_types` | `PREFERRED_CATEGORIES`(카테고리 기반) |
| 끼니 내 중복 | 후보 내 회피, **후보 간 중복 허용** | 하루 같은 food **1회 hard 금지** |
| 학습 신호 | 음식 **글로벌 통계**(`default_frequency`/`score`/`favorites`) | **사용자별 이벤트**(반복 7일 + 온라인 전환/refresh) |
| 실패 처리 | 빈 결과(범위 느슨·풀 커서 거의 안 남) | **구조화 실패 6종**, 숨기거나 조용히 완화 안 함 |
| 음식 낭비 | leftovers 재사용 | 없음(로드맵 밖) |
| 탐색 | 비공개(범위 제약 + 샘플링 추정) | 결정적 beam search(ladder) |

---

## 4. 설계 철학 대비

- **ETM = 다양성·속도·규모.** 큰 풀에서 느슨한 매크로 범위를 만족하는 조합을 빠르게 여럿 샘플링한다. 매번 새로움을 주고, 인기는 음식 레코드의 글로벌 통계로 학습한다. 범위가 넓어 실패를 거의 만들지 않는다.
- **우리 앱 = 안전·검증·설명가능.** 알러지 위반 불가가 1순위이고, hard constraint 충족을 보장하며, 실패를 숨기지 않는다. 검증된 후보만 쓰고 결과는 결정적·재현 가능하다. 학습은 사용자별 이벤트를 우선한다(계획 §1 계약).

두 제품은 같은 문제(매크로 범위 만족 + 포션 스케일 + 끼니 적합 + 빈도/선호 랭킹)를 푼다. 갈림길은 **"실패를 없애려 범위를 넓힐 것인가(ETM)"** vs **"범위를 좁혀서라도 안전·검증·투명을 보장할 것인가(우리)"** 다.

---

## 5. 채택 검토 후보 (우리 계약과 충돌하지 않는 것)

> 아래 후보의 구체 설계(매크로 밴드×비대칭 결합, 끼니 수·목표 적응형 분배 + 학술 근거, 이산 제공량, 끼니 역할)는 `docs/exec-plans/DIET_RECOMMENDATION_ETM_ENHANCEMENTS.md`에 정리했다.

1. **다중 후보 버퍼링.** ETM `num_results=10`이 "다시 추천" 즉시성을 만든다. 우리 `alternativeCount`를 늘려 미리 생성해 두면 iOS의 대안 식단 refresh 지연을 없앨 수 있다. 우리 엔진은 이미 ranked 대안을 만들므로 비용이 낮다. → `DailyDietRecommendationUseCases` `maxSolutions` 상향 + iOS 프리페치.
2. **이산 제공량 단위.** ETM `is_discrete`/`minimum_discrete_amount`로 쪼갤 수 없는 음식(계란 1개, 김 1장)을 표현한다. 우리 `FoodServingOption`에 이산 step 개념을 보강하면 검증 제공량의 현실성이 올라간다. → `FoodServingOption` / `verifiedServingGramOptions`.
3. **끼니 역할(main/side) 구분.** ETM `main_dish`/`side_dish`로 메인+사이드 조합을 만든다. 우리 `PREFERRED_CATEGORIES`(카테고리)를 역할 축으로 보강하면 끼니 구성이 더 자연스러워진다. → `ConstraintRecommendationEngine` 끼니 채우기.
4. **끼니별 칼로리 가중 노출.** ETM `size_slider`처럼 끼니 비율을 사용자가 조정하게 노출할 수 있다. 우리 `BASE_RATIOS`는 상수이므로, 사용자 끼니 패턴(아침 거름 등)을 반영하는 가변 비율 검토. → 정책/요청 파라미터.
5. **글로벌 빈도 신호 보강.** 우리 `OnlinePreferencePolicy`(사용자별)에 더해, ETM `average_frequency` 같은 **전역 인기/적정빈도**를 cold-start soft 신호로 쓸 수 있다(사용자 이벤트가 적을 때). hard 아님 유지.

---

## 6. 명시적으로 채택하지 않을 것

- **느슨한 균일 범위 + 확률 샘플링.** 우리의 목표 유형별 비대칭 hard constraint·결정성·실패 투명성 계약과 충돌한다(계획 §1.4, §8.3).
- **클라이언트가 남은 영양/프로필을 계산해 보내는 구조.** 우리는 남은 영양 계산을 서버 권위로 유지한다(계획 §4). 클라 신뢰는 알러지 안전과 충돌.
- **검증 안 된 전체 풀 투입.** 알러지 등록 사용자는 완결 프로필 검증 후보만 통과한다(계획 §5.1). ETM처럼 전체 풀을 그대로 쓰지 않는다.
- **leftovers(음식 재사용).** 흥미로운 차별화 기회지만 현재 로드맵 밖(계획 §13 제외 범위 성격). 별도 제안으로만 기록.

---

## 7. 참고

- 관찰 원본 요약(메모리): `etm_reverse_engineering`
- 우리 엔진 계약·로드맵: `docs/exec-plans/DIET_RECOMMENDATION_OPTIMIZATION.md` §1, §5, §8
- 제약 엔진: `backend/src/main/java/com/healthcare/domain/diet/recommendation/engine/ConstraintRecommendationEngine.java`
- 오케스트레이션: `backend/src/main/java/com/healthcare/domain/diet/recommendation/usecase/DailyDietRecommendationUseCases.java`
- 학습 정책: `OnlinePreferencePolicy.java`, `UserRepetitionPolicy.java`
