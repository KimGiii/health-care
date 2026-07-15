# 식단 추천 고도화 — 4인 팀 결정 기록

작성일: 2026-06-29
브랜치: dev (origin/dev 최신화 후 논의)
방법: 브랜드 디렉터 · 백엔드 개발자 · 기획자 · 영양관리사 4개 직군을 서브에이전트로 구성해 dev 코드/문서 기준 라운드테이블 진행
관련: `DIET_RECOMMENDATION_ETM_ENHANCEMENTS.md`(보강 설계), `DIET_RECOMMENDATION_OPTIMIZATION.md`(상위 계획), `docs/references/EAT_THIS_MUCH_RECOMMENDATION_TEARDOWN_2026-06-20.md`(경쟁 분석)

> 이 문서는 식단 추천 고도화의 **확정 합의·실행 순서·역할별 입력**을 기록한다. 설계 상세는 ENHANCEMENTS, 정량값은 정책 버전·benchmark로 확정한다(의료 확정값 아님).

---

## 0. 배경

5개 보강 후보(① 매크로 밴드×비대칭, ② 끼니 수·목표 적응형 분배, ③ 이산 제공량, ④ 끼니 역할 main/side, ⑤ 알러지 안전 강화)를 어떻게 진행할지 직군별로 검토했다. 1차 논의는 prod 브랜치에서 진행돼 설계 문서가 보이지 않아 일부 전제가 틀렸고, dev 최신 기준으로 재논의해 바로잡았다.

## 1. 정정된 전제 (1차 prod 논의 → dev 최신)

| 1차 "발견 제약" | dev 최신 실제 | 결론 |
|---|---|---|
| `NutritionTargets`에 체중 없음 → `g/kg` 밴드 불가 | 체중은 `User.weightKg`로 항상 있고, `NutritionCalculator`가 이미 `weightKg × proteinPerKg`로 단백질 산출. `canCalculate`가 null이면 추천을 막아 정책 진입 시 non-null 보장 | **`g/kg` 채택 가능** |
| `ServingOptionDeriver` 미존재 | 존재함(117줄+테스트). 단 `OFFICIAL_SERVING`만 생성하고 `COUNT_UNIT` 미채움 | ③ 갭은 "deriver의 COUNT_UNIT 분기"로 축소 |
| 검증 풀이 막연한 병목 | dedup 전량적재로 노출 풀 canonical ~32만, 단 **추천 자격 통과 풀(알러젠 검증 + 제공량 + 매크로 완전)은 ~54개** | 병목이 "데이터 양"→"검증 큐 사람 처리량"으로 이동 |

## 2. 확정 결정 (전원 합의)

- **D1. 한 릴리스 = 한 축만 조인다.** ①(매크로 밴드)+⑤(알러지)를 동시에 강화하면 작은 검증 풀에서 feasible이 붕괴한다.
- **D2. 진행 순서: ③ → ① → ②+④**, ⑤는 별도 트랙.
- **D3. ⓪ 추천 자격 풀 확장(검증 큐)을 ① 앞 선결로 격상.** 추천 통과 풀이 ~54개라 풀 확장 없이 밴드를 조이면 깨진다. 검증 우선순위 공식(`OPTIMIZATION.md` §5.3)을 알고리즘과 동급 자원으로 운영.
- **D4. soft → hard 단계 승격.** 비핵심 매크로·per-meal 단백질 하한·끼니 역할은 soft로 시작, 매 단계 `RecommendationBenchmarkRegressionGate`(이미 존재) 회귀 0일 때만 hard 승격.
- **D5. 실패는 "못함 → 안함(안전 위해)" + 조정 가능 액션.** 알러지·목표 절대제약 완화는 절대 제안하지 않는다.
- **D6. 과장 광고 금지.** "자주 먹으면 살 빠진다"(끼니 수는 대사 중립이라 거짓)·의료 효능 표방 전면 차단. 영양사 가드레일을 카피 승인 게이트로.

## 3. 선결 결정 — 매크로 밴드 표현은 `g/kg`

- **채택: `g/kg`** (보조로 `%E`). 근거:
  - 체중이 항상 접근 가능(`User.weightKg`, `canCalculate` non-null 보장)
  - 엔진 단백질 hard 하한이 이미 `weightKg × proteinPerKg` 산출이라 단위 정합
  - 구현은 `GoalAwareNutritionPolicy.resolve(goalType, targets, weightKg)` 인자 1개 추가(인프라 신설 0)
- **`%E` 보조 적용**: 탄수·지방 *균형 상한*, 지구력 탄수 *비중 하한*처럼 "총열량 대비"가 자연스러운 곳에만.
- 영양사 단서: 비만 사용자는 실체중 g/kg가 과대 → 보정체중(adjusted BW) 규칙은 후속 합의. 체중 결측 시 추정 금지, `proteinTargetG`(이미 체중 반영된 절대값)를 fallback hard로.

## 4. 실행 로드맵

```
[상시 트랙 ⓪] 검증 자격 풀 확장 — 데이터 운영
   알러젠 완결 프로필·제공량 검증 큐 처리량(§5.3 우선순위 공식). ①의 선결이자 모든 보강의 상한.
   대시보드: /admin/diet/candidate-pool/verification-priorities

[R1 · 현실성]  위험 낮음, 먼저
   ③ 이산 제공량 (ServingOptionDeriver COUNT_UNIT 정수배 분기 + 엔진 분수배 배제, "계란 2개" 표시)
   + 다중 후보 버퍼링 (maxSolutions 상향 + iOS 프리페치 = "다시 추천" 즉시성, 결정성 유지)
   + ★추천 이벤트 식품별 매핑 보강★ (KPI 측정의 전제 — 없으면 전환율·재추천사유·온라인튜닝 측정 불가)

[R2 · 정확성]  feasible 위험 중, 단독 출시
   ① 매크로 밴드 × 비대칭 (g/kg). 핵심 매크로만 hard, 비핵심은 넓은 가드레일+soft 중심유도,
      지방 하한은 초기 soft → benchmark 후 hard 승격

[R3 · 자연스러움]  엔진 핵심 변경
   ② 끼니 수·목표 적응형 분배 (단백질을 칼로리 비율에서 분리 + per-meal 하한)
   + ④ 끼니 역할 main/side (데이터 의존 겹쳐 병행, 휴리스틱→큐레이션)

[별도 트랙 ⑤]  알러지 verified-only 강화 (ADR-0005) — 안전 회귀 시 단독 롤백
```

### 진행 상태 (2026-07-15 업데이트)

- **R1 완료** (이슈 #83, 브랜치 `feat/issue-83-diet-reco-r1`, 엔지니어링 리뷰 통과):
  - ③ 이산 제공량 — `ServingOptionDeriver` COUNT_UNIT 정수배 전개("N개" 표시), iOS 반영 ✅
  - 다중 후보 버퍼링 — `DEFAULT_ALTERNATIVE_BUFFER=4`, iOS 프리페치 위임 ✅
  - 추천 이벤트 식품별 매핑 — V40 `food_catalog_id`, GENERATED/REFRESHED/RECORDED food별 fan-out, `OnlinePreferenceSignalLoader`(본인 30일 참여 신호)로 `noSignals()` 대체 ✅
  - 부수: 추천 실패 Counter(`healthcare.diet.recommendation.failure{reason}`), funnel IT(실 PG 적재 보증), strict 게이트 죽은 분기 제거(ADR-0005 정합)
- **R1 배포 후 2주 관찰 대기** — KPI 판정 쿼리 3개(실패율 20%·음식기인 refresh 40%·상위10 전환 점유 70% 임계 + 판정 분기)로 **R2 vs ⓪ 우선순위 결정**. 관찰 기간 중 ⓪ 큐레이션 배치 병행(verified 풀 54→90개 목표). 상세: office-hours 설계 문서(`~/.gstack/projects/.../design-*.md`).
- **R2·R3·⑤·⓪ 미착수** — R1 데이터가 정확도(→R2) vs 가용성(→⓪) 중 무엇을 가리키는지 확인 후 착수.

## 5. 직군별 핵심 입력

### 5.1 영양관리사 — 목표별 매크로 밴드 가설 (정책 가설, 의료 확정값 아님)

표기: P=단백질, C=탄수, F=지방, %E=총열량 비중. 등급 = hard(H)/soft(S) + 근거강도(★).

| 목표 | 칼로리 | 단백질 | 탄수 | 지방 |
|---|---|---|---|---|
| 감량·체형개선 | atMost 98% [H★★★] | [1.6g/kg(또는 proteinTargetG), 2.4g/kg], 하한 [H★★★] | ≤50%E [S★★], 하한 ≥15%E [S★] | 0.5g/kg(초기 S★★→H) , ≤35%E [S★★] |
| 근육증가 | 100~110% [H★★★] | [1.6, 2.2g/kg] [H★★★] | ≥45%E 또는 3~4g/kg [S★★] | ≥0.5g/kg [S★★], ≤35%E [S★] |
| 지구력 | atLeast 100% [H★★★] | [1.2, 1.6g/kg] 하한 [S★★] | atLeast + ≥50%E [H★★★] | ≤30%E [S★] |
| 건강유지 | 95~105% [H★★] | 90~110% [H★] | 90~110% [H★] | 90~110% [H★] |

- **hard 영양 안전선**: 단백질 하한(감량·근육·체형), 탄수 하한(지구력), 칼로리 상·하한(전 목표).
- **soft 시작**: 지방 하한(호르몬·지용성비타민이나 작은 풀 feasible 위험), 비핵심 매크로 균형 상한.
- per-meal 단백질 하한: 근육 0.4 · 감량 0.3~0.4 · 유지/지구력 0.25 g/kg/끼. 충족 불가 시 끼니 수 축소 제안 또는 `PROTEIN_PER_MEAL_INFEASIBLE`.

### 5.2 끼니 분배 가설 (대사 중립 → 순응·포만·단백질 분배 기준)

| 끼니 수 | 칼로리 비율 | 단백질 |
|---|---|---|
| 2 | 0.45 / 0.55 | 끼니당 하한 큼 |
| 3 | 0.30 / 0.40 / 0.30 (저녁 비편중) | 균등 |
| 4 | 0.27 / 0.35 / 0.28 + 간식 0.10 | 균등 |
| 5~6 | 메인 3 + 간식 2~3 | **균등 분배 최우선** |

- 단백질은 칼로리 비율에서 **분리**해 끼니 간 균등(Mamerow 2014: 균등이 편중보다 24h MPS 25%↑).
- front-loading은 감량·체형에만 약한 soft(효과 보통, 순응·포만 보조). 한국인 아침 결식 현실 → 2끼·아침 경량을 정상 옵션으로 허용.

### 5.3 백엔드 — 변경 파일 요지

- **③**: `ServingOptionDeriver`(COUNT_UNIT 정수배 분기) → `DietRecommendationCandidate.verifiedServingGramOptions()`(type 보존) → `ConstraintRecommendationEngine.chooseServingCombination`(분수배 배제) → `RecommendedFoodEntry`(표시 단위).
- **①**: `GoalAwareNutritionPolicy.resolve(...,weightKg)` + 비핵심 매크로 밴드. 엔진 `satisfies`/`softScore`는 이미 4매크로를 봐 무변경.
- **②**: `BASE_RATIOS` 상수 → `MealDistributionPolicy` 추출(`search`/`partialScore`/`pruneBeam` 의존부 동시 수정), 단백질 분배 분리, `PROTEIN_PER_MEAL_INFEASIBLE` 신설.
- **④**: `DietRecommendationCandidate.mealRole`(MAIN/SIDE/EITHER) + `PREFERRED_CATEGORIES`를 역할 슬롯으로.
- 결정성: 새 프로파일/슬롯 도입 시 tie-break(`rotationKey`/`stableKey`/`stableSignature`) 유지, 정책 버전 bump + 결정성 테스트 동반 갱신.

### 5.4 기획 — 실패 6종 → 메시지·조정 액션 매핑

| 실패 코드 | 사용자 메시지(요지) | 조정 액션(절대제약 완화 금지) |
|---|---|---|
| `ALLERGEN_VERIFIED_POOL_INSUFFICIENT` | 안전하게 검증된 식품이 아직 부족 | 끼니 수 줄이기 / AVOID(알러지 아님) 임시 해제 / 검증 요청 신호 |
| `NUTRIENT_DATA_INCOMPLETE` | 영양정보 검증된 식품 부족 | 끼니 수 줄이기 / 직접 기록 |
| `CALORIE_PROTEIN_CONFLICT` | 저열량+고단백 동시 충족 불가 | 끼니 수 조정 / 목표일 재확인 (목표 자체는 불변) |
| `SERVING_OPTIONS_INFEASIBLE` | 현실적 제공량으로 목표 불가 | 끼니 수/간식 on-off |
| `REMAINING_MEALS_INFEASIBLE` | 오늘 남은 끼니로 불가 | 내일 추천 / 남은 끼니 수 |
| `TARGET_PROFILE_MISSING` | 목표·프로필 필요 | 목표 설정 딥링크 |

- `SEARCH_LIMIT_REACHED`는 사용자 비노출(내부 재시도). 모든 카피는 **이유 + 안전 의도 + 다음 행동** 3단 구조.

### 5.5 브랜드 — 포지셔닝·배지·금지표현

- **포지셔닝**: "먹어도 되는 것만 골라주는 식단 추천 — 등록한 알러지·기피는 단 한 번도 어기지 않습니다."
- **검증 배지(사용자 언어)**: 🟢 검증 완료(`DIRECT_VERIFIED`/`LABEL_DERIVED`) / 🛡 알러지 프로필 검토됨 / ⚪ 주의-미검증 / 📅 검증일.
- **금지표현**: "자주 먹으면 살 빠진다", "의학적으로 안전 / 100% 무알러지", "AI 영양사·진단·처방", "효과 보장", 배지에 "안전" 단독. → "등록 조건 기준 제외 + 검증일" 설명형으로.

## 6. 배포 게이트 (모든 릴리스 공통, 절대 0)

알러지·기피 위반 0 / 목표별 hard constraint 위반 0 / 허용 안 된 제공량 0 / 동일 조건 재현성 위반 0. 회귀 시 단독 롤백.

## 7. 미해결 · 다음 액션

- **미해결**: 비만 보정체중(adjusted BW) 규칙, 끼니 단위 Swap UX(restrictions §2), 온라인 가중치 튜닝값(운영 데이터 후).
- **다음 액션**: R1 착수 시 이슈 생성 → `feat/issue-N` 브랜치(우리 워크플로). 첫 작업은 **③ 이산 제공량(ServingOptionDeriver COUNT_UNIT 분기)** TDD.
