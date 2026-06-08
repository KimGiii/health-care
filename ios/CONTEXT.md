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
