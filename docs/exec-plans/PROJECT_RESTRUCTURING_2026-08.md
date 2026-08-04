# 프로젝트 재구조화 실행 계획

- 작성일: 2026-08-04
- 상태: 제안 / 착수 전
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

### 1.3 인프라가 사전 출시 단계 대비 과하다

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

### Phase 1 — 인프라 비용 즉시 절감 (1일)

| 작업 | 근거 | 예상 절감 |
|---|---|---:|
| ElastiCache Redis 제거, Caffeine 인프로세스 캐시로 교체 | 단일 인스턴스, 사용처 3곳, 그중 1곳은 de-scope 대상 | ~$15/월 |
| EC2 t3.medium → t3.small 다운사이징 | 사전 출시, 사용자 없음. 부하 지표로 검증 후 | ~$19/월 |
| CloudWatch 알람 4개 정리 | 사용자 없는 단계에서 RDS 연결수·CPU 알람 불필요 | ~$0.4/월 |
| RDS 자동 백업 보존 기간 점검 | 스토리지 비용 | 소액 |

**합계 ~$34/월 절감(약 45%).** 개발은 `backend/docker-compose.yml`(PostgreSQL·Redis·LocalStack)로 로컬 수행하므로 상시 개발서버가 없어도 지장 없다.

> RDS는 유지를 권한다. `deletion_protection = true` + 최종 스냅샷 설정이 걸려 있고, 데이터 재구축 비용이 월 $21보다 크다.

### Phase 2 — Terraform 정리 (2~3일)

1. `rehearsal` 죽은 state 삭제, 디렉터리 제거.
2. `aws` 스택을 `environment` 변수 기반 구조로 재편.
   ```
   infra/terraform/
   ├── modules/          # network, compute, database, storage, dns
   └── envs/
       ├── prod/
       └── dev/          # 필요 시 apply, 평소 destroy 상태 유지
   ```
3. **state를 S3 백엔드 + DynamoDB 잠금으로 이전.** 최우선 항목이다.
4. DB 비밀번호를 SSM Parameter Store(SecureString) 또는 Secrets Manager로 이전, `tfvars`에서 제거.
5. `infra/iam/health-care-dev-policy.json`을 실제 IAM 사용자에게 부착. 부착 절차를 `infra/iam/README.md`에 검증 명령과 함께 기록.
6. `dev` 환경은 "상시 가동"이 아니라 **필요할 때 apply → 끝나면 destroy**하는 일회성 환경으로 재정의. 이번 destroy를 그 기본 상태로 삼는다.

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
   1일              2~3일                    2~3주                  1~2주                 0.5일
```

Phase 4를 Phase 3보다 앞에 두는 이유: **제품의 새 심장을 먼저 키우고, 그 다음에 기존 무게중심을 덜어내는 것이 안전하다.** 반대 순서면 `diet`를 줄이는 동안 제품에 남는 것이 없다.

Phase 2의 state 원격화는 다른 모든 인프라 작업의 전제이므로 Phase 1 직후에 둔다.

---

## 4. 착수 전 확인이 필요한 것

1. **실제 AWS 청구액** — Billing 콘솔에서 위 추정치 검증
2. **EC2 t3.medium 실제 부하** — 다운사이징 판단 근거
3. **베타 테스터 모집 진행 상태** — 식단 추천 de-scope 폭에 영향
4. **`diet/external` 수집 파이프라인 중 curated pool 유지에 필수인 범위**

---

## 5. GitHub Issue 분해 제안

프로젝트 워크플로우에 따라 Phase별로 이슈를 만든다.

| Phase | 이슈 제목 | 브랜치 접두어 |
|---|---|---|
| 1 | 인프라 비용 절감 — Redis 제거·EC2 다운사이징·알람 정리 | `refactor/` |
| 2 | Terraform 재구성 — 모듈화·S3 원격 state·시크릿 이전 | `refactor/` |
| 4 | insights 도메인 강화 — 주간 변화 루프 백엔드 | `feat/` |
| 3 | 식품 카탈로그 축소 및 curated pool 품질 회복 | `refactor/` |
| 5 | 저장소 위생 — 빌드 산출물·대용량 문서 자산 정리 | `chore/` |
