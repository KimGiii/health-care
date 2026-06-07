# 제외 식품·알러지 기반 하루 식단 추천 실행 계획

작성일: 2026-06-02 (개정: 2026-06-04)
상태: 계획 수정 (두 PRD 결정 반영)
대상: 백엔드, iOS, 제품 기획
상위 문서: [DIET_RECOMMENDATION_RESTRICTIONS_PRD.md](../product-specs/DIET_RECOMMENDATION_RESTRICTIONS_PRD.md) (제품 기획서)
결정 기준: 제품 PRD와 `docs/exec-plans/diet_recommendation_prd.md`의 합의 사항을 함께 따른다.

> 본 문서는 **구현 방법(How)**을 다룬다. 기능의 목적·범위·제품 결정(What/Why)은 위 결정 기준 문서를 따른다.
> 2026-06-04 확정된 두 PRD 기준에 맞춰 v1 출시 범위, 안전 원칙, 데이터 운영 경계, iOS 진입점을 재정리함.

## 1. 기능 목표

사용자가 알러지, 먹지 않는 음식, 피하고 싶은 식품군을 미리 등록하면 추천 식단에서 해당 항목을 완전히 제외한다. 이후 기존 목표 설정과 사용자 영양 목표를 기준으로 하루 단위의 식단을 추천한다.

v1의 추천 단위는 하루 식단이다. **사용자가 끼니 수를 선택**하며(기본 아침·점심·저녁·간식 4슬롯, 간식 on/off 등), 각 끼니별 추천 식품과 1회 제공량을 제공한다. 추천은 규칙 기반으로 생성하며 AI 생성 문장이나 의료적 판단은 포함하지 않는다.

진입점은 **식단 탭 상단 상시 카드**이며, v1은 **전체 무료**로 출시한다(프리미엄 게이팅 없음). 상세 근거는 PRD §6, §11 참조.

성공 기준은 다음과 같다.

- 추천 결과에 사용자가 등록한 알러지, 제외 식품, 제외 키워드, 제외 카테고리가 포함되지 않는다.
- 하루 추천 총합이 사용자 칼로리 목표의 ±10% 범위에 들어온다.
- 하루 추천 단백질 총합이 사용자 단백질 목표의 90% 이상을 만족한다.
- 후보 식품이 부족하거나 알러지 안전성이 불명확하면 무리하게 추천하지 않고 실패 사유를 반환한다.

## 2. 핵심 제품 결정

- 추천 범위: 하루 식단
- 추천 방식: 규칙 기반
- 제한 조건 처리: 알러지와 안 먹는 음식 모두 완전 제외
- 목표 피지크 해석: v1에서는 별도 이미지 기반 체형 모델이 아니라 기존 활성 목표와 영양 목표로 해석
- 추천 후보: 기존 식품 카탈로그의 글로벌 식품과 사용자 커스텀 식품
- 진입점: 식단 탭 상단 상시 카드
- 과금: v1 전체 무료. 프리미엄 게이팅과 사용량 제한은 v2 AI 도입 시 재검토
- 기록 행동: 추천 결과를 별도 식단 계획으로 저장하기보다, 사용자가 선택한 끼니를 기존 식단 기록으로 바로 저장
- 외부 자료: 추천 런타임에서는 호출하지 않고 사전 배치 임포트 또는 수동 검수 데이터로만 사용
- 재추천 정책: PRD에서 아직 미결이다. v1 필수 범위는 전체 재추천이며, 끼니 단위 Swap은 v1.1 후보로 둔다.

### 2.1 PRD 정합성 기준

| PRD 결정 | 실행 계획 반영 |
|---|---|
| 안전이 추천보다 우선 | 알러지 태그 미검토 후보는 알러지 보유 사용자에게 추천하지 않음 |
| 회피는 내부 데이터 책임 | `food_allergen_tags`와 서버 필터가 최종 게이트 |
| 제안이지 처방 아님 | 의료 안전 보증 문구 금지, 실패 사유를 정상 응답으로 설계 |
| 기존 자산 재사용 | `NutritionTargetService`/`NutritionCalculator`, `FoodCatalog`, 기존 식단 기록 API 재사용 |
| v1 무료·규칙 기반 | AI 생성, 프리미엄 게이팅, 사용량 제한 제외 |
| 식단 탭 상단 카드 | iOS 첫 진입점은 식단 탭 상단 상시 카드 |

## 3. 백엔드 변경 계획

### 3.1 사용자 식단 제한 조건

새 도메인으로 식단 제한 조건을 추가한다.

예상 테이블:

```sql
CREATE TABLE diet_restrictions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    restriction_type VARCHAR(20) NOT NULL CHECK (restriction_type IN ('ALLERGY', 'AVOID')),
    target_type VARCHAR(20) NOT NULL CHECK (target_type IN ('FOOD', 'CATEGORY', 'KEYWORD', 'ALLERGEN_TAG')),
    food_catalog_id BIGINT REFERENCES food_catalog(id),
    category VARCHAR(30),
    keyword VARCHAR(100),
    allergen_tag VARCHAR(30),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);
```

중복 등록을 막기 위해 사용자별 `restriction_type`, `target_type`, 대상 값 조합에 유니크 인덱스를 둔다. `keyword`는 기존 식품명 정규화 방식과 동일하게 NFC 정규화, 연속 공백 축약, 앞뒤 공백 제거를 적용한다.

### 3.2 알러지 태그 매핑

추천 안정성을 위해 식품 카탈로그와 알러지 태그를 분리한다.

예상 테이블:

```sql
CREATE TABLE food_allergen_tags (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    food_catalog_id BIGINT NOT NULL REFERENCES food_catalog(id) ON DELETE CASCADE,
    allergen_tag VARCHAR(30) NOT NULL,
    -- 회피("없음") 신뢰 레벨 (PRD §6.6). 식품이 아니라 "이 알러젠이 없다"는 주장의 신뢰도.
    confidence_level VARCHAR(20) NOT NULL,  -- DIRECT_VERIFIED | LABEL_DERIVED | RECIPE_DERIVED | UNKNOWN
    source VARCHAR(30) NOT NULL,            -- 데이터 출처(아래)
    reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (food_catalog_id, allergen_tag)
);

-- 복합식품 → 재료 분해 (RECIPE_DERIVED 산출 근거)
CREATE TABLE food_recipe_ingredient (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    food_catalog_id   BIGINT NOT NULL REFERENCES food_catalog(id) ON DELETE CASCADE,
    ingredient_name   VARCHAR(150) NOT NULL,   -- 음식별 식품재료량 DB의 재료명
    ingredient_food_code VARCHAR(30),          -- 매칭된 식약처 식품코드(있으면)
    amount_g          DOUBLE PRECISION,
    source            VARCHAR(30) NOT NULL DEFAULT 'KHANES',  -- 국민건강영양조사 음식별 식품재료량
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_recipe_ingredient_food ON food_recipe_ingredient (food_catalog_id);
```

v1 알러지 태그 — **식약처 식품 표시기준 의무표시 대상**을 단일 기준으로 채택한다(글로벌 FALCPA 8종이 아니라 한국 표시기준 기준. 근거: PRD §7). 사용자 선택 태그는 조개류 세부 품목을 `SHELLFISH`로 묶어 표현한다.

| 태그 | 대상 | 태그 | 대상 |
|---|---|---|---|
| `EGG` | 난류(가금류) | `PORK` | 돼지고기 |
| `MILK` | 우유 | `PEACH` | 복숭아 |
| `BUCKWHEAT` | 메밀 | `TOMATO` | 토마토 |
| `PEANUT` | 땅콩 | `SULFITE` | 아황산류 |
| `SOY` | 대두 | `WALNUT` | 호두 |
| `WHEAT` | 밀 | `CHICKEN` | 닭고기 |
| `MACKEREL` | 고등어 | `BEEF` | 쇠고기 |
| `CRAB` | 게 | `SQUID` | 오징어 |
| `SHRIMP` | 새우 | `SHELLFISH` | 조개류(굴·전복·홍합 포함) |
| `PINE_NUT` | 잣 | | |

추가 합성 태그(후속 확정):
- `GLUTEN` — 밀·호밀·보리·귀리 및 교배종. `WHEAT`를 포함하되 호밀·보리·귀리도 OR로 묶어 글루텐 회피를 표현한다. 단 **글루텐을 의무표시 알러지 태그로 볼지, 식이 제한 태그로 볼지 제품 표현 기준은 후속 확정**한다. 기본 의무표시 태그와 시각적으로 분리해 표기한다.

영문 코드명은 위 표를 잠정안으로 두되, 앱 내 표시명은 한국어 기준으로 제공한다.

`source` 값은 `MFDS_CLASS`(분류매핑), `KHANES_RECIPE`(재료분해), `FOODQR`, `OPEN_FOOD_FACTS`, `USER_CUSTOM` 중 하나로 제한한다.

#### 신뢰 레벨 산출 (PRD §6.6 — 핵심)

`confidence_level`은 **"이 알러젠이 없다"는 회피 주장의 신뢰도**다. 식품 유형별로 다음과 같이 채운다:

| 식품 유형 | 산출 방법 | confidence_level | source |
|---|---|---|---|
| 단일재료 (원물·명확한 단일 식품) | 식약처 식품분류 중/소분류 → 알러젠 결정적 매핑 (예: 분류 "새우류"→`SHRIMP`) | `DIRECT_VERIFIED` | `MFDS_CLASS` |
| 복합식품 (재료 분해 가능) | `food_recipe_ingredient`의 각 재료에 분류매핑 적용 → **합집합** | `RECIPE_DERIVED` | `KHANES_RECIPE` |
| 가공·브랜드 제품 | 푸드QR·OFF의 의무표시 알레르기 정보 | `LABEL_DERIVED` | `FOODQR`/`OPEN_FOOD_FACTS` |
| 재료·라벨 미상, 사용자 커스텀 | — | `UNKNOWN` | `USER_CUSTOM` 등 |

**완결성 규칙**: 부분 태그는 위험하다. 예) 베이글에 `WHEAT`만 달고 우유·계란을 모르면, 우유 알러지 사용자에게 "안전"으로 추천된다(엔진은 "매칭 태그 없음=안전"으로 해석). 따라서 알러젠 집합이 **완결일 때만** 해당 신뢰 레벨을 부여한다. 단일재료는 자명하게 완결, 복합식품은 재료 분해가 완결될 때만 `RECIPE_DERIVED`.

#### 추천 회피 판정 (2모드)

```
기본 모드:  confidence ∈ {DIRECT_VERIFIED, LABEL_DERIVED, RECIPE_DERIVED} 에서
            해당 알러젠 "없음"이면 통과 (+디스클레이머). UNKNOWN은 낮은 우선순위/주의.
Strict 모드: confidence ∈ {DIRECT_VERIFIED, LABEL_DERIVED} 에서만 통과.
            RECIPE_DERIVED·UNKNOWN 제외 (레시피 누락·혼입가능성 미보장 때문).
```

#### 시드 전략 (메뉴젠 폐기)

- **메뉴젠 API는 폐기**한다 — 공공누리 **제4유형(상업 이용금지)** 확정으로 상업 서비스 사용 불가.
- 시드 계층:
  1. `DIRECT_VERIFIED` — 식약처 식품영양성분DB(제한 없음)의 분류 코드로 단일재료 일괄 자동 태깅. 수작업 없음.
  2. `RECIPE_DERIVED` — **국민건강영양조사 음식별 식품재료량 DB**(질병청)를 `food_recipe_ingredient`로 적재 → 재료별 분류매핑 union.
  3. `LABEL_DERIVED` — 푸드QR·OFF는 v2 보강.
- 외부 데이터는 사전 배치 임포트 전용. 추천 런타임은 내부 테이블만 조회.
- **선결 조건(블로커)**: 음식별 식품재료량 DB의 상업 재사용 라이선스 확인. 막히면 기본 모드의 복합식품 회피가 성립하지 않는다(§7 오픈이슈).

### 3.3 제한 조건 API

엔드포인트:

```http
GET    /api/v1/diet/restrictions
POST   /api/v1/diet/restrictions
DELETE /api/v1/diet/restrictions/{id}
```

등록 요청 예시:

```json
{
  "restrictionType": "ALLERGY",
  "targetType": "ALLERGEN_TAG",
  "foodCatalogId": null,
  "category": null,
  "keyword": null,
  "allergenTag": "MILK"
}
```

검증 규칙:

- `targetType`별로 필요한 대상 값은 정확히 하나만 받는다.
- `FOOD`는 존재하는 `foodCatalogId`만 허용한다.
- `CATEGORY`는 `FoodCategory` enum 값만 허용한다.
- `KEYWORD`는 공백 제거 후 1자 이상 100자 이하만 허용한다.
- `ALLERGEN_TAG`는 앱이 정의한 태그만 허용한다.
- 삭제는 본인 제한 조건만 가능하다.

### 3.4 하루 식단 추천 API

엔드포인트:

```http
POST /api/v1/diet/recommendations/daily
```

요청 예시:

```json
{
  "date": "2026-06-02",
  "mealTypes": ["BREAKFAST", "LUNCH", "DINNER", "SNACK"],
  "strictAllergyMode": false
}
```

`strictAllergyMode`(기본 false)는 §3.2 회피 게이트를 결정한다. true면 `DIRECT_VERIFIED`/`LABEL_DERIVED`로만 회피 확인된 후보만 사용한다. 사용자 설정(심한 알러지 모드)에서 가져오며, 응답엔 각 끼니의 회피 신뢰 레벨과 기본 모드 디스클레이머 문구를 함께 내려준다.

`mealTypes`는 생략 시 기본 4슬롯으로 처리한다. 전달된 경우 중복이 없어야 하며, 서버가 지원하는 끼니 타입만 허용한다. 응답 순서는 서버의 표준 끼니 순서(아침, 점심, 저녁, 간식)를 따른다.

응답에는 다음을 포함한다.

- 적용된 하루 목표 칼로리, 단백질, 탄수화물, 지방
- 적용된 제한 조건 목록
- 끼니별 추천 식품, 식품 카탈로그 ID, 제공량 g, 영양소
- 끼니별 총합과 하루 총합
- 목표 대비 오차
- 추천 불가 또는 주의가 필요한 경우의 메시지

추천 실패는 `422 BUSINESS_RULE_VIOLATION`으로 처리한다.

대표 실패 케이스:

- 프로필 또는 영양 목표가 부족함
- 활성 목표와 사용자 영양 목표를 계산할 수 없음
- 제한 조건 적용 후 추천 후보가 부족함
- 알러지 태그 검토가 부족해 안전한 후보를 만들 수 없음

v1은 추천 결과를 `diet_plan` 같은 새 영속 테이블에 저장하지 않는다. 추천은 계산 결과 응답으로 제공하고, 사용자가 `기록하기`를 누를 때 기존 `DietLog`/`FoodEntry` 저장 흐름으로만 영속화한다. 같은 날 다시 추천은 새 계산으로 처리하되, 프리미엄 정책이나 일일 횟수 제한은 두지 않는다.

### 3.5 규칙 기반 추천 엔진

기존 `NutritionTargetService`와 `NutritionCalculator`에서 산출된 사용자 영양 목표를 재사용한다.

하루 목표 분배는 **사용자가 선택한 끼니 구성에 따라 동적으로 재분배**한다(PRD 확정: 끼니 수 사용자 선택).

기본 4슬롯:

| 끼니 | 칼로리 비율 |
|---|---:|
| 아침 | 25% |
| 점심 | 35% |
| 저녁 | 30% |
| 간식 | 10% |

간식 제외(3슬롯) 시 간식 비율을 나머지 끼니에 비례 재분배한다. 예: 아침 28% · 점심 39% · 저녁 33%. 일반화하면 선택된 끼니들의 기본 비율을 합이 100%가 되도록 정규화한다.

```
normalizedRatio(meal) = baseRatio(meal) / Σ baseRatio(selectedMeals)
```

후보 필터링 순서:

1. 삭제된 식품 제외
2. 영양 정보가 부족한 식품 제외
3. 사용자 `FOOD` 제한 제외
4. 사용자 `CATEGORY` 제한 제외
5. 사용자 `KEYWORD` 제한 제외
6. 사용자 `ALLERGEN_TAG` 제한 제외 — 각 등록 알러젠에 대해 `food_allergen_tags`에 **"있음" 매핑이 있으면 제외**
7. 회피 신뢰 레벨 게이트 (§3.2 2모드):
   - **기본 모드**: 해당 알러젠 "없음"을 `DIRECT_VERIFIED`/`LABEL_DERIVED`/`RECIPE_DERIVED` 중 하나로 확인 가능한 후보만 통과. `UNKNOWN`은 풀에 남기되 낮은 우선순위/주의 표시.
   - **Strict 모드**: `DIRECT_VERIFIED`/`LABEL_DERIVED`로만 "없음" 확인된 후보만 통과. `RECIPE_DERIVED`·`UNKNOWN` 제외.

추천 점수 기준:

- 끼니별 목표 칼로리 오차
- 끼니별 목표 단백질 오차
- 하루 총합의 탄수화물, 지방 오차
- 같은 식품 반복 페널티
- 사용 빈도 높은 식품 가산점

단백질 목표 달성을 중요하게 보기 위해 단백질 오차의 가중치를 칼로리 외 매크로보다 높게 둔다.

## 4. iOS 변경 계획

### 4.1 제한 조건 설정 화면

마이페이지 또는 식단 기록 화면 진입점에서 `식단 제한 설정` 화면을 연다.

화면 기능:

- 알러지 태그 선택
- 안 먹는 음식 직접 검색 후 식품 단위 등록
- 제외 키워드 입력
- 제외 카테고리 선택
- 등록된 제한 조건 목록 조회
- 제한 조건 삭제

사용자 문구는 단정적으로 의료 안전을 보장하지 않는다. 예시는 다음과 같다.

> 등록한 제한 조건을 기준으로 추천 식단에서 제외합니다.

### 4.2 추천 식단 화면

식단 탭 **상단 상시 카드**로 `오늘의 추천 식단`을 노출한다(PRD 확정: 진입점).

추천 화면 기능:

- 끼니 수 선택(간식 on/off 등)
- 추천 로딩 상태
- 끼니별 추천 음식 목록
- 끼니별 칼로리와 탄단지 요약
- 하루 목표 대비 총합 표시
- 적용된 제한 조건 표시
- 다시 추천(전체)
- 후보 부족 또는 추천 실패 메시지 표시
- 끼니 단위로 기존 식단 기록 API에 저장

**재추천 정책**: v1 필수 구현은 `다시 추천`으로 전체 하루 식단을 새로 계산하는 흐름이다. 규칙 기반이라 호출 비용이 낮으므로 횟수 제한은 두지 않는다. 끼니 단위 교체(Swap)는 경쟁 서비스에서 유효한 패턴이지만, PRD에서 아직 확정된 범위가 아니므로 v1.1 후보로 분리한다.

추천 식단을 바로 저장할 때는 기존 `POST /api/v1/diet/logs`를 재사용한다.

### 4.3 iOS 모델과 네트워크

추가 대상:

- `DietRestriction`
- `CreateDietRestrictionRequest`
- `DailyDietRecommendationRequest`
- `DailyDietRecommendationResponse`
- `RecommendedMeal`
- `RecommendedFoodEntry`

`APIEndpoint`에는 제한 조건 API와 하루 추천 API를 추가한다.

iOS에서는 추천 결과를 장기 캐시하지 않는다. 화면 재진입 중 임시 상태 유지는 허용하지만, 사용자가 기록하지 않은 추천은 서버의 식단 기록 데이터로 간주하지 않는다.

## 5. 테스트 계획

### 5.1 백엔드 단위 테스트

- 제한 조건 등록 시 `targetType`별 필수 값 검증
- 같은 제한 조건 중복 등록 방지
- 키워드 정규화 적용
- `FOOD`, `CATEGORY`, `KEYWORD`, `ALLERGEN_TAG` 제한 조건별 후보 제외
- 알러지 제한이 있을 때 검토되지 않은 식품 제외
- 후보 부족 시 `BUSINESS_RULE_VIOLATION` 발생
- 하루 추천 총합이 칼로리 ±10%, 단백질 90% 이상 조건을 만족
- 선택된 `mealTypes`의 비율 정규화가 합계 100%를 만족
- 추천 런타임에서 외부 식품 API가 호출되지 않음

### 5.2 백엔드 통합/권한 테스트

- 본인 제한 조건만 조회된다.
- 다른 사용자의 제한 조건 삭제가 거부된다.
- 추천 API는 인증이 필요하다.
- 프로필 정보 또는 영양 목표가 부족하면 추천 실패 응답을 반환한다.
- 제한 조건 적용 후 추천 결과에 금지 식품이 포함되지 않는다.
- 추천 결과 `기록하기`는 기존 식단 기록 API 요청 형식으로 변환된다.

### 5.3 iOS 테스트

- 제한 조건 목록 로딩, 추가, 삭제 ViewModel 테스트
- 추천 식단 로딩 성공/실패 상태 테스트
- 추천 끼니를 식단 기록으로 저장하는 요청 생성 테스트
- 후보 부족 메시지 표시 테스트
- 식단 탭 상단 카드 노출 및 무료 사용자 접근 테스트
- 의료 안전을 단정하는 문구가 노출되지 않는지 스냅샷 또는 카피 테스트

## 6. 구현 순서

1. PRD 기준 수용 조건 확정: 무료, 규칙 기반, 하루 단위, 끼니 수 선택, 상단 카드, 기존 기록 API 재사용
2. 백엔드 마이그레이션 추가
3. 제한 조건 엔티티, DTO, Repository, Service, Controller 구현
4. 알러지 태그 엔티티와 Repository 구현
5. 허용 가능한 출처 기준에 맞춰 최소 알러젠 시드 구성
6. 추천 요청/응답 DTO 구현
7. 규칙 기반 추천 엔진 구현
8. 하루 추천 Controller 구현
9. 백엔드 테스트 추가
10. iOS APIEndpoint와 모델 추가
11. 제한 조건 설정 ViewModel/화면 구현
12. 식단 탭 상단 카드와 추천 식단 ViewModel/화면 구현
13. 추천 끼니 저장 흐름 연결
14. iOS 테스트 추가

## 7. v1 제외 범위

- AI 식단 생성
- 주간 식단 추천
- 장보기 목록
- 식재료 단위 레시피 추천
- 이미지 기반 목표 피지크 분석
- 의료적 알러지 안전 보증
- 외부 식품 DB 실시간 알러지 추론
- 추천 결과 영속 저장용 `diet_plan` 도메인
- 끼니 단위 Swap API
- 무료/프리미엄 기반 추천 횟수 제한

## 8. 가정과 주의사항

- 활성 목표가 있으면 활성 목표의 영양 목표를 우선 사용한다.
- 활성 목표가 없으면 사용자 프로필에 저장된 영양 목표를 사용한다.
- 사용자 프로필과 영양 목표가 모두 부족하면 추천하지 않는다.
- 알러지 회피는 PRD §6.6의 2모드(기본 베스트에포트+디스클레이머 / Strict는 LABEL 이상만)로 운영한다. "불명확하면 무조건 제외"가 아니라 신뢰 레벨로 차등한다.
- 추천 결과는 사용자의 기록 편의를 돕는 식단 제안이며, 질병 치료나 의료 처방이 아니다. 알러지 회피는 "표준 레시피 기준 베스트에포트"이며 보장이 아니다.
- 외부 데이터는 이용허락 검토가 끝나기 전까지 필수 런칭 의존성으로 두지 않는다.
- **(블로커) `RECIPE_DERIVED`는 국민건강영양조사 음식별 식품재료량 DB 상업 재사용 라이선스에 의존한다.** 막히면 기본 모드의 복합식품 회피가 성립하지 않으므로 착수 전 1순위로 확인한다. 대안: 라이선스 가능한 범위(단일재료 `DIRECT_VERIFIED`)만으로 우선 출시하되, 복합식품은 기본 모드에서 `UNKNOWN`(주의 표시)으로 노출.
- 메뉴젠(공공누리 제4유형)·AllergieShield(영국·OFF래퍼·AI의존)는 폐기됨. 알러젠 회피 판정을 외부 AI/제3자에 위임하지 않는다.
- 두 결정 기준 문서의 제품 결정이 바뀌면 본 실행 계획도 그 결정을 따라간다.
