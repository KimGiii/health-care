# 버거킹 코리아 메뉴 영양·알레르기 정보 수집 레퍼런스

수집일: 2026-06-09  
출처: 버거킹 코리아 공식 웹사이트  
용도: 브랜드 공식 메뉴 영양정보 수동 검수, 식품 카탈로그 보강, 알레르기 기반 식단 추천 제한 규칙 참고

## 1. 결론

버거킹 코리아는 사람이 표를 직접 입력하지 않아도 공식 웹사이트의 내부 JSON 트랜잭션으로 메뉴 목록과 영양·알레르기 메타데이터를 받을 수 있다.

| 항목 | 값 |
|---|---|
| 메뉴 목록 트랜잭션 | `BKR0632` |
| 영양·알레르기·원산지 트랜잭션 | `BKR0347` |
| 엔드포인트 형식 | `https://www.burgerking.co.kr/burgerking/{trcode}.json` |
| 요청 방식 | `POST`, `application/x-www-form-urlencoded` |
| 요청 바디 | `message=<JSON 문자열>` |

## 2. 생성 파일

| 파일 | 내용 |
|---|---|
| `docs/references/BURGER_KING_KOREA_MENU_LIST_RAW_2026-06-09.json` | `BKR0632` 원본 메뉴 목록 JSON |
| `docs/references/BURGER_KING_KOREA_MENU_METADATA_RAW_2026-06-09.json` | `BKR0347` 원본 영양·알레르기·원산지 JSON |
| `docs/references/BURGER_KING_KOREA_NUTRITION_ALLERGY_2026-06-09.json` | 메뉴명 기준 영양·알레르기 정규화 JSON |
| `docs/references/BURGER_KING_KOREA_MENU_NUTRITION_ALLERGY_2026-06-09.json` | 메뉴 목록과 메타데이터를 메뉴명으로 보조 병합한 JSON |

## 3. 수집 결과 요약

| 항목 | 건수 |
|---|---:|
| 공식 메뉴 목록 항목 | 210 |
| 영양성분 원본 행 | 193 |
| 알레르기 원본 행 | 193 |
| 메뉴명 기준 정규화 그룹 | 188 |
| 원산지 그룹 | 12 |

메뉴 목록과 메타데이터는 메뉴명 문자열만으로 완전한 1:1 매칭이 되지 않는다. 서비스 적재 시에는 `BKR0347`의 영양·알레르기 메타데이터를 우선 보존하고, 메뉴 목록의 `menuCd`, 이미지, 카테고리는 별도 참조로 합치는 편이 안전하다.

## 4. 요청 예시

```bash
curl -L -X POST 'https://www.burgerking.co.kr/burgerking/BKR0347.json' \
  -H 'Content-Type: application/x-www-form-urlencoded; charset=UTF-8' \
  -H 'Accept-Language: ko' \
  --data-urlencode 'message={"header":{"result":true,"error_code":"","error_text":"","info_text":"","message_version":"","login_session_id":"","trcode":"BKR0347","cd_call_chnn":"01"},"body":{}}'
```

## 5. 적재 전 정규화 메모

| 원문 요소 | 권장 내부 필드 |
|---|---|
| 브랜드 | `brand_name = "버거킹"` |
| 메뉴명 | `name` 또는 `name_ko` |
| 메뉴 코드 | `source_menu_code` |
| 메뉴 카테고리 | `category` |
| 중량 | `serving_size_g` |
| 열량 | `calories_kcal` |
| 단백질 | `protein_g`, 기준치 비율은 별도 메타 |
| 나트륨 | `sodium_mg`, 기준치 비율은 별도 메타 |
| 당류 | `sugar_g` |
| 포화지방 | `saturated_fat_g`, 기준치 비율은 별도 메타 |
| 카페인 | `caffeine_mg` |
| 알레르기 유발 성분 | `allergens` 배열 |
| 원산지 | `origin_notes` |
| 공식 검증 상태 | `verification_status` |

## 6. 주의사항

- 이 자료는 2026-06-09에 공식 웹사이트에서 수집한 현재값이다.
- 버거킹 웹 앱의 내부 트랜잭션 구조가 바뀌면 재수집 방식도 바뀔 수 있다.
- 중복 메뉴명이 일부 존재하므로 정규화 JSON은 `nutritionRows`, `allergenRows` 배열을 유지한다.
- 메뉴명 기반 병합 파일은 보조 자료이며, 서비스 적재의 단일 근거로 쓰지 않는다.
