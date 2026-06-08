# health-care 프로젝트 지침

## 기술 스택

- **백엔드**: Spring Boot 3 (Java 21), JPA/Hibernate, Flyway, PostgreSQL, Redis, JWT
- **iOS**: Swift 5.9, SwiftUI, Combine
- **인프라**: Docker Compose (로컬), AWS S3/LocalStack, Terraform

## 빌드 명령

```bash
# 백엔드 테스트
cd backend && ./gradlew test

# 백엔드 빌드
cd backend && ./gradlew build

# Docker 서비스 시작 (PostgreSQL, Redis, LocalStack)
docker compose up -d
```

## 적용 규칙

이 프로젝트는 다음 규칙만 사용한다. 나머지(web, zh, perl, php, rust, cpp, golang 등)는 무시:

- `rules/common/` — 공통 원칙
- `rules/java/` — Spring Boot 패턴 (해당 시)
- `rules/swift/` — SwiftUI 패턴 (해당 시)

## 주요 컨벤션

- 컨트롤러 테스트: `MockMvc standaloneSetup` + `.setCustomArgumentResolvers(new CurrentUserIdArgumentResolver())` + `SecurityTestSupport.authenticate(USER_ID)` / `clear()` (Spring Security 필터 제외)
- 인증 사용자 ID 주입: `@CurrentUserId Long userId` 파라미터 사용 (컨트롤러에서 JWT 직접 파싱 금지 — `JwtAuthenticationFilter`가 SecurityContext에 세팅, 리졸버가 추출)
- Jackson 날짜: `write-dates-as-timestamps: false` 설정으로 ISO-8601 직렬화
- 테스트 커버리지 목표: 80% 이상

## 패키지 구조

```
backend/src/main/java/com/healthcare/
├── common/          # 공통 (예외, 응답 래퍼, 보안)
└── domain/
    ├── auth/
    ├── user/
    ├── exercise/
    ├── diet/
    ├── bodymeasurement/
    ├── progressphoto/
    └── goals/

ios/HealthCare/
├── Core/            # 네트워크, 인증
├── DesignSystem/    # 공통 컴포넌트
└── Features/        # 기능별 화면
```

## 응답 언어

**항상 한국어**로 답변한다.

## Agent skills

### Issue tracker

GitHub Issues (`KimGiii/Gainsy`)에서 이슈를 관리합니다. `gh` CLI 사용. See `docs/agents/issue-tracker.md`.

### Triage labels

canonical 라벨 이름을 그대로 사용 (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`). See `docs/agents/triage-labels.md`.

### Domain docs

멀티 컨텍스트 — 루트의 `CONTEXT-MAP.md`와 `backend/`, `ios/` 별 `CONTEXT.md` + `docs/adr/`. See `docs/agents/domain.md`.
