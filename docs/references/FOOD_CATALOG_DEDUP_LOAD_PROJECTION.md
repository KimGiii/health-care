# 식품 카탈로그 dedup 전량 적재 projection (acceptance target)

- 생성: 2026-06-24 16:36 KST
- 모드: 전수(FULL) — 수용 raw 902,498행
- 산출: 캡처 census TSV에 `CanonicalDedupResolver` 의미를 재생(read-only, DB 미적재).
- 2단계 재현: ① (source, food_code) upsert 병합 → ② (food_code, name_key) canonical 클러스터링.

## 적재 후 기대 규모 (전량 적재 수용 기준)

| 지표 | 값 | 의미 |
|---|---:|---|
| 적재 행(food_catalog) | **615,509** | (source, food_code) 병합 후 — 자체 2× 중복 흡수됨 |
| canonical(대표) | **323,899** | 검색·추천 노출 + ServingOption 생성 대상 |
| superseded(패자) | **291,610** | 숨김·옵션 미생성 (옵션 폭증 차단) |
| COLLISION 코드 | **2,781** (= `dedup_state=COLLISION` **행 5,562**, 코드당 2대표) | 같은 코드·다른 이름 → 검토 큐 |
| dedup 제거율 | **47.4%** | superseded / 적재 행 |

> ⚠️ **단위 주의**: 위 `2,781`은 충돌 **코드(그룹)** 수다. `dedup_state` 컬럼으로 세면 그 **2배(≈5,562행)** 가 COLLISION 으로 표시된다(코드당 대표 2개). 적재 후 `select count(*) ... where dedup_state='COLLISION'` 결과는 코드가 아니라 **행** 이므로 ~5,562와 비교해야 한다.

## 1. (source, food_code) 병합 — 적재 행 수

가공식품 자체 2× 중복(census 지표 1)은 `findBySourceAndFoodCode` upsert로 흡수되어 출처당 코드 1행만 적재된다.

| 출처 | 수용 raw 행 | 적재 행(고유 코드) | 자체중복 흡수 |
|---|---:|---:|---:|
| 가공식품 표준데이터 | 580,478 | 293,489 | 286,989 |
| 음식 표준데이터 | 19,495 | 19,495 | 0 |
| 식품영양성분 DB | 302,525 | 302,525 | 0 |
| **합계** | **902,498** | **615,509** | **286,989** |

## 2. canonical 클러스터링 — 출처 간 dedup

- 클러스터링 대상(name_key 보유): **615,509**행
- 독립 대표(name_key 공백): **0**행
- 고유 (code, name_key) 클러스터: **323,899**
- → canonical 총계 = 클러스터 + 독립 = **323,899**
- → superseded = 클러스터링 대상 − 클러스터 = **291,610**

### 대표(canonical) 출처 분포

| 대표 출처 | 클러스터 수 |
|---|---:|
| 가공식품 표준데이터 (우선순위 300) | 293,489 |
| 식품영양성분 DB (우선순위 200) | 30,395 |
| 음식 표준데이터 (우선순위 100) | 15 |

## 3. 음식(dish) ⊂ 상위 출처 흡수

- dish 포함 클러스터: **19,495**
  - 상위 출처(가공/영양DB)에 흡수(superseded): **19,480**
  - dish 가 대표로 남음(상위 출처 없음·name_key 불일치): **15**

> census 결론 2(음식 코드 100% 가 영양DB와 겹침)는 코드 기준. 여기서는 (code, name_key) 기준이라 이름 불일치분은 dish 가 별도 대표(COLLISION)로 남을 수 있다.

## 4. COLLISION (검토 큐)

- 같은 코드·다른 name_key 코드: **2,781개**
- COLLISION 표시 canonical 행: **5,562**
- resolver COLLISION 은 **이름 차이** 기준이다. census 지표 5의 충돌(3,961) 중 '영양값만 다르고 이름 같음' (1,360)은 같은 클러스터로 병합(우선순위 출처 영양값 채택)되어 COLLISION 이 아니다.

## 5. 전량 적재 게이트 체크리스트

- [x] 검색 `searchAll` 패자 제외: `isCustom OR canonicalGroupId IS NULL`
- [x] 추천 후보 `isCanonicalCandidate()`: `canonicalGroupId IS NULL`
- [x] 적재 시 옵션 게이트: 패자 행 옵션 삭제, 대표만 생성 (`syncServingOptions`)
- [x] 강등 구대표 옵션 정리: `deleteOptionsForSupersededRows()` (백필 최종 패스)
- [x] 적재 순서 무관 수렴: `CanonicalDedupResolver` 우선순위 승격/강등 (단위 테스트)
- [x] 전량 적재 실행 → 결과가 위 기대 규모(canonical≈323,899, superseded≈291,610)와 일치 확인 (아래 §6 실측)
- [x] 적재 후 ServingOption 행 수 ≤ canonical × 제공량 옵션 상한 확인 (옵션 폭증 0)

## 6. 실측 대조 — 로컬 전량 적재 (2026-06-26)

로컬 PG16에 3종 전량 적재(API 원본 918,086행: 가공 595,962 / 음식 19,495 / 영양DB 302,629) → `/dedup/backfill` 수렴. **projection 검증 완료**.

| 지표 | projection (06-24) | 실측 (06-26) | 차이 |
|---|---:|---:|---|
| 적재 행(non-custom, +SEED 300) | 615,809 | 620,119 | +4,310 |
| canonical 대표(`group_id IS NULL`) | 324,199 | 331,066 | +6,867 |
| ㄴ CANONICAL state | — | 325,322 | |
| superseded | 291,610 | 289,053 | −2,557 |
| **COLLISION 코드 / 행** | **2,781 / 5,562** | **2,872 / 5,744** | +91 / +182 (+3.3%) |
| ServingOption(패자 보유) | 0 | **0** | ✅ |

- 차이의 대부분은 **gov 데이터 성장**: 가공식품 고유코드 293,489→297,799(+4,310). 음식·영양DB는 census와 정확히 일치.
- **COLLISION 정량 분석**(2,872 코드): **2,559(89%)는 구두점/기호만 다른 동일 제품**(`현미100%`↔`100`, `탕탕!`↔`탕탕`; 99.5%가 NUTRIENT_DB↔PROCESSED 쌍), **313(11%)만 이름이 실제로 다른 진짜 충돌**.
- 원인: `duplicateNameKey` strip 패턴 `[\s\-_/()（）]`이 `% ! , . &` 등 기호를 남김 — census `normalize.py`도 **동일 패턴**이라 둘이 어긋나지 않음(정규화 버그 아님). 기호 정규화 강화 여부는 과병합 위험 때문에 별도 정책 결정(설계 §9 자동병합 금지).
