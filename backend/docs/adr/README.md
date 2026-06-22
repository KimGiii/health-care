# Backend ADRs

백엔드 컨텍스트 ADR 인덱스입니다. Spring Boot API, persistence, auth, notification, AI integration, storage에 한정된 결정은 이 디렉터리에 기록합니다.

## Current ADRs

- [0001. 식단 기록 규칙을 유스케이스 모듈에 둔다](0001-diet-log-use-case-module.md)
- [0002. 식단 추천 후보 정책을 후보 풀 모듈에 둔다](0002-diet-recommendation-candidate-pool-module.md)
- [0003. 추천 큐레이션 불변 조건을 값 객체 모듈에 둔다](0003-recommendation-curation-module.md)
- [0004. 식단 추천 엔진은 후보 값 객체만 입력으로 받는다](0004-diet-recommendation-engine-candidate-interface.md)

## When To Add One

- 엔티티/트랜잭션 경계 변경
- 인증/인가 처리 방식 변경
- 외부 연동(OpenAI, FCM, S3, 공공 식품 API) 방식 변경
- 스케줄러, 캐시, 배치, 장애 처리 정책 변경
- 테스트 전략이나 모듈 경계에 관한 지속적인 결정

## Format

새 ADR은 `0001-short-title.md` 형식으로 추가하고, 최소한 다음 섹션을 포함합니다.

- Status
- Context
- Decision
- Consequences
