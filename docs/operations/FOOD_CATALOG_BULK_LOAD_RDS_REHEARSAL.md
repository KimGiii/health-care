# 식품 카탈로그 전량 적재 — G2 RDS 스냅샷 리허설 runbook

운영자가 직접 수행하는 수동 절차다. 코드/검증 게이트(G1)는 이미 커밋·CI에서 자동 실행된다.
이 문서는 **prod RDS에 본 적재를 돌리기 전**, 동일 적재를 격리된 임시 인스턴스에서 1회 리허설해
용량·소요·정합성을 측정하는 절차를 정의한다.

> **위치**: 전량 적재 운영 순서 표는 [FOOD_CATALOG_GUIDE](../FOOD_CATALOG_GUIDE.md#공공데이터-전량-적재-운영-순서),
> dedup 설계는 [DIET_FOOD_CATALOG_SOURCE_PRIORITY_DEDUP](../exec-plans/DIET_FOOD_CATALOG_SOURCE_PRIORITY_DEDUP.md).
> 이 runbook은 그 표의 **0번(사전 조건)을 RDS 관점에서 확장**한 것이다 — 적재 절차 자체는 중복 기술하지 않는다.

## 왜 리허설이 필요한가 (부하 대상의 비대칭)

배포 토폴로지가 앱 호스트와 DB로 물리 분리돼 있어, 적재 부하가 떨어지는 위치를 혼동하기 쉽다.

| 작업 | 떨어지는 곳 | 여유 |
|---|---|---|
| importer/JVM, MFDS API ~9,027콜 | **앱 호스트 EC2 t3.medium**(4GB) | 여유 있음 |
| 615,509행 INSERT + ~130만 ServingOption + 부분 유니크 인덱스 빌드 쓰기 | **prod RDS db.t3.micro**(1GB, 20→100GB 오토스케일) | **빠듯 — 측정 필요** |

→ 앱 호스트가 t3.medium이라 해도 **쓰기 부하는 전적으로 db.t3.micro로 간다**. 본 적재 전에
임시 인스턴스에서 실제 쓰기량·인덱스 빌드 시간·스토리지 증가폭·메모리/IOPS 압력을 확인하고,
본 적재 창 동안 prod RDS 클래스를 일시 상향할지 판단한다.

## 게이트 단계 위치

- **G1 정합성(자동, 완료)**: `FoodCatalogDedupLoadIT` — 실 PG17 + Flyway V39에서 적재 경로 전체 검증. CI postgres 17 서비스.
- **G2 운영 리허설(이 문서)**: prod 스냅샷 → 임시 인스턴스 → 전량 적재 1회 → acceptance target 대조 + 용량 측정 → 임시 인스턴스 폐기.
- **본 적재**: G2 통과 후 prod RDS에 실행([FOOD_CATALOG_GUIDE 운영 순서](../FOOD_CATALOG_GUIDE.md#공공데이터-전량-적재-운영-순서) 4~10번).

## 사전 조건

- AWS 콘솔/CLI 권한(RDS 스냅샷·복원·삭제, 보안그룹)
- prod RDS 식별자 및 현재 클래스 확인(`db.t3.micro`, PG `17.7`, `infra/terraform/aws/database.tf`)
- 임시 인스턴스를 적재할 백엔드 1대(앱 호스트와 별도 EC2 또는 로컬에서 `DB_URL`만 임시 인스턴스로 오버라이드)
- 시크릿/환경값: `PUBLIC_FOOD_API_KEY`, `ADMIN_OPERATION_TOKEN`, 임시 `DB_URL`/`DB_USERNAME`/`DB_PASSWORD`
  (prod 앱은 `DB_URL`/`DB_USERNAME`/`DB_PASSWORD` env를 읽는다 — `application-prod.yml`, 시크릿 `PROD_DB_*`)
- 검증 기준값(acceptance target): 적재 **615,509**행 → canonical **323,899** / superseded **291,610**(47.4%) / COLLISION 코드 **2,781**
  ([FOOD_CATALOG_DEDUP_LOAD_PROJECTION](../references/FOOD_CATALOG_DEDUP_LOAD_PROJECTION.md))

## 절차

| 순서 | 작업 | 명령/확인 |
|---:|---|---|
| 0 | prod 스냅샷 생성 | `aws rds create-db-snapshot --db-instance-identifier <prod-id> --db-snapshot-identifier catalog-load-rehearsal-$(date +%Y%m%d)`. `available` 까지 대기 |
| 1 | 임시 인스턴스 복원 | `aws rds restore-db-instance-from-db-snapshot --db-instance-identifier catalog-rehearsal --db-snapshot-identifier <위 스냅샷> --db-instance-class db.t3.medium`. **클래스를 의도적으로 한 단계 위로** 복원해 "여유 있을 때의 소요"를 먼저 잡는다 |
| 2 | 격리 확인 | 임시 인스턴스는 **prod와 다른 보안그룹/서브넷**에 두거나 인바운드를 적재용 백엔드 IP로만 제한. prod 트래픽이 임시 인스턴스를 보지 못하게 한다 |
| 3 | 적재 백엔드 배선 | 적재용 백엔드의 `DB_URL`을 임시 인스턴스 엔드포인트로 오버라이드(prod `DB_URL` 건드리지 않음). Flyway가 V39까지 이미 적용돼 있는지(스냅샷 시점 스키마) 확인 |
| 4 | 전량 적재 실행 | [운영 순서 표](../FOOD_CATALOG_GUIDE.md#공공데이터-전량-적재-운영-순서) 1~6번 그대로. `processed-foods`→`dish-foods`→`nutrient-db` 각각 `exhausted=true`까지 반복. **source별 `pageSize` 고정**(체크포인트는 pageSize 미저장) |
| 5 | dedup 수렴 | `POST /dedup/backfill`(멱등) 실행 |
| 6 | 정합성 대조 | `dedup_state`별 count가 acceptance target(canonical 323,899 / superseded 291,610 / COLLISION 2,781, + 기존 SEED/BRAND 보정)과 일치하는지. ServingOption은 canonical 행에만 생성(옵션 폭증 0) |
| 7 | **용량·소요 측정** | 아래 "측정 항목" 기록. CloudWatch(prod 알람 동형: cpu/storage/connections)와 적재 소요시간 |
| 8 | 판단 | 측정값으로 본 적재 창 동안 **prod 클래스 일시 상향 필요 여부** 결정(아래 "판단 기준") |
| 9 | 임시 인스턴스 폐기 | `aws rds delete-db-instance --db-instance-identifier catalog-rehearsal --skip-final-snapshot`. 리허설 스냅샷도 불필요하면 삭제 |

### 측정 항목 (7번에서 기록)

- 적재 총 소요(source별 + dedup backfill + 인덱스 빌드 체감)
- 스토리지 증가폭: 적재 전→후 `FreeStorageSpace` / allocated. 20GB 시작·100GB 오토스케일 한도 내인지
- 메모리 압력: `FreeableMemory` 최저점(db.t3.micro 1GB — 인덱스 빌드/정렬 시 스왑/압력 여부)
- `WriteIOPS`/`WriteLatency` 피크, `DatabaseConnections`
- 부분 유니크 인덱스 `uq_food_catalog_canonical` 빌드가 적재와 겹칠 때의 락/지연

### 판단 기준 (8번)

- 임시 db.t3.medium에서도 메모리 압력·IOPS 한계가 보이면 → 본 적재 창 동안 prod를 **최소 동급(db.t3.medium) 이상으로 일시 상향** 후 적재, 완료 후 원복.
- db.t3.medium에서 여유로우면 → prod db.t3.micro 유지하되, **적재 창에 트래픽 한산 시간대 선택 + 스냅샷 선행** 조건으로 진행 가능. 단 micro에서의 micro-급 재측정은 별도 판단.
- 스토리지가 오토스케일(>20GB) 트리거되면 → 적재 후에도 축소되지 않으므로(오토스케일은 단방향) prod 적용 시 비용 영향 사전 합의.

## 함정 / 주의

- **prod `DB_URL`을 절대 임시 인스턴스로 바꾸지 않는다.** 적재는 별도 백엔드/오버라이드에서만. prod 앱 배포(`dev-to-prod.yml`)는 `PROD_DB_URL` 시크릿을 그대로 쓴다.
- **스토리지 오토스케일은 비가역**(단방향 확장). 리허설에서 20GB를 넘기면 prod 본 적재에서도 동일하게 넘긴다는 신호다.
- **체크포인트는 pageSize를 저장하지 않는다.** 한 source 적재 중 pageSize를 바꾸면 중간 row를 건너뛴다. 리허설과 본 적재의 pageSize를 동일하게 유지(권장 100).
- **COLLISION은 이름 차이 2,781 기준**이다(census 충돌 총수 3,961 중 영양값만 다른 1,360은 우선순위 출처값으로 병합돼 COLLISION 아님). 대조 시 2,781을 기준값으로 본다.
- 자동 병합 금지 — `/dedup/collisions`는 검토 큐 생성까지만. 코드 정정/양쪽 유지는 운영 검토 후 결정(설계 §9).
- 임시 인스턴스는 prod 데이터 사본이다. 작업 종료 즉시 폐기하고, 접근 IP를 적재 백엔드로 제한한다(PHI/데이터 노출 방지).

## 완료 기준

- [ ] 임시 인스턴스 적재 결과 `dedup_state` 분포가 acceptance target과 일치
- [ ] source별 마지막 응답 `exhausted=true`, `attemptedCount`/`skippedRatio` 기록
- [ ] 측정 항목(소요·스토리지·메모리·IOPS) 기록 → 본 적재 클래스 상향 판단 도출
- [ ] 임시 인스턴스 삭제, 리허설 스냅샷 정리
- [ ] 본 적재 창·클래스 정책을 운영 기록(또는 이슈 #68)에 남김
