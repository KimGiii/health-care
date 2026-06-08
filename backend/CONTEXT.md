# Backend Domain Glossary

Gainsy 백엔드에서 사용하는 도메인 용어입니다. 모듈, 테스트, 이슈, PR 설명을 작성할 때 이 용어를 우선 사용합니다.

---

## 사용자 (User)

**Definition:** Gainsy에서 운동, 식단, 신체 변화, 목표 데이터를 소유하는 계정 주체. 인증된 요청에서는 `@CurrentUserId`로 식별된다.

**Avoid:** "member", "account owner" (코드의 `User`와 다른 개념처럼 보일 수 있음)

**Where it lives:** `backend/src/main/java/com/healthcare/domain/user/`

---

## 사용자 식별 정보 (User Identity)

**Definition:** 사용자의 로그인 경로와 외부 제공자 식별자를 나타내는 정보. 소셜 로그인과 일반 인증 흐름에서 사용자 계정을 찾거나 연결하는 기준이다.

**Avoid:** "social account" (제공자 계정 자체와 혼동)

**Where it lives:** `backend/src/main/java/com/healthcare/domain/user/entity/UserIdentity.java`

---

## 운동 세션 (Exercise Session)

**Definition:** 사용자가 특정 날짜에 수행한 운동 기록 묶음. 여러 운동 세트를 포함하며, 운동 카탈로그의 종목을 기준으로 기록된다.

**Avoid:** "workout" (앱 화면명이나 마케팅 문맥이 아니면 범위가 흐려짐)

**Where it lives:** `backend/src/main/java/com/healthcare/domain/exercise/`

---

## 운동 세트 (Exercise Set)

**Definition:** 운동 세션 안의 개별 수행 단위. 종목, 횟수, 무게, 시간 등 세부 수행 값을 담는다.

**Avoid:** "set item", "exercise row"

**Where it lives:** `backend/src/main/java/com/healthcare/domain/exercise/entity/ExerciseSet.java`

---

## 운동 카탈로그 (Exercise Catalog)

**Definition:** 사용자가 운동 세션에 추가할 수 있는 운동 종목 목록. 기본 종목과 사용자 커스텀 운동을 포함한다.

**Avoid:** "exercise master", "exercise dictionary"

**Where it lives:** `backend/src/main/java/com/healthcare/domain/exercise/entity/ExerciseCatalog.java`

---

## 식단 기록 (Diet Log)

**Definition:** 사용자가 특정 식사 또는 날짜에 섭취한 음식을 저장한 기록. 여러 식품 항목과 영양 합계를 포함한다.

**Avoid:** "meal" (식사 한 끼와 저장된 기록의 경계가 흐려짐)

**Where it lives:** `backend/src/main/java/com/healthcare/domain/diet/`

---

## 식단 기록 유스케이스 (Diet Log Use Cases)

**Definition:** 식단 기록 생성, 조회, 수정, 삭제에서 사용자 소유권 검증, 식품 항목 검증, 영양 합계 계산, 식품 카탈로그 사용 횟수 조정을 한곳에서 처리하는 백엔드 모듈.

**Avoid:** "diet CRUD service" (HTTP 동작과 도메인 규칙이 단순 CRUD처럼 보임)

**Where it lives:** `backend/src/main/java/com/healthcare/domain/diet/usecase/DietLogUseCases.java`

---

## 식품 항목 (Food Entry)

**Definition:** 식단 기록을 구성하는 개별 음식 하나. 식품 카탈로그 항목과 섭취량을 기준으로 영양 값을 계산한다.

**Avoid:** "food item", "diet item"

**Where it lives:** `backend/src/main/java/com/healthcare/domain/diet/entity/FoodEntry.java`

---

## 식품 카탈로그 (Food Catalog)

**Definition:** 식단 기록에 사용할 수 있는 식품 데이터 목록. 공공 식품 데이터와 사용자 커스텀 식품을 포함한다.

**Avoid:** "nutrition DB item", "food master"

**Where it lives:** `backend/src/main/java/com/healthcare/domain/diet/entity/FoodCatalog.java`

---

## 영양 목표 (Nutrition Targets)

**Definition:** 사용자의 프로필과 목표를 바탕으로 계산된 일일 영양 기준. 식단 기록과 주간 회고에서 비교 기준으로 사용된다.

**Avoid:** "macro goals" (영양소 범위가 탄단지로만 좁아질 수 있음)

**Where it lives:** `backend/src/main/java/com/healthcare/domain/nutrition/`

---

## 신체 측정 (Body Measurement)

**Definition:** 체중, 체지방률, 근육량 등 사용자의 신체 지표를 날짜별로 기록한 데이터.

**Avoid:** "body metric" (개별 지표와 기록 묶음이 혼동됨)

**Where it lives:** `backend/src/main/java/com/healthcare/domain/bodymeasurement/`

---

## 진행 사진 (Progress Photo)

**Definition:** 사용자의 신체 변화를 시각적으로 비교하기 위해 저장하는 사진. S3 Presigned URL, EXIF 제거, 썸네일 생성 흐름과 연결된다.

**Avoid:** "body photo", "before-after photo" (현재 기능 범위를 과장할 수 있음)

**Where it lives:** `backend/src/main/java/com/healthcare/domain/bodymeasurement/entity/ProgressPhoto.java`

---

## 목표 (Goal)

**Definition:** 사용자가 달성하려는 신체 변화 또는 건강 목표. 목표 유형, 목표 값, 기간, 진행률 계산 기준을 포함한다.

**Avoid:** "target" (영양 목표와 혼동)

**Where it lives:** `backend/src/main/java/com/healthcare/domain/goals/`

---

## 목표 체크포인트 (Goal Checkpoint)

**Definition:** 목표 진행 상황을 주기적으로 기록하는 기준점. 스케줄러가 누락된 체크포인트를 보정하고 주간 회고의 입력으로 사용한다.

**Avoid:** "snapshot" (사진/측정과 혼동)

**Where it lives:** `backend/src/main/java/com/healthcare/domain/goals/entity/GoalCheckpoint.java`

---

## 인사이트 (Insights)

**Definition:** 운동, 식단, 신체 측정, 목표 데이터를 바탕으로 사용자에게 변화 분석과 주간 요약을 제공하는 결과.

**Avoid:** "analytics" (내부 통계 시스템처럼 들릴 수 있음)

**Where it lives:** `backend/src/main/java/com/healthcare/domain/insights/`
