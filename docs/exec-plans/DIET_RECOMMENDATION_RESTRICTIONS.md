# 제외 식품·알러지 기반 하루 식단 추천 실행 계획

작성일: 2026-06-02  
상태: 계획 확정  
대상: 백엔드, iOS, 제품 기획

## 1. 기능 목표

사용자가 알러지, 먹지 않는 음식, 피하고 싶은 식품군을 미리 등록하면 추천 식단에서 해당 항목을 완전히 제외한다. 이후 기존 목표 설정과 사용자 영양 목표를 기준으로 하루 단위의 식단을 추천한다.

v1의 추천 단위는 하루 식단이다. 아침, 점심, 저녁, 간식 4개 슬롯을 기본으로 구성하고, 각 끼니별 추천 식품과 1회 제공량을 제공한다. 추천은 규칙 기반으로 생성하며 AI 생성 문장이나 의료적 판단은 포함하지 않는다.

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
    source VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

v1 알러지 태그:

- `MILK`
- `EGG`
- `PEANUT`
- `TREE_NUT`
- `WHEAT`
- `SOY`
- `FISH`
- `SHELLFISH`

알러지 제한이 있는 사용자의 추천 후보는 알러지 태그가 검토된 식품을 우선 사용한다. 태그 검토가 불충분한 경우 추천 실패 응답을 반환해 사용자가 위험한 추천을 받지 않도록 한다.

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
  "mealTypes": ["BREAKFAST", "LUNCH", "DINNER", "SNACK"]
}
```

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

### 3.5 규칙 기반 추천 엔진

기존 `NutritionTargetService`와 `NutritionCalculator`에서 산출된 사용자 영양 목표를 재사용한다.

하루 목표 분배 기본값:

| 끼니 | 칼로리 비율 |
|---|---:|
| 아침 | 25% |
| 점심 | 35% |
| 저녁 | 30% |
| 간식 | 10% |

후보 필터링 순서:

1. 삭제된 식품 제외
2. 영양 정보가 부족한 식품 제외
3. 사용자 `FOOD` 제한 제외
4. 사용자 `CATEGORY` 제한 제외
5. 사용자 `KEYWORD` 제한 제외
6. 사용자 `ALLERGEN_TAG` 제한 제외
7. 알러지 제한이 있는 경우 태그 검토가 불명확한 식품 제외

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

식단 기록 화면에 `오늘 추천 식단` 진입 버튼을 추가한다.

추천 화면 기능:

- 추천 로딩 상태
- 끼니별 추천 음식 목록
- 끼니별 칼로리와 탄단지 요약
- 하루 목표 대비 총합 표시
- 적용된 제한 조건 표시
- 후보 부족 또는 추천 실패 메시지 표시
- 끼니 단위로 기존 식단 기록 API에 저장

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

## 5. 테스트 계획

### 5.1 백엔드 단위 테스트

- 제한 조건 등록 시 `targetType`별 필수 값 검증
- 같은 제한 조건 중복 등록 방지
- 키워드 정규화 적용
- `FOOD`, `CATEGORY`, `KEYWORD`, `ALLERGEN_TAG` 제한 조건별 후보 제외
- 알러지 제한이 있을 때 검토되지 않은 식품 제외
- 후보 부족 시 `BUSINESS_RULE_VIOLATION` 발생
- 하루 추천 총합이 칼로리 ±10%, 단백질 90% 이상 조건을 만족

### 5.2 백엔드 통합/권한 테스트

- 본인 제한 조건만 조회된다.
- 다른 사용자의 제한 조건 삭제가 거부된다.
- 추천 API는 인증이 필요하다.
- 프로필 정보 또는 영양 목표가 부족하면 추천 실패 응답을 반환한다.
- 제한 조건 적용 후 추천 결과에 금지 식품이 포함되지 않는다.

### 5.3 iOS 테스트

- 제한 조건 목록 로딩, 추가, 삭제 ViewModel 테스트
- 추천 식단 로딩 성공/실패 상태 테스트
- 추천 끼니를 식단 기록으로 저장하는 요청 생성 테스트
- 후보 부족 메시지 표시 테스트

## 6. 구현 순서

1. 백엔드 마이그레이션 추가
2. 제한 조건 엔티티, DTO, Repository, Service, Controller 구현
3. 알러지 태그 엔티티와 Repository 구현
4. 추천 요청/응답 DTO 구현
5. 규칙 기반 추천 엔진 구현
6. 하루 추천 Controller 구현
7. 백엔드 테스트 추가
8. iOS APIEndpoint와 모델 추가
9. 제한 조건 설정 ViewModel/화면 구현
10. 추천 식단 ViewModel/화면 구현
11. 추천 끼니 저장 흐름 연결
12. iOS 테스트 추가

## 7. v1 제외 범위

- AI 식단 생성
- 주간 식단 추천
- 장보기 목록
- 식재료 단위 레시피 추천
- 이미지 기반 목표 피지크 분석
- 의료적 알러지 안전 보증
- 외부 식품 DB 실시간 알러지 추론

## 8. 가정과 주의사항

- 활성 목표가 있으면 활성 목표의 영양 목표를 우선 사용한다.
- 활성 목표가 없으면 사용자 프로필에 저장된 영양 목표를 사용한다.
- 사용자 프로필과 영양 목표가 모두 부족하면 추천하지 않는다.
- 알러지는 안전 민감 기능이므로, 불명확한 후보는 추천하지 않는 방향을 기본값으로 둔다.
- 추천 결과는 사용자의 기록 편의를 돕는 식단 제안이며, 질병 치료나 의료 처방이 아니다.
