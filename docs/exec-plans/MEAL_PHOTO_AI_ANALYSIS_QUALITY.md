# 사진 기반 AI 식단 분석 품질 개선 실행 계획

작성일: 2026-06-16
상태: 계획 확정(착수 전): 비동기+폴링, Anthropic 전환 반영. 1차 원인(PRO 잠김) 코드 검증 완료.
대상: 백엔드, iOS, 제품 기획
작업 브랜치: `codex/meal-photo-ai-analysis-quality`
관련 코드: `backend/.../domain/diet/mealphoto/**`, `ios/.../Features/Record/Diet/**`, `ios/.../Core/Auth/AuthState.swift`

> 본 문서는 **구현 방법(How)** 을 다룬다. 범위는 "PRO 잠김 복구 + 실제 사진 분석 안정화 + 영양 데이터 품질 개선"이다.
> 정확도 수치는 고정 benchmark 결과가 확보된 경우에만 `docs/product-specs/GAINSY_QUANTIFIED_PROGRESS.md`에 반영한다.

## 0. 1차 원인 (코드 검증 완료)

사용자 피해는 "프리미엄인데도 식단 기록 화면에서 사진 분석 버튼이 잠김"이다. 근본 원인은 **권한 동기화 누락**이다.

| 주장 | 코드 확인 | 판정 |
|---|---|---|
| `/api/v1/users/me`가 `isPremium` 미전달 | `UserProfileResponse`에 premium 필드 없음. 엔티티 `User.isPremium`만 존재 | 확인 |
| 마이페이지 방문 전엔 free처럼 동작 | `AuthState.updatePremiumStatus`는 **오직** `MyPageViewModel.loadProfile()`에서만 호출. `AuthState.isPremium` 기본값 false, 사진 버튼이 `AddDietLogView`에서 이 값에 게이팅 | 확인 |
| 최종 권한은 서버가 검증 | initiate/analyze/confirm 모두 `PremiumAccessGuard.assertPremium` 호출 | 확인 |

→ 사진 버튼 UI는 클라이언트 캐시(`AuthState.isPremium`)에 의존하는데, 이 캐시는 마이페이지를 방문해야만 채워진다. `/me`가 권한을 안 내려주고, 앱 시작 시 동기화 호출도 없다.

## 1. 핵심 변경

### 1.1 프로필/권한 계약 (P0 — 잠김 복구)
- `UserProfileResponse`에 `isPremium: boolean` 추가, `UserProfileResponse.from(User)`에서 매핑.
- iOS `AuthState`에 `premiumStatus = unknown | free | premium` 도입.
- **앱 시작/로그인 직후**(예: `MainTabView.task`)에 `/me`를 호출해 `premiumStatus` 동기화. **마이페이지 진입에만 의존하지 않는다.**
- `unknown` 상태에서는 사진 버튼을 잠그지 않고 **서버 검증에 맡긴다.** 최종 권한은 기존 `PremiumAccessGuard`(403 `PREMIUM_REQUIRED`)가 유지.

### 1.2 사진 입력 품질 (iOS)
- `PhotosPicker` 원본 `Data`를 그대로 보내지 않고 `UIImage` 디코딩 → 긴 변 약 1024px 이하, JPEG 품질 0.85, `image/jpeg`로 정규화 후 업로드.
- HEIC/대용량/메타데이터 문제 완화. 디코딩 실패 시 "다른 사진을 선택해 주세요" 오류 표시.

### 1.3 분석 데이터 — 10종 영양소 엔드투엔드 배선
> **주의(범위 교정):** `MealPhotoAnalysisItem` 엔티티와 `FoodCatalog`는 이미 10종 영양소 컬럼을 **보유**하고 있다. 따라서 10종 영양소를 위한 컬럼 추가가 아니라 **confirm → 커스텀 식품 생성 경로의 배선 누수**를 고친다. 단, `ANALYZING` 상태 추가를 위한 Flyway `V29`는 별도로 필요하다(현재 최신 마이그레이션이 `V28__allergen_profile_verified_constraint.sql`이므로 다음 번호는 `V29`).
- 현재 `MealPhotoAnalysisService.confirm()`은 커스텀 식품 생성 시 칼로리·단백·탄수·지방 **4종만** `CreateCustomFoodRequest`로 전달한다. 나머지 6종이 엔티티엔 있어도 DietLog까지 흐르지 않는다.
- 6종 추가 대상: `sugarsG`, `dietaryFiberG`, `saturatedFatG`, `transFatG`, `cholesterolMg`, `sodiumMg`.
- 배선 누수 수정 경로(전 구간):
  `MealAnalysisProvider.DetectedItem`(4→10) → provider 매핑 → `MealPhotoAnalysisItemResponse` → `ConfirmMealPhotoAnalysisRequest` item → **`CreateCustomFoodRequest` 10종(per100g 환산 포함)** → iOS `MealPhotoAnalysisItem`.
- 카탈로그 매칭 성공 시 서버 카탈로그 값을 우선 사용, 미매칭 항목은 AI 추정값으로 커스텀 식품 생성.

### 1.4 Anthropic provider 전환
> 식단 사진 분석 provider는 `AnthropicMealAnalysisProvider`로 전환한다. 기존 텍스트 기반 AI 영양 추정/운동 추정 기능 보호를 위해 전역 `OPENAI_API_KEY`를 제거하지 않고, **사진 분석 provider만** Anthropic 설정으로 분리한다.
- 신규 `AnthropicMealAnalysisProvider`는 Anthropic Messages API를 사용한다.
- 이미지 입력은 image content block(base64)로 전달한다. iOS에서 JPEG 정규화 후 업로드하고, 백엔드는 S3 객체를 base64로 읽어 provider에 전달한다.
- 구조화 출력은 tool use(JSON schema)로 강제한다. `tool_choice`를 `emit_meal_photo_analysis` 도구로 고정하고, `tool_use` 블록이 없거나 비어 있으면 실패로 처리한다.
- 기본 모델은 `claude-haiku-4-5` 계열(운영에서는 dated model ID pin 권장)로 두고, 복잡 사진은 설정으로 `claude-sonnet-4-6` 계열로 승격 가능하게 한다.
- 설정 namespace는 사진 분석 전용으로 분리한다.
  - `app.ai.meal-photo.provider=anthropic`
  - `app.ai.meal-photo.anthropic-api-key=${ANTHROPIC_API_KEY:}`
  - `app.ai.meal-photo.model=${ANTHROPIC_MEAL_PHOTO_MODEL:claude-haiku-4-5}`
  - `app.ai.meal-photo.connect-timeout-seconds=10`
  - `app.ai.meal-photo.read-timeout-seconds=60`
- API 키 부재 또는 provider 비활성 상태에서는 `FallbackMealAnalysisProvider`를 유지한다.
- 실패 처리: timeout, provider exception, malformed/empty tool result, S3 객체 로딩 실패는 분석 상태를 **`FAILED`** 로 기록하고 `AI_ANALYSIS_FAILED` 사용자 오류로 반환한다. 재분석은 `CONFIRMED` 전까지 허용한다.

### 1.5 `ANALYZING` 상태 + `202 Accepted` + iOS polling (필수)
> 사진 분석은 외부 vision provider 호출 지연이 길 수 있으므로 `analyze` 요청에서 결과 생성을 기다리지 않는다.
- 신규 Flyway `V29__meal_photo_analyzing_status.sql`에서 기존 `meal_photo_analyses.status` CHECK 제약을 `INITIATED`, `ANALYZING`, `ANALYZED`, `FAILED`, `CONFIRMED`로 재생성한다.
- `MealPhotoAnalysis.Status`에 `ANALYZING`, `markAnalyzing()`을 추가한다.
- `POST /api/v1/diet/photo-analyses/{id}/analyze`는 소유권/프리미엄/상태 검증 후 상태만 `ANALYZING`으로 바꾸고 **`202 Accepted`** 와 현재 analysis 응답을 즉시 반환한다.
- 실제 분석은 `MealPhotoAnalysisAsyncRunner`가 `@Async`로 실행한다. self-invocation을 피하기 위해 서비스 내부 메서드 직접 호출이 아니라 별도 컴포넌트로 둔다.
- S3 다운로드와 Anthropic 호출은 DB 트랜잭션 밖에서 수행하고, `ANALYZED`/`FAILED` 저장만 짧은 트랜잭션으로 감싼다.
- iOS는 `analyze` 호출 후 `getMealPhotoAnalysis`를 지수 백오프로 폴링한다. 최대 대기 시간은 약 60초이며, `ANALYZED`면 draft 생성, `FAILED`면 재시도 가능한 오류, timeout이면 사용자가 다시 시도할 수 있는 오류를 표시한다. 구현 메모: iOS polling 상태를 UI에 노출한다.
- `CONFIRMED` 상태에서는 재분석과 재확정을 막는다. `ANALYZING` 상태에서 중복 analyze 호출이 오면 기존 분석 작업을 재사용하거나 202로 현재 상태만 반환한다.

### 1.6 매칭 품질과 제한 대조
- `MealPhotoAnalysisService.matchFoodCatalog()`는 단순 첫 결과 선택 대신 정규화된 query, 관련성 점수, confidence 임계값을 사용한다.
- 임계값 미달이면 카탈로그 매칭을 보류하고 AI 추정값을 유지한다.
- 매칭된 식품은 `FoodAllergenTag`와 사용자 `DietRestriction`을 대조해 항목별 알러젠/제한 경고를 생성한다.
- iOS 검토 화면은 항목별 경고 배지와 caution 문구를 표시한다.

## 2. 실행 순서
1. **Phase 1 — 비동기화 + 폴링**
   - `V29__meal_photo_analyzing_status.sql`, `ANALYZING`, `markAnalyzing()`, `202 Accepted`, `MealPhotoAnalysisAsyncRunner`, `ANALYZED/FAILED` 상태 전이를 구현한다.
   - iOS `MealPhotoAnalyzing`은 `analyze -> getMealPhotoAnalysis` polling으로 전환한다.
2. **Phase 2 — Anthropic provider/config 전환**
   - `AnthropicMealAnalysisProvider`와 명시적 provider configuration을 추가한다.
   - `OpenAiMealAnalysisProvider`는 사진 분석 경로에서 제거하되, 텍스트 AI 추정용 OpenAI 설정은 유지한다.
3. **Phase 3 — 사진 입력/매칭 품질**
   - iOS 1024px JPEG 다운스케일을 추가한다.
   - 카탈로그 매칭 관련성 점수와 confidence 임계값을 적용한다.
4. **Phase 4 — 알러젠/제한 연동**
   - 매칭된 카탈로그와 사용자 `DietRestriction`/`FoodAllergenTag`를 대조해 항목별 경고를 만든다.
   - iOS 검토 화면에 경고 배지를 추가한다.
5. **Phase 5 — 테스트와 검증**
   - 백엔드 서비스/MockMvc/provider 테스트와 iOS polling 테스트를 보강한다.
   - 커버리지 80%+를 수용 기준으로 둘 경우 Jacoco와 Xcode coverage 측정 설정을 함께 추가한다.
6. 정확도 benchmark 확보 시에만 `GAINSY_QUANTIFIED_PROGRESS.md`를 갱신한다.

## 3. 테스트 계획

### 3.1 백엔드
- 프리미엄/무료 사용자 `/me` 응답에 `isPremium`이 정확히 직렬화.
- 무료 사용자는 사진 분석 initiate에서 `403 PREMIUM_REQUIRED`.
- 프리미엄 사용자는 `initiate -> upload(mock) -> analyze(202) -> polling get -> confirm` 성공.
- 상태 전이: `INITIATED -> ANALYZING -> ANALYZED`, `INITIATED -> ANALYZING -> FAILED`, `ANALYZED -> CONFIRMED`.
- `ANALYZING` 상태에서는 confirm을 막고, `ANALYZED` 상태에서만 confirm을 허용한다.
- Anthropic timeout, provider exception, malformed tool use, empty tool result, S3 로딩 실패가 500이 아닌 **`FAILED`/사용자 오류**로 처리.
- 미매칭 항목 confirm 시 **10종 영양소가 커스텀 식품에 저장**됨.
- 외부 호출이 트랜잭션 밖에서 수행되어 실패 시에도 상태가 `FAILED`로 커밋됨.
- MockMvc: analyze endpoint가 `202 Accepted`를 반환하고, get endpoint가 `ANALYZING`/`ANALYZED`/`FAILED`를 반환.

### 3.2 iOS
- `UserProfile.isPremium` 디코딩 true/false/누락 케이스.
- 앱 시작 후 `/me` 동기화 **전/후** 사진 버튼 상태(unknown→서버 검증 위임).
- HEIC/PNG/JPEG 선택 시 JPEG 전처리 결과가 `APIClientMealPhotoAnalyzer`로 전달.
- 사진 분석 성공 polling, `premiumRequired`, `FAILED`, polling timeout, 빈 결과 warning 케이스.
- polling은 지수 백오프와 최대 약 60초 제한을 검증한다.

## 4. 수용 기준
- 프리미엄 사용자는 **마이페이지 방문 없이도** 식단 기록 화면에서 "사진으로 시작"을 사용할 수 있다.
- 무료 사용자는 **서버 응답 기준**으로 paywall을 본다.
- HEIC 사진도 분석 요청 전에 JPEG로 정규화된다.
- Anthropic/S3 문제가 발생해도 앱은 일반 500 대신 **재시도 가능한 분석 실패 메시지**를 표시한다.
- `analyze` 요청은 `202 Accepted`로 즉시 반환되고, iOS polling으로 최종 분석 상태를 반영한다.
- 사진 분석으로 생성된 식단 기록은 기존 식단 기록 유스케이스를 재사용하고, **10종 영양소 표준과 일치**한다.
- 외부 provider 호출은 DB 트랜잭션 밖에서 수행되어 커넥션 풀을 점유하지 않는다.
- 커버리지 80%+는 Jacoco/Xcode coverage 설정이 추가된 경우에만 정량 수용 기준으로 사용한다.

## 5. 비고
- `application-local.yml`의 라이브 AI 키는 git-ignore이어도 공유 이력이 있으면 폐기·재발급 권장.
- 10종 영양소 컬럼은 기존 보유 상태다. 다만 `ANALYZING` 상태 추가를 위해 신규 Flyway `V29__meal_photo_analyzing_status.sql`이 필요하다.
- 기존 텍스트 AI 기능은 OpenAI 설정을 계속 사용할 수 있으므로, provider 전환 작업에서 전역 OpenAI 설정을 제거하지 않는다.
