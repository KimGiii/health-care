# 써브웨이 코리아 메뉴 영양·알레르기 정보 수집 레퍼런스

수집일: 2026-06-09  
출처: 써브웨이 코리아 공식 웹사이트 및 공식 모바일 재료소개 페이지  
용도: 브랜드 공식 메뉴 영양정보 수동 검수, 식품 카탈로그 보강, 알레르기 기반 식단 추천 제한 규칙 참고

## 1. 결론

써브웨이는 모든 조합을 사람이 직접 입력하면 안 된다. 공식 웹사이트에서 메뉴 상세 영양성분표를 자동 수집하고, 조합형 데이터는 재료 단위 칼로리 자료와 함께 관리하는 방식이 맞다.

| 데이터 | 수집 방식 |
|---|---|
| 메뉴 목록 | `https://www.subway.co.kr/menuList/{category}` HTML 파싱 |
| 메뉴별 영양성분 | `https://www.subway.co.kr/menuView/{category}?menuItemIdx={id}` HTML 파싱 |
| 재료별 칼로리 | `https://devm.subway.co.kr/more/freshInfo` HTML 파싱 |
| 알레르기 정보 | 공식 이미지 표 원문 보존 |
| 원산지 정보 | `https://www.subway.co.kr/sandwichCountry` HTML 원문 보존 |

## 2. 생성 파일

| 파일 | 내용 |
|---|---|
| `docs/references/SUBWAY_KOREA_MENU_NUTRITION_2026-06-09.json` | 메뉴 목록과 상세 영양성분표 자동 추출 JSON |
| `docs/references/SUBWAY_KOREA_FRESH_INFO_2026-06-09.json` | 빵·야채·치즈·소스 재료별 칼로리 JSON |
| `docs/references/SUBWAY_KOREA_FRESH_INFO_RAW_2026-06-09.html` | 재료소개 원본 HTML |
| `docs/references/SUBWAY_KOREA_SANDWICH_ALLERGY_RAW_2026-06-09.html` | 알레르기 팝업 원본 HTML |
| `docs/references/SUBWAY_KOREA_SANDWICH_ALLERGY_TABLE_2026-05-26.png` | 알레르기 유발성분 표 이미지 |
| `docs/references/SUBWAY_KOREA_SANDWICH_COUNTRY_RAW_2026-06-09.html` | 원산지 팝업 원본 HTML |

## 3. 수집 결과 요약

| 항목 | 건수 |
|---|---:|
| 메뉴 카테고리 | 7 |
| 메뉴 목록 항목 | 99 |
| 상세 페이지 영양성분 행 | 69 |
| 재료별 칼로리 항목 | 33 |
| 재료 카테고리 | 4 |

메뉴 카테고리별 수집 건수:

| 카테고리 | 건수 |
|---|---:|
| `sandwich` | 31 |
| `grain_salad` | 19 |
| `salad` | 20 |
| `unit` | 3 |
| `morning` | 4 |
| `sidedrink` | 16 |
| `catering` | 6 |

재료별 칼로리 항목:

| 카테고리 | 건수 |
|---|---:|
| 빵 | 6 |
| 야채 | 9 |
| 치즈 | 3 |
| 소스 | 15 |

## 4. 알레르기 정보 처리

써브웨이 알레르기 팝업은 2026-06-09 기준 HTML 표가 아니라 이미지 파일로 제공된다.

| 항목 | 값 |
|---|---|
| 이미지 URL | `https://www.subway.co.kr/images/menu/allergy_img.png?2026052601` |
| 저장 파일 | `docs/references/SUBWAY_KOREA_SANDWICH_ALLERGY_TABLE_2026-05-26.png` |
| 표기 방식 | `● 포함`, `★ 포함 가능성 있음` |

로컬 환경에는 OCR 도구가 없어 이번 작업에서는 이미지 원문을 보존했다. 알레르기 성분을 구조화 데이터로 쓰려면 OCR 또는 수동 검수 단계가 추가로 필요하다.

## 5. 적재 전 정규화 메모

| 원문 요소 | 권장 내부 필드 |
|---|---|
| 브랜드 | `brand_name = "써브웨이"` |
| 메뉴명 | `name` 또는 `name_ko` |
| 영문명 | `name_en` |
| 메뉴 ID | `source_menu_id` |
| 카테고리 | `category` |
| 영양 행의 제공량 라벨 | `serving_reference` |
| 중량 | `serving_size_g`, 라벨에서 파싱 |
| 열량 | `calories_kcal` |
| 단백질 | `protein_g`, 기준치 비율은 별도 메타 |
| 포화지방 | `saturated_fat_g`, 기준치 비율은 별도 메타 |
| 당류 | `sugar_g` |
| 나트륨 | `sodium_mg`, 기준치 비율은 별도 메타 |
| 알레르기 이미지 표 | `allergen_matrix_source` |
| 원산지 | `origin_notes` |
| 공식 검증 상태 | `verification_status` |

## 6. 주의사항

- 써브웨이는 조합형 메뉴 구조라 완제품만 저장하면 데이터가 금방 깨진다.
- 추천/기록 계산에는 `메뉴 기본 영양성분 + 재료별 칼로리 + 사용자가 선택한 옵션` 모델이 적합하다.
- 알레르기 정보는 이미지 표 원문을 보존했지만, 구조화하려면 OCR/검수가 필요하다.
- 이 자료는 2026-06-09에 공식 웹사이트에서 수집한 현재값이다.
