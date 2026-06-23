# Architecture Decision Records

전역 ADR 인덱스입니다. 백엔드와 iOS를 함께 건드리거나 제품 동작, 데이터 계약, 배포/인프라에 영향을 주는 결정은 이 디렉터리에 기록합니다.

## Current ADRs

- [0001. 식단 추천 알러젠 회피 모델과 Strict 모드](0001-diet-allergen-strict-mode.md) — Superseded by ADR-0005
- [0002. 검증된 후보로 목표별 남은 영양량을 제약 최적화한다](0002-goal-aware-nutrition-optimization.md)
- [0003. 식단 추천 제약 최적화를 순수 Java 결정적 탐색으로 구현한다](0003-constraint-recommendation-engine.md)
- [0004. 완성요리(복합 식품)를 추천 후보로 도입한다](0004-composite-dish-recommendation.md) — Proposed
- [0005. 알러지 추천은 버전된 근거와 fail-closed 완결 프로필로 판정한다](0005-versioned-allergen-evidence-fail-closed.md)

## When To Add One

- API 계약이나 데이터 모델을 바꾸는 결정
- 백엔드와 iOS 양쪽에 영향을 주는 제품 동작 결정
- 인증, 보안, 인프라, 배포 방식에 관한 지속적인 결정
- 기존 설계 문서와 다른 방향을 선택하는 결정

## Format

새 ADR은 `0001-short-title.md` 형식으로 추가하고, 최소한 다음 섹션을 포함합니다.

- Status
- Context
- Decision
- Consequences
