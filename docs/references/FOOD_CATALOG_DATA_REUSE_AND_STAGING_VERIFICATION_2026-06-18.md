# 식품 카탈로그 데이터 재사용·staging 검증 근거

작성일: 2026-06-18
상태: P2 후속 운영 판단 기록
관련 문서: `docs/exec-plans/FOOD_CATALOG_ENRICHMENT.md`, `docs/FOOD_CATALOG_GUIDE.md`

> 이 문서는 제품/엔지니어링 운영 판단 기록이며 법률 자문이 아니다. 서비스 약관·라이선스 문구가 바뀔 수 있으므로 운영 전량 적재 또는 앱 내 출처 고지 전에는 검토일과 원문 URL을 다시 확인한다.

## 1. 결정

2026-06-18 기준으로 **운영 DB 공공데이터 전량 적재는 즉시 실행하지 않는다.**

대신 staging에서 전량 적재를 1회 검증한 뒤, 아래 조건을 모두 통과할 때만 운영 적재를 진행한다.

1. staging 3개 source가 모두 `exhausted=true`로 종료된다.
2. 429, timeout, DB 길이 초과, Flyway 실패 없이 완료된다.
3. source별 row count, checkpoint, `SEARCH_ONLY` 기본값이 기록된다.
4. import 응답의 `attemptedCount`, `skippedCount`, `skippedRatio`를 source별로 남긴다.
5. skip 비율이 비정상적으로 높으면 대표 rejected/skip 사유를 확인한 뒤 재실행 여부를 결정한다.
6. `/dedup/report`를 실행해 상위 중복 후보를 검수 목록으로 분리한다. 자동 병합은 하지 않는다.
7. 데이터 재사용 조건이 `사용 가능` 또는 `제한 조건을 충족하면 사용 가능`으로 정리된 source만 운영 적재 대상에 포함한다.

운영 전량 적재를 보류하는 이유:

- local에서는 smoke, 제한 배치, 가공식품 대량 장애 케이스까지 검증해 파이프라인 신뢰성은 충분히 확인했다.
- 운영 전량 적재는 검색 품질보다 dedup 검수·출처 고지·라이선스 확인 비용을 먼저 만든다.
- 표준데이터 2종의 상세 페이지는 데이터 범위·제공기관·갱신일은 확인되지만, 본문에서 `이용허락범위` 문구가 직접 노출되지 않았다.
- 푸드QR과 국민건강영양조사 음식별 식품재료량 DB는 직접 재사용 조건을 아직 확정하지 못했다.

## 2. staging 전량 적재 runbook

### 2.1 실행 순서

| 순서 | source | 엔드포인트 | 기준 |
|---:|---|---|---|
| 1 | `MFDS_STANDARD_PROCESSED` | `POST /api/v1/admin/diet/catalog/import/processed-foods` | `exhausted=true`까지 반복 |
| 2 | `MFDS_STANDARD_DISH` | `POST /api/v1/admin/diet/catalog/import/dish-foods` | `exhausted=true`까지 반복 |
| 3 | `MFDS_FOOD_NUTRIENT_DB` | `POST /api/v1/admin/diet/catalog/import/nutrient-db` | `exhausted=true`까지 반복 |
| 4 | dedup | `GET /api/v1/admin/diet/catalog/dedup/report` | 자동 병합 없이 검수 목록 생성 |

호출 예시:

```bash
curl -X POST "$BASE_URL/api/v1/admin/diet/catalog/import/processed-foods?pageSize=100&maxPages=500" \
  -H "X-Admin-Token: $ADMIN_OPERATION_TOKEN"
```

체크포인트는 source별 마지막 완료 페이지 번호만 저장한다. 같은 source를 이어 실행하는 동안 `pageSize`를 바꾸지 않는다. `pageSize`를 바꿔야 한다면 해당 source의 checkpoint와 이미 적재된 smoke row 처리 방식을 먼저 정한다.

### 2.2 rate limit 판단

초기값은 기존 설정과 같은 `app.food-api.import-page-delay-millis=0`으로 smoke/제한 배치를 실행한다.

다음 중 하나가 발생하면 delay를 올린다.

| 증상 | 조치 |
|---|---|
| 429 또는 공공 API 제한 응답 | `500ms`로 올리고 같은 source 재시도 |
| timeout 또는 간헐적 5xx 반복 | `1000ms`로 올리고 maxPages를 낮춰 재시도 |
| DB/애플리케이션 병목 | API delay가 아니라 DB batch/인덱스/로그 병목으로 분리 진단 |

운영은 staging에서 확정한 delay 값을 그대로 사용한다. 운영 중 delay를 바꿔야 하면 checkpoint/pageSize 불변 조건을 먼저 확인한다.

### 2.3 적재 후 검증 SQL

source별 row count:

```sql
SELECT source, COUNT(*) AS row_count
FROM food_catalog
WHERE deleted_at IS NULL
GROUP BY source
ORDER BY source;
```

공공데이터 신규 항목 추천 상태:

```sql
SELECT source, recommendation_status, COUNT(*) AS row_count
FROM food_catalog
WHERE deleted_at IS NULL
  AND source IN ('MFDS_STANDARD_PROCESSED', 'MFDS_STANDARD_DISH', 'MFDS_FOOD_NUTRIENT_DB')
GROUP BY source, recommendation_status
ORDER BY source, recommendation_status;
```

체크포인트:

```sql
SELECT source, last_completed_page, updated_at
FROM food_catalog_import_checkpoints
ORDER BY source;
```

대표 검색어:

```sql
SELECT source, name_ko, food_code, recommendation_status
FROM food_catalog
WHERE deleted_at IS NULL
  AND (
    name_ko ILIKE '%김치찌개%'
    OR name_ko ILIKE '%닭가슴살%'
    OR name_ko ILIKE '%샐러드%'
    OR name_ko ILIKE '%와퍼%'
  )
ORDER BY source, name_ko
LIMIT 100;
```

### 2.4 운영 기록 양식

| 항목 | 값 |
|---|---|
| 환경 | staging / production |
| 실행일 | |
| source | |
| pageSize | |
| maxPages | |
| import-page-delay-millis | |
| 시작 checkpoint | |
| 종료 checkpoint | |
| 마지막 summary.exhausted | |
| summary.createdCount | |
| summary.updatedCount | |
| summary.skippedCount | |
| summary.attemptedCount | |
| summary.skippedRatio | |
| source row count | |
| `SEARCH_ONLY` row count | |
| 429/timeout 여부 | |
| dedup totalGroups / totalCandidates | |
| 후속 조치 | |

## 3. 데이터 재사용 조건

| 데이터원 | 현재 판단 | 근거 | 운영 사용 조건 |
|---|---|---|---|
| 전국통합식품영양성분정보 가공식품 표준데이터 `15100066` | 조건부 사용 | 공공데이터포털 공식 상세 페이지에서 제공기관은 식품의약품안전처, 갱신주기 연간, 수정일 2026-06-05로 확인. 그리드 다운로드는 5만 건 제한이며 전체 데이터는 API 활용 안내가 있다. 다만 상세 본문에서 `이용허락범위` 문구는 직접 확인되지 않았다. | source URL, 제공기관, 수정일, 데이터기준일자를 보존한다. 운영 전량 적재 전 공공데이터포털 메타데이터/제공기관 문의로 이용허락범위를 캡처한다. |
| 전국통합식품영양성분정보 음식 표준데이터 `15100070` | 조건부 사용 | 공공데이터포털 공식 상세 페이지에서 제공기관은 식품의약품안전처, 갱신주기 연간, 수정일 2026-05-06으로 확인. 설명에는 국민건강영양조사 음식별 식품재료량 자료집 기반 산출 정보가 포함된다. 표준데이터 본문에서 `이용허락범위` 문구는 직접 확인되지 않았다. | 가공식품 표준데이터와 동일. 국민건강영양조사 원자료를 직접 재사용하는 것이 아니라 표준데이터 결과값을 쓰는 범위로 제한한다. |
| 식품영양성분DB정보 `FoodNtrCpntDbInfo02` / `15127578` | 사용 가능, 운영 심의 조건 있음 | 공공데이터포털 공식 OpenAPI 상세에서 비용 무료, 개발계정 트래픽 10,000, 운영계정은 활용사례 등록 시 트래픽 증가 가능, 운영단계 심의승인, 이용허락범위 제한 없음으로 확인. | 운영키/심의 상태와 트래픽 한도를 운영 기록에 남긴다. `MFDS_FOOD_NUTRIENT_DB`는 기존 표준데이터 2종의 보강 source로만 사용한다. |
| 국민건강영양조사 음식별 식품재료량 DB | 직접 사용 보류 | 질병관리청 국민건강영양조사 사이트는 원시자료 이용지침서/코드자료 메뉴를 제공하지만, 음식별 식품재료량 DB의 상업 서비스 재사용 조건은 이번 검토에서 확정하지 못했다. | 직접 적재·알러젠 추론 source로 쓰지 않는다. 필요 시 질병관리청 원자료 이용지침과 해당 DB별 이용 조건을 별도 확인한다. |
| 푸드QR | 직접 사용 보류 | 원재료·알레르기 표시 source로 유용하지만, 공식 공개 API와 재사용 조건을 이번 검토에서 확정하지 못했다. | 자동 수집 또는 대량 적재 금지. 공식 API/약관/이용허락범위가 확인된 뒤 `LABEL_DERIVED` source로 설계한다. |
| Open Food Facts | 제한 조건 충족 시 사용 가능 | Open Food Facts API 문서는 누구나 재사용할 수 있는 open data라고 설명하고, DB는 ODbL, 개별 contents는 Database Contents License, product images는 CC BY-SA라고 명시한다. API rate limit은 product read 15 req/min/IP, search 10 req/min/IP이며, 대량 조회는 CSV/JSONL 다운로드 권장이다. ODbL 요약은 attribution, share-alike, keep-open 조건을 둔다. | `allergens_tags` 같은 라벨 파생 필드만 검토한다. 출처/라이선스 고지와 ODbL share-alike 영향을 제품/법무 관점에서 수용하기 전에는 내부 폐쇄형 DB에 대량 병합하지 않는다. product image는 사용하지 않는다. |
| 브랜드 공식 자료 | 제한적 사용 | 현재 CSV는 버거킹·맥도날드·롯데리아의 공식 또는 사용자 제공 원문을 수동 검수해 사실 정보만 보존했다. 브랜드별 원문 URL/수집일/검수일을 별도 reference 문서에 남겼다. | 로고, 이미지, 마케팅 문구를 복제하지 않는다. 영양·알러젠 사실값, source URL, last_verified_at만 저장한다. 자동 크롤링은 하지 않고 관리자 CSV/수동 검수로 제한한다. 약관 충돌이 확인되면 해당 브랜드 source를 중단한다. |

## 4. 확인한 공식 근거 URL

- 공공데이터포털 이용정책: https://www.data.go.kr/ugs/selectPortalPolicyView.do
- 공공누리 유형 안내: https://www.kogl.or.kr/info/license.do
- 전국통합식품영양성분정보 가공식품 표준데이터: https://www.data.go.kr/data/15100066/standard.do
- 전국통합식품영양성분정보 음식 표준데이터: https://www.data.go.kr/data/15100070/standard.do
- 식품영양성분DB정보 OpenAPI: https://www.data.go.kr/data/15127578/openapi.do
- 질병관리청 국민건강영양조사: https://knhanes.kdca.go.kr/knhanes/main.do
- Open Food Facts API 문서: https://openfoodfacts.github.io/openfoodfacts-server/api/
- ODbL 요약: https://opendatacommons.org/licenses/odbl/summary/

## 5. P2 후속 작업

1. 표준데이터 2종의 `이용허락범위`를 공공데이터포털 메타데이터 또는 제공기관 문의로 확인해 이 문서를 갱신한다.
2. staging full-load 실행 후 이 문서의 운영 기록 양식을 채운다.
3. dedup report 상위 그룹을 `docs/references/` 검수 CSV로 분리한다.
4. 푸드QR과 국민건강영양조사 음식별 식품재료량 DB는 공식 이용 조건이 확인될 때까지 알러젠 자동 추론 source에서 제외한다.
