# iOS TODO — 2026년 4월 22일 기준

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
- [x] 목표 생성/목록/진행 화면 구현
- [x] endurance 목표 단위를 분 기준으로 표시하도록 정합성 반영

### ✅ 신체 측정 / 진행 사진

- [x] 신체 측정 목록/추가 흐름 구현
- [x] 최신 측정값 기반 요약 카드 표시
- [x] 진행 사진 목록/상세/업로드 화면 구현
- [x] 진행 사진 업로드 3단계 플로우 연결
- [x] presigned URL 발급 → S3 PUT → 메타데이터 등록까지 실연결

---

## 다음 순서

### 1. 진행 사진 UX 보강 및 백엔드 후처리 대응

- [ ] 진행 사진 삭제 UX 추가
- [ ] 같은 부위 기준 before/after 비교 뷰 추가
- [ ] 업로드 실패/부분 완료 상태 fallback 문구와 재시도 UX 정리
- [ ] 서버 썸네일 생성 도입 시 원본/썸네일 URL 분기 표시 반영
- [ ] 촬영 시점 선택 및 메모 입력 흐름 다듬기

완료 기준:
- 사용자가 사진 업로드 이후 비교, 재시도, 삭제까지 앱 안에서 자연스럽게 처리할 수 있다.

### 2. 신체 측정 히스토리 시각화

- [x] 체중/허리둘레 등 핵심 지표 추세 그래프 추가
- [x] 기간 필터(1주, 1개월, 3개월) 정의
- [ ] 목표 진행률 화면과 측정 히스토리 간 이동 흐름 정리
- [ ] 빈 데이터 상태와 최초 기록 유도 UX 보강

완료 기준:
- 사용자가 숫자 목록이 아니라 추세 변화로 자신의 상태를 이해할 수 있다.

### 3. iOS 테스트 보강

- [ ] `APIClient` 토큰 refresh 및 401 재시도 테스트
- [ ] 주요 ViewModel 테스트 (`HomeViewModel`, `GoalProgressViewModel`, `ProgressPhotoViewModel`, `MyPageViewModel`)
- [ ] 인증/온보딩/메인 진입 smoke UI 테스트 추가
- [ ] 핵심 작성 플로우 UI 테스트 추가 (운동 기록, 식단 기록, 신체 측정)

완료 기준:
- 현재 템플릿 수준의 테스트를 넘어서 핵심 사용자 플로우 회귀를 자동 검증할 수 있다.

## 중간 우선순위

### 4. 회고 / 변화 분석 화면 실데이터 연결 범위 정리

- [x] `WeeklyRetrospectiveView` — `GET /api/v1/insights/weekly-summary` 실데이터 연결, 주간 네비게이션 구현
- [x] `ChangeAnalysisView` — `GET /api/v1/insights/change-analysis` 실데이터 연결, 기간 선택 UI 구현
- [x] `EditGoalView` + `EditGoalViewModel` 신설 — `GoalProgressView`에서 목표 수정 진입점 추가
- [x] `InsightsModels.swift` — `WeeklySummaryResponse`, `ChangeAnalysisResponse` 모델 정의
- [x] `APIEndpoint` — `.getWeeklySummary`, `.getChangeAnalysis` case 추가
- [ ] `HistoryCalendarView` 데이터 연결 (별도 작업)

### 5. 알림 및 앱 상태 대응

- [ ] Firebase 연동 범위 점검
- [ ] 푸시 알림 수신 후 라우팅 규칙 정의
- [ ] 토큰 만료, 네트워크 오류, 빈 상태에 대한 공통 사용자 메시지 톤 정리

## 후순위 (출시 준비)

### 6. 출시 준비용 iOS 작업

- [ ] 실제 배포 환경 Base URL 및 설정값 점검
- [ ] 접근성 점검 (Dynamic Type, VoiceOver 기본 대응)
- [ ] 로딩/에러/빈 상태 화면 일관성 정리
- [ ] 앱 아이콘, 런치, 문구, 개인정보 안내 최종 점검
- [ ] TestFlight 배포 전 체크리스트 문서화

## 메모

- 현재 iOS는 화면 목업 수준이 아니라 인증, 홈, 운동, 식단, 신체 측정, 진행 사진, 목표, 마이페이지까지 실데이터 연동 범위가 넓다.
- `APIClient`는 actor 기반이며 JWT 만료 선제 체크와 refresh 재시도 흐름까지 이미 포함되어 있다.
- 반면 테스트는 `TokenStore`, `AuthState`, 온보딩 노출 정도만 있어 자동 회귀 방어선이 약하다.
- 진행 사진은 업로드 MVP는 완성됐지만, 비교/삭제/썸네일 대응 같은 실제 사용성 보강이 남아 있다.
- 신체 측정 그래프는 백엔드 `range`, `at-or-before` API를 사용해 기간/지표별 추세를 확인할 수 있는 상태다.
- `MainTabView` 기준 메인 정보 구조는 대시보드, 다이어리, 탐색, 프로필 탭으로 구성되어 있어 이후 회고/변화 분석 정보 배치 기준점으로 활용 가능하다.
