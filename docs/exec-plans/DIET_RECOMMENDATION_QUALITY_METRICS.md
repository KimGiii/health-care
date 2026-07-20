# 추천 품질 지표 (RecommendationQualityMetrics) 근거 문서

작성일: 2026-07-20
상태: 구현 완료 (이슈 #80, ETM 보강 토대 [0])
대상: 백엔드
관련: `DIET_RECOMMENDATION_ETM_ENHANCEMENTS.md` §5, `DIET_RECOMMENDATION_OPTIMIZATION.md` §11, `DIET_RECOMMENDATION_ENHANCEMENT_DECISIONS_2026-06-29.md`

## 1. 왜 필요한가

ETM 보강 후속 항목(① 매크로 밴드×비대칭, ② 끼니 분배 재설계, ③ 이산 제공량, ④ 끼니 역할)은 모두 추천 엔진의 산출을 바꾼다. 각 항목이 benchmark baseline 대비 **회귀했는지 객관적으로 측정할 공용 계산기**가 없었다.

기존 도구의 공백:

- `ConstraintEngineBenchmarkTest` — 시나리오별 **절대 불변식**(budget 충족, 중복 0, 허용 제공량)만 단언한다. "이전보다 나빠졌는가"라는 상대 비교가 불가능하다.
- `DIET_RECOMMENDATION_OPTIMIZATION.md` §11 — 배포 차단 지표(알러지 위반 0, hard 위반 0, 허용 안 된 제공량 0)를 정의하지만, 품질의 **연속량**(매크로 분포, 다양성, 끼니별 단백질)은 다루지 않는다.

`RecommendationQualityMetrics`는 이 공백을 메우는 순수 계산기다. 추천 결과(`List<RecommendedMeal>`) 하나를 받아 품질 지표를 산출하며, 부수효과가 없다.

## 2. 측정 지표와 산식

| 지표 | 산식 | 용도 |
|---|---|---|
| 매크로 합계 | 끼니별 `totalCalories/ProteinG/CarbsG/FatG` 합 | 목표 대비 절대량 회귀 감지 |
| 매크로 칼로리 분포 % | `매크로g × Atwater 계수(4/4/9) ÷ macroEnergyKcal × 100` | 항목 ① 밴드 검증의 기초 축 |
| 끼니별 단백질 + 최소 끼니 단백질 | `perMealProteinG`, `minMealProteinG()` | 항목 ② 분배 회귀의 원시 신호 |
| 다양성 | 서로 다른 `foodCatalogId` 수 | 반복 페널티 회귀 감지 |
| 0 에너지 가드 | `macroEnergyKcal == 0`이면 분포 0% 반환 | NaN 전파 방지 |

### 분포 분모를 보고 칼로리가 아니라 Atwater 환산 에너지로 쓰는 이유

- 분자(매크로별 kcal)와 분모가 같은 계수이므로 **세 매크로 %의 합이 항상 100%로 정합**한다.
- 음식 DB의 라벨 칼로리 오차·반올림이 분포 지표에 노이즈로 섞이지 않아 **재현성 원칙**(ETM §0.3)에 부합한다.
- 알려진 한계: `carbsG`가 총 탄수(식이섬유 포함)면 탄수 %가 다소 과대. 회귀 **상대비교**에는 무해하나, 항목 ① 밴드를 정책으로 구현할 때는 분모 정의를 정책과 일치시켜야 한다.

### `minMealProteinG()`는 원시 신호일 뿐이다

끼니 단백질 분배(항목 ②)의 완전한 판정은 이 절대값으로 할 수 없다:

1. 근단백합성 역치 0.4 g/kg는 **체중 함수**라 g/kg 지표가 필요하다.
2. 편중도는 최소값이 아니라 **분산/변동계수**로 봐야 한다.
3. 간식을 포함하면 정상적인 5–6끼 구성을 오탐한다(메인 끼니 한정 필요).

체중·끼니 역할을 주입해야 하는 이 지표들은 항목 ①·② 착수 시 그 생산자 코드와 함께 추가한다.

## 3. test 소스셋에 두는 이유 (의도적 결정)

`backend/src/test/.../recommendation/benchmark/` 에 위치한다. production 소비자(KPI API, 운영 대시보드)가 아직 없으므로 main 소스셋에 두면 죽은 코드가 된다(YAGNI). 소비자는 benchmark 테스트뿐이다. production 노출이 필요해지는 시점(예: KPI 조회 API)에 승격을 재검토한다.

## 4. 의도적으로 연기한 것과 추가 시점

| 연기 항목 | 추가 시점 | 근거 |
|---|---|---|
| `RecommendationFailureReason.PROTEIN_PER_MEAL_INFEASIBLE` 등 enum 확장 | 항목 ② 착수 시 | 생산자 코드가 그때 생긴다 — 지금 추가하면 speculative |
| 체중·목표 밴드 주입 지표(per-meal g/kg, 역치충족 끼니비율, 단백질 분배 CV, 지방 하한, 칼로리 적합도) | 항목 ① 착수 직전 | 팀 평가 HIGH 피드백 — 그 테스트가 구동해야 정의가 확정됨 |
| feasibility 회귀 측정 + baseline 스냅샷 게이트 | 항목 ① 착수 직전 | 밴드 조임이 feasible율을 깎는 게 주 위험이라 그때 필요 |

## 5. 검증 상태

- `RecommendationQualityMetricsTest` — 매크로 합계, 분포 %(합 100% 정합), 끼니별 단백질, 다양성, 0 에너지 가드 단위 테스트
- 기존 benchmark 회귀 없음
- 2명 개발자 + RD 팀 평가 완료, 저비용 피드백 반영(부동소수점 비교, Javadoc 근거)
