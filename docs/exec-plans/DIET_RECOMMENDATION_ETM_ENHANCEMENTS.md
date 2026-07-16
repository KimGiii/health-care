# ETM 관찰 기반 식단 추천 보강 설계

작성일: 2026-06-20
상태: 설계 제안. **확정 합의·실행 순서는 `DIET_RECOMMENDATION_ENHANCEMENT_DECISIONS_2026-06-29.md`(4인 팀 결정 기록) 참조.** 정량값은 정책 버전·benchmark로 확정한다.
상위: `docs/exec-plans/DIET_RECOMMENDATION_OPTIMIZATION.md`, `docs/adr/0002-goal-aware-nutrition-optimization.md`
관찰 근거: `docs/references/EAT_THIS_MUCH_RECOMMENDATION_TEARDOWN_2026-06-20.md`

> **정정(2026-06-29, dev 최신 확인)**: 본 문서 초안의 두 전제가 dev 코드와 달랐다 — ① `ServingOptionDeriver`는 이미 존재한다(§3) ② 체중은 `User.weightKg`로 항상 접근 가능해 매크로 밴드는 `g/kg`로 간다(§1). 아래 본문에 반영.

## 0. 서비스 정체성 (전제)

이 서비스의 추천은 **"맛있는 음식 추천"이 아니라 "알러지·기피를 절대 위반하지 않으면서 목표 영양을 채우는 식단 추천"**이다. 일반 음식 추천은 우리 제품에서 가치가 없다. 따라서 아래 모든 보강은 다음 우선순위를 깨지 않는다.

1. **알러지·기피 hard 게이트가 최상위.** 어떤 보강도 알러지 위반 가능성을 만들지 않는다(`OPTIMIZATION.md` §5.1 검증 풀).
2. **실패를 숨기지 않는다.** 보강이 feasible 해를 줄이면 조용히 완화하지 않고 구조화 실패(`§8.3`)로 노출한다.
3. **결정성·재현성 유지.** 동일 입력 → 동일 결과(`rotationKey`·`stableKey`).

ETM은 ~50만 풀에서 느슨한 범위로 빠르게 샘플링해 실패를 거의 만들지 않는다. 우리는 반대로 **검증 풀이 작아도 안전·정확을 보장**한다. 아래 보강은 ETM의 좋은 아이디어를 우리 계약 안으로 들여오는 것이지, ETM의 느슨함을 모방하는 것이 아니다.

---

## 1. 매크로 min/max 밴드 × 목표 유형별 비대칭 결합

### 현재
`GoalAwareNutritionPolicy.resolve`는 목표별로 비대칭 제약을 두지만 **비핵심 매크로가 `unconstrained`** 다.

| 목표 | 칼로리 | 단백질 | 탄수 | 지방 |
|---|---|---|---|---|
| 감량·체형개선 | atMost 98% | atLeast 목표 | **무제한** | **무제한** |
| 근육증가 | between 100~110% | atLeast 목표 | **무제한** | **무제한** |
| 지구력 | atLeast 목표 | **무제한** | atLeast 목표 | **무제한** |
| 건강유지 | 95~105% | 90~110% | 90~110% | 90~110% |

문제: 감량인데 지방이 칼로리의 60%를 차지하는 조합도 hard로는 통과한다(단백질·칼로리만 보므로). ETM은 모든 매크로에 min/max 밴드를 둬 이런 극단을 구조적으로 막는다.

### 설계 — "방향성 hard + 가드레일"
ETM처럼 **전 매크로에 밴드**를 두되, ETM의 균일 범위가 아니라 **목표가 정의하는 매크로는 타이트하게, 나머지는 느슨한 가드레일**로 비대칭을 유지한다. `NutrientConstraint`는 이미 `between/atLeast/atMost`를 지원하므로 표현력은 충분하다 — 채울 값만 정하면 된다.

초기 가설값(g 기준, benchmark로 확정 · 의료 확정값 아님):

| 목표 | 칼로리 | 단백질 | 탄수 | 지방 |
|---|---|---|---|---|
| 감량·체형개선 | atMost 98% | **between** [목표, 2.2g/kg] | **atMost** ≤ 칼로리의 50% | **between** [0.5g/kg, 칼로리의 35%] |
| 근육증가 | between 100~110% | **between** [1.6, 2.2 g/kg] | **atLeast** (에너지 확보 하한) | **atLeast** 0.5g/kg(호르몬 하한) |
| 지구력 | atLeast 목표 | between [적정] | **atLeast** + 비중 하한 | atMost (지방 상한) |
| 건강유지 | 95~105% | 90~110% | 90~110% | 90~110% |

- **핵심 매크로**(목표를 정의: 감량=칼로리·단백질, 근육=단백질·칼로리, 지구력=탄수)는 hard로 둔다.
- **비핵심 매크로**는 극단만 막는 **넓은 가드레일**. 너무 좁히면 작은 검증 풀에서 feasible이 사라진다.
- 지방 하한(0.5g/kg)은 호르몬·지용성비타민 위해 모든 목표 공통 권장. 단, hard로 두면 실패가 늘 수 있어 **초기에는 soft**로 두고 benchmark에서 hard 승격 여부를 본다.

### hard vs soft 배치 원칙
- 검증 풀이 충분히 큰 매크로 차원만 hard로 올린다.
- 나머지 가드레일은 `ConstraintRecommendationEngine`의 `softScore` 중심거리에 이미 들어가는 항으로 유도(현재 `normalizedDistance`가 칼로리·단백질·탄수·지방 전부 본다).
- 즉 **"비핵심 매크로 = 넓은 hard 가드레일 + soft 중심 유도"** 조합이 ETM 밴드 효과를 내면서 실패율을 통제한다.

### 영향 파일
- `nutrition/policy/GoalAwareNutritionPolicy.java` — `resolve(goalType, targets, weightKg)`로 **체중 인자 추가** + 비핵심 매크로 밴드 채움. 밴드 단위는 **`g/kg` 채택**(탄수·지방 균형 상한 등 보조에만 `%E`).
- `nutrition/policy/NutritionPolicy.java` / `RemainingNutritionBudget.java` — 표현 그대로 사용(`NutrientConstraint` between/atLeast/atMost로 충분)
- 호출부 `usecase/DailyDietRecommendationUseCases.java` — `user.getWeightKg()` 전달. 체중은 `canCalculate`가 null이면 추천을 막으므로 정책 진입 시 non-null 보장(`NutritionTargets`엔 체중이 없지만 호출부가 `user`를 보유)
- benchmark 시나리오 추가(목표별 극단 조합 회피 검증)

---

## 2. 끼니 분배 재설계 — 끼니 수·목표 적응형

### 현재
`ConstraintRecommendationEngine.BASE_RATIOS`는 **고정 상수**(아침 0.25 : 점심 0.35 : 저녁 0.30 : 간식 0.10)이며, 선택된 끼니의 비율을 정규화만 한다. 끼니 **개수**나 **목표 유형**에 따라 구성이 달라지지 않는다. 단백질은 칼로리 비율과 같은 분배(`macroAim.scaled(mealRatio)`)를 따른다.

### 학술 근거 (요약)
- **끼니 수 자체는 대사 중립.** 총 칼로리·매크로가 같으면 끼니 빈도(1–2 / 3–4 / 5+)에 따른 체중·체성분 차이는 실질적으로 없음 — Schoenfeld BJ, Aragon AA, Krieger JW, *Effects of meal frequency on weight loss and body composition: a meta-analysis*, Nutr Rev 2015; eating-frequency 메타분석(IJBNPA 2023). → **끼니 수 구성은 대사 우위가 아니라 순응도·포만·단백질 분배로 정당화해야 한다.**
- **단백질 균등 분배가 유리.** 균등(30/30/30g)이 편중(10/15/65g)보다 24h 근단백합성 25%↑ — Mamerow MM et al., *Dietary Protein Distribution Positively Influences 24-h MPS*, J Nutr 2014. per-meal 포화 용량 ~0.4 g/kg, 최소 4끼로 1.6 g/kg/일 — Schoenfeld BJ, Aragon AA, *How much protein can the body use in a single meal*, JISSN 2018.
- **저녁 과편중 회피·아침 비중 유지.** 동일 칼로리에서 아침 과식이 저녁 과식보다 감량·혈당·포만 우위 — Jakubowicz D et al., Obesity 2013. 단 효과 크기는 보통이고 주로 포만/순응 측면 — Ruddick-Collins et al., PMC9605877(아침/저녁 로딩 간 총에너지소비·감량 차이 없음, 공복감만 아침이 낮음).

### 설계 원칙
1. **칼로리 분배는 끼니 수별 "현실적 비율 프로파일"로.** 대사 중립이므로 포만·순응 기준으로 정하고, **저녁을 최대로 두지 않는다**(ETM `size_slider` 저녁 125와 반대 방향). 현 우리 비율(점심 최대)은 근거상 합리적.
2. **단백질은 칼로리 비율과 분리해 끼니 간 균등 분배 + per-meal 하한**(목표가 근육·체형일 때 강하게). 현재 `macroAim.scaled(mealRatio)`는 단백질도 칼로리 비율을 따르므로, 칼로리가 작은 끼니의 단백질 하한이 무너진다 → 분리 필요.
3. **목표 유형이 분배를 조정**: 감량=아침/낮 front-load + 저녁 가볍게, 근육=단백질 끼니 균등 최우선, 지구력=운동 전후 탄수 배치(운동 기록 연계는 후속).

초기 가설 프로파일(칼로리 비율, benchmark로 확정):

| 끼니 수 | 구성(예: 비율) | 비고 |
|---|---|---|
| 2 | 0.45 / 0.55 (또는 아침·점심 중심) | 큰 두 끼. 단백질 끼니당 하한 큼 |
| 3 | 0.30 / 0.40 / 0.30 | 점심 최대, 저녁 비편중 |
| 4 | 0.27 / 0.35 / 0.28 + 간식 0.10 | 3끼 + 간식 1 |
| 5–6 | 메인 3 + 간식 2–3 | 단백질 끼니 균등 분배 강조 |

- 정량값은 상수로 흩뜨리지 않고 정책 버전으로 관리한다(`OPTIMIZATION.md` §3 컨벤션과 동일).
- per-meal 단백질 하한은 사용자 체중 기반(예: 근육 목표 0.4 g/kg/끼). 끼니 수가 많아 끼니당 하한 충족이 불가능하면 **조용히 낮추지 않고** 끼니 수 축소를 제안하거나 구조화 실패로 노출.

### 영향 파일
- `ConstraintRecommendationEngine.java` — `BASE_RATIOS` 상수를 끼니 수·목표 함수로 교체(예: `MealDistributionPolicy`로 추출), 단백질 분배를 칼로리 비율에서 분리, per-meal 단백질 하한 추가
- 필요 시 per-meal 하한 위반을 `RecommendationFailureReason`에 추가(예: `PROTEIN_PER_MEAL_INFEASIBLE`)
- benchmark: 끼니 수 2/3/4/5별 시나리오, 목표별 분배 검증

---

## 3. 이산 제공량 단위 (discrete servings)

### 현재
`FoodServingOption.ServingType`에 이미 `COUNT_UNIT`(1개, 2개)이 있다. 그러나 `DietRecommendationCandidate.verifiedServingGramOptions()`는 모든 검증 옵션을 **g 값으로 평탄화**하고, 엔진 `chooseServingCombination`은 g 옵션 조합을 탐색한다. `OFFICIAL_SERVING`의 0.5× 같은 분수 배수가 섞이면 "계란 0.5개"처럼 **이산 식품의 비현실적 분량**이 나올 수 있다(ETM `is_discrete`/`minimum_discrete_amount`가 막는 문제).

### 설계
> **정정(dev 최신)**: `ServingOptionSnapshot`의 `servingType`과 `ServingOptionDeriver`(diet/external/importer)는 **이미 존재**한다. 진짜 갭은 deriver가 모든 옵션을 `OFFICIAL_SERVING`으로만 생성해 `COUNT_UNIT`을 채우지 않고, 엔진이 servingType을 무시(g로 평탄화)하는 것이다.
- **이산 식품**(`COUNT_UNIT`): deriver에 **정수 배수 분기** 추가(1개=50g, 2개=100g … / 0.5개 금지).
- 엔진은 이산 식품의 양을 정수 단위 옵션에서만 고른다.
- 표시: g가 아니라 "2개"로 라벨(`label`/`labelKo`, iOS UX).
- `minimum_discrete_amount` 대응: 최소 1단위 미만 추천 금지.
- 데이터: 카탈로그에 "낱개 식품" 신호가 거의 없어 초기엔 카테고리 화이트리스트(계란·과일 일부)로 시작, 큐레이션 후속.

### 영향 파일
- `diet/external/importer/ServingOptionDeriver.java` — (이미 존재, 현재 `OFFICIAL_SERVING`만 생성) **`COUNT_UNIT` 정수배 분기 추가**
- `diet/entity/ServingOptionSnapshot.java` — `servingType` **이미 보존됨**(candidate까지 운반만)
- `DietRecommendationCandidate.verifiedServingGramOptions()` — 이산/연속 구분(또는 type 포함 반환)
- `ConstraintRecommendationEngine.chooseServingCombination` — 이산 식품 분수배 배제
- `dto/RecommendedFoodEntry.java` — 표시 단위(개수)

---

## 4. 끼니 역할 구분 (main / side)

### 현재
끼니 구성은 `PREFERRED_CATEGORIES`(아침=곡물/유제품/과일/단백 …)로 **카테고리**만 본다. ETM `main_dish`/`side_dish` 같은 **역할 축이 없어** "메인 1 + 사이드 N" 구조를 보장하지 못한다(예: 사이드만 3개로 한 끼가 구성될 수 있음).

### 설계
- 식품에 **역할** 축 추가: `MAIN` / `SIDE` / `EITHER`. 데이터 출처는 초기엔 `FoodCategory` 휴리스틱(밥·면·국·고기요리=MAIN, 나물·김치·반찬=SIDE)로 시작하고, 이후 큐레이션/메타태그로 정밀화.
- 끼니 채우기를 **역할 구성**으로: 메인 1개 우선 확보 후 사이드 보충(현재 `selectItems`/`mealFillVariations`에 역할 슬롯 도입).
- 한식 특성 반영: 한 끼 = 밥(메인) + 반찬 2–3(사이드)의 현실적 조합. 비현실 조합 방지(`OPTIMIZATION.md` §8 "최소 제약·동점 해소"와 일치).

### 영향 파일
- `diet/entity/FoodCatalog.java` 또는 파생 — 역할 분류(휴리스틱/큐레이션)
- `DietRecommendationCandidate.java` — `mealRole` 필드
- `ConstraintRecommendationEngine.java` — `PREFERRED_CATEGORIES`를 역할 슬롯 구성으로 보강
- benchmark: 끼니별 메인 존재·사이드 수 현실성 체크

---

## 5. 우선순위와 의존성

| 항목 | 가치 | 위험 | 권장 순서 |
|---|---|---|---|
| 1. 매크로 밴드×비대칭 | 추천 품질(극단 조합 제거) | feasible 감소 → 실패율 | **선행**(정책+benchmark로 안전 확인) |
| 3. 이산 제공량 | 현실성(분량 신뢰) | 낮음(국지적) | 선행 가능(독립적) |
| 2. 끼니 분배 재설계 | 목표 적합·단백질 분배 | 중간(엔진 핵심 변경) | 1 이후 |
| 4. 끼니 역할 main/side | 끼니 현실성 | 데이터 분류 품질 의존 | 2와 병행 |

공통: 모든 변경은 **benchmark baseline 대비 feasible율·목표 적합·다양성 회귀 검증**을 동반한다(`OPTIMIZATION.md` §11). 알러지 안전·실패 투명성·결정성은 불변.

> **확정 로드맵(2026-06-29, 4인 팀 합의)**: 위 가치/위험 순위에 더해 **⓪ 추천 자격 풀 확장(검증 큐)을 ① 매크로 밴드 앞 선결로 격상**(추천 통과 풀이 ~54개라 풀 확장 없이 밴드를 조이면 feasible이 깨짐). 릴리스 단위: **R1**(③ 이산 + 다중후보 버퍼링 + 추천 이벤트 식품별 매핑) → **R2**(① 매크로 밴드 g/kg) → **R3**(② 끼니 분배 + ④ 역할). **⑤ 알러지 강화는 단독 롤백 가능한 별도 트랙.** 상세는 `DIET_RECOMMENDATION_ENHANCEMENT_DECISIONS_2026-06-29.md`.

---

## 6. 참고 문헌

- Schoenfeld BJ, Aragon AA, Krieger JW. *Effects of meal frequency on weight loss and body composition: a meta-analysis.* Nutr Rev. 2015.
- *Eating frequency and body composition / cardiometabolic health: systematic review with meta-analysis.* Int J Behav Nutr Phys Act. 2023.
- Mamerow MM et al. *Dietary Protein Distribution Positively Influences 24-h Muscle Protein Synthesis in Healthy Adults.* J Nutr. 2014.
- Schoenfeld BJ, Aragon AA. *How much protein can the body use in a single meal for muscle-building? (per-meal 0.4 g/kg × ≥4 meals).* J Int Soc Sports Nutr. 2018.
- Jakubowicz D et al. *High caloric intake at breakfast vs. dinner differentially influences weight loss.* Obesity. 2013.
- Ruddick-Collins LC et al. *Timing of daily calorie loading affects appetite and hunger responses without changes in energy metabolism.* (PMC9605877).
- AHA Scientific Statement. *Meal Timing and Frequency: Implications for Cardiovascular Disease Prevention.* Circulation. 2017.
