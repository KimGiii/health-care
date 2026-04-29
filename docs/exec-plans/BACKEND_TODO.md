# 백엔드 TODO — 2026년 4월 28일 기준

## 목적

- 현재 백엔드 코드를 기준으로, MVP 완성에 직접 연결되는 작업만 우선순위대로 정리한다.
- 장기 아이디어보다 "바로 착수 가능한 일" 중심으로 관리한다.
- 백엔드 단독 완료보다 `docs/exec-plans/BACKEND_IOS_SYNC_WORKFLOW.md`의 연동 슬라이스 기준을 우선한다.

## 완료된 항목

### ✅ 테스트 복구 및 안정화

- [x] `DietLogServiceTest.listDietLogs_returnsPaginatedResults` 수정
- [x] `./gradlew test` 전체 통과 확인

### ✅ 회원가입 스펙과 실제 구현 맞추기

- [x] `RegisterRequest`에 초기 프로필 필드 반영 (`sex`, `dateOfBirth`, `heightCm`, `weightKg`, `activityLevel`, `locale`, `timezone`)
- [x] `AuthService.register()` 저장 로직 확장
- [x] `goalType`은 목표 도메인 책임으로 유지

### ✅ 진행 사진 업로드 API 구현

- [x] presigned URL 방식으로 업로드 → 메타데이터 저장 → signed download 조회
- [x] S3/LocalStack 연동 설정
- [x] Terraform AWS 골격 (`infra/terraform/aws`)
- [x] `ProgressPhotoServiceTest` 추가

### ✅ 신체 측정 TDD 및 atOrBefore 쿼리

- [x] `BodyMeasurementServiceTest`: 20개 단위 테스트 (Mockito)
- [x] `getMeasurementAtOrBefore()` 서비스 메서드
- [x] `GET /api/v1/body-measurements/at-or-before?date=` 엔드포인트

### ✅ 목표 진행률 API 완성

- [x] `GET /api/v1/goals/{id}/progress` — 신체 측정 기반 진행률 계산 구현
- [x] `GoalCheckpointRepository` 연결 — 조회 시 자동 upsert
- [x] 목표 타입별 분기 (체중 감량 / 근육 증가 / 체지방 감소 / 지구력)
- [x] `GoalProgressResponse` 완성 (percentComplete, trackingStatus, trackingColor, checkpoints, projectedCompletionDate)
- [x] Jackson `write-dates-as-timestamps: false` 설정 — iOS `LocalDate` 디코딩 버그 수정
- [x] `GoalController` Bearer 토큰 검증 개선 (`required = false` + resolveUserId null 체크)
- [x] `GoalControllerTest` 11개 단위 테스트 추가 (MockMvc standaloneSetup)
- [x] `GoalServiceTest` 2개 추가 (총 23개) — SLIGHTLY_BEHIND, null값 케이스

---

### ✅ AI 기반 검색 폴백 (음식 / 운동)

- [x] `AiNutritionEstimationService` — 한국어 음식명 → 100g 기준 영양성분 AI 추정
  - OpenAI Responses API (`/v1/responses`) 재사용, `@ConditionalOnExpression` 비활성화 안전
  - `POST /api/v1/diet/ai-estimate` 엔드포인트 신규 추가
  - 응답에 `isAiEstimated: true` + `disclaimer` 포함 (AI기본법 대응)
- [x] `AiExerciseEstimationService` — 한국어 운동명 → muscleGroup, exerciseType, MET값 AI 추정
  - `POST /api/v1/exercise/ai-estimate` 엔드포인트 신규 추가
- [x] `V11__exercise_catalog_seed.sql` — 110개 운동 시드 데이터
  - 근육군 14종 전체 커버, 한/영 이름, Compendium of Physical Activities 기준 MET값

---

## 다음 순서

### 2. 컨트롤러/보안 통합 테스트 보강

- [x] `AuthController` MockMvc 단위 테스트
- [x] `UserController` MockMvc 단위 테스트
- [x] JWT 필터/보안 체인 통합 테스트 (`JwtSecurityIntegrationTest`)
- [x] 보안 실패 응답 JSON 통일 (`RestAuthenticationEntryPoint`, `RestAccessDeniedHandler`)
- [x] 주요 도메인 컨트롤러 보안 테스트 (타 사용자 접근 시나리오)
  - `ExerciseAuthorizationBoundaryTest` — 운동 세션 GET/DELETE 크로스유저 401
  - `DietLogAuthorizationBoundaryTest` — 식단 기록 GET/DELETE 크로스유저 401
  - `BodyMeasurementAuthorizationBoundaryTest` — 신체 측정 GET/PATCH/DELETE 크로스유저 401
  - `GoalAuthorizationBoundaryTest` — 목표 GET/진행률/포기 크로스유저 401, 잘못된 토큰 형식 401

완료 기준:
- 핵심 인증/인가 흐름이 서비스 단위 테스트 외 보안 체인까지 포함해 검증된다.
- 도메인별 권한 경계 시나리오 검증 완료.

### 3. API 설계 문서와 실제 경로 정합성

- [x] `ProgressPhotoController` 경로를 `/api/v1/body-measurements/photos`로 통일
- [x] iOS `APIEndpoint`에서 진행 사진 관련 계약 반영
- [x] `DB_SCHEMA.md`의 endurance 목표 단위를 실제 구현 기준(`minutes`)으로 정리

완료 기준:
- 문서, 서버, iOS 클라이언트가 같은 경로와 응답 계약을 사용한다.

## 중간 우선순위

### 3.5. AI 추정 서비스 단위 테스트

- [ ] `AiNutritionEstimationServiceTest` — OpenAI 응답 파싱, 카테고리 매핑, 예외 처리 케이스
- [ ] `AiExerciseEstimationServiceTest` — muscleGroup/exerciseType 파싱, MET 기본값 폴백

완료 기준:
- AI 응답 파싱 실패 시 기본값 반환, 카테고리 매핑 정확성, `@ConditionalOnExpression` 비활성화 케이스가 테스트로 검증된다.

### 4. 신체 측정 후처리 (EXIF, 썸네일)

- [ ] EXIF 제거 서버 측 구현 (`ExifStripper`)
- [ ] 썸네일 비동기 생성 (150px, 400px, 800px)
- [ ] 업로드 완료 검증 로직 (`progress_photos.upload_completed_at` 활용)

### 5. 인사이트/알림 기반 작업

- [x] `GET /api/v1/insights/weekly-summary` — 주간 요약 API 구현 (운동/식단/신체/목표 집계)
- [x] `GET /api/v1/insights/change-analysis` — 기간별 신체 변화 분석 API 구현
- [x] ENDURANCE 목표 진행률 — 운동 세션 합산 기반 계산 (`ExerciseSessionRepository.sumDurationMinutesByUserIdAndDateRange`)
- [x] GoalSummary.percentComplete 채우기 — 목록 API에서 읽기 전용 경량 계산 (`calculatePercentCompleteReadOnly`)
- [x] `InsightsControllerTest` 10개 추가 — weekOffset, 빈 데이터, 401, 날짜 유효성, from>to 검증
- [x] `InsightsServiceTest` 11개 추가 — 델타 반올림, ENDURANCE 스킵, WEIGHT_LOSS 달성률 등
- [x] `ProgressPhotoResponse.isBaseline` `@JsonProperty` 직렬화 버그 수정
- [ ] FCM Admin SDK 실제 사용 흐름 연결
- [ ] 알림 발송 조건 정의 (주간 회고 등)
- [ ] 스케줄링 또는 이벤트 트리거 방식 결정

## 후순위 (출시 준비)

### 6. 출시 준비용 백엔드 작업

- [ ] 운영 프로필 점검 (`application-prod.yml`)
- [ ] 환경 변수 체크리스트 정리
- [ ] 헬스체크/로그/모니터링 기준 정리
- [ ] E2E 테스트 시나리오 정리

## 메모

- AI 추정 서비스(`AiNutritionEstimationService`, `AiExerciseEstimationService`)는 기존 `OpenAiMealAnalysisProvider`와 동일한 OpenAI Responses API 패턴을 재사용한다. `OPENAI_API_KEY`는 `backend/.env`에 추가하면 된다.
- 운동 카탈로그는 V11 마이그레이션으로 110개 시드 추가. 이후 검색 결과 없으면 AI 추정 엔드포인트 폴백 사용.
- AI 추정 응답의 `isAiEstimated: true` + `disclaimer` 필드는 AI기본법(2026) 대응이다 — 클라이언트 View에서 반드시 표시해야 한다.
- Insights 도메인은 "InsightsController/Service 구현 + InsightsControllerTest 10개 + InsightsServiceTest 11개 완료" 상태다.
- 백엔드 신체 측정은 "CRUD + atOrBefore + TDD 20개 완료" 상태다.
- 진행 사진은 "presigned URL 발급 + 메타데이터 등록 + signed download 조회 + 경로 정합성 반영" 상태다.
- 목표 도메인은 "CRUD + 진행률 API + GoalControllerTest 11개 + GoalServiceTest 23개 + endurance minutes 정규화" 상태다.
- 식단 검색은 공백 무시 검색과 prefix 우선 정렬까지 반영된 상태다.
- Jackson `write-dates-as-timestamps: false` 설정으로 전 도메인 LocalDate ISO-8601 직렬화 보장.
