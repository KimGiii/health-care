# 식품 API 3종 전수 중복 프로파일링 하니스

`food_catalog` 에 **적재하지 않고**(read-only) 식품 공공 API 3종을 전수 페이징하며
행별 핑거프린트만 TSV 로 남긴 뒤, 프로덕션 dedup 의미를 그대로 적용해 6개 중복 지표를 산출한다.

## 대상 API

| 출처(`--source`) | totalCount | 페이지크기 | 콜수 | 보고번호 | 브랜드 매핑 |
|---|---:|---:|---:|---|---|
| `processed` (가공식품 표준데이터) | 580,478 | 100 | 5,805 | `itemMnftrRptNo` | 없음(null) |
| `dish` (음식 표준데이터) | 19,495 | 100 | 195 | 없음 | `restNm`(식당명) |
| `nutrient_db` (식품영양성분 DB) | 302,629 | 100 | 3,027 | 없음 | `SELLER_MANUFAC_NM`(대개 공백) |
| **합계** | **902,602** | | **≈9,027** | | |

> **페이지크기 100 고정 이유**: 표준데이터 API 는 `numOfRows=1000` 이면 페이지당 ~61s
> (타임아웃 직전, 행당 ~61ms)로 비정상적으로 느리다. `100` 이면 ~1.2s(행당 ~12ms)로
> 처리량·안정성이 모두 우수해 총 콜수는 늘지만(581→5,805) 실측 wall-clock 은 오히려 짧다.
> `nutrient_db` 는 `numOfRows>100` 이면 빈 응답이라 어차피 100 고정. 개발계정 일 10,000콜 내.

## 정규화 충실도

`normalize.py` 는 프로덕션 자바 3종을 1:1 포팅(파일 하단 self-test 로 검증):
`FoodCatalogImportText.normalize`, `FoodDisplayNameNormalizer`(`경단_깨`→`깨경단`),
`FoodCatalogIdentity.duplicateKey`. 따라서 산출 중복은 실제 적재 시 dedup 결과와 정합.

## 실행

```bash
cd scripts/food-census
python3 normalize.py                      # 정규화 포팅 self-test

# 표본 검증 (소스별 3페이지)
python3 census.py run --mode sample --sample-pages 3

# 전수 (재개 가능 — 중단 후 같은 명령 재실행 시 이어받음)
python3 census.py fetch --mode full --delay 0.12
python3 census.py profile

# 전량 적재 검증: 캡처 TSV에 프로덕션 dedup(CanonicalDedupResolver) 의미를 재생해
# 적재 후 기대 canonical/superseded/COLLISION 수(acceptance target)를 산출 (read-only)
python3 census.py project
```

API 키는 `--api-key`, `PUBLIC_FOOD_API_KEY` 환경변수, `application-local.yml` 순으로 해석.

## 산출물

- 중간 데이터: `data/{source}.tsv` + `{source}.ckpt` (gitignore, 재개용)
- 리포트(`profile`): `docs/references/FOOD_API_CENSUS_DEDUP_PROFILE.md` (6개 지표)
- 리포트(`project`): `docs/references/FOOD_CATALOG_DEDUP_LOAD_PROJECTION.md` (전량 적재 acceptance target)

## 6개 지표

1. 출처별 `food_code` 자체 중복
2. 출처 간 동일 `food_code`
3. 동일 품목제조보고번호(`itemMnftrRptNo`)
4. 이름+제조사(production `dup_key`) 일치 — 검토 후보
5. 같은 코드인데 이름·영양값이 다른 충돌
6. 합산 후 최종 고유 식품 예상 수 (보수/적극 범위)

## `profile` vs `project`

`profile` 의 지표 6은 `food_code` **단독** 병합(U₁=321,118 / U₂=256,925 범위)이라 실제 적재 모델을
반영하지 못한다. 프로덕션 dedup 은 `(source, food_code)` upsert 후 **`(food_code, name_key)`** 로 클러스터링하므로
정확한 적재 결과는 `project` 가 산출한다(예: canonical 323,899 / superseded 291,610 / COLLISION 2,781).
`project` 는 캡처 TSV에 `CanonicalDedupResolver` 의미를 그대로 재생하며, 출력은 `profile` 지표 1·5와 교차검증된다.
