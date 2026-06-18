# Context Map

이 저장소의 에이전트용 도메인 문서 지도입니다. 작업을 시작하기 전에 관련 컨텍스트의 `CONTEXT.md`와 ADR 인덱스를 읽습니다.

## Contexts

| Context | Domain doc | ADR index | When to read |
| --- | --- | --- | --- |
| Backend | `backend/CONTEXT.md` | `backend/docs/adr/README.md` | Spring Boot API, persistence, auth, notification, AI integration, storage |
| iOS | `ios/CONTEXT.md` | `ios/docs/adr/README.md` | SwiftUI app, navigation, API client, auth state, feature UI |

## Shared Decisions

System-wide ADRs live in `docs/adr/README.md` and sibling ADR files under `docs/adr/`.

Read shared ADRs when a change crosses backend/iOS boundaries, affects product behavior, changes data contracts, or touches infrastructure.

## Shared Metrics

Gainsy의 구현 규모, 운영 경험, 품질 개선, 테스터/사용자 수, AI 음식 분석 정확도처럼 숫자로 표현하는 지표는 `docs/product-specs/GAINSY_QUANTIFIED_PROGRESS.md`를 기준 문서로 유지합니다.

API mapping, Flyway table, 운영 장애, 심사 리젝, TestFlight/운영 사용자, AI benchmark 결과가 바뀌는 작업을 할 때는 코드나 실행 계획만 수정하지 말고 이 정량 지표 문서도 함께 갱신합니다.

## Consumer Rules

- Read the context docs before naming domain concepts in issues, tests, code, or refactor plans.
- Use the glossary's canonical terms. If a term is missing but clear from code or product docs, add it to the relevant `CONTEXT.md`.
- If an ADR is missing for a real architecture decision, create a new ADR instead of leaving the decision only in chat.
- If a change affects measurable project progress, update `docs/product-specs/GAINSY_QUANTIFIED_PROGRESS.md` with the metric value, basis, and source.

## Feature Development Workflow

Before starting any feature development:

1. Create a GitHub Issue (`gh issue create --repo KimGiii/Gainsy`).
2. Check if a branch for the issue already exists: `git branch -a | grep issue-<number>`. Check out existing branch if found; create a new one if not.
3. Branch naming by work type:
   - Feature development → `feat/issue-<number>-<short-description>`
   - QA / testing → `qa/issue-<number>-<short-description>`
   - Bug fix → `fix/issue-<number>-<short-description>`
4. Reference the issue in commit messages (`Closes #<number>`) and the PR description.
5. Never commit directly to `dev` without a corresponding issue and branch.
