# 브랜드 알러젠 프로필 보강 큐

작성일: 2026-06-30

대상 DB: local Docker PostgreSQL `healthcare_local`

관련 파일:

- [brand_allergen_profile_gap_summary_2026-06-30.csv](brand_allergen_profile_gap_summary_2026-06-30.csv)
- [brand_allergen_profile_candidate_queue_2026-06-30.csv](brand_allergen_profile_candidate_queue_2026-06-30.csv)
- [recommendation_curation_csv_with_evidence_template.csv](recommendation_curation_csv_with_evidence_template.csv)

## 결론

로컬 `food_catalog`에는 브랜드/프랜차이즈성 canonical 추천 후보가 이미 충분히 들어와 있다. 신규 브랜드 SKU를 더 넣기 전에, 이 후보들에 공식 알러젠 근거와 완결 프로필을 붙이는 편이 추천 성공률 개선에 더 빠르다.

다만 이 큐는 바로 적용할 CSV가 아니다. `review_source_url`, `allergen_tags`, `allergen_profile_verified`를 사람이 공식 근거로 채운 뒤 적용해야 한다.

## 기준선

| 항목 | 수 |
|---|---:|
| active `food_catalog` | 620,120 |
| canonical 대표 row | 325,323 |
| canonical 추천 후보 | 11,984 |
| canonical 브랜드/제조사 추천 후보 | 11,024 |
| 브랜드/제조사 추천 후보 중 알러젠 완결 프로필 보유 | 0 |

후보 조건:

- `deleted_at is null`
- `dedup_state = CANONICAL`
- `recommendation_status in (RECOMMENDABLE, RECOMMENDABLE_WITH_CAUTION)`
- 열량, 단백질, 탄수화물, 지방 완전
- 검증된 제공량 옵션 보유
- `brand_name` 또는 유효 `maker` 보유
- `food_allergen_profiles` 없음

## 상위 보강 묶음

| 순위 | 브랜드/제조사 | 후보 수 | 검수 레인 |
|---:|---|---:|---|
| 1 | 도미노피자 | 347 | 브랜드 공식 알러젠표 |
| 2 | 쿡베이스 | 257 | 라벨/FoodQR |
| 3 | 파리바게뜨 | 208 | 브랜드 공식 알러젠표 |
| 4 | 크로플덕오리아가씨 | 94 | 브랜드 공식 알러젠표 |
| 5 | 롤링핀 | 68 | 브랜드 공식 알러젠표 |
| 6 | 배스킨라빈스 | 57 | 브랜드 공식 알러젠표 |
| 7 | 노모어피자 | 47 | 브랜드 공식 알러젠표 |
| 8 | 주식회사 플라잉닥터 제2공장 | 42 | 라벨/FoodQR |
| 9 | 현대그린푸드스마트푸드센터 | 40 | 라벨/FoodQR |
| 10 | 요거트아이스크림의 정석 | 40 | 브랜드 공식 알러젠표 |

## CSV 사용법

`brand_allergen_profile_candidate_queue_2026-06-30.csv`는 브랜드당 최대 10개 row를 뽑은 검수 대기열이다.

중요 컬럼:

- `review_lane`: 공식 근거 확보 방식
- `source + food_code`: 큐레이션 적용 키
- `review_source_url`: 공식 알러젠표/라벨 URL을 사람이 채운다.
- `allergen_tags`: 포함 또는 교차접촉 알러젠을 사람이 채운다.
- `allergen_profile_verified`: 공식 근거 확인 전에는 `false`; 확인 후에만 `true`
- `allergen_data_source`: 외식/프랜차이즈 공식표는 `BRAND_OFFICIAL`, 포장 라벨/FoodQR은 `FOODQR`
- `allergen_confidence_level`: 공식 라벨/알러젠표는 `LABEL_DERIVED`

적용용 CSV로 바꿀 때는 다음 헤더를 사용한다.

```csv
source,food_code,recommendation_status,recommendation_reason,last_verified_at,review_source_url,allergen_tags,allergen_profile_verified,allergen_data_source,allergen_confidence_level
```

## 코드 변경 메모

기존 추천 큐레이션 CSV는 food source를 기준으로 알러젠 evidence source를 자동 결정했다. 그래서 `MFDS_FOOD_NUTRIENT_DB` row에 브랜드 공식 알러젠표를 붙이면 근거가 `FOODQR`로 저장되는 문제가 있었다.

이를 막기 위해 큐레이션 CSV에 선택 컬럼을 추가했다.

- `allergen_data_source`
- `allergen_confidence_level`

두 컬럼을 함께 입력하면 food source와 독립적으로 알러젠 근거 source를 저장한다. 기존 8컬럼 CSV도 계속 허용한다.

## 다음 실행

1. `brand_allergen_profile_gap_summary_2026-06-30.csv`에서 처리할 브랜드를 고른다.
2. 해당 브랜드의 공식 알러젠표 또는 공식 라벨 URL을 확보한다.
3. `brand_allergen_profile_candidate_queue_2026-06-30.csv`의 해당 row에 `review_source_url`, `allergen_tags`, `allergen_profile_verified=true`를 채운다.
4. 적용용 CSV 헤더로 정리해 `/api/v1/admin/diet/catalog/curation-csv`에 업로드한다.
5. `CandidatePoolSummary`에서 `allergenProfileVerifiedTotal`과 `engineReadyTotal` 증가를 확인한다.

주의: 피자/베이커리/디저트 브랜드는 알러젠 범위가 넓어 알러지 사용자 통과율 개선이 작을 수 있다. 대량 처리 전에 각 브랜드 5~10개 row로 benchmark를 먼저 확인한다.
