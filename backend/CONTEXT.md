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

## 추천 큐레이션 (Recommendation Curation)

**Definition:** 식품 카탈로그 항목이 추천 후보로 쓰일 수 있는지와 주의 사유를 함께 표현하는 값 객체. CSV 운영 입력 검증, DB 저장용 사유, 추천 응답의 주의 문구를 같은 불변 조건으로 다룬다. 브랜드 공식 메뉴의 추천 상태 변경은 CSV 재적재가 기준 경로이며, `RECOMMENDABLE_WITH_CAUTION`은 공식 영양값의 결측·나트륨·당류·포화지방 같은 운영 주의 사유를 응답 caution으로 전달한다.

**Avoid:** "status flag", "reason helper", "caution string" (추천 후보 상태와 주의 사유의 불변 조건이 분리되어 보임)

**Where it lives:** `backend/src/main/java/com/healthcare/domain/diet/entity/RecommendationCuration.java`

---

## 식단 추천 후보 풀 (Diet Recommendation Candidate Pool)

**Definition:** 식단 추천 런타임에서 사용할 수 있는 후보 값을 생성하는 모듈. 추천 적합성 상태, 사용자 제한 조건, 알러젠 신뢰 게이트를 통과한 식품 카탈로그를 엔진 입력용 `DietRecommendationCandidate`로 변환한다.

**Avoid:** "filtered catalog list", "candidate helper", "recommendation spec" (추천 후보 정책이 단순 조회 조건처럼 보임)

**Where it lives:** `backend/src/main/java/com/healthcare/domain/diet/recommendation/candidate/DietRecommendationCandidatePool.java`

---

## 식단 추천 후보 (Diet Recommendation Candidate)

**Definition:** 식단 추천 엔진이 끼니 구성과 제공량 계산에 사용하는 식품 후보 스냅샷. 식품 카탈로그 ID, 영양값, 카테고리, 사용 횟수, 안정적인 rotation key, 알러젠 신뢰도, 응답용 주의 문구를 포함하며 JPA 엔티티와 알러젠 태그 맵을 엔진에서 숨긴다.

**Avoid:** "FoodCatalog row", "tag map", "recommendation DTO" (엔진 입력 경계가 persistence나 응답 형태에 다시 결합됨)

**Where it lives:** `backend/src/main/java/com/healthcare/domain/diet/recommendation/candidate/DietRecommendationCandidate.java`

---

## 알러지 제한 (Allergy Restriction)

**Definition:** 사용자가 지원되는 표준 알러젠 태그로 등록하는 절대 제외 조건. 알러지 제한이 있으면 검토 범위가 완결된 유효 알러젠 프로필을 가진 식품만 추천 후보가 된다.

**Avoid:** "Strict 모드", "알러지 키워드", "기피 알러젠" (사용자 토글·이름 검색·일반 기피와 다른 fail-closed 계약임)

**Where it is specified:** `docs/adr/0005-versioned-allergen-evidence-fail-closed.md`

---

## 알러젠 근거 (Allergen Evidence)

**Definition:** 특정 식품이 표준 알러젠을 원재료로 포함하거나 교차접촉 가능성이 있음을 출처·버전과 함께 표현하는 검토 사실. 같은 식품에 대한 여러 출처의 근거는 덮어쓰지 않고 이력과 유효 상태를 보존한다.

**Avoid:** "알러젠 태그 행", "안전 판정", "없음 태그" (원본 근거와 최종 추천 판정을 혼동함)

**Where it is specified:** `docs/adr/0005-versioned-allergen-evidence-fail-closed.md`, `docs/exec-plans/DIET_ALLERGEN_VERIFIED_ONLY_HARDENING.md`

---

## 알러젠 프로필 (Allergen Profile)

**Definition:** 식품별로 어떤 표준 알러젠 집합을 완결 검토했는지와 그 검토의 출처·버전·유효성을 나타내는 계약. 포함·교차접촉 근거가 0개인 검토 완료 상태도 표현하며, 알러지 제한 태그가 검토 범위에 들어 있어야 통과 근거가 된다.

**Avoid:** "알러젠 태그 검증", "allergen_profile_verified 플래그", "알러젠 없음" (근거 부재와 완결 검토를 혼동함)

**Where it lives:** `backend/src/main/java/com/healthcare/domain/diet/allergen/entity/FoodAllergenProfile.java`, `backend/src/main/java/com/healthcare/domain/diet/allergen/AllergenSafetyGate.java`

---

## 식이 기준 (Dietary Requirement)

**Definition:** 글루텐 프리처럼 검증된 식품 속성을 요구하는 사용자 조건. 알러지 제한이나 메뉴 정체성 기반 기피와 별개의 후속 제품 계약이다.

**Avoid:** "알러젠 태그", "기피 키워드" (포함 위험이나 음식 취향과 검증 방식이 다름)

**Where it is specified:** `docs/adr/0005-versioned-allergen-evidence-fail-closed.md`

---

## 검증된 추천 후보 (Verified Recommendation Candidate)

**Definition:** 알러지 사용자의 완결 알러젠 프로필, 목표별 필수 영양 데이터, 현실적인 제공량 옵션, 유효한 데이터 버전, canonical 대표 조건을 모두 통과해 제약 최적화에 투입할 수 있는 식품 후보. `RECOMMENDABLE` 상태만으로는 이 자격을 충족하지 않는다.

**Avoid:** "safe food", "추천 가능 row" (의료적 안전을 보증하거나 단일 상태값으로 자격이 결정되는 것처럼 보임)

**Where it is specified:** `docs/adr/0002-goal-aware-nutrition-optimization.md`, `docs/exec-plans/DIET_RECOMMENDATION_OPTIMIZATION.md`

---

## 남은 영양량 (Remaining Nutrition Budget)

**Definition:** 목표별 일일 영양 정책에서 추천 전에 확정된 섭취 기록을 차감한 값. 남은 끼니 전체를 공동 최적화하는 입력이며, 미검증·AI 추정 섭취 기록은 열량 상한과 단백질 하한을 보수적으로 반영한다.

**Avoid:** "remaining calories" (단백질·탄수화물·지방과 불확실성까지 포함하는 개념을 열량으로 축소함)

**Where it lives:** `backend/src/main/java/com/healthcare/domain/nutrition/policy/RemainingNutritionCalculator.java`, `docs/exec-plans/DIET_RECOMMENDATION_OPTIMIZATION.md`

---

## 목표별 영양 정책 (Goal-aware Nutrition Policy)

**Definition:** 체중 감량, 근육량 증가, 체형 개선, 지구력 향상, 건강 유지마다 다른 영양 상·하한 hard constraint와 soft objective를 제공하는 버전된 정책. 목표 기간은 희망 일정으로 취급하고 주간 진행 추세에 따라 목표와 예상 달성일을 조정한다.

**Avoid:** "target tolerance", "macro weight" (대칭 오차나 단순 점수 가중치처럼 보임)

**Where it lives:** `backend/src/main/java/com/healthcare/domain/nutrition/policy/GoalAwareNutritionPolicy.java`, `docs/adr/0002-goal-aware-nutrition-optimization.md`

---

## 식품 제공량 옵션 (Food Serving Option)

**Definition:** 추천 엔진이 사용할 수 있도록 source와 검증 근거를 가진 이산 섭취량. 단위명, 환산 g, 허용 multiplier 또는 step, 최소·최대 수량을 포함한다. 식단 기록하기의 직접 g 입력과 달리 추천은 검증된 옵션만 사용한다.

**Avoid:** "servingG", "portion guess" (저장된 최종 중량이나 임의 추정값과 혼동)

**Where it is specified:** `docs/exec-plans/DIET_RECOMMENDATION_OPTIMIZATION.md`

---

## canonical 식품 그룹 (Canonical Food Group)

**Definition:** 여러 source의 동일 식품 레코드를 삭제·강제 병합하지 않고 하나의 대표 식품 아래 연결하는 비파괴 동일성 경계. 추천 중복 제거와 선호·노출·전환 집계에 사용하며, 불확실한 그룹에는 검증 결과를 전파하지 않는다.

**Avoid:** "merged food", "deduped row" (원본 source row가 사라지거나 자동 병합된다고 오해할 수 있음)

**Where it is specified:** `docs/exec-plans/DIET_RECOMMENDATION_OPTIMIZATION.md`

---

## 식단 추천 최적화 (Diet Recommendation Optimization)

**Definition:** DB hard filter와 후보 축소 후, 목표별 hard constraint를 만족하는 남은 하루 식단의 상위 해를 생성하는 모듈 경계. 다양성·최근 반복·사용자 선호는 feasible 해 안에서만 순위에 반영한다.

**Avoid:** "greedy scorer", "meal generator" (현행 구현 방식이나 완결 메뉴 생성 기능과 혼동)

**Where it is specified:** `docs/adr/0002-goal-aware-nutrition-optimization.md`, `docs/exec-plans/DIET_RECOMMENDATION_OPTIMIZATION.md`

---

## 추천 스냅샷과 추천 이벤트 (Recommendation Snapshot and Event)

**Definition:** 추천 당시 입력 정책·후보·결과를 재현하고 노출, 재추천, 기록 전환, 삭제·교체, 선택형 사유를 연결하기 위한 단기 품질 데이터. 식단 계획이나 섭취 기록 자체가 아니며 사용자 연결 원본은 기본 90일만 유지한다.

**Avoid:** "diet plan", "recommendation log" (영구 계획 도메인 또는 일반 애플리케이션 로그와 혼동)

**Where it is specified:** `docs/exec-plans/DIET_RECOMMENDATION_OPTIMIZATION.md`

---

## 추천 기록 전환 (Recommendation Conversion)

**Definition:** 사용자가 추천 스냅샷의 특정 끼니를 실제 식단 기록으로 확정하는 행위. 일반 식단 기록과 달리 추천 이후 바뀐 제한 조건과 검증 근거를 다시 확인하는 경계다.

**Avoid:** "추천 저장", "자동 기록" (추천 스냅샷 영속화나 사용자 확인 없는 기록 생성과 혼동함)

**Where it is specified:** `docs/exec-plans/DIET_ALLERGEN_VERIFIED_ONLY_HARDENING.md`

---

## 식품 카탈로그 동일성 (Food Catalog Identity)

**Definition:** 식품 카탈로그 항목을 소스 적재, 중복 후보 리포트, 검색 정규화에서 같은 개념으로 다루기 위한 식별 규칙. 브랜드 공식 메뉴의 `food_code`, 브랜드 인식 중복 키, 표시 이름 정규화를 한곳에서 계산한다.

**Avoid:** "name util", "dedup helper", "food code formatter" (동일성 규칙이 단순 문자열 처리처럼 보임)

**Where it lives:** `backend/src/main/java/com/healthcare/domain/diet/identity/FoodCatalogIdentity.java`

---

## 식품 카탈로그 적재 (Food Catalog Ingest)

**Definition:** 공공 식품 데이터나 브랜드 공식 메뉴 입력을 검증된 식품 카탈로그 후보로 바꾼 뒤, `source + food_code` 기준으로 생성/갱신/거절 결과를 집계하는 백엔드 모듈. 소스별 importer는 원본 row를 후보로 변환하고, 적재 모듈은 upsert와 추천 큐레이션 보존/교체 정책을 처리한다. 외부 공공데이터 문자열은 `food_catalog` 컬럼 한도에 맞춰 정규화하며, 식별자인 `food_code`가 한도를 넘으면 후보를 거절한다.

**Avoid:** "import helper", "upsert util", "catalog sync" (소스 변환과 저장 정책의 역할 분리가 흐려짐)

**Where it lives:** `backend/src/main/java/com/healthcare/domain/diet/external/importer/FoodCatalogIngestService.java`

---

## 공공데이터 전량 적재 실행 (Public Data Full Ingest Run)

**Definition:** 공공데이터 3개 source(`MFDS_STANDARD_PROCESSED`, `MFDS_STANDARD_DISH`, `MFDS_FOOD_NUTRIENT_DB`)를 내부 `food_catalog`에 채우는 운영 절차. smoke, 제한 배치, source별 반복 적재, 체크포인트 검증, 중복 후보 리포트 순서로 진행하며, 각 source는 `FoodCatalogBatchImportSummary.exhausted=true`가 나올 때까지 같은 관리자 카탈로그 작업을 반복한다. local DB에서는 smoke, 제한 배치, 대표 장애 케이스 검증까지만 수행해도 충분하며 모든 row를 끝까지 적재하지 않는다.

**Avoid:** "API 한번 돌리기", "전체 동기화", "추천 데이터 적재" (한 번의 호출로 끝나지 않고, 신규 공공데이터 항목은 기본 `SEARCH_ONLY`라 추천 후보 승격과 다름)

**Where it lives:** `docs/exec-plans/FOOD_CATALOG_ENRICHMENT.md`, `docs/FOOD_CATALOG_GUIDE.md`, `backend/src/main/java/com/healthcare/domain/diet/admin/FoodCatalogAdminOperations.java`

---

## 브랜드 공식 메뉴 (Brand Official Menu)

**Definition:** 브랜드가 공식 영양정보로 공개하고 운영자가 검수한 메뉴 단위 식품 데이터. 자동 크롤링 결과가 아니라 관리자 카탈로그 작업을 통해 식품 카탈로그에 적재된다.

**Avoid:** "brand food", "franchise item", "crawled menu"

**Where it lives:** `backend/src/main/java/com/healthcare/domain/diet/external/importer/BrandMenuCsvImporter.java`

---

## 관리자 카탈로그 작업 (Admin Catalog Operation)

**Definition:** 공공 식품 데이터 배치 적재, 브랜드 공식 메뉴 CSV 적재, 중복 후보 리포트, canonical dedup 백필(`/dedup/backfill`), 충돌 검토 큐(`/dedup/collisions`)처럼 운영자가 내부 카탈로그를 변경하거나 점검하는 작업. 일반 사용자 JWT 인증과 별도로 `X-Admin-Token` operation token을 요구한다.

**Avoid:** "admin API" (HTTP 경로만 떠올라 operation 규칙과 데이터 변경 위험이 흐려짐)

**Where it lives:** `backend/src/main/java/com/healthcare/domain/diet/admin/FoodCatalogAdminOperations.java`, `backend/src/main/java/com/healthcare/domain/diet/controller/FoodCatalogAdminController.java`, `backend/src/main/java/com/healthcare/common/security/AdminOperationGuard.java`

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

---

## 정량 지표 갱신 규칙 (Quantified Progress)

**Definition:** Gainsy의 구현 API 수, DB 테이블 수, 운영 장애/버그 해결 수, AWS 운영 기간, AI 음식 분석 정확도처럼 숫자로 표현하는 프로젝트 변화 지표. 기준 문서는 `docs/product-specs/GAINSY_QUANTIFIED_PROGRESS.md`이다.

**Backend update triggers:** Controller mapping 추가·삭제, Flyway table 추가·삭제, 운영 장애 해결, 보안/품질 리뷰 이슈 해결, AI 음식 분석 provider·prompt·매칭 로직 변경.

**Rule:** 백엔드 변경이 위 지표의 값이나 집계 기준을 바꾸면 구현 문서나 회고만 갱신하지 말고 정량 지표 문서도 함께 갱신한다. AI 정확도 개선 수치는 고정 benchmark 결과가 있을 때만 확정 수치로 작성한다.


---

## 기능 개발 워크플로우

백엔드 작업 시작 전 반드시:

1. `gh issue create --repo KimGiii/Gainsy`로 이슈 생성.
2. 이슈에 대응하는 브랜치가 이미 있는지 확인: `git branch -a | grep issue-<번호>`. 있으면 체크아웃, 없으면 새로 생성.
3. 작업 성격에 따라 브랜치 접두어 구분:
   - 기능 개발 → `feat/issue-<번호>-<짧은-설명>`
   - 검수·테스트 → `qa/issue-<번호>-<짧은-설명>`
   - 오류 수정 → `fix/issue-<번호>-<짧은-설명>`
4. 커밋 메시지 또는 PR 본문에 `Closes #<번호>` 기재.
5. `dev`에 직접 커밋하지 않는다.
