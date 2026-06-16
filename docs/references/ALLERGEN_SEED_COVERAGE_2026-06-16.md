# 알러젠 seed 커버리지 리포트

작성일: 2026-06-16
범위: `V25__seed_recommendation_curation.sql`의 seed 추천 후보 allowlist 42개
기준 데이터: `V4`/`V12` seed `food_catalog`, `V26` 알러젠 포함 태그 seed, `V27` Strict 프로필 검토 backfill

## 요약

| 항목 | 수치 |
|---|---:|
| 추천 가능 seed allowlist | 42개 |
| `V26` 포함 태그가 1개 이상 붙은 allowlist 식품 | 12개 |
| 포함 태그가 없는 allowlist 식품 | 30개 |
| Strict 프로필 검토 완료 allowlist 식품 | 6개 |
| `V26`에 seed row가 0개인 알러젠 태그 | 3개 |

`V26`은 단일재료 중심으로 시작했기 때문에 기본 모드의 "포함 태그 매칭 시 제외"에는 일부 효과가 있지만, 출시 추천 후보 전체 기준 커버리지는 아직 낮다. 특히 `TOMATO`는 추천 allowlist에 `토마토`가 있는데도 태그가 없어서 P1에서 바로 보강해야 한다.

## 태그별 seed 현황

| 태그 | `V26` 전체 seed row | allowlist 매칭 row | Strict 검토 완료 allowlist row | 메모 |
|---|---:|---:|---:|---|
| `EGG` | 3 | 1 | 1 | `달걀` |
| `MILK` | 16 | 4 | 1 | `우유(저지방)`만 Strict 검토 완료 |
| `BUCKWHEAT` | 1 | 1 | 0 | `메밀` |
| `PEANUT` | 1 | 0 | 0 | allowlist에 땅콩 후보 없음 |
| `SOY` | 2 | 2 | 0 | `두부`, `콩나물` 누락 |
| `WHEAT` | 15 | 0 | 0 | `통밀빵` 누락 |
| `MACKEREL` | 1 | 0 | 0 | allowlist에 고등어 후보 없음 |
| `CRAB` | 1 | 0 | 0 | allowlist에 게 후보 없음 |
| `SHRIMP` | 1 | 1 | 1 | `새우` |
| `PINE_NUT` | 1 | 0 | 0 | allowlist에 잣 후보 없음 |
| `PORK` | 4 | 1 | 1 | `돼지고기(안심)` |
| `PEACH` | 0 | 0 | 0 | seed catalog에는 `복숭아` 존재 |
| `TOMATO` | 0 | 0 | 0 | allowlist에 `토마토` 존재 |
| `SULFITE` | 0 | 0 | 0 | 명시 seed 없음. 라벨 기반 데이터 필요 |
| `WALNUT` | 1 | 0 | 0 | allowlist에 호두 후보 없음 |
| `CHICKEN` | 3 | 1 | 1 | `닭가슴살` |
| `BEEF` | 3 | 0 | 0 | allowlist에 쇠고기 후보 없음 |
| `SQUID` | 1 | 1 | 1 | `오징어` |
| `SHELLFISH` | 4 | 0 | 0 | allowlist에 조개류 후보 없음 |
| `GLUTEN` | 16 | 1 | 0 | 현재 `메밀`이 포함되어 정책 재검토 필요 |

## 추천 allowlist 상세

| 식품 | category | seed `name` | 현재 태그 | Strict 검토 |
|---|---|---|---|---|
| 현미밥 | `GRAIN` | `Brown Rice` | - | - |
| 통밀빵 | `GRAIN` | `Whole Wheat Bread` | - | - |
| 오트밀 | `GRAIN` | `Oatmeal` | - | - |
| 고구마 | `GRAIN` | `Sweet Potato` | - | - |
| 옥수수 | `GRAIN` | `Corn` | - | - |
| 감자 | `GRAIN` | `Potato` | - | - |
| 보리 | `GRAIN` | `Barley` | - | - |
| 메밀 | `GRAIN` | `Buckwheat` | `BUCKWHEAT`, `GLUTEN` | - |
| 닭가슴살 | `PROTEIN_SOURCE` | `Chicken Breast` | `CHICKEN` | 완료 |
| 돼지고기(안심) | `PROTEIN_SOURCE` | `Pork Tenderloin` | `PORK` | 완료 |
| 연어 | `PROTEIN_SOURCE` | `Salmon` | - | - |
| 참치(캔) | `PROTEIN_SOURCE` | `Tuna (Canned)` | - | - |
| 달걀 | `PROTEIN_SOURCE` | `Egg (Whole)` | `EGG` | 완료 |
| 두부 | `PROTEIN_SOURCE` | `Tofu` | - | - |
| 광어 | `PROTEIN_SOURCE` | `Flounder (Flatfish)` | - | - |
| 오징어 | `PROTEIN_SOURCE` | `Squid` | `SQUID` | 완료 |
| 새우 | `PROTEIN_SOURCE` | `Shrimp` | `SHRIMP` | 완료 |
| 순두부 | `PROTEIN_SOURCE` | `Soft Tofu (Sundubu)` | `SOY` | - |
| 브로콜리 | `VEGETABLE` | `Broccoli` | - | - |
| 시금치 | `VEGETABLE` | `Spinach` | - | - |
| 양배추 | `VEGETABLE` | `Cabbage` | - | - |
| 당근 | `VEGETABLE` | `Carrot` | - | - |
| 오이 | `VEGETABLE` | `Cucumber` | - | - |
| 토마토 | `VEGETABLE` | `Tomato` | - | - |
| 깻잎 | `VEGETABLE` | `Perilla Leaf (Kkaennip)` | - | - |
| 양파 | `VEGETABLE` | `Onion` | - | - |
| 배추 | `VEGETABLE` | `Napa Cabbage` | - | - |
| 콩나물 | `VEGETABLE` | `Soybean Sprout` | - | - |
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

## P1 보강 제안

1. `TOMATO` seed를 추가한다.
   - 최소: `Tomato`, `Cherry Tomato`, `Tomato Juice`
   - 출시 후보 직접 영향: `토마토`
2. `SOY` seed를 추천 allowlist 중심으로 보강한다.
   - 최소: `Tofu`, `Soybean Sprout`
   - 이미 있음: `Soft Tofu (Sundubu)`, `Soy Milk (Plain)`
3. `WHEAT`/`GLUTEN` seed를 추천 allowlist 중심으로 보강한다.
   - 최소: `Whole Wheat Bread`
   - `GLUTEN` 합성 태그는 내부 정책대로 `Barley`, `Oatmeal` 포함 여부를 확정해야 한다.
4. `PEACH` seed를 추가한다.
   - 최소: `Peach`
   - 현재 V25 allowlist에는 없지만 사용자 선택 태그로는 노출된다.
5. `SULFITE`는 seed 단일재료 자동 태깅보다 라벨 기반 데이터 경로로 처리한다.
   - 김치/가공식품에 추정으로 붙이지 않는다.
   - 브랜드 공식 메뉴·가공식품 라벨 적재 시 `LABEL_DERIVED`로 넣는다.
6. `Buckwheat`의 `GLUTEN` 태그는 재검토한다.
   - 현재 문서상 `GLUTEN`은 밀·호밀·보리·귀리 및 교배종 기준이다.
   - `Buckwheat`는 이미 `BUCKWHEAT` 의무표시 태그가 있으므로 `GLUTEN`까지 붙일지 별도 제품 결정이 필요하다.

## 운영 기준

- 기본 모드는 포함 태그 매칭 시 제외하므로, allowlist 후보의 명시 알러젠 누락을 줄이는 것이 우선이다.
- Strict 모드는 `allergen_profile_verified=true`가 필요하므로, 단순 포함 태그 추가와 별개로 "완결 프로필 검토" 여부를 보수적으로 운영해야 한다.
- `RECIPE_DERIVED` 기반 복합식품 보강은 레시피 데이터 라이선스와 누락 위험 검토 전까지 Strict 통과 근거로 쓰지 않는다.
