# 백엔드 TODO — 2026년 4월 22일 기준

## 목적

- 현재 백엔드 코드를 기준으로, MVP 완성에 직접 연결되는 작업만 우선순위대로 정리한다.
- 장기 아이디어보다 "바로 착수 가능한 일" 중심으로 관리한다.

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

## 다음 순서

### 2. 컨트롤러/보안 통합 테스트 보강

- [ ] `AuthController` MockMvc 통합 테스트
- [ ] `UserController` MockMvc 통합 테스트
- [ ] JWT 필터 테스트 (인증 없는 요청, 만료 토큰)
- [ ] 주요 도메인 컨트롤러 보안 테스트 (타 사용자 접근 시나리오)

완료 기준:
- 핵심 인증/인가 흐름이 서비스 단위 테스트 외 컨트롤러 계층에서도 검증된다.

### 3. API 설계 문서와 실제 경로 정합성

- [ ] `API_DESIGN.md`와 실제 구현 경로 차이 정리
  - `/api/v1/measurements` vs `/api/v1/body-measurements` (현재 컨트롤러 기준 후자)
  - 진행 사진 경로 확인
- [ ] 문서 또는 컨트롤러 경로 중 하나로 통일
- [ ] iOS API 클라이언트 계약 일치 여부 점검

완료 기준:
- 문서, 서버, iOS 클라이언트가 같은 경로와 응답 계약을 사용한다.

## 중간 우선순위

### 4. 신체 측정 후처리 (EXIF, 썸네일)

- [ ] EXIF 제거 서버 측 구현 (`ExifStripper`)
- [ ] 썸네일 비동기 생성 (150px, 400px, 800px)
- [ ] 업로드 완료 검증 로직 (`progress_photos.upload_completed_at` 활용)

### 5. 인사이트/알림 기반 작업

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

- 백엔드 신체 측정은 "CRUD + atOrBefore + TDD 20개 완료" 상태다.
- 목표 도메인은 "CRUD + 진행률 API + GoalControllerTest 11개 + GoalServiceTest 23개 완료" 상태다.
- iOS GoalProgressView 이미 구현 완료 → 백엔드 진행률 API 완성으로 연동 가능 상태.
- Jackson `write-dates-as-timestamps: false` 설정으로 전 도메인 LocalDate ISO-8601 직렬화 보장.
