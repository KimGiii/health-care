# 프로젝트 현황 — 2026년 4월 22일

## 전체 진행률

```
Phase 0: 환경 구축              ████████████████████ 100%
Phase 1: 인증 & 사용자          ████████████████████ 100%
Phase 2: 운동 기록             ████████████████████ 100%
Phase 3: 식단 기록             ███████████████████░  92%
Phase 4: 신체 측정 & 진행 사진    ███████████████████░  94%
Phase 5: 목표 & 인사이트        ████████████░░░░░░░░  58%
Phase 6: MVP 출시 준비          ░░░░░░░░░░░░░░░░░░░░   0%
```

## 현재 판단 기준

- 2026-04-22 기준, 인증/사용자 컨트롤러 테스트, JWT 보안 체인 통합 테스트, 식단 검색 품질 보정, 진행 사진 iOS 화면, 신체 측정 추세 그래프 연동, 목표 단위 정규화, 홈·기록·마이 화면 개편을 반영했다.
- Phase 1은 `AuthControllerTest`, `UserControllerTest`, `JwtSecurityIntegrationTest` 추가와 보안 체인 `401 JSON` 응답 정리까지 반영해 완료 처리했다.
- Phase 4는 iOS 진행 사진 업로드/목록/상세 흐름과 경로 정합성 반영으로 90%까지 상향했다.
- Phase 5는 endurance 목표 단위 분 정규화, 홈 활성 목표 진행률 연결, 목표 설정 UX 보정으로 58%까지 상향했다.

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
- [x] 신체 측정 도메인 (CRUD + atOrBefore 쿼리 + TDD 20개)
- [x] 진행 사진 업로드 MVP (presigned URL, 메타데이터 저장, signed download)
- [x] S3/LocalStack 설정, Terraform AWS 골격
- [x] 목표 도메인 기본 구현 (생성/목록/상세/수정/포기, endurance 단위 minutes 정규화)
- [x] 서비스 단위 테스트 다수 (Auth, BodyMeasurement, ProgressPhoto, MealPhotoAnalysis, GoalService) + 주요 컨트롤러 단위 테스트

### iOS 완료

- [x] 인증 플로우 (회원가입/로그인/토큰 관리)
- [x] 홈 대시보드 (실데이터 연결 + 활성 목표 진행률 반영)
- [x] 운동 기록 화면 (세션 기록, 히스토리)
- [x] 식단 기록 화면 (식품 검색, AI 사진 진입점, 실데이터 연결)
- [x] 신체 측정 화면 (체중 + 5개 둘레 입력, LatestStatsCard, 기간/지표별 추세 그래프)
- [x] 진행 사진 화면 (목록/상세/업로드)
- [x] 목표 설정 화면 (GoalSettingView, 단위/주간 변화량 보정)
- [x] 목표 진행 화면 (GoalProgressView 완전 구현)
- [x] 마이페이지 화면 (프로필 조회/수정/계정 삭제)

### 구현은 되었지만 보완이 필요한 항목

- [~] 신체 측정: EXIF 제거, 썸네일 생성, 업로드 완료 검증 미구현
- [~] 컨트롤러/보안 테스트: Auth/User/Goal 단위 테스트와 JWT 보안 체인 통합 테스트는 있으나 도메인별 권한 경계 검증은 더 필요
- [~] iOS 진행 사진: 촬영 시점 선택, 삭제/비교 UX, 썸네일 상태 fallback 개선 필요

### 아직 미구현 또는 미완성

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

### Phase 3: 식단 기록 — 92%

- AI 사진 분석 워크플로 포함 구현 완료
- 검색 품질 보정(공백 무시, prefix 우선 정렬) 반영
- 외부 API 장애 대응 및 회귀 테스트 보강 필요

### Phase 4: 신체 측정 & 진행 사진 — 90%

- 백엔드: 측정 CRUD + atOrBefore + 진행 사진 presigned URL MVP + 경로 정합성 완료
- iOS: 모든 둘레 입력 필드 + LatestStatsCard + 진행 사진 목록/상세/업로드 완료
- 남은 것: EXIF 제거, 썸네일 생성, 업로드 후처리, 사진 비교 UX

### Phase 5: 목표 & 인사이트 — 58%

- 백엔드: 목표 CRUD + 진행률 API + endurance minutes 정규화 완료
- iOS: GoalProgressView, 홈 활성 목표 진행률 연동, 목표 단위/주간 변화량 UX 보정 완료
- 남은 것: 인사이트 엔진, FCM, 목표 관련 추가 리포트 UX

### Phase 6: MVP 출시 준비 — 0%

## 현재 알려진 이슈

- `./gradlew test` 재확인 필요 (이번 변경 기준으로 재실행 예정)
- Gradle deprecation warning 잔존 (테스트 실패와 무관)

## 권장 다음 단계

### 백엔드 우선순위

1. **도메인 권한 경계 통합 테스트** — 타 사용자 리소스 접근, 삭제/수정 권한 경계 검증
2. **진행 사진 후처리 파이프라인** — EXIF 제거, 썸네일 생성, 업로드 완료 검증
3. **운동 카탈로그 보강 전략 구체화** — 외부 레퍼런스 기반 시드/관리자 플로우 설계

### iOS 우선순위

1. 진행 사진 비교/삭제 UX 추가
2. 신체 측정 히스토리 그래프 (Charts 프레임워크)
3. 홈/마이/기록 화면 회귀 점검 및 스타일 일관성 보강

### 문서 운영 원칙

- 장기 일정과 이상적 완성 정의는 `docs/exec-plans/MVP_ROADMAP.md`
- 실제 구현 진척과 현재 우선순위는 이 문서와 `docs/exec-plans/BACKEND_TODO.md`에서 관리
- 백엔드-iOS 연동 단위 작업 흐름은 `docs/exec-plans/BACKEND_IOS_SYNC_WORKFLOW.md`를 기준으로 관리

---

**마지막 업데이트**: 2026-04-22
