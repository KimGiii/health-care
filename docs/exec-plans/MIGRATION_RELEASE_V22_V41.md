# 마이그레이션 릴리스 계획 — V22 → V41

- 작성일: 2026-08-04
- **상태: 완료 (2026-08-04 실행)**
- 대상: `prod` DB를 V21에서 V41로 전진시키는 백엔드 릴리스
- 배경: [PROJECT_RESTRUCTURING_2026-08.md](PROJECT_RESTRUCTURING_2026-08.md) Phase 1 진행 중 `dev`와 `prod`의 격차가 드러나 분리한 계획

> **실행 결과 요약** — 마이그레이션 20개 전부 성공, 총 실행시간 **930ms**, 데이터 유실 없음.
> 사전 점검에서 `food_catalog` **390행**이 확인되어 §2의 위험도 판정(잠금 시간 무시 가능)이 그대로 성립했다.
> 상세는 [§9 실행 결과](#9-실행-결과-2026-08-04)를 본다.

---

## 1. 결론

**이 릴리스는 스키마 관점에서 위험이 낮고, 다크 릴리스로 안전하게 나갈 수 있다.** 진짜 게이트는 스키마가 아니라 **데이터**다.

세 가지 근거가 위험도를 낮춘다.

1. **prod `food_catalog`은 시드 250~300행 규모다.** 620,120건은 로컬 DB에 MFDS 임포터로 적재한 결과이고, 어떤 마이그레이션도 대량 데이터를 넣지 않는다. 따라서 V23의 전체 테이블 UPDATE·`SET NOT NULL`·CHECK 검증·UNIQUE 인덱스 생성이 모두 초 단위로 끝난다.
2. **출시된 iOS 앱은 새 엔드포인트를 호출하지 않는다.** `origin/prod`의 `ios/`에는 추천 관련 파일이 하나도 없다. `POST /api/v1/diet/recommendations/daily`는 살아나지만 이를 부르는 클라이언트가 없다.
3. **기존 API 계약이 바뀌지 않았다.** 출시된 앱이 쓰는 컨트롤러 중 변경된 것은 `DietLogController` 하나이고, 내용은 `DietLogService` → `DietLogUseCases` 내부 리팩터링뿐이다. 엔드포인트·요청·응답이 모두 동일하다. DTO 변경도 순수 추가(+42/−0)라 Swift `Codable`이 무시한다.

반대로, **이 릴리스만으로 추천 기능이 동작하지는 않는다.** `food_serving_options`는 어떤 마이그레이션도 시드하지 않고, 카탈로그 평가에 따르면 SEED 300건에는 검증된 제공량이 하나도 없어 런타임 hard filter에서 전부 탈락한다. 데이터를 채우지 않은 채 iOS에서 기능을 열면 추천이 **항상 실패**한다.

따라서 순서는 다음과 같다.

```
① 백엔드 릴리스(다크)  →  ② prod 카탈로그 데이터 적재·품질 회복  →  ③ iOS 기능 출시
```

---

## 2. 대상 마이그레이션 20개

전부 9KB 이하다. 대량 DML이 없다.

| 마이그레이션 | 성격 | 대상 테이블 | 위험 |
|---|---|---|---|
| V22 allergen_restriction_schema | CREATE | 신규 | 낮음 |
| **V23** food_catalog_source_recommendation_fields | **ALTER + 전체 UPDATE + NOT NULL + CHECK + UNIQUE** | **food_catalog(기존)** | **중간 — 이 릴리스의 유일한 잠금 지점** |
| V24 food_catalog_import_checkpoints | CREATE | 신규 | 낮음 |
| V25 seed_recommendation_curation | INSERT (2.2KB) | 신규 | 낮음 |
| V26 seed_allergen_tags | INSERT (8.8KB) | 신규 | 낮음 |
| V27 allergen_profile_verified | ADD COLUMN NOT NULL DEFAULT FALSE + 조건부 backfill | food_allergen_tags(V22 신규) | 낮음 |
| V28 allergen_profile_verified_constraint | CHECK | 위와 동일 | 낮음 |
| **V29** diet_restrictions_active_unique_indexes | **dedup UPDATE ×4 + 부분 UNIQUE ×4** | diet_restrictions(V22 신규) | 낮음 |
| V30 seed_allergen_tags_p1_coverage | INSERT (3.5KB) | 신규 | 낮음 |
| V31 brand_official_allergen_source | ALTER | 신규 | 낮음 |
| V32 food_catalog_search_alias | ADD COLUMN (nullable) | food_catalog(기존) | 낮음 |
| V33 recommendation_data_contracts | CREATE | 신규 | 낮음 |
| V34 food_serving_options | CREATE | 신규 | 낮음 |
| V35 canonical_group_id | ADD COLUMN (nullable) + 자기참조 FK + 인덱스 | food_catalog(기존) | 낮음 |
| V36 recommendation_snapshots | CREATE | 신규 | 낮음 |
| V37 diet_log_recommendation_link | ADD COLUMN (nullable) + FK | **diet_logs(기존, 사용자 데이터)** | 낮음 |
| V38 food_serving_options_sort_order_integer | 타입 변경 SMALLINT→INTEGER | food_serving_options(V34 신규·비어있음) | 낮음 |
| V39 food_catalog_source_priority_dedup | ADD COLUMN NOT NULL DEFAULT + CHECK + UNIQUE | food_catalog(기존) | 낮음 |
| V40 recommendation_event_food_mapping | ALTER | 신규 | 낮음 |
| V41 recommendation_event_swap | ALTER | 신규 | 낮음 |

### 잘 작성된 부분

- **V29**는 부분 UNIQUE 인덱스를 만들기 전에 4가지 조건 각각으로 활성 중복을 soft-delete 한다. 주석에 "정리하지 않으면 기존 데이터에서 인덱스 생성이 실패한다"고 이유까지 남겼다. 유니크 제약이 기존 데이터에서 터지는 전형적 사고를 미리 막았다.
- **V27·V39**는 `NOT NULL DEFAULT <상수>` 형태다. PostgreSQL 11+에서 이 조합은 테이블 재작성 없이 메타데이터만 바꾸므로 빠르다.
- **V32·V35·V37**은 nullable 컬럼 추가라 잠금이 짧다.

### V23만 따로 보는 이유

한 마이그레이션 안에서 다음이 연달아 일어난다.

```sql
ALTER TABLE food_catalog ADD COLUMN ... (11개, nullable)   -- 빠름
UPDATE food_catalog SET source = ... WHERE source IS NULL;  -- 전체 행 재작성
UPDATE food_catalog SET recommendation_status = ...;        -- 전체 행 재작성
ALTER TABLE food_catalog ALTER COLUMN ... SET NOT NULL;     -- 전체 스캔 + ACCESS EXCLUSIVE
ADD CONSTRAINT ... CHECK (...);                             -- 전체 스캔 검증
CREATE UNIQUE INDEX ...;                                    -- CONCURRENTLY 아님 → 잠금
```

행 수가 300 규모면 전부 합쳐 1초 미만이다. **행 수가 예상과 다르면(예: 누군가 prod에 임포터를 돌린 적이 있다면) 이 지점이 길어진다.** 사전 점검에서 반드시 실측한다.

---

## 3. 사전 점검 (릴리스 당일)

`health-care-prod` IAM 사용자로는 RDS가 private subnet에 있어 여기서 직접 조회할 수 없었다. 아래는 EC2를 경유해 확인한다.

```bash
# EC2 접속 후
psql "$PROD_DB_URL" -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 3;"
psql "$PROD_DB_URL" -c "SELECT count(*) AS food_catalog_rows FROM food_catalog;"
psql "$PROD_DB_URL" -c "SELECT count(*) AS diet_logs_rows FROM diet_logs;"
psql "$PROD_DB_URL" -c "SELECT count(*) AS users FROM users WHERE deleted_at IS NULL;"
```

| 확인 항목 | 기대값 | **실측값 (2026-08-04)** | 판정 |
|---|---|---|---|
| `flyway_schema_history` 최신 | V21, success = true | **V21, 실패 이력 0건** | 통과 |
| `food_catalog` 행 수 | 수백 규모 | **390행 / 256 kB** (커스텀 90) | 통과 — V23 잠금 무시 가능 |
| `diet_logs` 행 수 | 소규모 | **71행 / 80 kB** | 통과 |
| 활성 사용자 수 | 소규모 | **49명** | 실사용자 존재 — 다운타임 영향 고려 대상 |

최대 테이블도 `notification_logs` 802행 / 656 kB에 불과했다. DB 전체가 수 MB 규모다.

> **전제 정정:** 계획 수립 초기에 이 프로젝트를 "사용자 없는 사전 출시 단계"로 서술한 대목이 있었으나, 실제로는 **활성 사용자 49명**이 있다(베타 테스터로 추정). 마이그레이션 위험도 판정은 그대로 유효하지만, 다운타임과 데이터 유실은 실제 사용자에게 영향을 준다는 전제로 다룬다.

### RDS 수동 스냅샷

```bash
aws rds create-db-snapshot \
  --db-instance-identifier healthcare-prod-postgres \
  --db-snapshot-identifier healthcare-prod-pre-v41-$(date +%Y%m%d)
```

> **실행 시 확인된 것:** `health-care-prod` 사용자에게 `rds:CreateDBSnapshot`과 `rds:DescribeDBSnapshots` 권한이 **없었다.** 수동 스냅샷을 만들지 못했다.
>
> 대신 **PITR로 보호했다.** `BackupRetentionPeriod = 7일`, `LatestRestorableTime`이 릴리스 시점 기준 약 5분 전이어서 마이그레이션 직전 시점으로 복구 가능한 상태였다. 스냅샷 복원과 PITR 모두 새 인스턴스를 만들어 앱을 재연결해야 하므로 복구 절차상 차이도 없다.
>
> 다만 PITR은 7일 창이 지나면 사라지므로, 라벨이 붙은 고정 복구 지점이 필요하면 콘솔에서 수동 스냅샷을 만든다. Phase 2에서 IAM 정책에 RDS 스냅샷 권한을 추가할 것.

---

## 4. 릴리스 절차

1. **사전 점검 통과 확인** (§3)
2. **RDS 수동 스냅샷 생성 및 available 상태 확인**
3. **`dev` → `prod` 브랜치 머지**
   ```bash
   git checkout prod && git merge origin/dev && git push origin prod
   ```
   `backend/**` 경로 변경이 있으므로 `dev-to-prod` 워크플로우가 트리거된다.
4. **GitHub Environment `prod` 수동 승인**
5. **배포 관찰** — blue-green이므로 green 컨테이너가 뜨고 헬스체크를 통과해야 nginx가 포트를 스왑한다. Flyway는 green 컨테이너 기동 중 실행된다.
6. **검증**
   ```bash
   curl -s https://api.gainsy.site/actuator/health
   psql "$PROD_DB_URL" -c "SELECT version, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;"
   ```
   기존 앱 경로 스모크: 로그인 → 식단 기록 조회 → 운동 기록 조회 → 목표 조회
7. **Grafana 대시보드 확인** — 5xx 비율, p99 지연, 힙, HikariCP

---

## 5. 실패 시나리오와 대응

| 시나리오 | 결과 | 대응 |
|---|---|---|
| Flyway가 중간에 실패 | green 컨테이너 기동 실패 → 헬스체크 미통과 → **포트 스왑 안 일어남 → 기존 blue 컨테이너가 계속 서비스** | 실패한 마이그레이션 수정 후 재배포. `flyway_schema_history`에서 실패 행 정리 필요 |
| Hibernate `ddl-auto: validate` 실패 | 위와 동일 (기동 실패) | 엔티티-스키마 불일치 수정. CI에서 전 구간 검증되므로 가능성 낮음 |
| 마이그레이션은 성공, 앱이 비정상 | 스왑 후 5xx | 이전 이미지 태그로 재배포(스키마는 전진 상태 유지 — 아래 참고) |
| 복구 불가 | — | RDS 스냅샷 복원 (스냅샷 이후 쓰기 유실) |

### 스키마가 전진한 채 구버전 앱이 도는 상태는 안전한가

**대체로 안전하다.** 이번 변경은 전부 가산적이다 — 신규 테이블, nullable 신규 컬럼, 신규 인덱스. 구버전 앱의 기존 쿼리는 그대로 동작한다. `ddl-auto: validate`는 기동 시점에만 실행되므로 이미 떠 있는 blue 컨테이너는 영향받지 않는다.

**단 하나 알아둘 것:** V23이 `food_catalog.source`에 `DEFAULT 'SEED'`를 건다. 이 상태에서 **구버전 앱이 사용자 커스텀 식품을 추가하면 `source`가 `USER_CUSTOM`이 아니라 `SEED`로 들어간다.** CHECK 제약은 통과하므로 오류는 나지 않고, `recommendation_status`는 `SEARCH_ONLY`로 올바르게 들어간다. 롤백 상태를 오래 유지할 경우에만 문제가 되며, 사후 UPDATE로 교정 가능하다.

---

## 6. 릴리스 이후 — 기능을 열기 전에 해야 할 일

백엔드가 나가도 추천 기능은 **동작하지 않는 상태**다. iOS에서 기능을 열기 전에 다음이 선행되어야 한다.

### 6.1 prod 카탈로그 데이터 적재

`food_serving_options`와 확장 카탈로그는 마이그레이션이 아니라 **관리자 임포터**로 채운다. `AdminOperationGuard`가 `app.admin.operation-token`으로 보호하므로 `PROD_ADMIN_OPERATION_TOKEN`이 필요하다.

적재 규모와 대상 범위는 별도 판단이 필요하다. 재구조화 계획 Phase 3은 **62만 건 전량 적재를 보류하고 curated pool만 유지**하는 방향을 제안한다.

### 6.2 카탈로그 품질 회복 (차단 요인)

[카탈로그 평가](../architecture-reviews/local-food-catalog-diet-recommendation-assessment-2026-08-03.md)가 지적한 문제는 데이터를 적재해도 남는다.

- 런타임 후보 11,979건 중 **75.8%가 `PROCESSED`**
- **`GRAIN` 0건, `VEGETABLE` 0건, `FRUIT` 0건** — 밥·채소·과일 없이 균형 식단을 구성할 수 없다
- 원인은 SEED 300건에 검증된 제공량이 없어 런타임 hard filter에서 전부 탈락하는 것

**이 상태로 기능을 열면 사용자는 가공식품 위주의 비현실적인 식사를 받거나 추천 실패 메시지를 받는다.** 평가 문서의 우선 3개 항목(제공량 추가 → 식품군 역할 기반 끼니 템플릿 → 영양 완결성 강화)이 iOS 출시의 실질적 선행 조건이다.

### 6.3 iOS 출시

`origin/prod`와 `origin/dev`의 iOS 커밋 차이는 52개다. 추천 UI 외에도 홈 IA 재구성, Explore 탭 제거, 목표 독립 탭 승격이 포함되어 있어 **사용자가 체감하는 변화가 크다.** 별도 릴리스 노트와 회귀 검증이 필요하며, 이 계획의 범위 밖이다.

---

## 7. 권장 판단

**백엔드 릴리스는 지금 진행해도 된다.** 다크 릴리스이고, 기존 계약이 유지되며, blue-green이 기동 실패를 막아준다. 미루면 `dev`와 `prod`의 격차만 더 벌어져 나중에 더 큰 릴리스가 된다.

**다만 §3 사전 점검, 특히 `food_catalog` 실제 행 수 확인은 건너뛰지 않는다.** 이 계획의 위험도 판정 전체가 "prod 카탈로그는 시드 규모"라는 추론 위에 서 있고, 그것만 검증하지 못했다.

**iOS 기능 출시는 §6.2 품질 회복 이후로 미룬다.**

> **사후 확인 (2026-08-04):** 사전 점검에서 `food_catalog` 390행이 확인되어 위 판단의 전제가 성립했고, 릴리스는 §9대로 완료됐다. iOS 출시 보류 판단은 유효하다 — §6.2의 카탈로그 품질 문제는 그대로 남아 있다.

---

## 8. Issue 분해 제안

| 제목 | 브랜치 접두어 | 선행 | 상태 |
|---|---|---|---|
| prod DB 사전 점검 + V22~V41 백엔드 릴리스 | `chore/` | — | **완료 (§9)** |
| prod 카탈로그 curated pool 적재 | `feat/` | 위 | 미착수 |
| 곡류·채소·과일 제공량 복구 + 끼니 템플릿 (평가 우선 3항목) | `feat/` | 위 | 미착수 |
| iOS 추천 기능 출시 + 홈 IA 변경 릴리스 노트 | `feat/` | 위 | 미착수 |

---

## 9. 실행 결과 (2026-08-04)

### 9.1 실행 경로

`dev` → `prod` fast-forward 머지(`0f1a258` → `21ff708`, prod 전용 커밋 0개) 후 push로 `dev-to-prod` 워크플로우 트리거. GitHub Environment `prod` 승인 대기 없이 진행되어 blue-green 배포 완료. 활성 포트가 8081 → **8080**으로 스왑됐다.

### 9.2 마이그레이션 실행시간 — 총 930ms

| 버전 | ms | 버전 | ms | 버전 | ms | 버전 | ms |
|---|---:|---|---:|---|---:|---|---:|
| V22 | 255 | V27 | 33 | V32 | 8 | V37 | 10 |
| **V23** | **215** | V28 | 12 | V33 | 19 | V38 | 12 |
| V24 | 11 | V29 | 54 | V34 | 14 | V39 | 14 |
| V25 | 60 | V30 | 40 | V35 | 9 | V40 | 6 |
| V26 | 113 | V31 | 16 | V36 | 20 | V41 | 9 |

20개 전부 `success = true`, 실패 0건. **§2에서 유일한 잠금 지점으로 지목한 V23은 215ms**로, 390행 규모에서는 전체 UPDATE·`SET NOT NULL`·CHECK 검증·UNIQUE 인덱스 생성을 모두 합쳐도 무시할 수 있는 시간이었다.

### 9.3 데이터 무결성

| 테이블 | 릴리스 전 | 릴리스 후 |
|---|---:|---:|
| `users` (활성) | 49 | 50 (릴리스 중 1명 신규 가입) |
| `diet_logs` | 71 | 71 |
| `food_catalog` | 390 | 390 |
| `exercise_sets` | 372 | 372 |

유실 없음.

### 9.4 서비스 검증

| 확인 | 결과 |
|---|---|
| `GET /actuator/health` | **200 / UP** |
| `POST /api/v1/auth/login` (잘못된 자격증명) | 401 |
| `GET /api/v1/diet/logs` (미인증) | 401 |
| `POST /api/v1/diet/recommendations/daily` (미인증) | 401 — 엔드포인트 활성, 호출 클라이언트 없음 |
| 컨테이너 | `healthcare-api-8080`, Grafana, Prometheus 정상 |
| 메모리 | 3844MB 중 1134MB 사용 (여유 2709MB) |

**다크 릴리스 판정이 맞았다.** 기존 API 계약이 유지되어 출시된 iOS 앱에 영향이 없었고, 새 엔드포인트는 호출하는 클라이언트가 없다.

부수 효과로 Phase 1 이후 남아 있던 `/actuator/health` 503(제거된 ElastiCache를 찾던 구버전 이미지의 Redis health indicator)이 함께 해소됐다.

### 9.5 릴리스와 무관하게 발견한 기존 문제

**FCM 푸시가 2026-05-15부터 실패 중 (미해결)**

```
ERROR c.h.common.notification.FcmConfig:
  credentials-path must point to a JSON file,
  but /app/fcm-credentials.json is not a regular file
```

호스트의 `/etc/healthcare/fcm-credentials.json`이 파일이 아니라 **디렉터리**다(생성일 `May 15 15:01`). 파일이 없는 경로를 Docker가 볼륨 마운트하면서 빈 디렉터리를 자동 생성한 결과다. 약 2개월 반 동안 주간 회고 푸시가 조용히 실패해 왔다. **실제 인증 정보 파일 배치가 필요하므로 별도 이슈로 다룬다.**

**prod 디스크 누적 (해소됨)**

배포 스크립트의 `docker image prune -f`는 dangling 이미지만 지우고 태그된 구버전 ECR 이미지를 남긴다. 32개가 쌓여 디스크가 83%(여유 3.3G)까지 찼고, 배포마다 이미지를 받으므로 곧 배포 실패로 이어질 상태였다.

`docker image prune -a -f --filter "until=168h"`로 정리해 **83% → 30%**(여유 13G)가 됐다. 배포 스크립트의 정리 로직을 보강하는 것이 근본 대책이다.
