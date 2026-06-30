# 브랜드 알러젠 프로필 큐레이션 배치 2026-06-30

## 목적

로컬 `food_catalog`의 브랜드/제조사 추천 후보 중 알러젠 프로필이 비어 있어 engine-ready 후보에서 빠지는 row를 HACCP/푸드QR 라벨 근거로 보강한다.

입력 큐:

- `docs/references/brand_allergen_profile_candidate_queue_2026-06-30.csv`

산출물:

- `docs/references/brand_allergen_profile_haccp_matches_2026-06-30.csv`
- `docs/references/brand_allergen_profile_curated_batch_2026-06-30.csv`

## 매칭 기준

- 대상은 `LABEL_OR_FOODQR_REQUIRED` 레인 150개로 제한했다.
- HACCP 라벨 파일의 `prdlstNm`과 큐의 `name_ko`가 정확 일치하거나, 공백/구두점을 제거한 정규화 이름이 일치하는 경우만 후보로 보았다.
- 최종 적용 후보는 제품명 일치만으로 확정하지 않고, 큐의 `maker`/브랜드 맥락과 HACCP `manufacture`/`seller`가 충돌하지 않아야 한다.
- 이름이 일반명이라 제조사 충돌 가능성이 있는 row는 적용하지 않는다.

## 결과

| 구분 | 건수 |
| --- | ---: |
| 검토 대상 `LABEL_OR_FOODQR_REQUIRED` row | 150 |
| HACCP 제품명 매칭 row | 2 |
| 적용 가능 row | 1 |
| 제조사 충돌로 보류 | 1 |

## 적용 가능 row

| food_catalog_id | source | food_code | 제품명 | 제조사 | 알러젠 태그 | 근거 |
| ---: | --- | --- | --- | --- | --- | --- |
| 2929 | `MFDS_STANDARD_PROCESSED` | `P112-013001300-0005` | 고기전용쌈장 | 씨제이제일제당(주) | `대두,밀` | HACCP 라벨 이미지 `1991045804731-1.jpg` |

검증 메모:

- canonical 대표 행: 통과
- 4대 매크로: 통과
- verified serving option: 통과
- primary serving option: 20g
- 주의 기준: 20g 기준 나트륨 약 462mg, 당류 약 3.8g, 포화지방 약 0.12g으로 `RECOMMENDABLE` 유지 가능
- 기존 알러젠 태그/프로필: 없음

## 보류 row

| food_catalog_id | 제품명 | 큐 제조사/브랜드 | HACCP 매칭 제조/판매 | 보류 사유 |
| ---: | --- | --- | --- | --- |
| 1262 | 고추장 불고기 양념 | (주)오뚜기 | 씨제이제일제당(주), 대상(주) 등 | 제품명이 일반명이고 제조사 맥락이 충돌한다. 이 row에는 해당 HACCP 알러젠을 붙이지 않는다. |

## 결론

이번 큐에서 “추천 가능하지 않은 이유”는 대부분 engine-ready 관점에서 `allergen_profile_verified=false`와 알러젠 태그 부재가 맞다. 다만 실제 보강 가능 여부는 별개다. 라벨/공식 출처가 정확히 같은 제품으로 확인되지 않으면 알러젠 태그를 채워도 검증 프로필로 승격하면 안 된다.

따라서 이번 배치는 1건만 적용 CSV로 만들고, 제조사 충돌이 있는 generic-name 매칭은 보류했다.
