# Batch A 추천 후보 큐레이션

작성일: 2026-06-30

관련 파일:

- [batch_a_brand_menu_2026-06-30.csv](batch_a_brand_menu_2026-06-30.csv)
- [batch_a_recommendation_curation_2026-06-30.csv](batch_a_recommendation_curation_2026-06-30.csv)
- [추천 후보 큐레이션 배치 runbook](../operations/DIET_RECOMMENDATION_CURATION_BATCH_RUNBOOK.md)

## 결과 요약

Batch A 1차 CSV에는 공식 출처에서 추천 승격 패키지를 확인한 1개 포장 SKU만 포함했다.

| 상태 | 수 | 설명 |
|---|---:|---|
| 승격 CSV 포함 | 1 | 동원참치 살코기 IN WATER 100g |
| 보류 | 5 | 라이스플랜 2종, 더:단백 초코, 매일아침 순생나또, 매일 바이오 그릭요거트 Delight |

보류 항목은 후보 가치가 낮아서가 아니라, 현재 확인한 공개 공식 페이지가 `영양 완전성 + 알러젠 완결 프로필 + 제공량`을 동시에 증명하지 못하기 때문이다. 추정값, 제3자 영양앱, 이름 기반 알러젠 추론은 쓰지 않는다.

## 승격 항목

| 브랜드 | 제품 | 상태 | 판단 |
|---|---|---|---|
| 동원 | 동원참치 살코기 IN WATER 100g | `RECOMMENDABLE` | 공식몰에서 100g 기준 열량, 탄수화물, 단백질, 지방, 당류, 포화지방, 나트륨과 원재료/교차접촉 문구를 확인했다. |

### 동원참치 살코기 IN WATER 100g

입력값:

| 필드 | 값 |
|---|---:|
| 제공량 | 100g |
| 열량 | 70kcal |
| 탄수화물 | 0g |
| 단백질 | 17g |
| 지방 | 0g |
| 당류 | 0g |
| 포화지방 | 0g |
| 나트륨 | 430mg |

추천 상태 판단:

- 나트륨 430mg, 당류 0g, 포화지방 0g이 현재 `RecommendationCautionPolicy` 기준 미만이므로 `RECOMMENDABLE`로 둔다.
- `source + food_code`는 `BRAND_OFFICIAL + 동원:동원참치_살코기_in_water_100g`이다.
- 브랜드 CSV에서는 먼저 `SEARCH_ONLY`로 생성하고, 추천 큐레이션 CSV에서 `RECOMMENDABLE`로 승격한다.

알러젠 처리:

- 공식몰 원재료는 가다랑어, 정제수, 야채즙, 다시마엑기스다.
- 공식몰에는 같은 제조시설 사용 알러젠 문구가 있다.
- 현재 모델은 `CONTAINS`와 `MAY_CONTAIN`을 분리하지 못하므로, 교차접촉 문구의 알러젠을 보수적으로 `allergen_tags`에 반영한다.
- 이 처리는 알러지 사용자의 추천 가능 범위를 좁히지만, `MAY_CONTAIN`을 hard exclusion으로 다룬다는 제품 안전 원칙과는 맞다.

반영 태그:

`난류, 우유, 메밀, 땅콩, 고등어, 게, 새우, 돼지고기, 복숭아, 아황산류, 조개류, 오징어, 닭고기, 잣, 토마토, 쇠고기, 밀`

## 보류 항목

| 제품 | 보류 사유 | 다음 확인 |
|---|---|---|
| 햇반 라이스플랜 렌틸콩현미밥+ 190g | 공식 보도자료는 단백질/식이섬유 특징을 확인하는 데는 충분하지만, 알러젠 완결 프로필과 전체 라벨 근거가 부족하다. | 현재 패키지 라벨 또는 공식 제품 상세의 영양/알러젠 표 |
| 햇반 라이스플랜 파로통곡물밥+ 190g | 공식 보도자료는 제품 존재와 일부 특징 확인용이다. 밀/글루텐 태그와 전체 영양 라벨을 별도 검수해야 한다. | 현재 패키지 라벨 또는 공식 제품 상세의 영양/알러젠 표 |
| 빙그레 더:단백 드링크 초코 250ml | 공식 제품 페이지는 제품/단백질 특징 확인에는 유용하지만, CSV 승격에 필요한 전체 라벨 필드가 공개 텍스트로 충분히 확인되지 않았다. | 현재 패키지 라벨의 탄단지, 당류, 포화지방, 나트륨, 우유 등 알러젠 |
| 풀무원 매일아침 순생나또 | 로컬 HACCP 참고 데이터에는 값이 있으나, `BRAND_OFFICIAL` 승격 근거로 쓸 현재 브랜드 공식 라벨 URL을 아직 확보하지 못했다. | 현재 패키지 라벨 또는 공식 제품 상세 |
| 매일 바이오 그릭요거트 Delight 무가당 플레인 80g | 공식몰에서 현재 판매 상품은 확인되지만, 전체 매크로와 알러젠 완결 근거를 승격 기준으로 고정하기엔 부족하다. | 현재 패키지 라벨 또는 공식 제품 상세 |

## 적용 순서

1. `batch_a_brand_menu_2026-06-30.csv`를 `POST /api/v1/admin/diet/catalog/import/brand-csv`로 적재한다.
2. 생성된 row의 serving option이 만들어졌는지 확인한다.
3. `batch_a_recommendation_curation_2026-06-30.csv`를 `POST /api/v1/admin/diet/catalog/curation-csv`로 적용한다.
4. `CandidatePoolSummary`에서 `engineReadyTotal`, `macroCompleteTotal`, `verifiedServingOptionTotal`, `allergenProfileVerifiedTotal` 변화를 기록한다.
5. 추천 benchmark gate에서 알러지 위반과 hard constraint 위반이 0인지 확인한다.
