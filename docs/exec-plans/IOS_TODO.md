# iOS TODO — 2026년 5월 20일 기준

> 최종 개정: 2026-06-22
> 알러지·기피 UI 분리와 verified-only 계약은 설계가 확정됐으며, 현재는 구현 대기 상태다.

## 목적

- 현재 iOS 앱 코드를 기준으로, MVP 완성에 직접 연결되는 작업만 우선순위대로 정리한다.
- 이미 화면 골격만 있는지, 실데이터 연동까지 끝났는지 구분해서 실제 착수 순서를 명확히 한다.
- 플랫폼 간 작업 순서와 완료 기준은 `docs/exec-plans/BACKEND_IOS_SYNC_WORKFLOW.md`를 함께 따른다.

## 완료된 항목

### ✅ 앱 기본 구조 및 공통 계층

- [x] SwiftUI 앱 진입 구조 구성 (`HealthCareApp`, `RootView`, `MainTabView`)
- [x] `AppContainer` 기반 의존성 주입 구조 구성
- [x] `TokenStore` + `AuthState` 기반 인증 상태 관리
- [x] `APIClient` actor 기반 네트워크 계층 구현
- [x] access token 만료 선제 체크 + refresh token 재발급 + 재시도 흐름 구현
- [x] `Debug.xcconfig`, `Release.xcconfig` 추가
- [x] 디자인 시스템 기본 컴포넌트와 컬러/타이포그래피 정리

### ✅ 인증 / 사용자 / 프로필

- [x] 회원가입, 로그인 화면 및 ViewModel 구현
- [x] 프로필 설정 화면 구현
- [x] 마이페이지 프로필 조회/수정/계정 삭제 실데이터 연동
- [x] 인증 상태에 따라 온보딩 → 프로필 설정 → 메인 탭으로 전환되는 흐름 구성

### ✅ 홈 / 기록 / 목표 핵심 흐름

- [x] 홈 대시보드에서 오늘 식단, 최근 운동, 활성 목표 조회
- [x] 활성 목표 진행률 API 연동 (`GET /api/v1/goals/{id}/progress`)
- [x] 운동 기록 화면, 운동 세션 추가/상세 흐름 구현
- [x] 식단 기록 화면, 식단 상세/추가 흐름 구현
- [x] 외부 식품 검색 및 AI 사진 분석 진입점 반영
- [x] 식단 검색 입력 500ms 디바운스 + 이전 요청 취소 + 검색어 삭제 시 즉시 초기화 반영
- [x] 목표 생성/목록/진행 화면 구현
- [x] endurance 목표 단위를 분 기준으로 표시하도록 정합성 반영

### ✅ AI 검색 폴백 연동

- [x] `APIEndpoint` — `.aiEstimateFood`, `.aiEstimateExercise`, `.createCustomFood`, `.createCustomExercise` 4개 case 추가
- [x] `DietModels.swift` — `AiNutritionEstimateResponse`, `AiNutritionEstimateRequest` 모델 추가
- [x] `ExerciseModels.swift` — `AiExerciseEstimateResponse`, `AiExerciseEstimateRequest` 모델 추가
- [x] `AddDietLogViewModel` — `estimateWithAI()`, `addAiEstimatedFood()` + `aiEstimateResult`/`isAiEstimating` 상태 추가
- [x] `AddExerciseSessionViewModel` — `estimateWithAI()`, `addAiEstimatedExercise()` + `aiEstimateResult`/`isAiEstimating` 상태 추가

### ✅ 신체 측정 / 진행 사진

- [x] 신체 측정 목록/추가 흐름 구현
- [x] 최신 측정값 기반 요약 카드 표시
- [x] 진행 사진 목록/상세/업로드 화면 구현
- [x] 진행 사진 업로드 3단계 플로우 연결
- [x] presigned URL 발급 → S3 PUT → 메타데이터 등록까지 실연결

---

## 다음 순서

### P0. 알러지·기피 UI 분리 및 근거 표시 — 설계 확정 / 구현 대기

- [ ] 알러젠 선택 화면과 기피 식품 설정 화면을 별도 흐름으로 분리한다.
- [ ] 알러젠 화면에서 `ALLERGY/AVOID` 유형 선택과 Strict 토글을 제거하고, 서버가 활성화한 알러젠 태그만 노출한다.
- [ ] 사용자 선택이 일시 비활성화된 태그를 포함하면 설정은 보존하되 `ALLERGEN_POLICY_TEMPORARILY_UNAVAILABLE`를 별도로 안내한다.
- [ ] 추천 항목에 근거 출처·검토일을 표시하고, 안전을 단정하는 표현 대신 라벨 재확인 안내를 제공한다.
- [ ] 추천 스냅샷을 전용 멱등 API로 식단 기록으로 전환하고, 재검증 실패 사유를 화면에서 구분한다.
- [ ] `strictAllergyMode`는 과도기 호환성을 위해 응답 모델에서 일시적으로 수용하되 UI에서는 사용하지 않는다.

관련 문서:

- `docs/adr/0005-versioned-allergen-evidence-fail-closed.md`
- `docs/exec-plans/DIET_ALLERGEN_VERIFIED_ONLY_HARDENING.md`
- `docs/product-specs/DIET_RECOMMENDATION_RESTRICTIONS_PRD.md`

### 0. AI 추정 결과 View UI — 완료

- [x] 음식 검색 결과 없을 때 "AI로 추정하기" + "직접 등록하기" 버튼 표시 (`AddDietLogView`)
- [x] `aiEstimateResult` 표시 카드 — 추정 영양성분 + "AI 추정값" 배지 + disclaimer 텍스트
- [x] 운동 검색 결과 없을 때 "AI로 추정하기" 버튼 표시 (`AddExerciseSessionView`)
- [x] `aiEstimateResult` 표시 카드 — 추정 muscleGroup/exerciseType/MET + 배지 + disclaimer

### 1. 진행 사진 UX 보강 — 완료

- [x] 진행 사진 삭제 UX 추가 (그리드 context menu + 상세 화면 삭제 버튼, 확인 알림)
- [x] 같은 부위 기준 before/after 비교 뷰 추가 (`PhotoCompareView`, 좌우 분할, 날짜·체중 오버레이)
- [x] 업로드 실패/부분 완료 상태 fallback 문구와 재시도 UX 정리 (2026-05-20)
- [ ] 서버 썸네일 생성 도입 시 원본/썸네일 URL 분기 표시 반영
- [ ] 촬영 시점 선택 및 메모 입력 흐름 다듬기

### 2. 신체 측정 히스토리 시각화

- [x] 체중/허리둘레 등 핵심 지표 추세 그래프 추가
- [x] 기간 필터(1주, 1개월, 3개월) 정의
- [ ] 목표 진행률 화면과 측정 히스토리 간 이동 흐름 정리
- [ ] 빈 데이터 상태와 최초 기록 유도 UX 보강

완료 기준:
- 사용자가 숫자 목록이 아니라 추세 변화로 자신의 상태를 이해할 수 있다.

### 3. iOS 테스트 보강

- [x] `AddDietLogViewModel` 검색 디바운스/즉시 검색/검색어 삭제/느린 응답 역전 방지 단위 테스트 추가
- [x] `APIClient` 토큰 refresh 및 401 재시도 테스트
- [x] 주요 ViewModel 테스트 (`HomeViewModel`, `GoalProgressViewModel`, `ProgressPhotoViewModel`, `MyPageViewModel`)
- [x] `ProgressPhotoViewModel` 삭제·비교 모드 테스트
- [x] 인증/온보딩/메인 진입 smoke UI 테스트 추가
- [x] 핵심 작성 플로우 UI 테스트 추가 (운동 기록, 식단 기록, 신체 측정) (2026-05-20, [PR #25](https://github.com/KimGiii/Gainsy/pull/25))

완료 기준:
- 현재 템플릿 수준의 테스트를 넘어서 핵심 사용자 플로우 회귀를 자동 검증할 수 있다.

## 중간 우선순위

### 4. 회고 / 변화 분석 화면 실데이터 연결 범위 정리

- [x] `WeeklyRetrospectiveView` — `GET /api/v1/insights/weekly-summary` 실데이터 연결, 주간 네비게이션 구현
- [x] `ChangeAnalysisView` — `GET /api/v1/insights/change-analysis` 실데이터 연결, 기간 선택 UI 구현
- [x] `EditGoalView` + `EditGoalViewModel` 신설 — `GoalProgressView`에서 목표 수정 진입점 추가
- [x] `InsightsModels.swift` — `WeeklySummaryResponse`, `ChangeAnalysisResponse` 모델 정의
- [x] `APIEndpoint` — `.getWeeklySummary`, `.getChangeAnalysis` case 추가
- [x] `HistoryCalendarView` — DiaryView와 역할 중복으로 파일 삭제 (구현 불필요)
- [x] 탐색 탭(`ExploreView`) — `WeeklyRetrospectiveView`, `ChangeAnalysisView` 진입점 연결
- [x] `ProgressPhotoView` — `onChange` iOS 16 호환 시그니처 수정

### 5. 알림 및 앱 상태 대응

- [x] Firebase 연동 범위 점검 — AppDelegate FCM 토큰 수신 기존 구현 확인
- [x] 푸시 알림 수신 후 라우팅 규칙 정의 — `WEEKLY_SUMMARY` 탭 → 탐색 탭 자동 이동 (`pushNotificationTapped` 브로드캐스트)
- [x] FCM 토큰 업로드 — `FcmTokenUploader` (fcmTokenRefreshed → PATCH /api/v1/users/me)
- [ ] 토큰 만료, 네트워크 오류, 빈 상태에 대한 공통 사용자 메시지 톤 정리

## 후순위 (출시 준비)

### 6. 출시 준비용 iOS 작업

- [x] 실제 배포 환경 Base URL 점검 (`api.gainsy.site`, HTTPS 전환 완료)
- [x] 접근성 점검 — Dynamic Type 전면 적용(Typography 스케일 재구성), VoiceOver 홈 대시보드 대응
- [x] `PrivacyInfo.xcprivacy` Privacy Manifest 추가 (App Store 필수)
- [x] `Info.plist` 권한 설명 업데이트 (카메라, 사진 라이브러리 등)
- [x] 앱 이름 `Gainsy`, Bundle ID `com.kingloo.gainsy.ios`, `DEVELOPMENT_TEAM` 설정
- [x] App Icon 전체 사이즈 추가 (20pt ~ 1024pt)
- [x] 개인정보 처리방침/이용약관 URL 확정 (GitHub Pages, `https://kimgiii.github.io/Gainsy/docs/legal/…`)
- [x] 다크모드 전면 지원 (어댑티브 컬러 토큰 전체 적용, Forest 톤 일관성)
- [x] **재심사 거절 3건 코드 대응** ([PR #24](https://github.com/KimGiii/Gainsy/pull/24), dev → prod 머지 완료)
  - [x] Guideline 2.5.1 — 미사용 HealthKit 권한 키 제거(`NSHealth{Share,Update}UsageDescription`)
  - [x] Guideline 2.1 — `TrackingPermissionView` 사전 설명 화면 도입, ATT 호출을 `applicationDidBecomeActive`에서 분리
  - [x] Guideline 1.4.1 — `MedicalSourcesView`(WHO·대한비만학회·식약처·USDA·면책 고지) 추가, BMI/영양 카드/마이페이지 4곳에 진입점
- [x] **PR #24 머지 + Xcode Archive → TestFlight 업로드 + 재심사 제출 완료** (2026-05-20)
- [x] **실기기 ATT 프롬프트 화면 녹화 및 App Review Notes 첨부** 완료
- [ ] 로딩/에러/빈 상태 화면 일관성 정리 (남은 화면)
- [x] 진행 사진 업로드 실패 fallback 문구 및 재시도 UX (2026-05-20)
- [x] 핵심 플로우 UI 테스트 (운동 기록, 식단 기록, 신체 측정) ([PR #25](https://github.com/KimGiii/Gainsy/pull/25), 2026-05-20)

## 메모

- AI 검색 폴백(식단·운동 모두)은 View UI(배지·disclaimer) 포함 완성 상태다.
- 식단 검색 시트는 이제 `onChange`마다 즉시 네트워크를 치지 않고, 입력 종료 후 500ms 뒤에만 검색한다.
- 진행 사진 삭제/비교 UX는 완료됐다. context menu 길게 누르기(그리드)와 상세 화면 삭제 버튼 두 경로 제공.
- `AddDietLogViewModelTests`는 추가됐지만 현재 `HealthCareTests` 타깃이 앱 소스 일부를 잘못 포함하고 있어 전체 `xcodebuild test`는 별도 타깃 정리가 필요하다.
- `estimateWithAI()` 호출 시점: 검색 결과(`catalogResults` + `externalResults`)가 모두 비어 있을 때 버튼을 활성화하거나 자동 호출하는 UX 결정 필요.
- `addAiEstimatedFood()`는 `POST /api/v1/diet/catalog`(커스텀 식품 생성)를 호출한다. 저장 후 항목이 사용자 카탈로그에 남는다.
- 현재 iOS는 화면 목업 수준이 아니라 인증, 홈, 운동, 식단, 신체 측정, 진행 사진, 목표, 마이페이지까지 실데이터 연동 범위가 넓다.
- `APIClient`는 actor 기반이며 JWT 만료 선제 체크와 refresh 재시도 흐름까지 이미 포함되어 있다.
- 반면 테스트는 `TokenStore`, `AuthState`, 온보딩 노출 정도만 있어 자동 회귀 방어선이 약하다.
- 진행 사진은 업로드 MVP는 완성됐지만, 비교/삭제/썸네일 대응 같은 실제 사용성 보강이 남아 있다.
- 신체 측정 그래프는 백엔드 `range`, `at-or-before` API를 사용해 기간/지표별 추세를 확인할 수 있는 상태다.
- `MainTabView` 기준 메인 정보 구조는 대시보드, 다이어리, 탐색, 프로필 탭으로 구성되어 있어 이후 회고/변화 분석 정보 배치 기준점으로 활용 가능하다.
