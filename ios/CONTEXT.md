# iOS Domain Glossary

Terms used in this codebase. When naming modules, tests, issues, or PR descriptions — use these terms. Don't drift to synonyms listed under "avoid."

---

## 식단 기록하기 (Diet Log Entry)

**Definition:** 사용자가 섭취한 음식을 기록하는 행동 전체. 음식을 식별하는 경로(검색, AI 추정, 직접 등록, 사진 분석)와 저장까지의 흐름을 포함한다.

**Avoid:** "식품 추가", "음식 입력", "diet entry creation" (너무 좁거나 구현 냄새가 남)

**Where it lives:** `Features/Record/Diet/`

---

## 식단 초안 (Diet Log Draft)

**Definition:** 저장 전 사용자가 구성 중인 식단 기록의 상태. 수동 입력과 사진 분석 두 가지 기원을 가지며, 기원에 따라 저장 API가 달라진다.

**Avoid:** "임시 저장", "pending entries"

**Constraint:** 한 초안 안에서 수동 항목과 사진 분석 항목의 혼합은 현재 지원하지 않는다.

---

## 식품 항목 (Food Entry)

**Definition:** 식단 초안을 구성하는 개별 음식 하나. 식품 정보(`FoodCatalogItem`)와 섭취량(g)으로 구성된다.

**Avoid:** "food item", "diet item"

---

## 정량 지표 갱신 규칙 (Quantified Progress)

**Definition:** Gainsy의 외부 테스터 수, 테스트 사용자 수, App Store 심사 대응 수, iOS 출시 상태, AI 음식 분석 사용자 검증 결과처럼 숫자로 표현하는 프로젝트 변화 지표. 기준 문서는 `docs/product-specs/GAINSY_QUANTIFIED_PROGRESS.md`이다.

**iOS update triggers:** TestFlight 외부 테스터 모집/완료, App Store 심사 리젝 대응, smoke test 완료 인원 확정, 사용자가 체감하는 AI 음식 분석 정확도 검증 결과 추가, 출시 버전별 주요 품질 수치 변경.

**Rule:** iOS 작업이 테스터·사용자·심사·AI 검증 수치를 바꾸면 릴리즈 노트나 실행 계획만 갱신하지 말고 정량 지표 문서도 함께 갱신한다. 운영 데이터가 없으면 추정치를 확정 수치처럼 쓰지 않는다.
