# 알러젠 seed 커버리지 리포트

작성일: 2026-06-16
범위: `V25__seed_recommendation_curation.sql`의 seed 추천 후보 allowlist 42개
기준 데이터: `V4`/`V12` seed `food_catalog`, `V26`/`V30` 알러젠 포함 태그 seed, `V27`/`V30` Strict 프로필 검토 backfill

## 요약

| 항목 | 수치 |
|---|---:|
| 추천 가능 seed allowlist | 42개 |
| 포함 태그가 1개 이상 붙은 allowlist 식품 | 18개 |
| 포함 태그가 없는 allowlist 식품 | 24개 |
| Strict 프로필 검토 완료 allowlist 식품 | 10개 |
| seed row가 0개인 알러젠 태그 | 1개 |

`V30`으로 P1 보강을 적용해 출시 추천 후보 중 `TOMATO`, `SOY`, `WHEAT`/`GLUTEN`의 직접 누락을 줄였다. `SULFITE`는 단일재료 seed 추정으로 처리하지 않고, 브랜드 공식 메뉴·가공식품 라벨 적재 시 `LABEL_DERIVED`로 보강한다.

## 태그별 seed 현황

| 태그 | 전체 seed row | allowlist 매칭 row | Strict 검토 완료 allowlist row | 메모 |
|---|---:|---:|---:|---|
| `EGG` | 3 | 1 | 1 | `달걀` |
| `MILK` | 16 | 4 | 1 | `우유(저지방)`만 Strict 검토 완료 |
| `BUCKWHEAT` | 1 | 1 | 0 | `메밀` |
| `PEANUT` | 1 | 0 | 0 | allowlist에 땅콩 후보 없음 |
| `SOY` | 4 | 4 | 1 | `콩나물`은 Strict 검토 완료, 두부류는 보수적으로 미검토 |
| `WHEAT` | 16 | 1 | 0 | `통밀빵`은 포함 태그만 부여 |
| `MACKEREL` | 1 | 0 | 0 | allowlist에 고등어 후보 없음 |
| `CRAB` | 1 | 0 | 0 | allowlist에 게 후보 없음 |
| `SHRIMP` | 1 | 1 | 1 | `새우` |
| `PINE_NUT` | 1 | 0 | 0 | allowlist에 잣 후보 없음 |
| `PORK` | 4 | 1 | 1 | `돼지고기(안심)` |
| `PEACH` | 1 | 0 | 0 | allowlist에는 없지만 사용자 선택 태그 노출 기준 보강 |
| `TOMATO` | 3 | 1 | 1 | `토마토` Strict 검토 완료 |
| `SULFITE` | 0 | 0 | 0 | 명시 seed 없음. 라벨 기반 데이터 필요 |
| `WALNUT` | 1 | 0 | 0 | allowlist에 호두 후보 없음 |
| `CHICKEN` | 3 | 1 | 1 | `닭가슴살` |
| `BEEF` | 3 | 0 | 0 | allowlist에 쇠고기 후보 없음 |
| `SQUID` | 1 | 1 | 1 | `오징어` |
| `SHELLFISH` | 4 | 0 | 0 | allowlist에 조개류 후보 없음 |
| `GLUTEN` | 19 | 3 | 2 | `메밀` 태그 제거, `통밀빵`·`보리`·`오트밀` 보강 |

## 추천 allowlist 상세

| 식품 | category | seed `name` | 현재 태그 | Strict 검토 |
|---|---|---|---|---|
| 현미밥 | `GRAIN` | `Brown Rice` | - | - |
| 통밀빵 | `GRAIN` | `Whole Wheat Bread` | `WHEAT`, `GLUTEN` | - |
| 오트밀 | `GRAIN` | `Oatmeal` | `GLUTEN` | 완료 |
| 고구마 | `GRAIN` | `Sweet Potato` | - | - |
| 옥수수 | `GRAIN` | `Corn` | - | - |
| 감자 | `GRAIN` | `Potato` | - | - |
| 보리 | `GRAIN` | `Barley` | `GLUTEN` | 완료 |
| 메밀 | `GRAIN` | `Buckwheat` | `BUCKWHEAT` | - |
| 닭가슴살 | `PROTEIN_SOURCE` | `Chicken Breast` | `CHICKEN` | 완료 |
| 돼지고기(안심) | `PROTEIN_SOURCE` | `Pork Tenderloin` | `PORK` | 완료 |
| 연어 | `PROTEIN_SOURCE` | `Salmon` | - | - |
| 참치(캔) | `PROTEIN_SOURCE` | `Tuna (Canned)` | - | - |
| 달걀 | `PROTEIN_SOURCE` | `Egg (Whole)` | `EGG` | 완료 |
| 두부 | `PROTEIN_SOURCE` | `Tofu` | `SOY` | - |
| 광어 | `PROTEIN_SOURCE` | `Flounder (Flatfish)` | - | - |
| 오징어 | `PROTEIN_SOURCE` | `Squid` | `SQUID` | 완료 |
| 새우 | `PROTEIN_SOURCE` | `Shrimp` | `SHRIMP` | 완료 |
| 순두부 | `PROTEIN_SOURCE` | `Soft Tofu (Sundubu)` | `SOY` | - |
| 브로콜리 | `VEGETABLE` | `Broccoli` | - | - |
| 시금치 | `VEGETABLE` | `Spinach` | - | - |
| 양배추 | `VEGETABLE` | `Cabbage` | - | - |
| 당근 | `VEGETABLE` | `Carrot` | - | - |
| 오이 | `VEGETABLE` | `Cucumber` | - | - |
| 토마토 | `VEGETABLE` | `Tomato` | `TOMATO` | 완료 |
| 깻잎 | `VEGETABLE` | `Perilla Leaf (Kkaennip)` | - | - |
| 양파 | `VEGETABLE` | `Onion` | - | - |
| 배추 | `VEGETABLE` | `Napa Cabbage` | - | - |
| 콩나물 | `VEGETABLE` | `Soybean Sprout` | `SOY` | 완료 |
| 바나나 | `FRUIT` | `Banana` | - | - |
| 사과 | `FRUIT` | `Apple` | - | - |
| 오렌지 | `FRUIT` | `Orange` | - | - |
| 블루베리 | `FRUIT` | `Blueberry` | - | - |
| 딸기 | `FRUIT` | `Strawberry` | - | - |
| 키위 | `FRUIT` | `Kiwi` | - | - |
| 우유(저지방) | `DAIRY` | `Milk (Skim)` | `MILK` | 완료 |
| 플레인 요거트 | `DAIRY` | `Plain Yogurt` | `MILK` | - |
| 리코타 치즈 | `DAIRY` | `Ricotta Cheese` | `MILK` | - |
| 케피어 | `DAIRY` | `Kefir` | `MILK` | - |
| 아보카도 | `FAT` | `Avocado` | - | - |
| 아몬드 | `FAT` | `Almond` | - | - |
| 두유(무가당) | `BEVERAGE` | `Soy Milk (Plain)` | `SOY` | - |
| 김치 | `OTHER` | `Kimchi` | - | - |

## P1 보강 결과

1. `TOMATO` seed를 추가했다.
   - 추가: `Tomato`, `Cherry Tomato`, `Tomato Juice`
   - 출시 후보 직접 영향: `토마토`
2. `SOY` seed를 추천 allowlist 중심으로 보강했다.
   - 추가: `Tofu`, `Soybean Sprout`
   - 이미 있음: `Soft Tofu (Sundubu)`, `Soy Milk (Plain)`
3. `WHEAT`/`GLUTEN` seed를 추천 allowlist 중심으로 보강했다.
   - `Whole Wheat Bread`에 `WHEAT`, `GLUTEN` 부여
   - 내부 `GLUTEN` 정책에 맞춰 `Barley`, `Oatmeal`, `Barley Tea (Boricha)`에 `GLUTEN` 부여
4. `PEACH` seed를 추가했다.
   - 추가: `Peach`
   - 현재 V25 allowlist에는 없지만 사용자 선택 태그로는 노출된다.
5. `SULFITE`는 seed 단일재료 자동 태깅보다 라벨 기반 데이터 경로로 처리한다.
   - 김치/가공식품에 추정으로 붙이지 않는다.
   - 브랜드 공식 메뉴·가공식품 라벨 적재 시 `LABEL_DERIVED`로 넣는다.
6. `Buckwheat`의 `GLUTEN` 태그는 제거했다.
   - 현재 문서상 `GLUTEN`은 밀·호밀·보리·귀리 및 교배종 기준이다.
   - `Buckwheat`는 `BUCKWHEAT` 의무표시 태그로만 관리한다.

## 운영 기준

- 기본 모드는 포함 태그 매칭 시 제외하므로, allowlist 후보의 명시 알러젠 누락을 줄이는 것이 우선이다.
- Strict 모드는 `allergen_profile_verified=true`가 필요하므로, 단순 포함 태그 추가와 별개로 "완결 프로필 검토" 여부를 보수적으로 운영해야 한다.
- `RECIPE_DERIVED` 기반 복합식품 보강은 레시피 데이터 라이선스와 누락 위험 검토 전까지 Strict 통과 근거로 쓰지 않는다.
