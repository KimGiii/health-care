# 제외 식품·알러지 기반 하루 식단 추천 실행 계획

작성일: 2026-06-02
개정일: 2026-06-22
상태: 현행 v1 구현 기록. 목표별 최적화는 `DIET_RECOMMENDATION_OPTIMIZATION.md`, 알러지 계약 전환은 `DIET_ALLERGEN_VERIFIED_ONLY_HARDENING.md`를 따른다.
대상: 백엔드, iOS, 제품 기획
상위 문서: [DIET_RECOMMENDATION_RESTRICTIONS_PRD.md](../product-specs/DIET_RECOMMENDATION_RESTRICTIONS_PRD.md) (제품 기획서)
결정 기준: 제품 PRD와 후속 최적화·알러지 강화 실행 계획의 합의 사항을 함께 따른다.
전역 ADR: [0002. 검증된 후보로 목표별 남은 영양량을 제약 최적화한다](../adr/0002-goal-aware-nutrition-optimization.md), [0005. 알러지 추천은 버전된 근거와 fail-closed 완결 프로필로 판정한다](../adr/0005-versioned-allergen-evidence-fail-closed.md)

> 본 문서는 **구현 방법(How)**을 다룬다. 기능의 목적·범위·제품 결정(What/Why)은 위 결정 기준 문서를 따른다.
> 2026-06-04 확정된 두 PRD 기준에 맞춰 v1 출시 범위, 안전 원칙, 데이터 운영 경계, iOS 진입점을 재정리함.
> 2026-06-18 합의로 verified-only 알러지 기본 게이트, 목표별 남은 영양량, 현실적인 제공량, 제약 최적화, 추천 이벤트가 후속 방향으로 확정되었다. 본 문서의 기본/Strict 2모드, ±10% 공통 검증, greedy 엔진, 추천 미영속 정책은 **현행 구현 설명**이며 새 구현의 결정 기준이 아니다.
> 2026-06-22 합의로 기본/Strict 2모드와 결합된 알러지·기피 UI를 폐기한다. 본 문서의 Strict 토글, 태그 행의 `allergen_profile_verified`, 추천 끼니의 일반 기록 API 직접 호출은 **역사적 구현 설명**이며 목표 계약은 [verified-only 알러지 강화 계획](DIET_ALLERGEN_VERIFIED_ONLY_HARDENING.md)이 우선한다.

## 0. 2026-06-17 작업 현황

현재 작업 브랜치는 `feat/allegen-recommendation`이며, 알러젠 식단 추천과 식품 카탈로그 강화 작업을 이 브랜치에서만 진행한다는 운영 원칙과 일치한다.

### 0.1 완료된 작업

| 영역 | 현황 |
|---|---|
| 백엔드 스키마 | `V22__allergen_restriction_schema.sql`로 `diet_restrictions`, `food_allergen_tags` 테이블 추가 완료 |
| 알러젠 태그 | 한국 의무표시 19종 + 합성 식이 제한 태그 `GLUTEN` enum 구현 완료 |
| 알러젠 시드 | `V26__seed_allergen_tags.sql`로 단일재료 중심 `DIRECT_VERIFIED` 태그 seed 추가 완료 |
| P1 seed 커버리지 리포트 | `docs/references/ALLERGEN_SEED_COVERAGE_2026-06-16.md`로 V25 추천 후보 42개 기준 커버리지와 `V30` 보강 결과 정리 완료 |
| P1 seed 보강 | `V30__seed_allergen_tags_p1_coverage.sql`로 `TOMATO`, `SOY`, `WHEAT`/`GLUTEN`, `PEACH`를 보강하고 `Buckwheat`의 `GLUTEN` 태그 제거 완료 |
| 사용자 제한 조건 API | `GET/POST/DELETE /api/v1/diet/restrictions` 구현 완료 |
| 관리자 알러젠 태그 API | `POST /api/v1/admin/diet/allergen-tags`, `POST /bulk`, `GET /food/{foodCatalogId}`, `DELETE /{tagId}` 구현 완료 |
| 하루 식단 추천 API | `POST /api/v1/diet/recommendations/daily` 구현 완료 |
| 추천 후보 필터 | `recommendation_status`, 음식/카테고리/키워드/알러젠 제한, Strict 모드 게이트 적용 완료 |
| 추천 응답 | 끼니별 추천, 총 영양 합계, 적용 제한 조건, `strictAllergyMode`, 알러젠 주의 문구 응답 완료 |
| P0 API 계약 | 제한/추천 API를 `ApiResponse` envelope로 정합화하고 iOS `NutritionTargets` DTO를 백엔드 필드명에 맞춤 |
| P0 실패 처리 | 후보 부족, 빈 끼니, 칼로리 ±10% 범위 실패, 단백질 90% 미달 시 `BUSINESS_RULE_VIOLATION` 반환 |
| P0 Strict 게이트 | `V27__allergen_profile_verified.sql`로 `allergen_profile_verified`를 추가하고 Strict 통과 조건에 반영 |
| P1 DB 중복 제약 | `V29__diet_restrictions_active_unique_indexes.sql`로 활성 제한 조건의 대상별 부분 유니크 인덱스 추가 완료 |
| iOS 모델/네트워크 | `APIEndpoint`, 제한 조건 모델, 하루 추천 모델 추가 완료 |
| iOS 화면 | `DietRestrictionView`, `DietRecommendationView`와 ViewModel 1차 구현, 식단 탭 상단 추천 카드, 추천 끼니 기록하기 연결 완료 |
| 테스트 | 백엔드 제한 조건, 알러젠 게이트, 관리자 알러젠 태그, 추천 후보 풀, 추천 API 단위/컨트롤러 테스트와 iOS 제한/추천 ViewModel 테스트 추가 완료 |
| 식품 카탈로그 local 검증 | 공공데이터 smoke/제한 배치와 `processed-foods` 대량 적재 검증 완료. local DB는 총 `food_catalog=136783`, `MFDS_STANDARD_PROCESSED=135459`, `MFDS_STANDARD_DISH=500`, `MFDS_FOOD_NUTRIENT_DB=499`, `SEED=300`, `USER_CUSTOM=25` 상태이며 공공데이터 항목은 모두 `SEARCH_ONLY` |
| 공공데이터 importer 보정 | `processed-foods` 1484페이지의 제조사명 150자 초과 장애를 재현하고, 외부 문자열을 `food_catalog` 컬럼 한도에 맞춰 정규화/절단하도록 보정 완료 |
| 브랜드 알러젠 적재 경로 | 브랜드 메뉴 CSV에 `allergen_tags`, `allergen_profile_verified`를 추가하고 `LABEL_DERIVED`/`BRAND_OFFICIAL` 태그 적재 경로 확정 완료 |
| 브랜드 공식 메뉴 알러젠 CSV | 버거킹·맥도날드·롯데리아 공식 알러젠/영양 376행을 `docs/references/brand_menu_allergen_verified_2026-06-17.csv`로 검수 완료. 실제 CSV 검증 결과 3개 브랜드, 전부 `SEARCH_ONLY`, `allergen_profile_verified=true` 331행, 알러젠 공란 45행, `profile_true_without_tags=0` |

### 0.2 현행 구현 기준

- `food_allergen_tags`의 레코드는 "이 식품에 해당 알러젠이 포함됨"을 의미한다.
- `confidence_level`은 "해당 알러젠이 없음"의 신뢰도가 아니라, 포함 태그의 검토 출처 신뢰 수준으로 쓴다.
- `allergen_profile_verified=true`는 해당 식품의 알러젠 집합을 완결된 프로필로 검토했다는 별도 신호다.
- 기본 모드는 제한 알러젠과 매칭되는 태그가 있으면 제외하고, 매칭 태그가 없으면 통과시킨다.
- Strict 모드는 제한 알러젠과 매칭되는 포함 태그가 없고, `allergen_profile_verified=true`이면서 `DIRECT_VERIFIED` 또는 `LABEL_DERIVED`인 검토 레코드가 있어야 통과한다.
- 이 모델은 PRD §6.6과 정합화했다. 별도 "없음 주장" 테이블은 v1에서 만들지 않고, 태그 없음만으로 안전을 단정하지 않는다.
- 공공데이터 적재 항목은 검색/기록 커버리지 보강용이며, 알러젠 검토와 추천 큐레이션 없이 추천 후보로 자동 승격하지 않는다. local에서는 smoke/제한 배치/대표 대량 장애 케이스 검증으로 충분하므로 모든 공공데이터를 끝까지 적재하지 않는다.

### 0.3 이후 작업

| 우선순위 | 작업 | 이유/완료 기준 |
|---:|---|---|
| P0 | verified-only 알러지 계약 강화 | ADR-0005와 `DIET_ALLERGEN_VERIFIED_ONLY_HARDENING.md`에 따라 버전된 근거, 검토 범위 프로필, Strict 제거, 알러지·기피 UI 분리, 재검증 하위 큐, 추천 전환 재검증을 구현한다. |
| 완료 | 출시 추천 후보 큐레이션 보강 | 브랜드 공식 메뉴 CSV 376행 중 12개를 `RECOMMENDABLE_WITH_CAUTION`으로 승격했다. 현재 로컬 DB 추천 후보는 seed 42개 + 브랜드 공식 주의 후보 12개 = 54개다. 브랜드 후보는 탄수화물·총지방 공식 미공개와 나트륨/포화지방 주의 사유를 응답 caution으로 남긴다. |
| 완료 | iOS 테스트 추가 | 제한 조건 목록/추가/삭제, 추천 성공/실패, Strict 토글, 후보 부족 메시지, 의료 안전 단정 문구 부재를 ViewModel 단위 테스트로 확인했다. |
| P2 | staging/운영 검증 | local DB에서는 Flyway, admin token fail-closed, 실제 공공 API smoke/제한 배치, `SEARCH_ONLY` 기본값, importer 길이 초과 보정을 확인했다. staging/운영에서는 전량 적재 실행 여부, rate limit, dedup 리포트, 출시 후보 추천 수를 별도 검증한다. |
| P2 | 데이터 라이선스 근거 정리 | 국민건강영양조사 음식별 식품재료량 DB, 푸드QR/OFF, 브랜드 공식 자료의 사용 범위를 문서화한다. |
| P2 | 카피/표시 정책 다듬기 | 검증 근거·검토일, 실제 라벨 확인 문구를 한국어/영어 로컬라이징 기준으로 고정한다. Strict 설명은 제거하고 글루텐 프리는 별도 `DIETARY_REQUIREMENT` 후속 범위로 분리한다. |
| P2 | 브랜드 공식 메뉴 추가 커버리지 | 서브웨이 알러젠 이미지표 OCR/수동 검수와 버거킹 무영양 플래그십 21개 재수집은 v1 필수 CSV 완료 범위에서 분리한다. |

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

설계 기준 테이블:

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

현행 구현 메모:

- 실제 `V22__allergen_restriction_schema.sql`은 `restriction_type VARCHAR(10)`과 조회 인덱스를 사용한다.
- `V29__diet_restrictions_active_unique_indexes.sql`로 활성 제한 조건의 대상별 부분 유니크 인덱스를 추가했다. 애플리케이션 레벨 중복 검증은 사용자에게 빠른 오류 메시지를 주는 1차 방어로 유지한다.

### 3.2 알러지 태그 매핑

추천 안정성을 위해 식품 카탈로그와 알러지 태그를 분리한다.

설계 기준 테이블:

```sql
CREATE TABLE food_allergen_tags (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    food_catalog_id BIGINT NOT NULL REFERENCES food_catalog(id) ON DELETE CASCADE,
    allergen_tag VARCHAR(30) NOT NULL,
    -- 포함 태그의 검토 출처 신뢰 레벨. 레코드가 있으면 해당 알러젠이 포함됨을 뜻한다.
    confidence_level VARCHAR(20) NOT NULL,  -- DIRECT_VERIFIED | LABEL_DERIVED | RECIPE_DERIVED | UNKNOWN
    source VARCHAR(30) NOT NULL,            -- 데이터 출처(아래)
    allergen_profile_verified BOOLEAN NOT NULL DEFAULT FALSE,
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

현행 구현 메모:

- 실제 `V22__allergen_restriction_schema.sql`에는 `food_recipe_ingredient`가 포함되지 않았다. `RECIPE_DERIVED`를 본격 사용하려면 별도 마이그레이션과 라이선스 확인이 필요하다.
- `V27__allergen_profile_verified.sql`로 `allergen_profile_verified`를 추가했다. Strict 모드에서 "매칭 포함 태그 없음"을 통과 근거로 쓰려면 이 값이 `true`여야 한다.
- 현재 `food_allergen_tags`는 포함 태그만 저장한다. 별도 "없음 주장" 레코드는 만들지 않는다.

#### 신뢰 레벨 산출 (PRD §6.6 — 핵심)

`confidence_level`은 포함 태그의 검토 출처 신뢰도다. `allergen_profile_verified`는 해당 식품의 알러젠 집합을 완결된 프로필로 검토했다는 별도 신호다.

| 식품 유형 | 산출 방법 | confidence_level | profile verified |
|---|---|---|---|
| 단일재료 (원물·명확한 단일 식품) | 식약처 식품분류 중/소분류 → 알러젠 결정적 매핑 | `DIRECT_VERIFIED` | 완결 검토 가능 항목만 `true` |
| 복합식품 (재료 분해 가능) | `food_recipe_ingredient`의 각 재료에 분류매핑 적용 → 합집합 | `RECIPE_DERIVED` | v1 Strict에서는 사용하지 않음 |
| 가공·브랜드 제품 | 푸드QR·OFF/브랜드 공식 라벨의 의무표시 알레르기 정보 | `LABEL_DERIVED` | 라벨 알러젠 집합 전체를 검토한 경우 `true` |
| 재료·라벨 미상, 사용자 커스텀 | — | `UNKNOWN` | `false` |

**완결성 규칙**: 부분 태그는 위험하다. 예) 베이글에 `WHEAT`만 달고 우유·계란을 모르면, 우유 알러지 사용자에게 잘못된 안심을 줄 수 있다. 따라서 Strict 모드에서는 `allergen_profile_verified=true`인 식품만 통과시킨다.

#### 추천 회피 판정 (2모드)

```
기본 모드:  등록 제한 알러젠과 매칭되는 포함 태그가 있으면 제외.
            매칭 태그가 없으면 통과하되 디스클레이머를 표시.
Strict 모드: 매칭 포함 태그가 없고, allergen_profile_verified=true 이며
            confidence ∈ {DIRECT_VERIFIED, LABEL_DERIVED} 인 검토 레코드가 있어야 통과.
            RECIPE_DERIVED·UNKNOWN·profile 미검토 후보는 제외.
```

#### 시드 전략 (메뉴젠 폐기)

- **메뉴젠 API는 폐기**한다 — 공공누리 **제4유형(상업 이용금지)** 확정으로 상업 서비스 사용 불가.
- 시드 계층:
  1. `DIRECT_VERIFIED` — 식약처 식품영양성분DB(제한 없음)의 분류 코드로 단일재료 일괄 자동 태깅. 수작업 없음.
  2. `RECIPE_DERIVED` — **국민건강영양조사 음식별 식품재료량 DB**(질병청)를 `food_recipe_ingredient`로 적재 → 재료별 분류매핑 union.
  3. `LABEL_DERIVED` — 푸드QR·OFF는 v2 보강.
- 외부 데이터는 사전 배치 임포트 전용. 추천 런타임은 내부 테이블만 조회.
- **현행 조사 메모**: 음식별 식품재료량 DB의 상업 재사용 라이선스 확인 전에는 `RECIPE_DERIVED`를 사용하지 않는다. ADR-0002 이후 이 source는 최종 안전 근거가 아니라 검수 후보 생성용이므로 출시 블로커가 아니다.

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

백엔드는 iOS 공통 네트워크 계층과 맞추기 위해 성공 응답을 `ApiResponse<T>` envelope로 감싼다. 삭제 응답도 204 raw 응답이 아니라 `ApiResponse<Void>` 형태의 200 응답을 반환한다.

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

현행 v1은 추천 결과를 `diet_plan` 같은 새 영속 테이블에 저장하지 않는다. 추천은 계산 결과 응답으로 제공하고, 사용자가 `기록하기`를 누를 때 기존 `DietLog`/`FoodEntry` 저장 흐름으로만 영속화한다. 같은 날 다시 추천은 새 계산으로 처리하되, 프리미엄 정책이나 일일 횟수 제한은 두지 않는다. 후속 Phase 4에서는 식단 계획 도메인이 아니라 품질 개선에 필요한 최소 추천 스냅샷·이벤트를 저장한다.

### 3.5 규칙 기반 추천 엔진 (현행 baseline)

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
   - **기본 모드**: 등록 제한 알러젠과 매칭되는 포함 태그가 없으면 통과하되 디스클레이머를 표시한다.
   - **Strict 모드**: 매칭 포함 태그가 없고, `allergen_profile_verified=true`이며, `DIRECT_VERIFIED` 또는 `LABEL_DERIVED`인 검토 레코드가 있어야 통과한다.

추천 점수 기준:

- 끼니별 목표 칼로리 오차
- 끼니별 목표 단백질 오차
- 하루 총합의 탄수화물, 지방 오차
- 같은 식품 반복 페널티
- 사용 빈도 높은 식품 가산점

단백질 목표 달성을 중요하게 보기 위해 단백질 오차의 가중치를 칼로리 외 매크로보다 높게 둔다.

이 엔진은 후속 benchmark의 baseline이다. 목표 상태는 `DIET_RECOMMENDATION_OPTIMIZATION.md`에 정의한 목표별 비대칭 hard constraint, 이미 섭취한 식단 차감, 남은 끼니 공동 최적화, 검증된 이산 제공량, feasible 상위 해 다양성이다.

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

현행은 추천 식단을 바로 저장할 때 기존 `POST /api/v1/diet/logs`를 재사용한다. ADR-0005 전환 후에는 일반 기록 API를 유지하되 추천 끼니는 전용 멱등 전환 API에서 현재 제한과 근거를 다시 검증한다.

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

| 순서 | 작업 | 상태 | 메모 |
|---:|---|---|---|
| 1 | PRD 기준 수용 조건 확정 | 완료 | 무료, 규칙 기반, 하루 단위, 끼니 선택, 기존 기록 API 재사용, 포함 태그 + 프로필 검토 기반 알러젠 모델로 정합화 |
| 2 | 백엔드 마이그레이션 추가 | 완료 | V22 제한/알러젠 스키마, V26 알러젠 seed, V27 Strict 프로필 검토, V29 제한 조건 중복 인덱스, V30 P1 seed 보강 추가 |
| 3 | 제한 조건 엔티티, DTO, Repository, Service, Controller 구현 | 완료 | `DietRestrictionUseCases`, `DietRestrictionController` 구현 |
| 4 | 알러지 태그 엔티티와 Repository 구현 | 완료 | `FoodAllergenTag`, `FoodAllergenTagRepository` 구현 |
| 5 | 허용 가능한 출처 기준에 맞춰 최소 알러젠 시드 구성 | 부분 완료 | 단일재료 중심 V26과 P1 allowlist 보강 V30 완료. 브랜드/라벨/복합식품 커버리지는 남음 |
| 6 | 추천 요청/응답 DTO 구현 | 완료 | `DailyDietRecommendationRequest/Response`, `RecommendedMeal`, `RecommendedFoodEntry` 구현 |
| 7 | 규칙 기반 추천 엔진 구현 | 완료 | 날짜 기반 deterministic rotation 포함 |
| 8 | 하루 추천 Controller 구현 | 완료 | `POST /api/v1/diet/recommendations/daily` |
| 9 | 백엔드 테스트 추가 | 부분 완료 | 후보 부족/목표 달성 실패/Strict 프로필 검토/ApiResponse 계약/제한 조건 DB 중복 제약/importer 길이 초과 보정 테스트 완료. staging/운영 검증은 P2 |
| 10 | iOS APIEndpoint와 모델 추가 | 완료 | 제한 조건, 하루 추천 endpoint/model 추가 |
| 11 | 제한 조건 설정 ViewModel/화면 구현 | 완료 | 알러젠/카테고리/키워드 제한 화면 1차 구현 |
| 12 | 식단 탭 상단 카드와 추천 식단 ViewModel/화면 구현 | 완료 | 식단 탭 상단 추천 카드와 하루 추천 화면 구현 |
| 13 | 추천 끼니 저장 흐름 연결 | 완료 | 추천 결과를 기존 식단 기록 API로 저장하는 iOS 액션 연결 |
| 14 | iOS 테스트 추가 | 미완료 | 현재 관련 테스트 없음 |
| 15 | 식품 카탈로그 local 검증 | 완료 | 공공데이터 smoke/제한 배치, `processed-foods` 대량 검증, `SEARCH_ONLY` 기본값, importer 길이 초과 장애 보정 확인. local 전량 적재는 중단 |

## 7. v1 제외 범위

- AI 식단 생성
- 주간 식단 추천
- 장보기 목록
- 식재료 단위 레시피 추천
- 이미지 기반 목표 피지크 분석
- 의료적 알러지 안전 보증
- 외부 식품 DB 실시간 알러지 추론
- 추천 결과 영속 저장용 `diet_plan` 도메인. 단, 품질 개선용 최소 추천 스냅샷·이벤트는 후속 범위에 포함한다.
- 끼니 단위 Swap API
- 무료/프리미엄 기반 추천 횟수 제한

## 8. 가정과 주의사항

- 활성 목표가 있으면 활성 목표의 영양 목표를 우선 사용한다.
- 활성 목표가 없으면 사용자 프로필에 저장된 영양 목표를 사용한다.
- 사용자 프로필과 영양 목표가 모두 부족하면 추천하지 않는다.
- 현행 알러지 회피는 PRD 구버전 §6.6의 2모드로 구현되어 있다. 후속 전환에서는 알러지 등록 사용자가 항상 verified-only이며, `strictAllergyMode`는 호환 기간 동안 수용하되 무시한 뒤 폐기한다.
- 추천 결과는 사용자의 기록 편의를 돕는 식단 제안이며, 질병 치료나 의료 처방이 아니다. 후속 verified-only 정책도 검증된 데이터 계약 안의 회피이며 교차오염과 실제 제품 변경을 보장하지 않는다.
- 외부 데이터는 이용허락 검토가 끝나기 전까지 필수 런칭 의존성으로 두지 않는다.
- `RECIPE_DERIVED`는 국민건강영양조사 음식별 식품재료량 DB 상업 재사용 라이선스에 의존한다. 확인 전에는 사용하지 않으며 후속 verified-only 추천의 최종 통과 근거로도 사용하지 않는다.
- 메뉴젠(공공누리 제4유형)·AllergieShield(영국·OFF래퍼·AI의존)는 폐기됨. 알러젠 회피 판정을 외부 AI/제3자에 위임하지 않는다.
- 제품 PRD, ADR-0002, ADR-0005가 본 현행 구현 기록보다 우선한다. 후속 작업은 `DIET_RECOMMENDATION_OPTIMIZATION.md`와 `DIET_ALLERGEN_VERIFIED_ONLY_HARDENING.md`를 따른다.
