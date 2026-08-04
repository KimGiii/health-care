# 프로젝트 재구조화 실행 계획

- 작성일: 2026-08-04
- 상태: **Phase 1 완료 (2026-08-04) · Phase 2~5 착수 전**
- 범위: 인프라 비용, Terraform 구성, 코드베이스 구조, 제품 방향 전환에 따른 정리
- 선행 근거: [`docs/design-docs/GAINSY_POSITIONING_STRATEGY_2026-07.md`](../design-docs/GAINSY_POSITIONING_STRATEGY_2026-07.md), [`docs/architecture-reviews/local-food-catalog-diet-recommendation-assessment-2026-08-03.md`](../architecture-reviews/local-food-catalog-diet-recommendation-assessment-2026-08-03.md)

---

## 0. 이미 완료된 것

2026-08-04 `infra/terraform/dev` 스택을 destroy했다. EC2 `i-03271b2c122d5faf3`(t3.micro), EIP `3.34.158.206`, Route53 `dev.api.gainsy.site` A 레코드, 보안 그룹, IAM 인스턴스 프로필, S3 부가 설정이 삭제됐다.

IAM 롤 `healthcare-dev-ec2-role`, 인라인 정책 `healthcare-dev-ec2-s3`, 빈 S3 버킷 `healthcare-dev-progress-photos-621770702801`은 `health-care-prod` 사용자에게 `iam:DeleteRolePolicy` 권한이 없어 잔존한다. 과금은 없다. 관리자 자격증명으로 `terraform destroy`를 재실행하면 정리된다.

---

## 1. 핵심 진단

### 1.1 코드 무게중심이 제품 방향과 반대다

포지셔닝 문서는 Gainsy를 **"한 주의 기록을 변화의 증거와 다음 행동으로 바꾸는 주간 변화 코치"**로 재정의했고, `목표 → 기록 → 즉시 의미 → 주간 변화 해석 → 다음 행동`의 닫힌 루프를 제품의 중심으로 선언했다. 동시에 **"가장 큰 음식 데이터베이스"를 경쟁하지 않을 영역**으로 명시했다.

그런데 백엔드 코드 분포는 정반대다.

| 도메인 | Java 파일 수 | 전체 대비 | 새 포지셔닝에서의 위상 |
|---|---:|---:|---|
| `diet` | 184 | 56.6% | 상당 부분이 de-scope 대상 |
| `exercise` | 24 | 7.4% | 기록 입력 |
| `bodymeasurement` | 21 | 6.5% | 변화 근거 |
| `goals` | 17 | 5.2% | 루프 시작점 |
| `nutrition` | 14 | 4.3% | 기록 보조 |
| `auth` | 11 | 3.4% | 인프라성 |
| `user` | 8 | 2.5% | 인프라성 |
| **`insights`** | **4** | **1.2%** | **새 제품의 심장** |
| 합계 | 325 | | |

`insights`는 `InsightsController`, `InsightsService`, `ChangeAnalysisResponse`, `WeeklySummaryResponse` 4개가 전부다. **제품이 약속하는 핵심 역량이 저장소에서 가장 얇은 코드다.**

`diet` 내부를 열면 격차가 더 분명하다.

| 하위 패키지 | 파일 수 | 성격 |
|---|---:|---|
| `external` | 54 | 외부 식품 데이터 수집·연동 |
| `recommendation` | 32 | 추천 엔진 |
| `mealphoto` | 18 | AI 사진 분석 |
| `allergen` | 15 | 알레르기 검증 |
| `admin` | 14 | 카탈로그 검수 |
| `dto`·`entity`·`controller` 등 | 51 | 공통 |

`external + recommendation + allergen + admin` = 115개 파일이 식품 카탈로그·추천 인프라에 묶여 있다. 전체 백엔드의 35%다.

### 1.2 식품 카탈로그는 현재 규모에서 자산이 아니라 부채다

어제 작성된 카탈로그 평가의 실사 수치가 이를 뒷받침한다.

- 활성 식품 **620,120건** 중 `SEARCH_ONLY`가 **604,676건**(97.5%)
- 런타임 추천 후보는 **11,979건**뿐이고, 그중 **75.8%가 `PROCESSED`**
- **`GRAIN` 0건, `VEGETABLE` 0건, `FRUIT` 0건** — 균형 잡힌 한국식 한 끼를 구성할 수 없다
- 식이섬유 결측 **592,285건** — 사실상 최적화에 사용 불가
- 알레르기 사용자가 통과할 수 있는 풀은 최대 **326건**

즉 62만 건의 카탈로그가 DB 용량과 검수 부담을 발생시키면서, 정작 추천 품질은 흰쌀밥·닭가슴살·브로콜리 같은 골격 식품을 못 쓰는 상태에 묶여 있다. **de-scope된 역량이 운영 비용의 상당 부분을 만들고 있다.**

### 1.3 인프라가 현재 사용 규모 대비 과하다

> **전제 정정 (2026-08-04):** 이 문서는 초기에 이 프로젝트를 "사용자 없는 사전 출시 단계"로 서술했으나, prod DB 실사 결과 **활성 사용자 약 50명**이 있다(2026-08-04 기준, 베타 테스터로 추정). 비용 절감 판단은 그대로 유효하지만, 다운타임과 데이터 유실이 실제 사용자에게 영향을 준다는 전제로 다뤄야 한다.

`dev` destroy 후 남은 `aws` 스택 38개 리소스 구성:

| 리소스 | 사양 | 월 비용 추정(ap-northeast-2, 온디맨드) |
|---|---|---:|
| EC2 `i-05e28e21e906ecc1f` | t3.medium 24/7 | ~$38 |
| RDS PostgreSQL 17.7 | db.t3.micro, 20GB, Single-AZ | ~$21 |
| ElastiCache Redis | cache.t3.micro | ~$15 |
| Route53 존 + CloudWatch 알람 4개 + S3 + ECR | | ~$2 |
| **합계** | | **~$76/월** |

> 이 수치는 공개 요금표 기반 **추정치**다. `health-care-prod` 사용자에게 `ce:GetCostAndUsage` 권한이 없어 실제 청구액을 확인하지 못했다. Billing 콘솔에서 검증이 필요하다.

Redis는 `RedisConfig`, `UserService`, `ExternalFoodSearchService` 3곳에서만 쓰인다. 이 중 `ExternalFoodSearchService`는 de-scope 대상인 식품 카탈로그 소속이다. **단일 인스턴스 애플리케이션에 전용 ElastiCache 클러스터를 붙일 이유가 남지 않는다.**

### 1.4 Terraform 구성이 흩어져 있다

- 스택 3개(`dev`·`rehearsal`·`aws`) 중 `dev`는 방금 비웠고, `rehearsal`은 리소스 0개인 **죽은 state**(serial 22)만 남아 있다.
- 공통 모듈이 없다. `dev`가 `aws`의 compute·dns·s3 구성을 복사해 갖고 있었다.
- **state가 로컬 파일에만 있다.** `.gitignore` 처리되어 원격 백업도, 잠금도 없다. 노트북 손실 = 인프라 관리 불능.
- `aws` 스택이 `environment = "prod"` 태그를 달고 있지만 실질적으로 유일한 환경이다. 이름과 실제 용도가 불일치한다.
- `terraform.tfvars`에 DB 비밀번호 `HealthCareAdmin1165!`가 평문으로 있다. git 추적은 안 되지만 로컬 평문이다.
- `health-care-prod` IAM 사용자 권한이 불완전하다. `iam:DeleteRolePolicy`, `s3:ListBucketVersions`, `ecr:ListImages`, `ce:GetCostAndUsage`, 자기 정책 조회까지 거부된다. `infra/iam/health-care-dev-policy.json`에는 필요한 권한이 정의돼 있으나 **실제로 사용자에게 부착되지 않았다.**

### 1.5 저장소 위생

- `ios/build` **1.4GB**가 작업 트리에 있다. `ios/.gitignore`로 추적은 안 되지만 로컬 용량을 점유한다.
- `.git` 71MB. `docs/references` 11MB + `docs/screenshots` 9.7MB가 git에 추적된다. 6.1MB CSV 1개, 3.0MB PNG 1개 등 대용량 바이너리가 히스토리에 박혀 있다.
- iOS는 이미 피벗을 반영 중이다. 최근 커밋에서 Explore 탭 제거, 목표 독립 탭 승격, 홈 히어로 카드 추가가 이뤄졌다. **iOS가 백엔드보다 앞서 있다.**

---

## 2. 실행 계획

### Phase 1 — 인프라 비용 즉시 절감 — **완료 (2026-08-04)**

이슈 [#111](https://github.com/KimGiii/Gainsy/issues/111) · PR [#112](https://github.com/KimGiii/Gainsy/pull/112) · 커밋 `f9f74ab`

| 작업 | 결과 | 실제 절감 |
|---|---|---:|
| ElastiCache Redis 제거, Caffeine 인프로세스 캐시로 교체 | **완료** — 클러스터·서브넷 그룹·보안 그룹 삭제 | ~$15/월 |
| ~~EC2 t3.medium → t3.small 다운사이징~~ → **t3a.medium 전환** | **계획 폐기 후 대체** (아래 참고) | ~$4/월 |
| CloudWatch 알람 4개 정리 | **완료** — 4개 모두 `alarm_actions`가 비어 아무에게도 알리지 않던 알람 | ~$0.4/월 |
| RDS 자동 백업 보존 기간 점검 | 7일 유지 적정, Performance Insights도 7일 무료 티어 | — |

**합계 ~$19/월 절감(약 25%).** 개발은 `backend/docker-compose.yml`(PostgreSQL·LocalStack)로 로컬 수행하므로 상시 개발서버가 없어도 지장 없다.

> 여전히 공개 요금표 기반 **추정치**다. `ce:GetCostAndUsage` 권한이 없어 실제 청구액은 미확인 상태이며 Billing 콘솔 검증이 남아 있다.

#### t3.small 다운사이징 계획을 폐기한 이유

초안의 ~$34/월은 t3.small 전환을 전제했으나 **불가능하다는 것이 확인됐다.**

blue-green 배포 중 앱 컨테이너 2개(각 `--memory 1400m`)에 Prometheus(250m)·Grafana(350m)가 동시에 떠서 피크가 **약 3.8GB**에 달한다. `infra/terraform/aws/variables.tf`에도 과거 t3.small에서 메모리 부족으로 t3.medium으로 상향한 기록이 주석으로 남아 있었다.

CPU는 14일 평균 3.8%·최대 37%로 명백히 과잉이지만, **제약은 CPU가 아니라 메모리다.** 사양(2 vCPU / 4 GiB / x86_64)이 동일한 AMD 변형 t3a.medium으로 전환해 단가만 낮췄다.

메모리를 실제로 줄이려면 blue-green을 롤링 단일 컨테이너 방식으로 바꾸거나 모니터링 스택을 앱 박스에서 분리해야 한다. 별도 과제다.

#### apply를 막고 있던 기존 지뢰 2건 (함께 수정)

Phase 1과 무관하게 존재하던 문제로, 다음 `terraform apply`에서 터졌을 것이다.

| 리소스 | 문제 | 수정 |
|---|---|---|
| `aws_instance.app` | `data.aws_ami.ubuntu`가 `most_recent = true`라 새 Ubuntu 이미지마다 `ami`가 바뀌고, ForceNew 속성이라 **인스턴스를 교체**하려 했다. certbot TLS 인증서·배포 스크립트가 수정한 nginx 설정·Prometheus/Grafana 도커 볼륨이 유실될 수 있었다 | `lifecycle { ignore_changes = [ami] }` |
| `aws_db_instance.postgres` | 실제 17.9인데 설정이 17.7에 고정되어 매번 **다운그레이드**를 계획했다. RDS는 다운그레이드를 지원하지 않아 apply가 실패한다 | 메이저만 고정 (`"17"`) |

#### 적용 결과

`terraform apply` — **0 added, 2 changed, 7 destroyed.** 교체 없이 인스턴스 ID(`i-05e28e21e906ecc1f`)와 EIP(`15.165.250.185`)가 보존됐다. EC2 stop/start로 수 분 다운타임이 발생했고 컨테이너 3개는 자동 복귀했다.

apply 직후 `/actuator/health`가 503을 반환했다 — 당시 실행 중이던 구버전 이미지가 `spring-boot-starter-data-redis`의 health indicator로 사라진 ElastiCache를 찾았기 때문이다. `readiness`는 UP이었고 실제 API도 정상(401 응답)이었으며, 예측대로 `CacheErrorHandler`가 DB 경로로 우회했다. 이 503은 [V22→V41 릴리스](MIGRATION_RELEASE_V22_V41.md)에서 Caffeine 빌드가 배포되며 해소됐다.

#### 남은 잔여 작업

- dev 스택의 IAM 롤 `healthcare-dev-ec2-role`·인라인 정책·빈 S3 버킷 — `iam:DeleteRolePolicy` 권한이 없어 잔존(과금 없음). 관리자 자격증명으로 `infra/terraform/dev`에서 `terraform destroy`
- GitHub Secrets `PROD_REDIS_HOST`·`DEV_REDIS_HOST` 삭제 가능
- `.github/workflows/deploy-dev.yml`은 destroy된 dev 서버 대상이라 현재 무효 — Phase 2 정리 대상
- Billing 콘솔에서 실제 절감액 확인

> RDS는 유지를 권한다. `deletion_protection = true` + 최종 스냅샷 설정이 걸려 있고, 데이터 재구축 비용이 월 $21보다 크다.

### Phase 2 — Terraform 정리 — **부분 완료 (2026-08-04)**

이슈 [#116](https://github.com/KimGiii/Gainsy/issues/116)

조사 결과 7개 항목 중 **2개 취소·보류**(전제가 틀렸거나 추측성 추상화), **4개 완료**, **1개는 IAM 권한 확보 후로 이월**됐다.


1. ~~`rehearsal` 죽은 state 삭제, 디렉터리 제거.~~ → **취소**

   README를 읽어보니 죽은 스택이 아니었다. 식품 카탈로그 전량 적재 전 용량·소요·정합성을 측정하는 **의도적으로 일회성인 "쓰고 버리는" 스택**이며, 리소스 0개는 destroy 후의 정상 상태다. 게다가 [마이그레이션 릴리스 계획 §6.1](MIGRATION_RELEASE_V22_V41.md)의 "prod 카탈로그 적재"가 바로 이 도구를 쓰는 작업이다. **유지한다.**

2. ~~`aws` 스택을 모듈 + 환경 디렉터리 구조로 재편.~~ → **보류**

   지금은 추측성 추상화다.

   - 실환경이 `aws/` 하나뿐이다. 모듈을 소비할 두 번째 대상이 없다.
   - `dev/`는 일회성으로 재정의되어 상시 존재하지 않는다. 중복 유지 비용이 낮다.
   - 디렉터리 이름을 바꾸면 App Store 심사 기록 등 **역사적 문서 8곳 이상의 링크가 깨진다.**

   `aws/`라는 이름과 `environment = "prod"`의 불일치는 겉모습 문제이고, 실제 문제였던 원격 state는 3번에서 해소됐다. 두 번째 상시 환경이 필요해질 때 다시 판단한다. 근거는 [infra/terraform/README.md](../../infra/terraform/README.md)에 기록했다.

3. **state를 S3로 원격화.** 최우선 항목. → **완료**

   `prod/terraform.tfstate`·`dev/terraform.tfstate` 두 키로 `healthcare-terraform-state-621770702801` 버킷에 이전했다. 버저닝·AES256 암호화·퍼블릭 전면 차단 적용. 이전 후 `terraform plan`이 **No changes**로 인프라와 일치함을 확인했다.

   **잠금은 DynamoDB가 아니라 S3 네이티브(`use_lockfile`)를 쓴다.** Terraform 1.10에서 도입됐고, DynamoDB 기반 잠금은 폐기 예정이며, 현재 실행 주체에 `dynamodb:*` 권한이 없다는 제약과도 맞는다. 이 결정으로 DynamoDB 권한 공백이 차단 요인에서 빠졌다.

   dev state도 함께 옮긴 이유: destroy 잔여 리소스(IAM 롤·정책·빈 버킷) 정리를 특정 노트북이 아니라 **권한을 가진 사람이 어디서든** 마무리할 수 있어야 하기 때문이다.

4. DB 비밀번호를 SSM Parameter Store 또는 Secrets Manager로 이전. → **이월** — `ssm:*`·`secretsmanager:*` 권한이 없다. 5번 선행 필요.

5. IAM 정책을 실제 부착 상태와 정합화. → **문서화 완료, 실행은 관리자 몫**

   조사 결과 문제는 "정책을 부착하지 않았다"가 아니라 **레포의 정책 파일이 실제와 양방향으로 어긋나 있다**는 것이었다.

   - `terraform-executor-policy.json`은 S3·IAM만 정의하는데 EC2·RDS·VPC apply가 실제로 성공한다 → **실제보다 좁다**
   - `health-care-dev-policy.json`은 `iam:DeleteRolePolicy`를 올바른 범위로 정의하지만 부착되지 않았다 → **실제보다 넓다**

   `health-care-prod` 사용자는 `iam:ListAttachedUserPolicies` 권한조차 없어 자기 정책을 조회할 수 없다. **권한 진단 자체가 불가능하다.** 확인된 공백 목록과 정합화 절차를 [infra/iam/README.md](../../infra/iam/README.md)에 기록했다. 정책 부착·수정은 관리자 자격증명이 필요해 에이전트가 수행할 수 없다.

6. `dev` 환경을 일회성으로 재정의. → **완료** — [infra/terraform/README.md](../../infra/terraform/README.md)에 스택별 수명, destroy 잔여물 정리 절차, `deploy-dev.yml`이 현재 무효라는 사실을 기록했다.

7. **배포 스크립트의 도커 이미지 정리 로직 보강.** → **완료** — `docker image prune -f`를 `-a --filter "until=168h"`로 바꿔 태그된 구버전 ECR 이미지도 대상에 넣되 최근 7일치는 롤백용으로 남긴다. 정리 후 디스크 사용률을 로그로 남기도록 추가했다.

#### Phase 1·릴리스 과정에서 확인된 IAM 권한 공백

`health-care-prod` 사용자에게 아래 권한이 없어 작업이 중단되거나 우회가 필요했다. Phase 2의 IAM 정책 정비에 반드시 포함한다.

| 액션 | 막힌 작업 |
|---|---|
| `iam:DeleteRolePolicy` | dev 스택 IAM 롤 destroy 미완 (리소스 잔존) |
| `rds:CreateDBSnapshot` · `rds:DescribeDBSnapshots` | 릴리스 전 수동 스냅샷 생성 불가 (PITR로 대체) |
| `ce:GetCostAndUsage` | 실제 청구액 확인 불가 (전 구간 추정치로 진행) |
| `cloudwatch:ListMetrics` | 메모리 메트릭 존재 여부 확인 불가 |
| `s3:ListBucketVersions` · `ecr:ListImages` | destroy 전 잔여 객체 확인 불가 |
| `iam:ListAttachedUserPolicies` | 자기 정책 조회조차 불가 — 권한 진단 자체가 어려움 |

`infra/iam/health-care-dev-policy.json`에는 `iam:DeleteRolePolicy`가 올바른 범위(`role/healthcare-dev-*`)로 정의돼 있으나 **실제 사용자에게 부착되지 않은 상태**였다. 정책 파일과 실제 부착 상태가 어긋나 있다는 것이 핵심 문제다.

### Phase 3 — 제품 방향 전환에 따른 코드 정리 (1~2주)

**원칙: 삭제가 아니라 격리 후 축소.** 식단 추천은 R1까지 진행됐고 베타 테스터 모집 문서까지 나온 상태라 통째로 버릴 대상이 아니다. 다만 **투자 비중을 낮추고 카탈로그를 축소**해야 한다.

1. **카탈로그 축소.** `SEARCH_ONLY` 604,676건을 검색 전용 경로로 완전히 격리하고, 추천은 curated pool만 바라보게 한다. 장기적으로 검색 카탈로그를 외부 API 조회로 전환하면 DB 용량과 검수 부담이 함께 줄어든다.
2. **평가 문서의 우선 3개 항목을 그대로 수행한다.** 곡류·단백질원·채소·과일에 검증 제공량 추가 → 식품군 역할 기반 끼니 템플릿 → 나트륨·식이섬유·당류 강화. 이건 규모 확장이 아니라 **소수 정예 풀의 품질 회복**이므로 새 포지셔닝과 충돌하지 않는다.
3. **`diet/external` 54개 파일의 유지 비용을 재평가한다.** 카탈로그 확장이 de-scope됐다면 수집 파이프라인 상당수가 유휴 자산이다. 어떤 것이 curated pool 유지에 필요한지 선별한다.
4. **`diet/admin` 14개 파일 검수 도구는 유지한다.** 풀 품질 회복이 Phase 3의 핵심이므로 오히려 필요하다.

### Phase 4 — `insights` 도메인 강화 (2~3주, 최우선 투자)

**이번 재구조화의 진짜 목적지다.** 새 포지셔닝의 핵심 루프를 담당할 코드가 4개 파일뿐이라는 것이 가장 큰 구조적 결함이다.

필요한 것:

- 주간 변화 해석 엔진 — 체중·수행·근육량·치수·사진을 한 주 단위로 연결
- "다음 한 걸음" 추천 — 유지/조정/관찰 중 하나를 근거와 함께 제시
- 비체중 변화 서사 — 포지셔닝 문서가 명시한 좌절 방지 장치
- 홈 화면이 소비할 주간 루프 API

`goals`(17) + `bodymeasurement`(21) + `insights`(4)가 실질적으로 하나의 응집된 도메인으로 동작해야 한다. 현재는 분리돼 있고 그 사이를 잇는 코드가 없다.

### Phase 5 — 저장소 위생 (0.5일)

- `ios/build` 1.4GB 로컬 정리 및 정리 스크립트화
- `docs/references`의 대용량 CSV를 git 추적에서 제외하고 외부 스토리지 또는 Git LFS로 이전 검토 (히스토리 재작성은 별도 판단)
- `docs/screenshots` PNG 최적화

---

## 3. 권장 순서와 근거

```
Phase 1 (비용)  →  Phase 2 (Terraform)  →  Phase 4 (insights)  →  Phase 3 (diet 축소)  →  Phase 5 (위생)
  ✅ 완료           🟡 부분 완료              2~3주                  1~2주                 0.5일
```

Phase 2의 잔여분(시크릿 이관)은 IAM 권한 정합화에 막혀 있고 그것은 관리자 자격증명이 필요하다. **Phase 4는 이 차단과 무관하게 착수할 수 있다.**

Phase 4를 Phase 3보다 앞에 두는 이유: **제품의 새 심장을 먼저 키우고, 그 다음에 기존 무게중심을 덜어내는 것이 안전하다.** 반대 순서면 `diet`를 줄이는 동안 제품에 남는 것이 없다.

Phase 2의 state 원격화는 다른 모든 인프라 작업의 전제이므로 Phase 1 직후에 둔다.

### 계획 밖에서 파생된 작업

Phase 1 진행 중 `dev`가 `prod`보다 백엔드 커밋 124개·Flyway 마이그레이션 20개 앞서 있다는 것이 드러나 별도 릴리스 계획으로 분리했다 → [MIGRATION_RELEASE_V22_V41.md](MIGRATION_RELEASE_V22_V41.md) (**2026-08-04 완료**).

그 계획의 §6이 이어질 작업을 정의한다 — prod 카탈로그 적재 → 품질 회복 → iOS 출시. **Phase 3의 카탈로그 축소·품질 회복과 같은 대상**이므로 실행 시 함께 다룬다.

---

## 4. 착수 전 확인이 필요한 것

| 항목 | 상태 |
|---|---|
| **실제 AWS 청구액** — Billing 콘솔에서 추정치 검증 | 미확인 (`ce:GetCostAndUsage` 권한 없음) |
| ~~**EC2 t3.medium 실제 부하** — 다운사이징 판단 근거~~ | **확인 완료** — CPU 14일 평균 3.8%·최대 37%. 다만 제약은 CPU가 아니라 blue-green 피크 메모리 3.8GB로 판명, 다운사이징 불가 |
| **베타 테스터 모집 진행 상태** — 식단 추천 de-scope 폭에 영향 | 활성 사용자 50명 확인. 모집 단계·목표는 미확인 |
| **`diet/external` 수집 파이프라인 중 curated pool 유지에 필수인 범위** | 미확인 |
| **prod 카탈로그 적재 범위** — 62만 건 전량 vs curated pool만 | 미결정 (제품 결정) |

### 별도 이슈가 필요한 발견

**FCM 푸시가 2026-05-15부터 실패 중이다.** 호스트의 `/etc/healthcare/fcm-credentials.json`이 파일이 아니라 디렉터리로 존재해(볼륨 마운트 시 Docker가 자동 생성) `FcmConfig`가 기동마다 ERROR를 남긴다. 약 2개월 반 동안 주간 회고 푸시가 조용히 실패해 왔다. 인증 정보 파일 배치가 필요하다.

이건 이 재구조화의 범위 밖이지만, **포지셔닝 문서가 "주간 회고"를 새 제품의 핵심 루프로 지목한 만큼 우선순위가 낮지 않다.**

---

## 5. GitHub Issue 분해 제안

프로젝트 워크플로우에 따라 Phase별로 이슈를 만든다.

| Phase | 이슈 제목 | 브랜치 접두어 | 상태 |
|---|---|---|---|
| 1 | 인프라 비용 절감 — ElastiCache 제거·알람 정리·t3a 전환 | `refactor/` | **완료** — [#111](https://github.com/KimGiii/Gainsy/issues/111) / [#112](https://github.com/KimGiii/Gainsy/pull/112) |
| — | V22→V41 백엔드 다크 릴리스 | `chore/` | **완료** — [계획서](MIGRATION_RELEASE_V22_V41.md) |
| 2 | Terraform 재구성 — S3 원격 state·IAM 정합·배포 정리 | `refactor/` | **부분 완료** — [#116](https://github.com/KimGiii/Gainsy/issues/116) (시크릿 이관 이월) |
| 4 | insights 도메인 강화 — 주간 변화 루프 백엔드 | `feat/` | 미착수 |
| 3 | 식품 카탈로그 축소 및 curated pool 품질 회복 | `refactor/` | 미착수 |
| 5 | 저장소 위생 — 빌드 산출물·대용량 문서 자산 정리 | `chore/` | 미착수 |
| — | FCM 인증 파일 복구 — 2026-05-15부터 푸시 실패 | `fix/` | [#115](https://github.com/KimGiii/Gainsy/issues/115) |
