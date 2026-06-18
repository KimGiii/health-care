# 현행 greedy 식단 추천 benchmark baseline

작성일: 2026-06-18
상태: Phase 1 고정 baseline
정책 버전: `2026-06-18.v1`

## 1. 목적

새 제약 최적화 엔진을 구현하기 전에 현행 `DietRecommendationEngine`이 목표별 hard constraint, 남은 영양량, 알러젠 검증, 영양 완전성, 제공량 옵션을 얼마나 위반하는지 같은 fixture로 재현한다.

이 baseline은 현행 엔진의 품질을 승인하는 기준이 아니다. Phase 2~3에서 새 데이터 계약과 엔진을 비교하기 위한 출발점이다.

## 2. 고정 시나리오

| ID | 목표·조건 | 현행 차단 원인 |
|---|---|---|
| `weight-loss-unverified-allergen` | 체중 감량, 알러지 제한, 미검증 후보 | 열량 상한, 알러젠 검증, 제공량 |
| `body-recomposition-after-breakfast` | 체형 개선, 확정 아침 섭취 반영 | 남은 열량 상한, 제공량 |
| `muscle-gain-low-protein` | 근육량 증가, 저단백 후보 | 열량·단백질 하한, 제공량 |
| `endurance-low-carbs` | 지구력 향상, 저탄수화물 후보 | 탄수화물 하한, 제공량 |
| `general-health-balanced` | 건강 유지, 완전한 영양값과 허용 제공량 | 차단 없음 |
| `general-health-incomplete-macros` | 건강 유지, 매크로 결측 후보 | 영양 완전성, 단백질·탄수화물·지방 범위 |

같은 날짜와 입력으로 각 시나리오를 두 번 실행해 재현성도 함께 확인한다.

## 3. baseline 결과

| 지표 | 결과 |
|---|---:|
| 전체 시나리오 | 6 |
| 배포 차단 시나리오 | 5 |
| 영양 hard constraint 위반 | 8 |
| 알러젠 검증 위반 시나리오 | 1 |
| 영양 데이터 결측 시나리오 | 1 |
| 허용되지 않은 제공량 | 15 |
| 동일 조건 재현성 위반 | 0 |

현행 엔진은 날짜 기반 deterministic rotation의 재현성은 지키지만, 목표별 정책·기존 섭취 차감·검증 제공량 계약을 충족하지 못한다. 새 엔진의 배포 게이트는 위 차단 지표가 모두 0일 때만 통과한다.

## 4. 실행 위치

- fixture와 runner: `backend/src/test/java/com/healthcare/domain/diet/recommendation/benchmark/`
- baseline 회귀 테스트: `GreedyRecommendationBaselineTest`
- 배포 차단 assertion: `RecommendationBenchmarkGate`

실행:

```bash
cd backend
./gradlew test --tests 'com.healthcare.domain.diet.recommendation.benchmark.*'
```
