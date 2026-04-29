# 프로젝트 현황 — 2026년 4월 28일

## 전체 진행률

```
Phase 0: 환경 구축              ████████████████████ 100%
Phase 1: 인증 & 사용자          ████████████████████ 100%
Phase 2: 운동 기록             ████████████████████ 100%
Phase 3: 식단 기록             ████████████████████  97%
Phase 4: 신체 측정 & 진행 사진    ███████████████████░  92%
Phase 5: 목표 & 인사이트        ████████████████░░░░  82%
Phase 6: MVP 출시 준비          ░░░░░░░░░░░░░░░░░░░░   0%
```

## 현재 판단 기준

- 2026-04-28 기준, AI 기반 음식/운동 검색 폴백 기능 구현 완료(백엔드 서비스·엔드포인트 + iOS ViewModel 연동), 운동 카탈로그 시드 110개 추가(V11 마이그레이션), 식단 검색 입력 500ms 디바운스 및 이전 요청 취소 반영을 적용했다.
- Phase 3은 AI 영양 추정 폴백(`POST /api/v1/diet/ai-estimate`) + iOS 연동 완료로 97%로 상향했다.
- Phase 2는 운동 카탈로그 시드 데이터(110개 한/영 이름·MET값) + AI 운동 추정 폴백 추가로 100% 유지(검색 편의성 완성).
- 2026-04-26 기준, Goals & Insights 도메인 풀스택 구현(InsightsController/Service, WeeklyRetrospectiveView, ChangeAnalysisView, EditGoalView), Insights 단위 테스트 21개, 탐색 탭 진입점 연결, ProgressPhotoResponse 직렬화 버그 수정, iOS 16 호환 수정을 반영했다.
- Phase 4는 진행 사진 iOS 16 호환 및 isBaseline 직렬화 버그 수정 반영으로 92%로 상향했다.
- Phase 5는 Insights API(주간 회고·변화 분석) 풀스택 구현 + 테스트 완료 + iOS 연동까지 반영해 82%로 상향했다.

## 최근 변경 사항 (2026-04-28)

### 백엔드

- [x] `AiNutritionEstimationService` — 한국어 음식명 → 100g 기준 영양성분 AI 추정 (OpenAI Responses API 재사용)
- [x] `POST /api/v1/diet/ai-estimate` — 공공 API 검색 결과 없을 때 클라이언트 폴백용 엔드포인트
- [x] `AiExerciseEstimationService` — 한국어 운동명 → muscleGroup, exerciseType, MET값 AI 추정
- [x] `POST /api/v1/exercise/ai-estimate` — 카탈로그 검색 결과 없을 때 클라이언트 폴백용 엔드포인트
- [x] `V11__exercise_catalog_seed.sql` — 110개 운동 시드 데이터 (근육군 14종, 한/영 이름, MET값 포함)
- [x] 두 AI 서비스 모두 `@ConditionalOnExpression`으로 `OPENAI_API_KEY` 미설정 시 자동 비활성화

### iOS

- [x] `APIEndpoint` — `.aiEstimateFood`, `.aiEstimateExercise`, `.createCustomFood`, `.createCustomExercise` 4개 case 추가
- [x] `DietModels.swift` — `AiNutritionEstimateResponse`, `AiNutritionEstimateRequest` 모델 추가
- [x] `ExerciseModels.swift` — `AiExerciseEstimateResponse`, `AiExerciseEstimateRequest` 모델 추가
- [x] `AddDietLogViewModel` — `estimateWithAI()`, `addAiEstimatedFood()` 메서드 + `aiEstimateResult`, `isAiEstimating` 상태 추가
- [x] `AddDietLogViewModel` — 식품 검색 500ms 디바운스, 진행 중 검색 취소, 느린 이전 응답 덮어쓰기 방지 로직 추가
- [x] `AddDietLogView` / `FoodSearchSheet` — `onChange` 즉시 호출 제거, `return` 즉시 검색 유지, 검색어 삭제 시 결과 초기화 경로 통일
- [x] `AddExerciseSessionViewModel` — `estimateWithAI()`, `addAiEstimatedExercise()` 메서드 + `aiEstimateResult`, `isAiEstimating` 상태 추가
- [x] `AddDietLogViewModelTests` — 디바운스, 즉시 검색, 검색어 삭제, 느린 응답 역전 방지 시나리오 단위 테스트 추가

### 규제 검토

- [x] 식약처 지침 확인 — 칼로리/식단 추적 앱은 비의료기기로 분류, AI 추정값 제공 허용
- [x] AI기본법(2026) 대응 — 응답에 `isAiEstimated: true` + `disclaimer` 필드 포함, 사용자 수정 후 저장 플로우 설계

## 최근 변경 사항 (2026-04-26)

### 백엔드

- [x] `InsightsControllerTest` 10개 추가 — 주간 회고 weekOffset, 빈 데이터, 401 인증 오류, 날짜 유효성 검증 등
- [x] `InsightsServiceTest` 11개 추가 — 델타 반올림(2자리), ENDURANCE 목표 스킵, WEIGHT_LOSS 달성률 계산 등
- [x] `ProgressPhotoResponse.isBaseline` `@JsonProperty` 누락 버그 수정 (직렬화 시 `is` prefix 탈락 방지)

### iOS

- [x] 탐색 탭(`ExploreView`)에서 `WeeklyRetrospectiveView`, `ChangeAnalysisView` 진입점 연결
- [x] `ProgressPhotoView` `onChange` iOS 16 호환 시그니처 수정
- [x] `DiaryView` 중복 `HistoryCalendarView`/`HistoryCalendarViewModel` 파일 삭제

## 최근 변경 사항 (2026-04-23)

### 백엔드

- [x] `InsightsController` / `InsightsService` 신규 구현 — `GET /api/v1/insights/weekly-summary`, `GET /api/v1/insights/change-analysis`
- [x] `GoalService`: ENDURANCE 진행률을 운동 세션 기간 합산으로 계산하는 `loadExercisePoints()` 추가
- [x] `GoalSummary.percentComplete` 목록 조회 시 읽기 전용 경량 계산(`calculatePercentCompleteReadOnly`) 적용
- [x] `GoalProgressResponse` `weeklyRateTarget` 필드 추가
- [x] `SecurityConfig`: `RestAuthenticationEntryPoint`, `RestAccessDeniedHandler` JSON 응답 적용
- [x] `JwtSecurityIntegrationTest` 추가 — 인증 없음/무효 토큰/유효 토큰 시나리오 검증
- [x] Redis 직렬화 회귀 케이스 및 비교 방식 안정화

### iOS

- [x] `InsightsModels.swift` — `WeeklySummaryResponse`, `ChangeAnalysisResponse` 모델 정의
- [x] `APIEndpoint` — `.getWeeklySummary`, `.getChangeAnalysis` case 추가
- [x] `WeeklyRetrospectiveView`/`ViewModel` — 주간 네비게이션 + 실데이터 연동 완성
- [x] `ChangeAnalysisView`/`ViewModel` — 기간 선택 프리셋 + 실데이터 연동 완성
- [x] `EditGoalView`/`EditGoalViewModel` — `GoalProgressView`에서 목표 수정 진입점 추가
- [x] `GoalModels`: `GoalProgressResponse.weeklyRateTarget` 필드 반영

## 최근 변경 사항 (2026-04-22)

### 백엔드

- [x] `AuthController`: `@AuthenticationPrincipal` 대신 Bearer 헤더 직접 해석으로 로그아웃 경로 정리
- [x] `UserController`: `GET/PATCH/DELETE /api/v1/users/me`에서 Bearer 헤더 직접 검증
- [x] `AuthControllerTest` 추가 — 회원가입/로그인/토큰 갱신/로그아웃 MockMvc 단위 테스트
- [x] `UserControllerTest` 추가 — 프로필 조회/수정/삭제 MockMvc 단위 테스트
- [x] `JwtSecurityIntegrationTest` 추가 — 실제 `SecurityFilterChain` + `JwtAuthenticationFilter` 기준 인증 없음/무효 토큰/유효 토큰 시나리오 검증
- [x] `SecurityConfig`: `RestAuthenticationEntryPoint`, `RestAccessDeniedHandler` 연결로 보안 실패 응답을 JSON 형식으로 통일
- [x] `ProgressPhotoController`: 경로를 `/api/v1/body-measurements/photos`로 통일
- [x] `FoodCatalogRepository`: 공백 무시 검색 + prefix 우선 정렬로 식품 검색 품질 개선
- [x] `FoodCatalogService`: 검색어 trim 정규화 및 테스트 추가
- [x] `ExternalFoodResult`: `@Jacksonized` 추가, 외부 DTO 역직렬화 안정화
- [x] `V10__normalize_endurance_goal_units_to_minutes.sql`: endurance 목표/체크포인트의 초 단위를 분 단위로 정규화
- [x] `docs/design-docs/DB_SCHEMA.md`: endurance 목표 단위를 `minutes` 기준으로 업데이트

### iOS

- [x] `ProgressPhotoModels`, `ProgressPhotoViewModel`, `ProgressPhotoView`, `AddProgressPhotoView` 추가
- [x] 진행 사진 업로드 플로우를 presigned URL 발급 → S3 PUT → 메타데이터 등록 3단계로 연결
- [x] 신체 측정 추세 그래프를 `GET /api/v1/body-measurements/range`, `GET /api/v1/body-measurements/at-or-before` 기반으로 연동
- [x] `APIEndpoint`: 진행 사진 업로드 URL 발급/등록/목록 조회 계약 추가
- [x] `GoalModels`: endurance 단위를 분 기준으로 표시하고 목표 타입별 주간 변화량 규칙 반영
- [x] `AddGoalViewModel`, `AddGoalView`: 목표 단위/주간 변화량 입력 UX 보정
- [x] `HomeViewModel`: 활성 목표 진행률 API 연동으로 홈 대시보드 정확도 개선
- [x] `AddExerciseSessionViewModel`, `AddExerciseSessionView`: 운동 시간 입력 및 ISO-8601 시작/종료 시각 전송 지원
- [x] `MyPageViewModel`, `MyPageView`: 프로필 조회/수정/삭제 실데이터 연결
- [x] `HomeView`, `RecordHubView`, `MyPageView`: 디자인 시스템을 활용한 UI 개편
- [x] `ios/Configs/Debug.xcconfig`, `Release.xcconfig`: 환경별 iOS 설정 파일 추가

### 문서 / 리서치

- [x] `docs/design-docs/EXERCISE_EXTERNAL_INTEGRATION.md`: 운동 외부 데이터 연동 원칙과 채택안 정리
- [x] `docs/references/EXERCISE_API_SURVEY_2026-04-22.md`: 운동 종목·칼로리 API 조사 문서 추가
- [x] `gan-harness/spec.md`, `gan-harness/eval-rubric.md`: 평가용 스펙과 루브릭 추가
- [x] `CLAUDE.md`: 저장소 작업 규칙 정리

## 현재 구현 상태

### 백엔드 완료

- [x] Spring Boot 프로젝트 구성, Flyway, PostgreSQL, Redis, JWT 보안 기본 구조
- [x] 인증 API (register/login/refresh/logout) + AuthController MockMvc 단위 테스트
- [x] 사용자 API (me 조회/수정/삭제) + UserController MockMvc 단위 테스트
- [x] 운동 기록 도메인 (카탈로그, 세션 CRUD)
- [x] 식단 기록 도메인 (식사 CRUD, 식품 검색 품질 보정, 외부 공공데이터 연동)
- [x] AI 사진 기반 식단 분석 워크플로 (OpenAI + fallback)
- [x] AI 텍스트 기반 영양 추정 (`POST /api/v1/diet/ai-estimate`) — 한국어 음식명 → 영양성분
- [x] AI 텍스트 기반 운동 추정 (`POST /api/v1/exercise/ai-estimate`) — 한국어 운동명 → MET/분류
- [x] 운동 카탈로그 시드 데이터 110개 (V11 마이그레이션, 근육군별 한/영 이름 + MET값)
- [x] 신체 측정 도메인 (CRUD + atOrBefore 쿼리 + TDD 20개)
- [x] 진행 사진 업로드 MVP (presigned URL, 메타데이터 저장, signed download)
- [x] S3/LocalStack 설정, Terraform AWS 골격
- [x] 목표 도메인 (생성/목록/상세/수정/포기, ENDURANCE 운동 세션 기반 진행률, endurance 단위 minutes 정규화)
- [x] 인사이트 도메인 (weekly-summary, change-analysis API 구현)
- [x] 서비스 단위 테스트 다수 (Auth, BodyMeasurement, ProgressPhoto, MealPhotoAnalysis, GoalService, InsightsService) + 컨트롤러 단위 테스트 (Auth, User, Goal, Insights)

### iOS 완료

- [x] 인증 플로우 (회원가입/로그인/토큰 관리)
- [x] 홈 대시보드 (실데이터 연결 + 활성 목표 진행률 반영)
- [x] 운동 기록 화면 (세션 기록, 히스토리)
- [x] 식단 기록 화면 (식품 검색, AI 사진 진입점, 실데이터 연결, AI 영양 추정 폴백 연동)
- [x] 식단 기록 화면 — 검색 입력 디바운스 및 요청 취소 반영
- [x] 운동 기록 화면 — AI 운동 추정 폴백 연동 (`estimateWithAI`, `addAiEstimatedExercise`)
- [x] 신체 측정 화면 (체중 + 5개 둘레 입력, LatestStatsCard, 기간/지표별 추세 그래프)
- [x] 진행 사진 화면 (목록/상세/업로드)
- [x] 목표 설정 화면 (GoalSettingView, AddGoalView, EditGoalView)
- [x] 목표 진행 화면 (GoalProgressView 완전 구현)
- [x] 마이페이지 화면 (프로필 조회/수정/계정 삭제)
- [x] 주간 회고 화면 (WeeklyRetrospectiveView, 주간 네비게이션 + 실데이터 연동)
- [x] 변화 분석 화면 (ChangeAnalysisView, 기간 선택 프리셋 + 실데이터 연동)
- [x] 탐색 탭 진입점 (ExploreView → WeeklyRetrospectiveView / ChangeAnalysisView)

### 구현은 되었지만 보완이 필요한 항목

- [~] 신체 측정: EXIF 제거, 썸네일 생성, 업로드 완료 검증 미구현
- [~] 컨트롤러/보안 테스트: Auth/User/Goal 단위 테스트와 JWT 보안 체인 통합 테스트는 있으나 도메인별 권한 경계 검증은 더 필요
- [~] iOS 진행 사진: 촬영 시점 선택, 삭제/비교 UX, 썸네일 상태 fallback 개선 필요
- [~] iOS 테스트 타깃: 식단 검색 ViewModel 테스트는 추가됐지만, 기존 `HealthCareTests`/`HealthCareUITests` 타깃 구성 및 Swift 6 actor 호환 이슈로 전체 `xcodebuild test`는 아직 안정적이지 않음

### 아직 미구현 또는 미완성

- [ ] AI 추정 결과 표시 View UI (AI 배지, disclaimer 텍스트 — AI기본법 대응)
- [ ] FCM 기반 인사이트/알림 흐름
- [ ] 주요 도메인 권한 경계 통합 테스트
- [ ] 진행 사진 썸네일 생성 및 업로드 후처리 파이프라인
- [ ] E2E 시나리오 및 출시 준비

## Phase별 상세 상태

### Phase 0: 환경 구축 — 완료

### Phase 1: 인증 & 사용자 — 완료

- 기능 구현 완료
- Auth/User 컨트롤러 MockMvc 단위 테스트 완료
- JWT 필터 및 보안 체인 통합 테스트 완료

### Phase 2: 운동 기록 — 완료

### Phase 3: 식단 기록 — 97%

- AI 사진 분석 워크플로 포함 구현 완료
- 검색 품질 보정(공백 무시, prefix 우선 정렬) 반영
- 식단 검색 입력 500ms 디바운스 + 이전 요청 취소 반영으로 외부 API 과호출 완화
- AI 텍스트 추정 폴백(`POST /api/v1/diet/ai-estimate`) + iOS ViewModel 연동 완료
- 남은 것: 외부 API 장애 대응 회귀 테스트, AI 추정 결과 표시 View UI (배지·disclaimer)

### Phase 4: 신체 측정 & 진행 사진 — 92%

- 백엔드: 측정 CRUD + atOrBefore + 진행 사진 presigned URL MVP + 경로 정합성 + isBaseline 직렬화 버그 수정 완료
- iOS: 모든 둘레 입력 필드 + LatestStatsCard + 진행 사진 목록/상세/업로드 + iOS 16 호환 수정 완료
- 남은 것: EXIF 제거, 썸네일 생성, 업로드 후처리, 사진 비교 UX

### Phase 5: 목표 & 인사이트 — 82%

- 백엔드: 목표 CRUD + 진행률 API + ENDURANCE 운동 세션 기반 계산 + Insights API(weekly-summary/change-analysis) + 전체 테스트(InsightsControllerTest 10개, InsightsServiceTest 11개) 완료
- iOS: GoalProgressView, EditGoalView, WeeklyRetrospectiveView, ChangeAnalysisView 실데이터 연동 + 탐색 탭 진입점 연결 완료
- 남은 것: FCM 알림 흐름, 알림 발송 조건 정의, 스케줄링/이벤트 트리거 연결

### Phase 6: MVP 출시 준비 — 0%

## 현재 알려진 이슈

- Gradle deprecation warning 잔존 (테스트 실패와 무관)
- `xcodebuild test` 전체 실행 시 기존 테스트 타깃 구성 문제로 `ProgressPhotoViewModel.swift`의 `APIClient`/`APIError` scope 오류가 발생한다.
- `HealthCareUITests.swift`는 Swift 6 actor 경고 대응을 일부 반영했지만, 테스트 스킴 전체 안정화는 별도 정리가 필요하다.

## 권장 다음 단계

### 백엔드 우선순위

1. **AI 추정 엔드포인트 테스트** — `AiNutritionEstimationService`, `AiExerciseEstimationService` 단위 테스트 추가
2. **FCM 알림 연결** — 주간 회고 등 알림 발송 조건 정의 + 스케줄링/이벤트 트리거 구현
3. **도메인 권한 경계 통합 테스트** — 타 사용자 리소스 접근, 삭제/수정 권한 경계 검증
4. **진행 사진 후처리 파이프라인** — EXIF 제거, 썸네일 생성, 업로드 완료 검증

### iOS 우선순위

1. **AI 추정 결과 View UI** — `aiEstimateResult` 표시, "AI 추정값" 배지, disclaimer 텍스트 (AI기본법 대응)
2. 진행 사진 비교/삭제 UX 추가
3. iOS ViewModel 테스트 보강 (`HomeViewModel`, `GoalProgressViewModel`, `ProgressPhotoViewModel`)
4. 홈/마이/기록 화면 회귀 점검 및 스타일 일관성 보강

### 문서 운영 원칙

- 장기 일정과 이상적 완성 정의는 `docs/exec-plans/MVP_ROADMAP.md`
- 실제 구현 진척과 현재 우선순위는 이 문서와 `docs/exec-plans/BACKEND_TODO.md`에서 관리
- 백엔드-iOS 연동 단위 작업 흐름은 `docs/exec-plans/BACKEND_IOS_SYNC_WORKFLOW.md`를 기준으로 관리

---

**마지막 업데이트**: 2026-04-28
