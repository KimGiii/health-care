# 식품 카탈로그 데이터 프로파일링 리포트

조사일: 2026-06-09

상태: 0단계 1차 완료

관련 실행 계획: `docs/exec-plans/FOOD_CATALOG_ENRICHMENT.md`

## 1. 목적

`food_catalog`를 검색, 기록, 추천의 공통 기반으로 강화하기 전에 현재 사용 중인 공공데이터 2종과 `FoodNtrCpntDbInfo02`의 필드, 샘플 응답, 중복 가능성, 브랜드 메뉴 커버리지를 비교한다.

이번 조사는 다음을 산출물로 남긴다.

- 데이터 비교 리포트
- 필드 매핑표
- source priority 초안
- 브랜드 공식 영양정보 적재 형식 조사

## 2. 조사 범위와 제한

### 2.1 호출 범위

API 키는 로컬 설정에서 읽어 사용했고 문서에는 기록하지 않는다.

| 소스 | 식별자 | 호출 방식 | 호출 시각 |
|---|---:|---|---|
| 전국통합식품영양성분정보 가공식품 표준데이터 | 15100066 | `pageNo=1`, `numOfRows=100`, 검색어 4종 | 2026-06-09 02:01 KST |
| 전국통합식품영양성분정보 음식 표준데이터 | 15100070 | `pageNo=1`, `numOfRows=100`, 검색어 4종 | 2026-06-09 02:01 KST |
| 식품영양성분DB정보 | 15127578 | `pageNo=1`, `numOfRows=100`, 검색어 4종 | 2026-06-09 02:01 KST |

검색어는 `김치찌개`, `와퍼`, `닭가슴살`, `샐러드`를 사용했다.

### 2.2 제한

- 전체 86만 건 이상을 모두 순회하지 않았다. 이번 리포트는 총 건수 확인, 첫 페이지 필드 커버리지, 대표 검색어별 샘플 비교를 기반으로 한다.
- `FoodNtrCpntDbInfo02`의 `AMT_NUM*` 필드는 전체 출력 메세지 파일과 추가 샘플 대조가 필요하다. 이번에는 핵심 영양소 중 샘플로 검증한 필드만 1차 확정한다.
- 브랜드 공식 메뉴는 자동 크롤링 적재가 아니라 관리자 CSV 수동 검수 적재 가능성을 판단하는 목적의 형식 조사다.

## 3. 기존 구현 확인

현재 백엔드는 사용자 검색 경로에서 공공데이터를 직접 호출할 수 있다.

| 항목 | 현재 구현 |
|---|---|
| 설정 | `app.food-api.processed-food-api-url`, `app.food-api.general-food-api-url` |
| 가공식품 URL | `https://api.data.go.kr/openapi/tn_pubr_public_nutri_process_info_api` |
| 음식 URL | `https://api.data.go.kr/openapi/tn_pubr_public_nutri_food_info_api` |
| 검색 파라미터 | `foodNm` |
| 현재 매핑 | `foodCd`, `foodNm`, `mfrNm`, `foodLv3Nm`, `nutConSrtrQua`, `enerc`, `prot`, `fatce`, `chocdf`, `sugar`, `fibtg`, `fasat`, `fatrn`, `chole`, `nat`, `itemMnftrRptNo` |
| 결과 처리 | 가공식품/음식 결과를 교차 병합 후 제한 |

현재 `FoodCatalog` 엔티티에는 100g 기준 10종 영양소, 카테고리, 커스텀 여부, 사용 횟수는 있으나 다음 필드는 없다.

- 출처 식별자: `food_code`, `source`, `source_detail`
- 브랜드/제조사: `brand_name`, `maker`
- 제공량: `serving_size_g`, `serving_reference`
- 추천 상태: `recommendation_status`, `recommendation_reason`
- 데이터 운영: `data_version`, `last_verified_at`

## 4. 데이터 소스 메타데이터

| 소스 | 공식 URL | 공개 정보 |
|---|---|---|
| 가공식품 표준데이터 | https://www.data.go.kr/data/15100066/standard.do | 수정일 2026-06-05, 갱신주기 연간, 가공식품 영양성분 |
| 음식 표준데이터 | https://www.data.go.kr/data/15100070/standard.do | 수정일 2026-05-06, 갱신주기 연간, 음식/외식 영양성분 |
| 식품영양성분DB정보 | https://www.data.go.kr/data/15127578/openapi.do | 등록일 2024-04-11, 수정일 2025-12-05, 업데이트 주기 실시간, 데이터포맷 JSON+XML, 이용허락범위 제한 없음 |
| 식품영양성분 DB Open API 안내 | https://various.foodsafetykorea.go.kr/nutrient/industry/openApi/info.do | 공공데이터포털의 영양정보 Open API 바로가기 제공 |

## 5. 총 건수와 첫 페이지 필드 관찰

| 소스 | 총 건수 | 첫 페이지 샘플 | 핵심 관찰 |
|---|---:|---:|---|
| 15100066 가공식품 | 580,478 | 100 | 제조사, 식품중량, 품목제조보고번호, 수입 여부 등 가공식품 식별 필드가 강하다. |
| 15100070 음식 | 19,495 | 100 | 외식/급식/가정식 음식군에 강하고 `restNm`으로 업체/브랜드가 들어온다. 첫 페이지 샘플은 일부 매크로 결측이 있었다. |
| 15127578 FoodNtr | 282,296 | 100 | 통합 DB 성격이다. `FOOD_CD`, `FOOD_NM_KR`, `SERVING_SIZE`, `Z10500`, `RESEARCH_YMD`, `UPDATE_DATE` 등 보강 필드가 많다. |

## 6. 검색어별 샘플 커버리지

`completeCoreRows`는 샘플 100건 이내에서 열량, 단백질, 탄수화물, 지방이 모두 숫자로 존재한 행 수다.

| 검색어 | 15100066 가공식품 | 15100070 음식 | 15127578 FoodNtr | 판단 |
|---|---|---|---|---|
| 김치찌개 | 40건 / core 40 | 5건 / core 5 | 330건 중 샘플 100 / core 100 | 세 소스 모두 잡히지만 FoodNtr는 파생명과 세부 구성이 훨씬 많다. |
| 와퍼 | 0건 | 0건 | 121건 중 샘플 100 / core 60 | 브랜드 메뉴 커버리지는 FoodNtr가 더 강하다. 단, 최신 공식 메뉴 여부는 별도 검수 필요. |
| 닭가슴살 | 2건 / core 2 | 0건 | 3,569건 중 샘플 100 / core 98 | 제품/브랜드 변형이 FoodNtr에 많이 잡힌다. |
| 샐러드 | 2건 / core 2 | 0건 | 2,580건 중 샘플 100 / core 51 | FoodNtr가 검색 폭은 넓지만 추천 후보로 쓰려면 결측/품질 게이트가 필요하다. |

## 7. 중복률 1차 판단

정규화된 식품명만으로 비교하면 정확한 중복률을 얻기 어렵다.

- `김치찌개`는 가공식품/음식 표준데이터 모두 동일 정규화명으로 잡혔지만, FoodNtr는 `김치찌개_돼지고기`, `김치찌개_꽁치`처럼 세부 파생명이 많다.
- `와퍼`, `닭가슴살`, `샐러드`는 FoodNtr가 압도적으로 많은 결과를 반환하지만 기존 2종과 정확 이름 교집합은 거의 없다.
- 따라서 자동 병합 기준은 `식품명` 단독이 아니라 `source + food_code`를 1차 키로 두고, 후보 중복 리포트에서 `정규화명 + 대표식품코드/카테고리 + 제조사/업체명 + 제공량 + 핵심 영양값 유사도`를 함께 봐야 한다.

## 8. 필드 매핑표

### 8.1 내부 컬럼 매핑

| 내부 컬럼 | 15100066 가공식품 | 15100070 음식 | 15127578 FoodNtr | 브랜드 CSV |
|---|---|---|---|---|
| `food_code` | `foodCd` | `foodCd` | `FOOD_CD` | 자체 코드 또는 `brand_name:menu_name` 해시 |
| `source` | `MFDS_STANDARD_PROCESSED` | `MFDS_STANDARD_DISH` | `MFDS_FOOD_NUTRIENT_DB` | `BRAND_OFFICIAL` |
| `source_detail` | API URL/데이터셋 ID | API URL/데이터셋 ID | `FoodNtrCpntDbInfo02` | 원본 URL/PDF/검수 파일명 |
| `name` | `foodNm` | `foodNm` | `FOOD_NM_KR` | `menu_name` |
| `name_ko` | `foodNm` | `foodNm` | `FOOD_NM_KR` | `menu_name` |
| `brand_name` | 보통 비움, 필요 시 `distNm` 보조 | `restNm` | `SELLER_MANUFAC_NM` 보조 | `brand_name` |
| `maker` | `mfrNm`, `imptNm` | `restNm` 보조 | `MAKER_NM`, `IMP_MANUFAC_NM`, `SELLER_MANUFAC_NM` | 비움 또는 운영사 |
| `category` | `foodLv3Nm` 기반 매핑 | `foodLv3Nm` 기반 매핑 | `FOOD_CAT1_NM` 기반 매핑 | CSV `category` |
| `serving_size_g` | `foodSize`에서 g 파싱 | `foodSize`에서 g 파싱 | `Z10500`, `SERVING_SIZE`에서 g 파싱 | `serving_size_g` |
| `serving_reference` | `servSize`, `nutConSrtrQua` | `servSize`, `nutConSrtrQua` | `NUTRI_AMOUNT_SERVING`, `DISH_ONE_SERVING`, `SERVING_SIZE` | CSV `serving_reference` |
| `data_version` | `crtrYmd` 또는 배치일 | `crtrYmd` 또는 배치일 | `UPDATE_DATE` 또는 배치일 | 검수 배치명 |
| `last_verified_at` | `crtrYmd` | `crtrYmd` | `UPDATE_DATE` | CSV `last_verified_at` |

`serving_size_g`는 `100ml`, `240ml`처럼 ml 기준인 행을 숫자 g로 강제 변환하지 않는다. 이 경우 `serving_reference`에 원문을 보존하고 `serving_size_g`는 비워 두거나 후속 밀도 변환 정책을 둔다.

### 8.2 영양소 매핑

| 내부 컬럼 | 15100066/15100070 | 15127578 FoodNtr | 상태 |
|---|---|---|---|
| `calories_per_100g` | `enerc` | `AMT_NUM1` | 1차 확정 |
| `protein_per_100g` | `prot` | `AMT_NUM3` | 1차 확정 |
| `carbs_per_100g` | `chocdf` | `AMT_NUM6` | 1차 확정 |
| `fat_per_100g` | `fatce` | `AMT_NUM4` | 1차 확정 |
| `sugars_per_100g` | `sugar` | `AMT_NUM7` | 1차 확정 |
| `dietary_fiber_per_100g` | `fibtg` | `AMT_NUM8` | 1차 확정 |
| `sodium_per_100g_mg` | `nat` | `AMT_NUM13` | 1차 확정 |
| `cholesterol_per_100g_mg` | `chole` | 출력 메세지 파일 대조 필요 | 미확정 |
| `saturated_fat_per_100g` | `fasat` | 출력 메세지 파일 대조 필요 | 미확정 |
| `trans_fat_per_100g` | `fatrn` | 출력 메세지 파일 대조 필요 | 미확정 |

FoodNtr 샘플에서 `AMT_NUM1~13`은 표준데이터의 초반 영양소 순서와 맞았다. 하지만 지방산/콜레스테롤 이후 구간은 `AMT_NUM*`가 157개까지 확장되어 있어, importer 구현 전에 `출력메세지_식품영양성분DB정보.xlsx`를 확보해 고정 매핑해야 한다.

## 9. source priority 초안

### 9.1 동일 식품 후보 병합 우선순위

1. `BRAND_OFFICIAL`
2. `MFDS_STANDARD_DISH`
3. `MFDS_STANDARD_PROCESSED`
4. `MFDS_FOOD_NUTRIENT_DB`
5. `SEED`
6. `USER_CUSTOM`

### 9.2 판단 기준

- 브랜드 공식 CSV는 검수 시각과 출처 URL이 명확할 때 같은 브랜드 메뉴의 최우선 소스로 둔다.
- `MFDS_STANDARD_DISH`는 외식/프랜차이즈 음식명과 `restNm`이 있는 경우 검색/기록 커버리지에 유리하다.
- `MFDS_STANDARD_PROCESSED`는 제조사, 품목제조보고번호, 수입 여부가 있어 가공식품 식별에 유리하다.
- `MFDS_FOOD_NUTRIENT_DB`는 기존 2종을 대체하지 않고, 기존 데이터에 없는 항목이나 제공량/식품중량/일자 보강이 필요한 항목을 추가하는 보강 소스로 둔다.
- `USER_CUSTOM`은 사용자가 기록하기 위한 데이터이며 기본 추천 후보에는 넣지 않는다.

## 10. 브랜드 공식 영양정보 형식 조사

| 브랜드 | 공식/준공식 위치 | 제공 형식 | 필드 | v1 판단 |
|---|---|---|---|---|
| 버거킹 | https://www.burgerking.co.kr/menu/nutrition | JS 앱 화면. 검색 노출상 영양정보 페이지 존재 | 중량, 열량, 단백질, 나트륨, 당류, 포화지방, 카페인 | 자동 크롤링은 보류. 관리자 CSV로 일부 메뉴만 검수 적재. FoodNtr에서 `와퍼` 121건이 잡히므로 공공 보강도 병행. |
| BBQ | https://m.bbq.co.kr/menu/menuView.asp | 모바일 메뉴 상세 HTML | 100g당 열량, 당류, 단백질, 포화지방, 나트륨, 알레르기 | 메뉴별 수동 검수에 적합. 치킨류는 보통 `SEARCH_ONLY` 또는 `RECOMMENDABLE_WITH_CAUTION`. |
| 서브웨이 | https://devm.subway.co.kr/more/freshInfo | 모바일 재료 소개 HTML | 재료별 kcal 중심 | 샌드위치 조합형이라 단일 메뉴 CSV 기준을 먼저 정해야 한다. 15cm 기본 조합 기준으로만 수동 적재 권장. |
| 샐러디 | https://salady.com/menu/content2, https://salady.com/pdf/nutrition.pdf?ver=2 | 공식 PDF | 구분, 메뉴명, 제공량, 열량, 탄수화물, 당류, 단백질, 지방, 포화지방, 나트륨 | v1 브랜드 CSV 적재 1순위. 2026.05 PDF이며 추천 후보 게이트 적용이 쉽다. |
| 프레퍼스 | 공개 검색 기준 공식 영양표 미확인 | 제3자/배달 플랫폼 중심 | 일부 메뉴 단백질/칼로리 | 공식 출처 확보 전까지 `BRAND_OFFICIAL` 적재 보류. 브랜드 제공 자료를 받으면 CSV로 검수. |

## 11. 0단계 결론

1. 기존 가공식품/음식 표준데이터 2종은 유지한다. 특히 가공식품 15100066은 수십만 건 규모와 제조사/보고번호 필드가 있어 검색 기반으로 가치가 크다.
2. `FoodNtrCpntDbInfo02`는 브랜드/제품 검색 커버리지가 좋지만, `AMT_NUM*` 매핑과 결측 관리가 필요하므로 단일 메인 소스로 승격하지 않는다.
3. 정확 중복률 산출은 전체 crawl 없이 불가능하다. 다음 단계 importer에는 중복 후보 리포트를 필수 산출물로 넣어야 한다.
4. 브랜드 공식 메뉴는 샐러디와 BBQ가 수동 검수 CSV에 가장 적합하다. 버거킹은 공식 JS 화면과 FoodNtr 보강을 병행하고, 서브웨이는 조합 기준을 먼저 고정해야 한다.
5. Phase 1 스키마 보강은 계획대로 진행 가능하다. 단, FoodNtr의 지방산/콜레스테롤 `AMT_NUM*` 매핑은 importer 구현 전까지 미확정으로 둔다.

## 12. 다음 작업 제안

1. `V23__food_catalog_source_recommendation_fields.sql`로 출처/제공량/추천 상태 필드를 추가한다.
2. `FoodCatalogSource`, `RecommendationStatus` enum을 엔티티에 반영한다.
3. `source + food_code` 부분 유니크 인덱스와 `recommendation_status` 인덱스를 추가한다.
4. 전체 crawl 프로파일러를 별도 배치/스크립트로 만들고, API 트래픽을 고려해 `numOfRows`, rate limit, 재시작 체크포인트를 둔다.

진행 메모(2026-06-10):

- 1~3번은 V23 스키마 보강과 백엔드 엔티티/응답 DTO 반영으로 완료했다.
- 기존 시드 식품은 `SEED + RECOMMENDABLE`, 사용자 커스텀 식품은 `USER_CUSTOM + SEARCH_ONLY`로 백필했다.
- 4번 전체 crawl 프로파일러의 기반으로 공공데이터 page fetcher, 배치 runner, source별 재시작 체크포인트를 2026-06-10에 구현했다. 남은 작업은 실제 API smoke 검증, 중복 후보 리포트, 운영 실행 트리거다.
- 구현상 다음 작업은 중복 후보 리포트와 운영 실행 트리거를 붙이고, 실제 공공 API 응답으로 page fetcher smoke 검증을 수행하는 것이다.
