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

## 제공량 선택 (Serving Selection)

**Definition:** 식단 기록하기에서 식품의 검증된 1회 제공량을 기준으로 0.5회·1회·1.5회·2회 같은 프리셋을 우선 선택하고, 실제 섭취량이 다르면 직접 g을 입력하는 행동. 추천 결과는 검증된 프리셋만 사용하지만 사용자의 기록은 직접 입력을 허용한다.

**Avoid:** "그램 입력", "portion picker" (프리셋 우선과 실제 섭취량 교정이라는 두 역할이 빠짐)

**Where it lives:** `Features/Record/Diet/` (후속 구현), `docs/exec-plans/DIET_RECOMMENDATION_OPTIMIZATION.md`

---

## 추천 근거 (Recommendation Rationale)

**Definition:** 추천이 남은 영양량의 어떤 부족분을 채웠는지, 목표 상·하한과 얼마나 차이 나는지, 어떤 제한 조건과 데이터 검증 근거를 적용했는지 사용자에게 설명하는 정보. raw solver 점수는 노출하지 않는다.

**Avoid:** "AI explanation", "score detail" (AI 생성 설명이나 내부 최적화 점수와 혼동)

**Where it lives:** `Features/Record/Diet/` (후속 구현)

---

## 추천 피드백 (Recommendation Feedback)

**Definition:** 사용자가 다시 추천을 요청할 때 선택적으로 남기는 원탭 사유와 추천 항목의 기록·삭제·교체 행동. 재추천 흐름을 막지 않으며 단순 미기록은 부정 선호로 해석하지 않는다.

**Avoid:** "rating", "review" (별점이나 장문 평가를 요구하는 기능처럼 보임)

**Where it lives:** `Features/Record/Diet/` (후속 구현)

---

## 정량 지표 갱신 규칙 (Quantified Progress)

**Definition:** Gainsy의 외부 테스터 수, 테스트 사용자 수, App Store 심사 대응 수, iOS 출시 상태, AI 음식 분석 사용자 검증 결과처럼 숫자로 표현하는 프로젝트 변화 지표. 기준 문서는 `docs/product-specs/GAINSY_QUANTIFIED_PROGRESS.md`이다.

**iOS update triggers:** TestFlight 외부 테스터 모집/완료, App Store 심사 리젝 대응, smoke test 완료 인원 확정, 사용자가 체감하는 AI 음식 분석 정확도 검증 결과 추가, 출시 버전별 주요 품질 수치 변경.

**Rule:** iOS 작업이 테스터·사용자·심사·AI 검증 수치를 바꾸면 릴리즈 노트나 실행 계획만 갱신하지 말고 정량 지표 문서도 함께 갱신한다. 운영 데이터가 없으면 추정치를 확정 수치처럼 쓰지 않는다.


---

## 기능 개발 워크플로우

iOS 작업 시작 전 반드시:

1. `gh issue create --repo KimGiii/Gainsy`로 이슈 생성.
2. 이슈에 대응하는 브랜치가 이미 있는지 확인: `git branch -a | grep issue-<번호>`. 있으면 체크아웃, 없으면 새로 생성.
3. 작업 성격에 따라 브랜치 접두어 구분:
   - 기능 개발 → `feat/issue-<번호>-<짧은-설명>`
   - 검수·테스트 → `qa/issue-<번호>-<짧은-설명>`
   - 오류 수정 → `fix/issue-<번호>-<짧은-설명>`
4. 커밋 메시지 또는 PR 본문에 `Closes #<번호>` 기재.
5. `dev`에 직접 커밋하지 않는다.
