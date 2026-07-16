# HACCP 브랜드 알러젠 전체 매칭 결과 2026-06-30

## 입력

- 후보 CSV: `docs/references/food_catalog_brand_allergen_gap_engine_ready_2026-06-30.csv`
- HACCP CSV: `docs/references/HACCP_ALLERGEN_PRODUCTS_2026-06-20.csv`
- 적용 배치 제한: 300건

후보 CSV는 로컬 DB에서 추출한 81MB 분석 입력 파일이라 커밋하지 않는다. 필요하면
`food_catalog`에서 canonical 대표 행, 브랜드/제조사 존재, 4대 매크로 완전,
verified serving option 존재, 알러젠 프로필 부재 조건으로 다시 추출해 스크립트에
입력한다.

## 전체 결과

| 지표 | 건수 |
| --- | ---: |
| engine-ready 알러젠 gap 후보 | 301588 |
| `MFDS_STANDARD_PROCESSED` 후보 | 294942 |
| HACCP 제품명 매칭 후보 | 5227 |
| 제품명+제조사/판매사 매칭 후보 | 1534 |
| 적용 가능 후보 | 1525 |
| 이번 curated batch row | 300 |

## 결정 분포

| decision | 건수 |
| --- | ---: |
| `APPLY` | 1525 |
| `REVIEW_CONFLICTING_ALLERGENS` | 9 |
| `REVIEW_NAME_ONLY` | 3693 |

## 이번 batch 상태 분포

| recommendation_status | 건수 |
| --- | ---: |
| `RECOMMENDABLE` | 225 |
| `RECOMMENDABLE_WITH_CAUTION` | 75 |

## 이번 batch 카테고리 분포

| category | 건수 |
| --- | ---: |
| `BEVERAGE` | 21 |
| `PROCESSED` | 279 |

## 로컬 DB 반영 결과

`brand_allergen_profile_curated_batch_full_2026-06-30.csv`를 local backend의
`/api/v1/admin/diet/catalog/curation-csv` endpoint로 반영했다.

응답:

| 항목 | 건수 |
| --- | ---: |
| updatedCount | 300 |
| skippedCount | 0 |
| rejectedRows | 0 |

반영 후 알러젠 근거 분포:

| source | confidence_level | profile count |
| --- | --- | ---: |
| `FOODQR` | `LABEL_DERIVED` | 300 |
| `MFDS_CLASS` | `DIRECT_VERIFIED` | 24 |
| `MFDS_CLASS` | `LABEL_DERIVED` | 2 |

반영 후 strict engine-ready 기준:

| 지표 | 건수 |
| --- | ---: |
| 추천 후보 전체 | 12279 |
| macro complete 후보 | 12279 |
| verified serving option 후보 | 11979 |
| verified allergen profile 후보 | 326 |
| service `engineReadyTotal` | 11979 |
| strict engine-ready 후보 | 300 |
| untagged 후보 | 11912 |

strict engine-ready는 `recommendation_status`가 추천 후보이고, canonical 대표 행이며,
4대 매크로·검증 제공량·검증 알러젠 프로필을 모두 가진 row로 계산했다. 현재
`CandidatePoolSummaryService.engineReadyTotal`은 알러젠 프로필을 포함하지 않으므로,
알러지 안전 관점의 실제 KPI는 strict engine-ready를 별도로 봐야 한다.

## 운영 메모

- `APPLY`는 제품명 정규화 일치, 제조사/판매사 맥락 일치, 단일 알러젠 세트 수렴을 모두 만족한 row다.
- `REVIEW_NAME_ONLY`는 제품명만 같고 제조사/판매사 맥락이 확인되지 않아 자동 승격하지 않는다.
- `REVIEW_CONFLICTING_ALLERGENS`는 회사 맥락은 맞지만 매칭 라벨 간 알러젠 세트가 달라 수동 확인이 필요하다.
- 이번 batch CSV는 `RecommendationCurationCsvImporter`에 바로 넣을 수 있는 형식이다.
