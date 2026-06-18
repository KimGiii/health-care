# 브랜드 공식 메뉴 알러젠 CSV 검수 리포트

작성일: 2026-06-17
산출물: `docs/references/brand_menu_allergen_verified_2026-06-17.csv`
대상 importer: `BrandMenuCsvImporter` (`source=BRAND_OFFICIAL`, `confidence=LABEL_DERIVED`)
검증: 실제 CSV를 `BrandMenuCsvImporter.parseCsv` + `importRows`(mock repo)로 통과시켜 거절 0·skip 0 확인

## 1. 요약

| 항목 | 수치 |
|---|---:|
| 총 검수 행 | 376 |
| 버거킹 | 167 |
| 맥도날드 | 116 |
| 롯데리아 | 93 |
| `allergen_profile_verified=true` | 331 |
| 알러젠 공란(음료·주스 등 공식표 알러젠 없음) | 45 |
| `recommendation_status` | `SEARCH_ONLY=364`, `RECOMMENDABLE_WITH_CAUTION=12` |

카테고리 분포: `PROCESSED=238`, `PROTEIN_SOURCE=59`(치킨/너겟/윙류), `BEVERAGE=55`, `OTHER=24`(소스/토핑).

## 2. 원자료(공식 알러젠 표)

| 브랜드 | 원자료 | 형식 |
|---|---|---|
| 버거킹 | `BURGER_KING_KOREA_NUTRITION_ALLERGY_2026-06-09.json` | 메뉴별 알러젠 문자열 + 제공량 영양 구조화 JSON |
| 맥도날드 | `MCDONALDS_KOREA_NUTRITION_ALLERGY_2026-06-09.md` | 알러젠 섹션(2.x) + 영양 섹션(3.x) |
| 롯데리아 | `LOTTERIA_KOREA_DELIVERY_NUTRITION_ALLERGY_RAW_2026-06-08.txt` | 배달용 탭 구분 원문 |

## 3. 검수·정규화 규칙

- 영양 입력은 모두 `nutrition_basis=PER_SERVING`(제공량 g 기준)으로 적재. importer가 100g당으로 정규화.
- 기준치 % 괄호 제거: `52(94)`→`52`, `1,080(54%)`→`1080`, `1g 미만`→`1`. 단위(`g`/`mg`/`ml`/`kcal`) 제거.
- 알러젠 한국어 라벨은 importer 매핑을 그대로 사용(`난류`→EGG, `대두`→SOY, `쇠고기`→BEEF 등).
  - `조개`/`조개류(가리비)`/`조개류(굴)` → `조개류`(SHELLFISH)로 정규화(괄호 세부품목 제거).
- 맥도날드 세트/콤보의 구성품 분해 알러젠(`빅맥(...), 후렌치후라이(...), 케첩(...)`)은 괄호 안 토큰 합집합으로 처리. 단, 구성품은 단품 행에 동일 알러젠이 보존되므로 세트 자체는 미적재.
- 공식표에 알러젠이 없는 메뉴(콜라·아메리카노·주스 등)는 `allergen_tags` 공란 + `allergen_profile_verified` 공란. v1 "없음 주장" 미표현 정책과 일치.
- 포함 알러젠이 1개 이상인 행만 `allergen_profile_verified=true`(완결 프로필 검토).

## 4. 적재 제외(skip) 결정

| 브랜드 | 제외 대상 | 사유 |
|---|---|---|
| 공통 | 세트·라지세트·콤보·해피밀·팩 | 공식표가 칼로리 범위만 제공, 제공량(g) 없음 → `PER_SERVING` 불가. 알러젠은 구성 단품에 보존 |
| 버거킹 | 무영양 행 21개(더블와퍼·불고기버거·통다리치킨버거·스모키마요롱치킨버거·스파이시크랩버거·할라피뇨와퍼주니어·소스류·주류·무료품) | 원본 JSON에서 중량·열량이 0이라 제공량 산출 불가 |
| 롯데리아 | 구성품 주석 서브행(`제품명 : 알러젠` 형식) | 독립 메뉴가 아닌 세트 구성 설명 행 |
| 맥도날드 | `해쉬 브라운` 중복(스낵·해피밀옵션) | 동일 영양 → food_code 기준 1건 유지 |

> 버거킹 플래그십 버거 일부(더블와퍼 등)는 원본 영양값이 0이라 이번 검수에서 제외했다. 추천 후보 큐레이션 전 공식 영양값 재수집이 필요하다.

## 5. 탄수화물·총지방 공란 사유

현행 CSV의 `carbs`, `fat` 컬럼은 **3사 모두 전행 공란**이다.

### 5.1 한국 프랜차이즈 간이 영양표시 법적 의무 범위

식품표시광고법 시행규칙 별표에 따라 프랜차이즈 매장의 메뉴 영양 의무표시 항목은 5종에 한정된다.

| 의무 표시 항목 | CSV 컬럼 |
|---|---|
| 열량 | `calories` |
| 나트륨 | `sodium` |
| 당류 | `sugar` |
| 포화지방 | `saturated_fat` |
| 단백질 | `protein` |

`탄수화물(carbs)`, `총지방(fat)`은 법적 의무 대상이 아니어서 3사 간이 영양표가 공개하지 않는다. 공란은 데이터 누락이 아니라 원자료 자체의 미공개 항목이다.

### 5.2 브랜드별 탄수화물·총지방 공개 현황

| 브랜드 | 공식 간이표 | 상세 전체 영양 공개 여부 |
|---|---|---|
| 버거킹 | 구조화 JSON — 5종 의무 항목만 | 별도 PDF 없음, 5종만 공개 |
| 롯데리아 | 배달용 탭 원문 — 5종만 | 별도 PDF 없음, 5종만 공개 |
| 맥도날드 | 영양 표 md — 5종만 | 별도 공식 PDF 확인 불가, 웹 공개 데이터도 5종만 제공 |

### 5.3 맥도날드 탄수화물·지방 수집 불가 결론 (2026-06-17)

맥도날드 코리아 공식 웹사이트(`www.mcdonalds.co.kr`)를 분석한 결과 **탄수화물·총지방 데이터를 공개하지 않는 것이 확인됐다.**

조사 내역:
- 웹사이트 Nuxt SSR 데이터 직접 파싱 — `carbohydrate`, `fat` 필드가 DB에 존재하나 전 메뉴 빈 문자열
- 카테고리 목록 페이지, 개별 제품 상세 페이지 모두 동일하게 비어 있음
- 공개된 영양 항목은 의무 표시 5종(열량, 포화지방, 당, 단백질, 나트륨)만 해당
- 업로드 경로 PDF 탐색 시도 — 찾을 수 없음
- **결론: 탄수화물·지방은 맥도날드 코리아가 웹에서 공개하지 않으며, CSV에 추가 불가**

**맥도날드 116행의 `carbs`·`fat`는 공백으로 유지한다.**
버거킹·롯데리아도 동일하게 상세 영양표 미공개로 `carbs`·`fat` 추가 불가.

## 6. 출시 추천 후보 큐레이션 결과

2026-06-17에 브랜드 공식 메뉴 중 12개를 `RECOMMENDABLE_WITH_CAUTION`으로 승격했다. 3사 공식 자료가 `carbs`·`fat`을 공개하지 않으므로 일반 추천(`RECOMMENDABLE`)은 사용하지 않았다.

선정 기준:

- `allergen_profile_verified=true`
- 1회 제공량 기준 단백질 15g 이상
- 1회 제공량 기준 300~400kcal대 중심
- 같은 브랜드 안에서 나트륨·포화지방이 상대적으로 낮은 메뉴
- 세트, 음료, 소스, 대형 플래그십, 무영양 원본 행 제외

공통 주의 사유:

> 공식 영양정보 기준 단백질 15g 이상이나 탄수화물·총지방 미공개 및 나트륨/포화지방 주의

| 브랜드 | 메뉴 | kcal | 단백질(g) | 나트륨(mg) | 포화지방(g) |
|---|---|---:|---:|---:|---:|
| 버거킹 | 비프불고기버거 | 384 | 15 | 492 | 5 |
| 버거킹 | 와퍼주니어 | 376 | 17 | 556 | 5 |
| 버거킹 | 트러플머쉬룸와퍼주니어 | 399 | 18 | 711 | 6 |
| 버거킹 | 햄버거 | 353 | 15 | 540 | 5.2 |
| 맥도날드 | 에그 맥머핀 | 306 | 19 | 712 | 5 |
| 맥도날드 | 베이컨 에그 맥머핀 | 327 | 19 | 687 | 6 |
| 맥도날드 | 베이컨 토마토 에그 머핀 | 341 | 17 | 666 | 3.6 |
| 맥도날드 | 치즈버거 | 318 | 16 | 612 | 6 |
| 롯데리아 | 미라클버거 | 382 | 15 | 600 | 4.6 |
| 롯데리아 | 치킨버거 | 355 | 15 | 620 | 3.8 |
| 롯데리아 | 화이어윙4조각 | 357 | 17 | 550 | 5 |
| 롯데리아 | 지파이 고소한맛(S) | 397 | 18 | 620 | 6 |

로컬 DB 재적재 검증:

- `POST /api/v1/admin/diet/catalog/import/brand-csv` 재적재 결과: `created=0`, `updated=376`, `skipped=0`, `rejectedRows=[]`
- `food_catalog` 총 137,159행
- `BRAND_OFFICIAL`: 376행(`SEARCH_ONLY=364`, `RECOMMENDABLE_WITH_CAUTION=12`)
- 전체 추천 후보: 54행(`SEED RECOMMENDABLE=42`, `BRAND_OFFICIAL RECOMMENDABLE_WITH_CAUTION=12`)
- `BRAND_OFFICIAL` 알러젠 태그: 1,436행
- `(food_catalog_id, allergen_tag)` 중복: 0건

CSV 재적재 중 기존 `BRAND_OFFICIAL` 알러젠 태그 삭제 후 재삽입이 unique 제약에 걸리는 문제가 확인되어, `FoodAllergenTagRepository.replaceBySource`에서 삭제 후 `flush()`하도록 보정했다. 관련 테스트(`BrandMenuCsvImporterTest`, `FoodCatalogAdminControllerTest`, `FoodCatalogAdminOperationsTest`) 통과.

## 7. 다음 단계

| 우선순위 | 작업 |
|---|---|
| 완료 | 추천 후보 큐레이션 — 브랜드별 4개씩 총 12개를 `RECOMMENDABLE_WITH_CAUTION`으로 승격 |
| P2 | 서브웨이 — 알러젠이 PNG 이미지표(`SUBWAY_KOREA_SANDWICH_ALLERGY_TABLE_2026-05-26.png`)라 OCR/수동 검수 후 별도 추가 |
| P2 | 버거킹 무영양 플래그십 메뉴 — 더블와퍼 등 21개 영양값 공식 재수집 |
