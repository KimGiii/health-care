# Gainsy 정량 변화 지표

**기준일:** 2026-06-25
**집계 브랜치:** `feat/issue-76-dedup-load-verification`
**목적:** Gainsy의 구현 규모, 운영 경험, 품질 개선, 사용자 검증 상태를 숫자로 설명할 수 있게 한곳에서 관리한다.
**원칙:** 코드나 운영 문서로 확인되는 값만 확정 수치로 적고, 확인되지 않은 값은 `미측정` 또는 `미기록`으로 남긴다.

---

## 1. 현재 스냅샷

| 지표 | 현재 값 | 집계 기준 | 근거 |
|---|---:|---|---|
| 구현 API 수 | 77개 | Spring Controller의 method-level `@(Get/Post/Put/Patch/Delete)Mapping` 수. 관리자 API 포함, Actuator/정적 페이지 제외 | `backend/src/main/java/com/healthcare/**/*Controller.java` |
| DB 테이블 수 | 23개 | Flyway migration의 `CREATE TABLE` 수. 인덱스/제약조건/뷰 제외 | `backend/src/main/resources/db/migration/` |
| DB 마이그레이션 수 | 39개 | Flyway `V*.sql` 파일 수 | `backend/src/main/resources/db/migration/` |
| 백엔드 테스트 실행 결과 | 749 pass / 754 total | `./gradlew test jacocoTestReport --no-daemon` 로컬 실행 결과. 0 fail, 5 skip | 2026-06-25 실행. PostgreSQL/Redis 테스트 환경 연결 후 측정 |
| 테스트 커버리지 | 백엔드 line 79.40%, branch 65.17% | Jacoco 루트 counter 기준. iOS는 테스트 실패로 조건부 측정값만 보유 | `docs/operations/COVERAGE_MEASUREMENT.md` |
| 개발 산출 정량치 | 536 commits / 48 active days | 2026-04-01부터 기준일까지 Git commit 날짜 기준. 생산성 향상률이 아니라 산출 활동량 지표 | `git log --since='2026-04-01'` |
| 문서 파일 수 | 96개 | `docs/**/*.md`, `docs/**/*.html` 파일 수. 원천 데이터 CSV/JSON/PNG 제외 | `docs/` |
| 외부 테스터 수 | 8명 | App Store Connect/TestFlight 외부 테스터 그룹의 실제 초대·참여 인원 | 운영주 보고(2026-06-19). App Store Connect 외부 테스터 그룹 기준 |
| 발견·수정한 버그/운영 리스크 수 | 최소 14건 | 해결 완료로 문서화된 코드 리뷰·운영 장애·심사 리젝만 포함 | 백엔드 코드 리뷰 9건, App Store 재심사 거절 3건, 운영 502 1건, Redis 캐시 장애 1건 |
| AWS 운영 기간 | 최소 41일 | 운영 도메인 장애가 문서화된 2026-05-15부터 기준일 2026-06-25까지 | `docs/operations/TROUBLESHOOTING.md`, `docs/retrospectives/2026-W20.md` |
| 사용자 수 | 12명 | 운영 DB, TestFlight, App Store Connect, 분석 도구에서 확인된 실제 사용자 수 | 운영주 보고(2026-06-19). 로컬 테스트 fixture의 "테스터" 문자열은 제외 |
| AI 음식 분석 정확도 개선 수치 | 미측정 | 동일 benchmark set에서 baseline과 current의 음식 인식률·칼로리/영양소 오차를 비교해야 함 | 현재 릴리즈 노트에는 "정확도 향상" 표현만 있고 검증 수치 없음 |

---

## 2. 구현 규모 상세

### 2.1 API 수

| 영역 | API 수 | 포함 컨트롤러 |
|---|---:|---|
| 인증·사용자 | 10개 | `AuthController`(7), `UserController`(3) |
| 운동 | 7개 | `ExerciseSessionController`(4), `ExerciseCatalogController`(2), `AiExerciseController`(1) |
| 식단·식품·AI·추천 | 35개 | `FoodCatalogAdminController`(9), `DietLogController`(5), `MealPhotoAnalysisController`(4), `FoodAllergenTagAdminController`(4), `CandidatePoolAdminController`(3), `DietRestrictionController`(3), `FoodCatalogController`(2), `ExternalFoodAdminController`(2), `RecommendationFeedbackController`(1), `DietRecommendationController`(1), `AiNutritionController`(1) |
| 신체 측정·진행 사진 | 12개 | `BodyMeasurementController`(8), `ProgressPhotoController`(4) |
| 목표 | 6개 | `GoalController` |
| 인사이트 | 2개 | `InsightsController` |
| 알림 | 5개 | `NotificationController` |
| **합계** | **77개** |  |

재집계 명령:

```sh
rg -n "@(Get|Post|Put|Patch|Delete)Mapping" backend/src/main/java/com/healthcare --glob '*Controller.java' | wc -l
```

### 2.2 DB 테이블 수

| 영역 | 테이블 수 | 테이블 |
|---|---:|---|
| 사용자·인증 | 3개 | `users`, `refresh_tokens`, `user_identities` |
| 운동 | 3개 | `exercise_catalog`, `exercise_sessions`, `exercise_sets` |
| 식단 기록·식품 카탈로그 | 3개 | `food_catalog`, `diet_logs`, `food_entries` |
| AI 식사 사진 분석 | 2개 | `meal_photo_analyses`, `meal_photo_analysis_items` |
| 식단 제한·알러젠 | 3개 | `diet_restrictions`, `food_allergen_tags`, `food_allergen_profiles` |
| 식품 서빙 옵션 | 1개 | `food_serving_options` |
| 추천 스냅샷·이벤트 | 2개 | `recommendation_snapshots`, `recommendation_events` |
| 목표 | 2개 | `goals`, `goal_checkpoints` |
| 신체 변화 | 2개 | `body_measurements`, `progress_photos` |
| 알림 | 1개 | `notification_logs` |
| 식품 적재 운영 | 1개 | `food_catalog_import_checkpoints` |
| **합계** | **23개** |  |

재집계 명령:

```sh
rg -n "CREATE TABLE|create table" backend/src/main/resources/db/migration | wc -l
```

---

### 2.3 테스트·커버리지

| 항목 | 현재 값 | 근거 |
|---|---:|---|
| 백엔드 테스트 파일 | 114개 | `find backend/src/test/java -name '*.java' \| wc -l` |
| 백엔드 테스트 실행 결과 | 749 pass / 754 total | `./gradlew test jacocoTestReport --no-daemon` 로컬 실행 결과 |
| iOS 테스트 파일 | 12개 | `find ios/HealthCareTests ios/HealthCareUITests -name '*.swift' \| wc -l` |
| 백엔드 커버리지 | line 79.40%, branch 65.17%, instruction 80.69%, method 79.71%, class 87.50% | `backend/build/reports/jacoco/test/jacocoTestReport.xml` |
| iOS 커버리지 | 조건부: total line 10.32%, `HealthCare.app` line 6.18% | `ios/build/coverage/HealthCareTests.xcresult`. 테스트 실패가 있어 확정 성과 수치로 쓰지 않음 |

백엔드 테스트 skip 내역:

| 테스트 | 상태 | 원인 |
|---|---|---|
| smoke/조건부 테스트 5건 | skip | 외부 API 키 등 조건 미충족 |

iOS 테스트 실패 내역:

| 항목 | 현재 값 | 비고 |
|---|---:|---|
| 실행 환경 | iPhone 17 Simulator, iOS 26.5 | `DESTINATION='platform=iOS Simulator,name=iPhone 17,OS=26.5'` |
| 테스트 결과 | 87 pass / 101 total | 14 failed, 0 skipped |
| 주요 실패 유형 | 인증/Keychain 관련 실패 | `unauthorized`, `TokenStoreWriteRead`, 인증 상태 유지 실패 |

커버리지 측정 절차는 `docs/operations/COVERAGE_MEASUREMENT.md`를 따른다.

### 2.4 개발 산출 정량치

| 항목 | 현재 값 | 집계 기준 |
|---|---:|---|
| 2026-04-01 이후 commit 수 | 536개 | `git rev-list --count --since='2026-04-01' HEAD` |
| active development day | 48일 | commit이 1개 이상 있는 날짜 수 |
| active day당 commit | 11.17개 | `commit 수 / active day` |
| 백엔드 main Java 파일 | 317개 | `backend/src/main/java/**/*.java` |
| 백엔드 test Java 파일 | 114개 | `backend/src/test/java/**/*.java` |
| iOS app Swift 파일 | 124개 | `ios/HealthCare/**/*.swift` |
| iOS Unit/UI test Swift 파일 | 12개 | `ios/HealthCareTests`, `ios/HealthCareUITests` |

위 값은 생산성 향상률이나 개발 효율을 직접 증명하지 않는다. 포트폴리오에서는 "개발 산출 활동량" 또는 "구현 규모"로만 사용한다.

---

## 3. 품질·운영 변화

| 변화 | 수치화 표현 | 근거 |
|---|---:|---|
| App Store 출시 차단 요소 해결 | BLOCKER 6개 해결 | `docs/retrospectives/2026-W20.md` |
| App Store 재심사 거절 대응 | 거절 3건 대응 후 통과 | `docs/operations/TROUBLESHOOTING.md` |
| 백엔드 보안·품질 리뷰 후속 | 해결 9건, 잔여 8건 이상 | `docs/retrospectives/2026-05-21-backend-code-review.md` |
| Access Token 유효시간 축소 | 24시간에서 1시간으로 95.8% 단축 | `docs/retrospectives/2026-05-21-backend-code-review.md` |
| 페이징 과부하 방지 | 최대 page size 100으로 제한 | `docs/retrospectives/2026-05-21-backend-code-review.md` |
| Redis 캐시 장애 격리 | 캐시 실패 1건을 DB fallback으로 완화 | `docs/operations/TROUBLESHOOTING.md` |
| 운영 도메인 장애 복구 | 502 장애 1건 복구 및 헬스체크 절차화 | `docs/operations/TROUBLESHOOTING.md` |

---

## 4. 아직 수치화하면 안 되는 항목

아래 항목은 포트폴리오나 README에 확정 수치처럼 쓰지 않는다. 실제 운영 데이터나 benchmark가 생기면 이 문서부터 갱신한다.

| 항목 | 필요한 근거 | 다음 액션 |
|---|---|---|
| iOS 확정 테스트 커버리지 | Xcode `xccov` 리포트 + 테스트 성공 | 현재 result bundle은 있으나 테스트 실패가 있어 확정 성과 수치로 쓰지 않음 |
| AI 음식 분석 정확도 | 고정된 식사 사진 benchmark set과 정답 라벨 | 음식 인식률, 칼로리 MAPE, 단백질/탄수화물/지방 MAPE를 baseline/current로 비교 |
| AI 정확도 개선률 | 동일 데이터셋에서 이전 버전과 현재 버전 결과 | `(baseline error - current error) / baseline error`로 개선률 산출 |
| 생산성 향상률 | 동일 기간·동일 기준의 before/after 작업량, 리드타임, 결함률 | 현재 Git 산출 지표를 생산성 향상률로 해석하지 않음 |
| 경쟁사 내부 알고리즘 | 공개 자료가 아닌 내부 구현 근거 | 공개 UI·응답 형태 관찰 기반 벤치마킹으로만 표현 |

> 외부 테스터 수(8명)·사용자 수(12명)는 2026-06-19 운영주 보고로 §1에 기록했다. App Store Connect/운영 DB 캡처로 교차검증되면 근거를 갱신한다. 활성 사용자(최근 7일)·기록 1회 이상 사용자는 아직 별도 집계가 없으므로 단일 "사용자 수"로만 표기한다.

---

## 5. 갱신 규칙

- API mapping을 추가·삭제하거나 컨트롤러를 분리하면 `구현 API 수`와 API 영역별 표를 갱신한다.
- Flyway migration에서 제품 테이블을 추가·삭제하면 `DB 테이블 수`와 테이블 영역별 표를 갱신한다.
- 테스트 수, 파일 수, 커버리지 리포트가 바뀌면 `테스트·커버리지`와 `개발 산출 정량치`를 함께 갱신한다.
- 운영 장애, 심사 리젝, 보안 리뷰 이슈를 해결하면 `품질·운영 변화`에 근거 문서와 함께 추가한다.
- TestFlight, App Store Connect, 운영 DB, 분석 도구에서 사용자 수가 확인되면 `미기록` 값을 실제 수치로 바꾼다.
- AI 음식 분석 모델, 프롬프트, 매칭 로직을 개선할 때는 benchmark 결과 없이는 정확도 개선 수치를 쓰지 않는다.
- 이 문서의 수치를 README, 포트폴리오, 이력서 문구에 사용할 때는 기준일과 집계 기준을 함께 확인한다.
