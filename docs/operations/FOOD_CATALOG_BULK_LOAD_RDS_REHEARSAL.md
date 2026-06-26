# 식품 카탈로그 전량 적재 — G2 ephemeral RDS 적재 리허설 runbook

운영자가 직접 수행하는 수동 절차다. 코드/검증 게이트(G1)는 이미 커밋·CI에서 자동 실행된다.
이 문서는 **prod RDS에 본 적재를 돌리기 전**, 동일 적재를 **일회성(ephemeral) 인스턴스**에서 1회 리허설해
용량·소요·정합성을 측정하는 절차를 정의한다. 리허설이 끝나면 인스턴스는 폐기한다.

> **위치**: 전량 적재 운영 순서 표는 [FOOD_CATALOG_GUIDE](../FOOD_CATALOG_GUIDE.md#공공데이터-전량-적재-운영-순서),
> dedup 설계는 [DIET_FOOD_CATALOG_SOURCE_PRIORITY_DEDUP](../exec-plans/DIET_FOOD_CATALOG_SOURCE_PRIORITY_DEDUP.md).
> 이 runbook은 그 표의 **0번(사전 조건)을 RDS 관점에서 확장**한 것이다 — 적재 절차 자체는 중복 기술하지 않는다.

## 결정 근거 — 왜 상시 dev RDS를 신설하지 않는가

"운영 전에 dev에서 미리 검증" 자체는 옳은 방향이다. 그러나 그 검증이 증명하려는 것은 **두 가지로 분리**되며,
이를 합치면 과투자(상시 dev RDS 신설)로 이어진다.

| 검증 대상 | 무엇에 의존 | 어디서 |
|---|---|---|
| **① 정합성** — 적재 결과가 acceptance target과 일치하는가 | PG 엔진 버전 + Flyway + **실 MFDS 데이터** | **아무 DB나** (싼 컨테이너/로컬 PG17로 충분) |
| **② 용량/성능** — db.t3.micro(1GB)가 615K 적재 + 인덱스 빌드를 버티는가 | **인스턴스 클래스 + 스토리지/IOPS** | **RDS급 하드웨어**, 단 **일회성** |

- dev 컨테이너 PG는 ①은 되지만 ②는 재현 못 한다(컨테이너 PG ≠ RDS db.t3.micro의 메모리 회계·IOPS·오토스케일).
- 그러나 ②를 위해 **상시 dev RDS는 불필요**하다. 상시 db.t3.micro는 24/7 월 ~$13–15 + 스토리지·백업이 *일회성 검증* 목적에 영구 발생하고,
  `infra/terraform/dev/`에 database.tf를 신설해 토폴로지를 영구 변경해야 한다.
- **결론: 일회성 검증엔 일회성 자원.** ②는 **ephemeral RDS**(Terraform up → 적재·측정 → `destroy`, 몇 시간치 = 푼돈)로 충분하며,
  이는 곧 이 runbook의 절차다. ①은 그보다 더 싸게 dev/로컬에서 선행한다(아래 "0단계: 정합성 선검증").
- prod food_catalog는 본 적재 전까지 소량이므로 **"prod 스냅샷 복원"과 "빈 인스턴스 신규 적재"는 사실상 등가**다.
  둘 중 편한 모드를 쓴다(아래 모드 A/B). dev RDS를 만들 이유는 없다.

> 이 결정은 비용·클라우드·DB 관점 합의(2026-06-25). 추후 재론 빈도가 높아지면 ADR로 승격한다.

## 게이트 단계 위치

- **G1 정합성(자동, 완료)**: `FoodCatalogDedupLoadIT` — 실 PG17 + Flyway V39에서 적재 경로 **표본** 검증. CI postgres 17 서비스.
- **0단계 정합성 선검증(싸게, 권장)**: dev 컨테이너/로컬 PG17에 **실 MFDS 전량 적재** → ingest 경로 결과가 acceptance target과 일치하는지.
  실데이터 정규화 충돌(동명이품 등)이 projection과 어긋나는지는 실데이터 ingest로만 잡힌다. (용량 측정은 목표 아님)
- **G2 용량 리허설(이 문서 본문)**: ephemeral RDS(PG17) → 전량 적재 1회 → acceptance target 대조 + **용량 측정** → 인스턴스 폐기.
- **본 적재**: G2 통과 후 prod RDS에 실행([FOOD_CATALOG_GUIDE 운영 순서](../FOOD_CATALOG_GUIDE.md#공공데이터-전량-적재-운영-순서) 4~10번).

### 리허설 클래스 결정 (2026-06-26): **db.t3.medium (PG17)**, micro 리허설 스킵

- **micro 리허설 안 함**: 로컬 전량 적재 실측이 **큰 개발 머신에서도 ~12시간**(processed 8.5h, 콜당 19→69분 가속 둔화)이었다. db.t3.micro(1GB·t3 버스트)는 이 지속 쓰기에서 **CPU 크레딧 고갈 + gp2 IOPS 한계로 베이스라인까지 스로틀** → 반나절이 하루+로 늘어진다. 실패 모드는 OOM 크래시("터짐")가 아니라 **throttle("기어감")** 이지만, 어느 쪽이든 micro에서 끝까지 돌려 확인할 실익이 없다.
- **medium으로 리허설 = 본 적재 플랜을 그대로 리허설**: prod는 micro지만 적재 창에는 **micro→medium 일시 상향 후 적재, 완료 후 원복**이 플랜(아래 판단 기준). 따라서 medium 리허설이 그 플랜을 동일 클래스에서 검증한다. 스로틀 없는 medium은 로컬 12h보다 빠를 것(추정 4–8h).
- **반드시 PG17**: 로컬은 PG16이었으니, 이 리허설로 prod 17.7 **엔진 패리티 갭도 함께 닫는다**.
- 비용 무시 가능: db.t3.medium ≈ $0.068/h × 수 시간 = $1 미만 + 스토리지.

## 사전 조건

- AWS 콘솔/CLI 권한(RDS 생성·복원·삭제, 보안그룹). Terraform 사용 시 일회성 스택 권한.
- prod RDS 사양 확인(`db.t3.micro`, PG `17.7`, `infra/terraform/aws/database.tf`) — ephemeral 인스턴스를 **동일 엔진(PG17)** 으로 맞춘다.
- 적재를 돌릴 백엔드 1대(별도 EC2 또는 로컬에서 `DB_URL`만 ephemeral 인스턴스로 오버라이드). 이 빌드에 **V39 + dedup 코드**가 포함돼 있어야 Flyway가 인스턴스에 스키마를 적용한다.
- 시크릿/환경값: `PUBLIC_FOOD_API_KEY`, `ADMIN_OPERATION_TOKEN`, ephemeral `DB_URL`/`DB_USERNAME`/`DB_PASSWORD`
  (앱은 `DB_URL`/`DB_USERNAME`/`DB_PASSWORD` env를 읽는다 — `application-prod.yml`/`application-dev.yml`, prod 시크릿 `PROD_DB_*`)
- 검증 기준값(acceptance target): 적재 **615,509**행 → canonical **323,899** / superseded **291,610**(47.4%) / COLLISION **코드 2,781 = `dedup_state` 행 ~5,562**
  ([FOOD_CATALOG_DEDUP_LOAD_PROJECTION](../references/FOOD_CATALOG_DEDUP_LOAD_PROJECTION.md))

## 인스턴스 모드 (택1)

| | 모드 A — 신규 ephemeral (권장) | 모드 B — prod 스냅샷 복원 |
|---|---|---|
| 만드는 법 | 빈 RDS PG17 신규 생성(Terraform 일회성 스택) | prod 스냅샷 → 복원 |
| 스키마 | 적재 백엔드 기동 시 **Flyway가 V1~V39 적용** | 스냅샷 시점 스키마(+ 백엔드 기동 시 pending 적용) |
| 데이터 노출 | prod 데이터 사본 아님 → **PHI 위험 없음** | prod 데이터 사본 → **격리·즉시 폐기 필수** |
| 언제 | 기본. prod가 본 적재 전 소량이라 충실도 충분 | prod 기존 데이터 볼륨 위에서의 동작까지 보고 싶을 때 |

## 절차

| 순서 | 작업 | 명령/확인 |
|---:|---|---|
| 0 | **정합성 선검증**(선택·권장) | dev/로컬 PG17에 실 MFDS 전량 적재 → `dedup_state` 분포가 target과 일치 확인. 통과 시 G2에서는 용량에 집중 |
| 1 | ephemeral 인스턴스 기동 | **클래스 = db.t3.medium, 엔진 = PG17 고정**(위 "리허설 클래스 결정"). **A**: `terraform apply`(일회성 db.t3.medium PG17 스택). **B**: `aws rds restore-db-instance-from-db-snapshot --db-instance-class db.t3.medium`. micro는 스로틀 자명이라 리허설 스킵 |
| 2 | 격리 확인 | **prod와 다른 보안그룹/서브넷**, 인바운드를 적재 백엔드 IP로만 제한. prod 트래픽이 인스턴스를 보지 못하게 한다 |
| 3 | 적재 백엔드 배선 | 백엔드 `DB_URL`을 ephemeral 엔드포인트로 오버라이드(**prod `DB_URL` 불변**). 기동 로그에서 Flyway가 **V39까지 적용**됐는지 확인(`flyway_schema_history` last=39) |
| 4 | 전량 적재 실행 | [운영 순서 표](../FOOD_CATALOG_GUIDE.md#공공데이터-전량-적재-운영-순서) 1~6번 그대로. `processed-foods`→`dish-foods`→`nutrient-db` 각각 `exhausted=true`까지 반복. **source별 `pageSize` 고정**(체크포인트는 pageSize 미저장) |
| 5 | dedup 수렴 | `POST /dedup/backfill`(멱등) 실행 |
| 6 | 정합성 대조 | `dedup_state`별 count가 acceptance target(canonical 323,899 / superseded 291,610 / **COLLISION 행 ~5,562** = 코드 2,781×2, + 기존 SEED/BRAND 보정)과 일치하는지. ⚠️ `dedup_state` count는 **행**이므로 코드 2,781이 아니라 행 ~5,562와 비교. ServingOption은 canonical 행에만 생성(옵션 폭증 0) |
| 7 | **용량·소요 측정** | 아래 "측정 항목" 기록. CloudWatch(prod 알람 동형: cpu/storage/connections)와 적재 소요시간 |
| 8 | 판단 | 측정값으로 본 적재 창 동안 **prod 클래스 일시 상향 필요 여부** 결정(아래 "판단 기준") |
| 9 | 인스턴스 폐기 | **A**: `terraform destroy`. **B**: `aws rds delete-db-instance --skip-final-snapshot` + 리허설 스냅샷 정리 |

### 예상 부하 (사전 추정 — 측정으로 갱신)

- food_catalog 615K행 × ~400B ≈ **~250MB**, ServingOption 1.3M × ~100B ≈ **~130MB**, 인덱스 포함 **1~2GB 미만** → allocated 20GB 안. **스토리지는 주 리스크 아님**(오토스케일 미트리거 가능).
- 주 리스크는 **1GB RAM에서 부분 유니크 인덱스 빌드/정렬 시 `maintenance_work_mem` 압박**과 **적재 소요/IOPS**. importer가 체크포인트 페이지네이션 + dedup 순서무관·재실행안전 + 백필 멱등이라 **치명적 OOM보다 "느림"** 쪽일 공산. 이 가정을 7번에서 실측으로 확인한다.

### 측정 항목 (7번에서 기록)

- 적재 총 소요(source별 + dedup backfill + 인덱스 빌드 체감)
- 스토리지 증가폭: 적재 전→후 `FreeStorageSpace` / allocated. 20GB 시작·100GB 오토스케일 한도 내인지
- 메모리 압력: `FreeableMemory` 최저점(db.t3.micro 1GB — 인덱스 빌드/정렬 시 스왑/압력 여부)
- `WriteIOPS`/`WriteLatency` 피크, `DatabaseConnections`
- 부분 유니크 인덱스 `uq_food_catalog_canonical` 빌드가 적재와 겹칠 때의 락/지연

### 판단 기준 (8번)

- **기본 플랜(권장)**: medium 리허설이 깔끔히 끝나면 → 본 적재 창 동안 prod를 **micro→db.t3.medium 일시 상향 → 적재 → 원복**. medium 리허설이 곧 이 플랜의 검증이므로 확신도가 높다.
- medium에서도 메모리 압력·IOPS 한계가 보이면 → 상향 클래스를 medium보다 더 위로(또는 적재 분할·창 확대) 재검토.
- (micro 유지 옵션은 권장 안 함 — 로컬·이론상 스로틀로 적재 창이 비현실적으로 길어짐.)
- 스토리지가 오토스케일(>20GB) 트리거되면 → 적재 후에도 축소되지 않으므로(오토스케일은 단방향) prod 적용 시 비용 영향 사전 합의.

## 함정 / 주의

- **prod `DB_URL`을 절대 ephemeral 인스턴스로 바꾸지 않는다.** 적재는 별도 백엔드/오버라이드에서만. prod 앱 배포(`dev-to-prod.yml`)는 `PROD_DB_URL` 시크릿을 그대로 쓴다.
- **스토리지 오토스케일은 비가역**(단방향 확장). 리허설에서 20GB를 넘기면 prod 본 적재에서도 동일하게 넘긴다는 신호다.
- **체크포인트는 pageSize를 저장하지 않는다.** 한 source 적재 중 pageSize를 바꾸면 중간 row를 건너뛴다. 리허설과 본 적재의 pageSize를 동일하게 유지(권장 100).
- **COLLISION 단위 = 코드 2,781 / `dedup_state` 행 ~5,562**(코드당 대표 2개). census 충돌 총수 3,961 중 영양값만 다른 1,360은 우선순위 출처값으로 병합돼 COLLISION 아님. `dedup_state` count(행)는 코드가 아니라 **행 ~5,562와 비교**. 실측(2026-06-26 로컬 전량): 2,872 코드 / 5,744 행이며, 그 **89%는 구두점만 다른 동일 제품**(`현미100%`↔`100`)이라 검토 큐 대부분은 사소한 노이즈다.
- 자동 병합 금지 — `/dedup/collisions`는 검토 큐 생성까지만. 코드 정정/양쪽 유지는 운영 검토 후 결정(설계 §9).
- **모드 B(스냅샷 복원)는 prod 데이터 사본**이다. 작업 종료 즉시 폐기하고 접근 IP를 적재 백엔드로 제한한다(PHI/데이터 노출 방지). 모드 A(신규)는 이 위험이 없다.

## 완료 기준

- [ ] (선택) 0단계 정합성 선검증 통과
- [ ] ephemeral 인스턴스 적재 결과 `dedup_state` 분포가 acceptance target과 일치
- [ ] source별 마지막 응답 `exhausted=true`, `attemptedCount`/`skippedRatio` 기록
- [ ] 측정 항목(소요·스토리지·메모리·IOPS) 기록 → 본 적재 클래스 상향 판단 도출
- [ ] ephemeral 인스턴스 삭제(+ 모드 B는 리허설 스냅샷 정리)
- [ ] 본 적재 창·클래스 정책을 운영 기록(또는 이슈 #68)에 남김
