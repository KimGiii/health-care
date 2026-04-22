# 프로젝트 현황 — 2026년 4월 22일

## 전체 진행률

```
Phase 0: 환경 구축              ████████████████████ 100%
Phase 1: 인증 & 사용자          ██████████████████░░  85%
Phase 2: 운동 기록             ████████████████████ 100%
Phase 3: 식단 기록             ██████████████████░░  90%
Phase 4: 신체 측정 & 진행 사진    █████████████████░░░  80%
Phase 5: 목표 & 인사이트        ██████████░░░░░░░░░░  45%
Phase 6: MVP 출시 준비          ░░░░░░░░░░░░░░░░░░░░   0%
```

## 현재 판단 기준

- 2026-04-22 기준, 최근 4개 커밋(body-measurement 완성, 목표 진행 화면, 진행 사진 워크플로, 홈·기록 실데이터 연결)을 반영했다.
- Phase 4는 iOS 신체 측정 화면 완성(가슴/허리/엉덩이/허벅지/팔 입력, LatestStatsCard 표시)과 백엔드 TDD 20개 테스트 추가로 80%로 상향.
- Phase 5는 iOS GoalProgressView 구현 완료와 GoalService 백엔드 구현으로 45%로 상향.

## 최근 변경 사항 (2026-04-20 이후)

### 백엔드

- [x] `BodyMeasurementServiceTest`: 20개 단위 테스트 추가, 전부 통과
- [x] `getMeasurementAtOrBefore()` 서비스 메서드 추가
- [x] `GET /api/v1/body-measurements/at-or-before?date=` 엔드포인트 추가
- [x] `BodyMeasurementRepository`: `findFirstByUserIdAndMeasuredAtLessThanEqualOrderByMeasuredAtDesc` 연결

### iOS

- [x] `CreateMeasurementRequest`: `chestCm`, `hipCm`, `thighCm`, `armCm` 필드 추가
- [x] `AddMeasurementViewModel`: 누락 둘레 필드 추가, `hasAnyValue` 조건 통합
- [x] `AddMeasurementView`: 가슴/허리/엉덩이/허벅지/팔 5개 입력 필드
- [x] `BodyMeasurementView LatestStatsCard`: 가슴/엉덩이/허벅지/팔 표시 추가
- [x] `APIEndpoint`: `getBodyMeasurementAtOrBefore(date:)` 엔드포인트 추가
- [x] `GoalProgressView`: 목표 진행 화면 전체 구현 (494줄)
- [x] `GoalModels`: 진행 관련 모델 추가
- [x] `GoalProgressViewModel`: 진행률 조회 뷰모델 추가
- [x] `AddDietLogView`: AI 사진 분석 진입점 포함 UI 대폭 개선

## 현재 구현 상태

### 백엔드 완료

- [x] Spring Boot 프로젝트 구성, Flyway, PostgreSQL, Redis, JWT 보안 기본 구조
- [x] 인증 API (register/login/refresh/logout)
- [x] 사용자 API (me 조회/수정/삭제)
- [x] 운동 기록 도메인 (카탈로그, 세션 CRUD)
- [x] 식단 기록 도메인 (식사 CRUD, 식품 검색, 외부 공공데이터 연동)
- [x] AI 사진 기반 식단 분석 워크플로 (OpenAI + fallback)
- [x] 신체 측정 도메인 (CRUD + atOrBefore 쿼리 + TDD 20개)
- [x] 진행 사진 업로드 MVP (presigned URL, 메타데이터 저장, signed download)
- [x] S3/LocalStack 설정, Terraform AWS 골격
- [x] 목표 도메인 기본 구현 (생성/목록/상세/수정/포기)
- [x] 서비스 단위 테스트 다수 (Auth, BodyMeasurement, ProgressPhoto, MealPhotoAnalysis, GoalService)

### iOS 완료

- [x] 인증 플로우 (회원가입/로그인/토큰 관리)
- [x] 홈 대시보드 (실데이터 연결)
- [x] 운동 기록 화면 (세션 기록, 히스토리)
- [x] 식단 기록 화면 (식품 검색, AI 사진 진입점, 실데이터 연결)
- [x] 신체 측정 화면 (체중 + 5개 둘레 입력, LatestStatsCard)
- [x] 목표 설정 화면 (GoalSettingView)
- [x] 목표 진행 화면 (GoalProgressView 완전 구현)

### 구현은 되었지만 보완이 필요한 항목

- [~] 목표 진행률 API: iOS 화면은 있으나 백엔드 `GET /api/v1/goals/{id}/progress` 미완성
- [~] 신체 측정: EXIF 제거, 썸네일 생성, 업로드 완료 검증 미구현
- [~] 컨트롤러/보안 통합 테스트: 서비스 단위 테스트 위주이며 MockMvc 수준 미비

### 아직 미구현 또는 미완성

- [ ] `GET /api/v1/goals/{id}/progress` — 체크포인트 기반 진행률 계산
- [ ] FCM 기반 인사이트/알림 흐름
- [ ] 컨트롤러/보안 중심 통합 테스트
- [ ] E2E 시나리오 및 출시 준비

## Phase별 상세 상태

### Phase 0: 환경 구축 — 완료

### Phase 1: 인증 & 사용자 — 85%

- 기능 구현 완료, 컨트롤러 수준 통합 테스트 미비

### Phase 2: 운동 기록 — 완료

### Phase 3: 식단 기록 — 90%

- AI 사진 분석 워크플로 포함 구현 완료
- 테스트 안정화 마무리 및 외부 API 에러 처리 보강 필요

### Phase 4: 신체 측정 & 진행 사진 — 80%

- 백엔드: 측정 CRUD + atOrBefore + 20개 TDD 완료, 진행 사진 presigned URL MVP 완료
- iOS: 모든 둘레 입력 필드 + LatestStatsCard 완성
- 남은 것: EXIF 제거, 썸네일 생성, 업로드 후처리

### Phase 5: 목표 & 인사이트 — 45%

- 백엔드: 목표 CRUD + GoalService 구현
- iOS: GoalProgressView, GoalProgressViewModel 구현 완료
- 남은 것: 백엔드 목표 진행률 API(체크포인트 연산), 인사이트 엔진, FCM

### Phase 6: MVP 출시 준비 — 0%

## 현재 알려진 이슈

- `./gradlew test` 전체 통과 확인 (2026-04-21 기준)
- Gradle deprecation warning 잔존 (테스트 실패와 무관)

## 권장 다음 단계

### 백엔드 우선순위

1. **목표 진행률 API 완성** — `GET /api/v1/goals/{id}/progress` (체크포인트 연산)
2. **컨트롤러/보안 통합 테스트** — AuthController, UserController MockMvc 기반
3. **API 설계 문서와 실제 경로 정합성** — `/api/v1/measurements` vs `/api/v1/body-measurements`

### iOS 우선순위

1. GoalProgressView → 백엔드 진행률 API 연동 (백엔드 완성 후)
2. 신체 측정 히스토리 그래프 (Charts 프레임워크)

### 문서 운영 원칙

- 장기 일정과 이상적 완성 정의는 `docs/exec-plans/MVP_ROADMAP.md`
- 실제 구현 진척과 현재 우선순위는 이 문서와 `docs/exec-plans/BACKEND_TODO.md`에서 관리

---

**마지막 업데이트**: 2026-04-22
