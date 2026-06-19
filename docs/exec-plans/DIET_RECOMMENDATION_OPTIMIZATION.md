# 목표별 남은 영양량 식단 추천 최적화 실행 계획

작성일: 2026-06-18
상태: Phase 1~4 완료, Phase 5 메커니즘 완료 (온라인 가중치 튜닝은 운영 데이터 후속)
대상: 백엔드, iOS, 데이터 운영, 제품 분석
상위 문서: `docs/product-specs/DIET_RECOMMENDATION_RESTRICTIONS_PRD.md`
전역 ADR: `docs/adr/0002-goal-aware-nutrition-optimization.md`

## 1. 목표

약 50만 건 규모의 검색·기록 카탈로그를 그대로 추천에 투입하지 않고, 검증된 후보와 현실적인 제공량을 이용해 다음 계약을 만족하는 추천으로 전환한다.

1. 등록한 알러지·기피 조건을 위반하지 않는다.
2. 사용자의 목표 유형과 목표 기간에 맞는 비대칭 영양 제약을 적용한다.
3. 이미 섭취한 식단을 제외한 남은 일일 영양량과 남은 끼니를 공동 최적화한다.
4. 추천 실패를 숨기거나 조건을 조용히 완화하지 않는다.
5. 추천 이벤트와 사용자 피드백으로 검증 풀과 순위 품질을 지속적으로 개선한다.

완결된 레시피·메뉴 생성은 이 계획의 핵심이 아니다. 식사 의미론은 비현실적 조합을 막는 최소 제약과 동점 해소 기준으로만 사용한다.

## 2. 현재 구현과 목표 상태의 차이

| 영역 | 현재 | 목표 |
|---|---|---|
| 알러지 기본 게이트 | 매칭 포함 태그가 없으면 미검토 후보도 통과 | 알러지 등록 사용자는 완결 프로필 검증 후보만 통과 |
| 영양 정책 | 모든 목표에 열량 90~110%, 단백질 90% 이상 | 목표 유형별 상·하한 hard constraint |
| 추천 기준 | 하루 목표 전체 | 이미 섭취한 영양을 차감한 남은 목표 |
| 엔진 | 카테고리 greedy 선택 후 사후 검증 | 후보 축소 후 제약 최적화로 feasible 해 생성 |
| 제공량 | 25~500g, 25g 반올림 | 검증된 제공량 옵션 |
| 중복 | 리포트 + 수동 검수 | 비파괴 canonical 그룹 + 애매한 그룹만 검수 |
| 개인화 | 전역 `usage_count`, 날짜 회전 | 사용자 이벤트·최근 섭취·선택형 피드백 |
| 품질 게이트 | 단위·통합 테스트 | 고정 시나리오 benchmark + 온라인 지표 |

## 3. 목표별 영양 정책

초기 정책은 다음 의미를 가진다. 정확한 숫자는 정책 버전과 benchmark로 관리하며 구현 상수로 흩뜨리지 않는다.

| 목표 | hard constraint | soft objective |
|---|---|---|
| 체중 감량 | 열량은 목표 미만, 단백질은 목표 이상 | 열량 하한과의 거리, 지방·탄수화물 균형 |
| 체형 개선 | 열량은 목표 미만, 단백질은 목표 이상 | 보수적 결손, 영양 분산 |
| 근육량 증가 | 단백질과 열량은 목표 이상, 과도한 열량 초과 금지 | 단백질 분산, 지방·탄수화물 균형 |
| 지구력 향상 | 열량과 탄수화물 하한 충족 | 끼니·운동 전후 탄수화물 분산 |
| 건강 유지 | 열량과 매크로 허용 범위 충족 | 나트륨·당류·포화지방, 다양성 |

### 3.1 불확실성 버퍼

- 공식 제공량과 검증 영양정보의 열량 상한은 초기 98% 수준을 benchmark 가설로 둔다.
- 제공량 추정 또는 source 간 편차가 있으면 초기 95% 수준을 가설로 둔다.
- 핵심 영양값 신뢰도가 낮은 식품은 추천 후보에서 제외한다.
- 비율은 의료 기준으로 확정된 값이 아니라 데이터 오차를 흡수하기 위한 운영 정책이며 프로파일링 후 조정한다.

### 3.2 목표 기간

- `requestedTargetDate`: 사용자가 희망한 목표일
- `projectedTargetDate`: 안전 정책과 현재 진행 추세로 계산한 예상일
- 목표일이 과도하게 짧으면 목표 열량을 안전 경계 아래로 낮추지 않고 `projectedTargetDate`를 제안한다.
- 일일 측정값으로 목표를 흔들지 않고 주간 추세로 주 1회 재계산한다.
- 하루 미달·초과분을 다음 날 급격히 보상하지 않는다.

### 3.3 운동 기록

- 활동 수준이 TDEE에 이미 반영되므로 운동 소모 열량을 식사 예산에 1:1로 더하지 않는다.
- 근육량 증가·지구력 목표에서는 운동 여부를 회복 영양 분배의 제한적 신호로 사용할 수 있다.
- MET 또는 AI 추정 소모 열량은 자동 보상 근거로 사용하지 않는다.

### 3.4 Phase 1 정책 버전

`2026-06-18.v1`은 새 엔진을 선택하기 위한 초기 benchmark 정책이다. 의료 기준 확정값이 아니며, 이후 비율 변경은 새 버전과 baseline 비교를 동반한다. 현행 추천 API에는 아직 연결하지 않았다.

| 목표 | 열량 hard constraint | 단백질 | 탄수화물 | 지방 |
|---|---|---|---|---|
| 체중 감량 | 목표의 98% 이하 | 목표 이상 | 제한 없음 | 제한 없음 |
| 체형 개선 | 목표의 98% 이하 | 목표 이상 | 제한 없음 | 제한 없음 |
| 근육량 증가 | 목표의 100~110% | 목표 이상 | 제한 없음 | 제한 없음 |
| 지구력 향상 | 목표 이상 | 제한 없음 | 목표 이상 | 제한 없음 |
| 건강 유지 | 목표의 95~105% | 목표의 90~110% | 목표의 90~110% | 목표의 90~110% |

## 4. 남은 영양량 모델

```text
remaining target = policy target - confirmed intake
```

- 추천 전 기록된 식품 항목을 확정 섭취로 고정한다.
- 남은 끼니 전체를 하나의 최적화 문제로 계산한다.
- 새 끼니가 실제 기록되면 실제 섭취량으로 남은 끼니를 다시 계산한다.
- 미검증·AI 추정 섭취 기록은 신뢰 구간을 유지한다.
  - 열량 상한 정책: 추정 열량 상한을 섭취량으로 차감
  - 단백질 하한 정책: 추정 단백질 하한을 섭취량으로 차감
- 이미 hard constraint를 초과했거나 남은 목표가 infeasible이면 완성 추천 대신 구조화된 실패를 반환한다.

## 5. 추천 후보 자격

추천 자격은 `recommendation_status` 하나가 아니라 다음 축의 곱으로 계산한다.

1. 알러젠 프로필 검증 상태
2. 목표별 필수 영양값 완전성
3. 현실적인 제공량 옵션 존재 여부
4. source와 데이터 버전의 유효성
5. canonical 그룹 대표 여부
6. 운영 큐레이션 상태

### 5.1 알러젠 검증 풀

- 공식 라벨·공식 메뉴 알러젠 표: `LABEL_DERIVED` 최종 근거 가능
- 결정적인 단일 원재료 분류: `DIRECT_VERIFIED` 최종 근거 가능
- 일반 레시피·음식명·AI 추론: 검수 큐 생성 전용
- 영양 API에 알러젠 근거가 없는 항목: 검색·기록 전용
- 원재료·알러젠·제공량·영양값 변경 시 추천 자격을 회수하고 재검증한다.

포함 알러젠이 0개인 완결 프로필도 표현하기 위해 `food_allergen_profiles` 성격의 별도 검토 레코드가 필요하다. 검토 표준 버전, 근거 source, source reference, 검토일, 만료/무효 사유를 보존한다.

### 5.2 영양 완전성

- 누락값을 0으로 해석하지 않는다.
- 모든 목표의 기본 후보에는 열량·단백질·탄수화물·지방·제공량이 필요하다.
- 지구력 정책에는 탄수화물 신뢰도가 필수다.
- 나트륨·당류·포화지방 상한을 보장하려면 해당 값이 있어야 한다.
- 추정값은 검색·기록 보조와 검수 우선순위에는 사용할 수 있지만 hard constraint 판정에는 사용하지 않는다.

### 5.3 검증 우선순위

검증 건수보다 추천 실패 감소량을 최대화한다.

```text
verification priority
= usage demand
× incremental feasibility coverage
× allergen-combination coverage
× verification confidence
÷ verification cost
```

최소 커버리지 확보 후 사용자 검색·기록 빈도를 가산한다. 유사 닭가슴살을 반복 검증하는 것보다 특정 알러지 조합에서 부족한 고단백·저열량·탄수화물·간식 역할을 우선한다.

## 6. canonical 식품 그룹

source row를 삭제하거나 강제 병합하지 않고 대표 그룹에 연결한다.

- 동일 공식 식품코드: 자동 그룹화 가능
- 동일 브랜드·제품명·제공량·영양 fingerprint: 고신뢰 그룹 후보
- 이름만 유사: 수동 검수 큐
- 추천에서는 그룹당 대표 후보 하나만 사용
- 선호·노출·전환 통계는 그룹 단위로 집계
- 불확실한 그룹에는 알러젠·영양 검증을 전파하지 않음

기존 dedup 리포트와 수동 정책은 유지하되, 50만 건 운영을 위해 `canonical_food_group` 성격의 비파괴 모델로 확장한다.

## 7. 제공량 모델과 식단 기록하기

### 7.1 추천 제공량

- 공식 1회 제공량: 0.5회, 1회, 1.5회, 2회 등 허용 multiplier
- 개수 식품: 1개, 2개 등 단위 옵션
- 중량 조절 식품: 검증된 g step과 최소·최대 범위
- 제공량 근거가 없거나 비현실적인 옵션만 있는 식품: 추천 제외

장기적으로 `food_serving_options` 성격의 모델에 단위명, 환산 g, 최소/최대 수량, step, source, 검증일을 둔다.

### 7.2 식단 기록하기 UX

- 검색 결과는 가능하면 1회 제공량 기준 영양을 표시한다.
- 식품 선택 시 제공량 프리셋을 우선 노출한다.
- 실제 섭취량 교정을 위해 직접 g 입력을 유지한다.
- 최종 저장 형식은 기존 `FoodEntry.servingG`를 유지할 수 있다.

## 8. 제약 최적화 엔진

### 8.1 파이프라인

1. DB에서 삭제·제한·검증·영양 완전성·추천 상태 hard filter
2. canonical 그룹 대표 선택
3. 제공량 옵션 전개
4. 남은 영양 벡터에 대한 유용도와 dominance로 후보 수백 개 수준 축소
5. 제한 시간이 있는 MILP/CP-SAT 계열 제약 최적화
6. feasible 상위 해 생성
7. 최근 반복, 사용자 선호, 다양성으로 재순위화

solver 제품 선택은 별도 backend ADR과 benchmark prototype으로 결정한다.

### 8.2 hard constraint와 soft objective

hard constraint:

- 알러지·기피 조건
- 목표 유형별 열량 상·하한
- 목표 유형별 단백질·탄수화물 하한
- 목표별 필수 영양 데이터 완전성
- 제공량 옵션의 최소·최대·step

soft objective:

- 목표 중심값과의 거리
- 나트륨·당류·포화지방
- 끼니별 열량·단백질 분산
- 최근 섭취·추천 반복
- 사용자 선호와 기록 전환 가능성
- 최소한의 식사 조합 현실성

다양성은 hard constraint를 통과한 해 안에서만 적용한다. 동일한 최적해 하나를 반복하지 않고 유사 품질의 상위 해를 사용자·날짜 기준으로 재현 가능하게 회전한다.

### 8.3 실패 계약

다음과 같은 구조화된 원인을 반환한다.

- `ALLERGEN_VERIFIED_POOL_INSUFFICIENT`
- `NUTRIENT_DATA_INCOMPLETE`
- `CALORIE_PROTEIN_CONFLICT`
- `SERVING_OPTIONS_INFEASIBLE`
- `REMAINING_MEALS_INFEASIBLE`
- `TARGET_PROFILE_MISSING`

조정 가능한 선택지만 안내하며 알러지 또는 목표별 절대 제약 완화를 권하지 않는다.

## 9. 추천 스냅샷과 피드백

식단 계획을 영구 도메인으로 만들지는 않지만, 품질 개선에 필요한 최소 스냅샷과 이벤트를 저장한다.

- 추천 생성·노출
- 다시 추천
- 기록 전환 및 연결된 `DietLog`
- 항목 삭제·교체
- 선택형 재추천 사유

재추천 사유 후보:

- 먹고 싶지 않음
- 최근에 먹었음
- 양이 적거나 많음
- 준비·구매가 어려움
- 영양 구성이 마음에 들지 않음
- 이유 없이 다시 추천

추천을 저장하지 않았다는 사실만으로 부정 선호를 추론하지 않는다. 사용자 연결 이벤트는 기본 90일 보관하고 장기 분석은 비식별 집계로 유지한다. 자유 입력 기피 키워드 원문은 분석 이벤트에 저장하지 않는다.

## 10. 설명 가능성

추천 응답은 raw solver 점수 대신 다음 근거를 제공한다.

- 남은 열량·단백질·탄수화물 중 무엇을 채웠는지
- 목표 상한 또는 하한과의 차이
- 적용한 알러지·기피 조건
- 알러젠·영양 데이터 근거와 마지막 검증일
- 추정 섭취 기록 때문에 불확실성 여유를 적용했는지

제품 문구는 "등록한 조건과 검증된 식품 정보를 기준으로 제외했다"고 설명한다. 교차오염과 실제 제품 변경까지 안전하다고 단정하지 않는다.

## 11. benchmark와 운영 지표

고정 시나리오는 목표 유형, 주요 알러지 조합, 이미 섭취한 영양 상태, 남은 끼니 수, 데이터 완전성을 조합한다.

배포 차단 지표:

- 알러지·기피 위반 0건
- 목표별 hard constraint 위반 0건
- 허용되지 않은 제공량 0건
- 동일 조건 재현성 위반 0건

품질 지표:

- 조건별 추천 성공률과 실패 사유
- 열량·단백질·탄수화물 목표 오차
- 제공량 현실성
- 최근 식품 반복률
- 추천 생성 시간
- 추천→기록 전환율
- 다시 추천률과 사유 분포
- 추천 식단 저장 후 수정·삭제율

검증 풀 KPI는 총 건수가 아니라 주요 제한 조건과 목표 조합의 추천 성공률이다.

## 12. 단계별 구현

### Phase 1. 정책과 benchmark

- 목표별 `NutritionPolicy` 계약과 정책 버전 정의
- 목표 기간·예상 달성일·주간 재계산 계약 정의
- 남은 영양량과 불확실성 계산 모듈 정의
- 고정 benchmark fixture와 배포 차단 assertion 구축
- 현행 greedy 엔진 baseline 측정

완료 기준: 새 정책을 구현하지 않아도 현행 엔진의 위반·실패가 수치로 재현된다.

구현 결과 (2026-06-18):

- `domain/nutrition/policy`: 목표별 버전형 `NutritionPolicy`, 위반 원인, 남은 영양량·불확실성 계산 계약
- `domain/goals/policy`: 희망 목표일과 예상 달성일 분리, ISO 주차당 1회 재계산 계약
- `domain/diet/recommendation/benchmark` 테스트: 목표 5종, 기존 섭취, 알러젠 검증, 영양 결측, 제공량 옵션, 재현성을 조합한 고정 fixture
- 현행 greedy baseline: 6개 시나리오 중 차단 5개, 영양 hard constraint 위반 8건, 알러젠 검증 위반 1개 시나리오, 영양 결측 1개 시나리오, 허용되지 않은 제공량 15건, 재현성 위반 0건
- baseline 상세: `docs/references/DIET_RECOMMENDATION_GREEDY_BASELINE_2026-06-18.md`

Phase 1은 정책 계약과 측정 기반만 추가한다. 현행 `DailyDietRecommendationUseCases`와 `DietRecommendationEngine`의 운영 동작은 바꾸지 않으며, 새 계약 연결은 Phase 2~3에서 단계적으로 수행한다.

### Phase 2. 데이터 계약

- [x] 알러젠 프로필 검토 모델 분리
  - `FoodAllergenProfile` JPA 엔티티 + `isVerifiedAt()` 도메인 메서드
  - `FoodAllergenProfileRepository` (findByFoodCatalogIdIn)
  - V33 Flyway 마이그레이션: `food_allergen_profiles` 테이블 + 기존 태그 백필
  - `FoodAllergenProfileGate`: 알러지 등록 사용자는 별도 검토 레코드 필수 통과
- [x] 영양 완전성 정책 (§5.2)
  - `DietRecommendationCandidate.macroDataComplete`: `from(FoodCatalog)` 시점에 4대 영양소 null 여부 자동 계산
  - `NutrientCompletenessPolicy`: 모든 목표 유형에 동일 정책 적용
  - `DietRecommendationCandidatePool`: 영양 완전성 요약 메서드 추가
- [x] source 신뢰도·버전 만료 정책
  - `DataFreshnessPolicy`: source별 최대 허용 연령 (MFDS 2년, BRAND_OFFICIAL 1년, SEED·USER_CUSTOM 무제한)
  - `DietRecommendationCandidatePool`: `dataFreshnessPolicy.isCurrent(food)` 인메모리 필터 추가
- [x] 제공량 옵션 모델 (`food_serving_options`)과 iOS 프리셋 UX
  - V34 Flyway 마이그레이션: `food_serving_options` 테이블 (label, equivalent_g, sort_order, serving_type, verified_at)
  - `FoodServingOption` JPA 엔티티 + `ServingOptionSnapshot` 공유 record
  - `FoodServingOptionRepository`: findByFoodCatalogIdIn / findByFoodCatalogIdOrderBySortOrderAsc
  - `DietRecommendationCandidate`: `servingOptions`, `hasVerifiedServingOptions` 필드 추가
  - `DietRecommendationCandidatePool`: serving options 벌크 로드 → 후보에 전달
  - `FoodCatalogService`: 검색 결과에 serving options 벌크 로드 (iOS 프리셋 UX용)
  - `FoodCatalogResponse`: `servingOptions: List<ServingOptionSnapshot>` 필드 추가
- [x] canonical 식품 그룹과 대표 후보 조회
  - V35 Flyway 마이그레이션: `canonical_group_id BIGINT` 컬럼 + 부분 인덱스
  - `FoodCatalog.canonicalGroupId` 필드 추가
  - `FoodCatalogSpecs.isCanonicalCandidate()`: `canonical_group_id IS NULL` predicate
  - `DietRecommendationCandidatePool`: Spec + 인메모리 dual-defense 필터 적용
- [x] 검증 우선순위 리포트 (운영자 조회)
  - `FoodAllergenTagRepository.findFoodIdsWithAnyAllergenTag()` 추가
  - `CandidatePoolSummary`: 카테고리별 후보 수·macro 완전성·미태깅 수·underrepresented 카테고리 집계
  - `CandidatePoolSummaryService`: 후보 풀 요약 집계
  - `VerificationPriorityService`: priorityScore(usageCount × macroWeight × allergenWeight) 기반 정렬
  - `CandidatePoolAdminController`: GET /api/v1/admin/diet/candidate-pool/summary, /verification-priorities

완료 기준: 목표별로 추천 가능한 후보 수와 부족한 역할을 운영자가 조회할 수 있다.

### Phase 3. 제약 최적화

- 남은 하루 공동 최적화 prototype
- solver/탐색 방식 benchmark 후 backend ADR 작성
- 상위 복수 해와 다양성 재순위화
- 구조화된 실패 응답과 추천 근거
- 기존 API 호환·전환 계획

완료 기준: benchmark에서 hard constraint 위반 0건이며 현행 대비 조건별 성공률 또는 영양 오차가 개선된다.

### Phase 4. 이벤트와 개인화

- 추천 스냅샷·이벤트 저장
- 식단 기록과 추천 ID 연결
- 선택형 재추천 사유 UI
- 최근 반복과 개인 선호 점수
- 90일 보관·탈퇴 삭제·비식별 집계
- 개인정보 처리방침·App Store Privacy Labels·분석 이벤트 명세 갱신

완료 기준: 노출→재추천→기록 전환 funnel과 사용자별 반복률을 측정할 수 있다.

### Phase 5. 검증 풀 확장과 지속 개선

- [x] source 변경 감지와 자동 추천 자격 회수 (Unit 1)
  - `FoodCatalog.recommendationFactsDifferFrom(imported)`: 4대 매크로·제공량·주의 영양소(나트륨·당류·포화지방)의 유의미한 변화 판정. 상대 5% 초과 또는 데이터 완전성 전환(null↔값)만 변경으로 본다. 미세 변동(반올림·소폭 갱신)은 무시.
  - `FoodCatalog.revokeRecommendationForStaleFacts()`: 추천 후보였던 항목만 `SEARCH_ONLY`로 강등하고 `recommendation_reason`에 재검증 사유 기록.
  - `FoodCatalogIngestService.updateExisting`: `PRESERVE_EXISTING` 재적재에서 사실이 유의미하게 바뀌면 자격 회수. `REPLACE_FROM_IMPORT`(브랜드 CSV)는 가져온 큐레이션으로 교체하므로 회수 대상 아님.
- [x] 조건별 실패 커버리지 기반 검증 큐 (Unit 2)
  - `RevalidationQueueService`: 자동 회수 항목(`REVOKED_STALE_FACTS`)과 신선도 만료 추천 후보(`DATA_EXPIRED`)를 사유 우선·usageCount 내림차순으로 집계. `GET /api/v1/admin/diet/candidate-pool/revalidation-queue` (admin 토큰 가드).
  - `VerificationPriorityService`: priorityScore에 `coverageWeight`(후보 부족 카테고리 2, 충분 1) 추가 — §5.3 incremental feasibility coverage 근사. `UNDERREPRESENTED_THRESHOLD`를 `CandidatePoolSummary`와 공유.
- [x] benchmark shadow run과 단계적 승격 (Unit 3)
  - `RecommendationBenchmarkRegressionGate`: 후보(candidate)를 baseline과 같은 고정 시나리오로 shadow run 후 배포 차단 안전 지표가 하나라도 악화되지 않았는지 검증(회귀 0 = 승격 가능). `assertDeployable`(절대 0)과 분리된 점진 승격 게이트.
  - `GreedyRecommendationBaselineTest`: 박제된 baseline 스냅샷 대비 현행 엔진 shadow run의 안전 회귀 없음을 검증 — 엔진·정책 교체 시 회귀 감지.
- [x] 온라인 지표를 이용한 순위 가중치 조정 — 메커니즘 (Unit 4)
  - `FoodEngagementStat`: 식품별 전환(recorded)·회피(refreshed) 집계 결과 계약.
  - `OnlinePreferencePolicy`: 집계 통계를 식품별 penalty(전환↑ 우대, 회피↑ 불이익)로 변환. `UserRepetitionPolicy`와 같은 `penaltyScore` 모양으로 엔진 점수화 seam에 합류 가능. 신호 없으면 무영향(`noSignals`).
  - **이후 작업**: 추천 이벤트는 현재 snapshot 단위라 식품별 매핑이 없다. 식품별 집계 파이프라인(스냅샷 항목-식품 연결 보강)과 엔진 실제 통합·가중치 튜닝은 운영 이벤트가 쌓인 뒤 benchmark 회귀 게이트(Unit 3)로 검증하며 단계적으로 반영한다.

완료 기준: 안전 위반 없이 주요 조건의 추천 성공률과 기록 전환율이 지속적으로 개선된다. (온라인 튜닝은 운영 데이터 확보 후 지속)

## 13. 명시적 제외 범위

- AI가 알러젠 안전 여부를 최종 판정하는 기능
- 목표 기간을 맞추기 위해 안전 경계를 무시하는 열량 조정
- 운동 소모 열량의 1:1 식사 예산 보상
- 자유 생성 레시피·조리 단계·장보기 목록
- 검증되지 않은 사용자 커스텀 식품의 자동 추천 승격
- 근접 식단을 hard constraint 충족 식단처럼 노출하는 fallback

## 14. 참고

- CDC, 현실적이고 점진적인 체중 감량 목표: https://www.cdc.gov/healthy-weight-growth/losing-weight/index.html
- NIDDK Body Weight Planner, 목표 체중·기간 기반 개인화 계획: https://www.niddk.nih.gov/health-information/weight-management/body-weight-planner
