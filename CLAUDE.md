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

## 기능 개발 워크플로우

기능 개발을 시작하기 **전에** 반드시 아래 순서를 따른다:

1. **GitHub Issue 생성** — `gh issue create --repo KimGiii/Gainsy` 로 이슈를 만든다.
2. **기존 브랜치 확인** — 이슈에 대응하는 브랜치가 이미 있는지 확인한다.
   ```bash
   git branch -a | grep issue-<번호>
   ```
   존재하면 해당 브랜치를 체크아웃하고, 없으면 새로 생성한다.
3. **브랜치 생성 및 연결** — 작업 성격에 따라 접두어를 구분한다.

   | 작업 유형 | 브랜치 접두어 | 예시 |
   |-----------|--------------|------|
   | 기능 개발 | `feat/` | `feat/issue-42-allergen-filter` |
   | 검수·테스트 | `qa/` | `qa/issue-55-diet-recommendation` |
   | 오류 수정 | `fix/` | `fix/issue-61-null-pointer` |

   ```bash
   git checkout -b <접두어>/issue-<번호>-<짧은-설명>
   ```

4. **작업 후 커밋·푸시** — 커밋 메시지 본문 또는 푸터에 `Closes #<번호>` 기재.
5. **PR 생성** — base 브랜치(`dev`)로 PR 생성 시 이슈 번호 연결 확인.

> 이슈 없이 새 브랜치를 만들거나 `dev`에 직접 커밋하지 않는다.

## 응답 언어

**항상 한국어**로 답변한다.

## Agent skills

### Issue tracker

GitHub Issues (`KimGiii/Gainsy`)에서 이슈를 관리합니다. `gh` CLI 사용. See `docs/agents/issue-tracker.md`.

### Triage labels

canonical 라벨 이름을 그대로 사용 (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`). See `docs/agents/triage-labels.md`.

### Domain docs

멀티 컨텍스트 — 루트의 `CONTEXT-MAP.md`, `backend/CONTEXT.md`, `ios/CONTEXT.md`, 전역/컨텍스트별 ADR을 읽습니다. 누락된 기본 문서는 생성해서 유지합니다. See `docs/agents/domain.md`.

### Architecture reviews

`/improve-codebase-architecture` 스킬 실행 시 생성하는 HTML 리포트는 `/tmp`에 먼저 열되, 반드시 `docs/architecture-reviews/<phase-or-topic>.html` 경로에도 복사해 영구 저장한다. 파일명 규칙: `phase<N>-<topic-kebab>.html` (예: `phase4-recommendation-curation.html`). 새 파일 저장 후 git add 대상에 포함시킨다.
