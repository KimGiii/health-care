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

## Consumer Rules

- Read the context docs before naming domain concepts in issues, tests, code, or refactor plans.
- Use the glossary's canonical terms. If a term is missing but clear from code or product docs, add it to the relevant `CONTEXT.md`.
- If an ADR is missing for a real architecture decision, create a new ADR instead of leaving the decision only in chat.
