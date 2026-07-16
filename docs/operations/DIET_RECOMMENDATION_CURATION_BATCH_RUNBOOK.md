# 추천 후보 큐레이션 배치 runbook

작성일: 2026-06-30

대상: 추천 후보 풀 보강, 브랜드 공식 포장 SKU, 기존 `SEARCH_ONLY` 승격

관련 문서:

- [FOOD_CATALOG_GUIDE](../FOOD_CATALOG_GUIDE.md)
- [추천 우선 브랜드 식품 조사](../references/RECOMMENDED_BRANDED_FOODS_2026-06-22.md)
- [추천 최적화 계획](../exec-plans/DIET_RECOMMENDATION_OPTIMIZATION.md)

## 목적

추천 후보 수를 단순히 늘리지 않고, 추천 엔진이 실제로 쓸 수 있는 `engine-ready` 후보를 작은 배치로 늘린다.

`engine-ready` 기준:

- `recommendation_status`가 `RECOMMENDABLE` 또는 `RECOMMENDABLE_WITH_CAUTION`
- canonical 대표 row
- 열량, 단백질, 탄수화물, 지방이 모두 존재
- 검증된 제공량 옵션 존재
- 유효한 완결 알러젠 프로필 존재

## Batch A 범위

샐러디는 드레싱/소스 bundle 계약이 확정되기 전까지 `RECOMMENDABLE` 승격하지 않는다. Batch A의 추천 승격 1순위는 포장 단일 SKU다.

| 레인 | 대상 | 목표 |
|---|---|---|
| A1 | 동원 IN WATER, 햇반 라이스플랜 등 포장 단일 SKU | 공식 라벨 기반 `BRAND_OFFICIAL` row 생성 후 승격 |
| A2 | 기존 seed 42개 | 알러젠 완결 프로필 보강 |
| A3 | MFDS `SEARCH_ONLY` canonical row | macro complete + serving option 보유 후보를 큐레이션 대기열로 축적 |

## 운영 순서

### 1. 현재 후보 풀 기준선 확인

```bash
curl "$BASE_URL/api/v1/admin/diet/candidate-pool/summary" \
  -H "X-Admin-Token: $ADMIN_OPERATION_TOKEN"
```

기록할 값:

- `recommendableTotal`
- `macroCompleteTotal`
- `verifiedServingOptionTotal`
- `allergenProfileVerifiedTotal`
- `engineReadyTotal`
- `categoryCounts`
- `underrepresentedCategories`

### 2. 큐레이션 대기열 확인

```bash
curl "$BASE_URL/api/v1/admin/diet/candidate-pool/curation-queue?limit=50" \
  -H "X-Admin-Token: $ADMIN_OPERATION_TOKEN"
```

우선 검토 대상:

- `SEARCH_ONLY`
- canonical 대표 row
- macro complete
- verified serving option 보유
- 공식 근거 또는 현재 패키지 라벨로 알러젠 완결 검토가 가능한 row

### 3. 신규 브랜드 SKU 생성 또는 갱신

신규 포장 SKU는 먼저 브랜드 CSV로 `BRAND_OFFICIAL` row를 만든다.

템플릿: [brand_menu_csv_template.csv](../references/brand_menu_csv_template.csv)

```bash
curl -X POST "$BASE_URL/api/v1/admin/diet/catalog/import/brand-csv" \
  -H "X-Admin-Token: $ADMIN_OPERATION_TOKEN" \
  -F "file=@batch-a-brand-menu.csv"
```

운영 원칙:

- 공식 웹 수치는 후보 발굴에만 쓴다.
- 최종 승격은 현재 패키지 라벨, 공식 원재료/알러젠 표, 검수일, 출처 URL을 함께 확인한 row만 한다.
- 포함 알러젠이 없더라도 공식 라벨에서 표시대상 알러젠 전체를 확인했다면 `allergen_tags` 공란 + `allergen_profile_verified=true`를 허용한다.
- `allergen_profile_verified=true`에는 `last_verified_at`이 필수다.

### 4. 추천 큐레이션 CSV 적용

이미 존재하는 row의 추천 상태와 검수 근거는 추천 큐레이션 CSV로 갱신한다.

템플릿: [recommendation_curation_csv_template.csv](../references/recommendation_curation_csv_template.csv)

```bash
curl -X POST "$BASE_URL/api/v1/admin/diet/catalog/curation-csv" \
  -H "X-Admin-Token: $ADMIN_OPERATION_TOKEN" \
  -F "file=@batch-a-recommendation-curation.csv"
```

CSV 키는 `source + food_code`다. `BRAND_OFFICIAL`의 `food_code`는 브랜드명과 제품명을 정규화한 값이므로, 가능하면 import 응답 또는 DB 조회 결과를 복사해서 사용한다.

추천 후보 승격 row는 다음 입력이 필요하다.

- `recommendation_status`: `RECOMMENDABLE` 또는 `RECOMMENDABLE_WITH_CAUTION`
- `recommendation_reason`: 주의 후보일 때 필수
- `last_verified_at`: `yyyy-MM-dd`
- `review_source_url`: 공식 라벨/제품/알러젠 근거 URL
- `allergen_tags`: 포함 알러젠만 입력, 없으면 공란
- `allergen_profile_verified`: `true`

### 5. 승격 후 검증

```bash
curl "$BASE_URL/api/v1/admin/diet/candidate-pool/summary" \
  -H "X-Admin-Token: $ADMIN_OPERATION_TOKEN"
```

통과 기준:

- `engineReadyTotal` 증가
- `macroCompleteTotal` 증가 또는 유지
- `verifiedServingOptionTotal` 증가 또는 유지
- `allergenProfileVerifiedTotal` 증가
- Batch A 대상 카테고리 분포가 한쪽으로만 치우치지 않음

### 6. 추천 benchmark 회귀 확인

Batch A 적재 후에는 추천 benchmark gate를 실행한다.

확인 기준:

- 알러지 위반 0
- hard constraint 위반 0
- 허용되지 않은 제공량 0
- 재현성 위반 0
- 주요 실패 사유 중 `NUTRIENT_DATA_INCOMPLETE`, `ALLERGEN_VERIFIED_POOL_INSUFFICIENT` 감소

## 거절 사유 대응

| 사유 | 조치 |
|---|---|
| `추천 후보는 4대 매크로가 모두 필요합니다.` | 공식 라벨에서 탄수화물/단백질/지방/열량을 모두 확보한다. 추정값은 쓰지 않는다. |
| `추천 후보는 검증된 제공량 옵션이 필요합니다.` | 브랜드 CSV의 `serving_size_g`와 `nutrition_basis`를 확인하고 serving option 생성 여부를 확인한다. |
| `추천 후보는 완결 알러젠 프로필 검토가 필요합니다.` | 공식 알러젠 표 또는 현재 패키지 라벨을 확인한 뒤 `allergen_profile_verified=true`로 재입력한다. |
| `canonical 대표 행만 큐레이션할 수 있습니다.` | 대표 row의 `source + food_code`를 사용한다. non-canonical row는 승격하지 않는다. |
| `주의 기준 초과 항목은 RECOMMENDABLE_WITH_CAUTION` | 나트륨/당류/포화지방 기준 초과 row는 주의 상태와 사유로 승격한다. |

## 완료 기준

- Batch A 적용 전후 `CandidatePoolSummary`를 기록했다.
- 새 row 또는 승격 row의 출처 URL과 검수일이 남아 있다.
- 알러젠 사용자를 위한 유효 완결 프로필이 생성됐다.
- benchmark gate에서 안전 위반이 없다.
- 실패 사유 분포가 줄어든 항목과 남은 병목을 기록했다.
