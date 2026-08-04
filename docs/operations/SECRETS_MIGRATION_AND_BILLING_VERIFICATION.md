# 시크릿 이관 · 청구액 검증 런북

- 작성일: 2026-08-04
- 상태: 실행 대기 — **1단계는 관리자 자격증명 필요**
- 이슈: [#118](https://github.com/KimGiii/Gainsy/issues/118)
- 배경: [재구조화 계획 Phase 2](../exec-plans/PROJECT_RESTRUCTURING_2026-08.md)

---

## 0. 지금 상태

DB 비밀번호가 두 곳에 평문으로 있고 수동 동기화된다.

| 위치 | 용도 | git 추적 |
|---|---|---|
| `infra/terraform/aws/terraform.tfvars` → `db_password` | Terraform이 RDS 마스터 비밀번호를 설정 | 미추적(gitignore) |
| GitHub Secret `PROD_DB_PASSWORD` | 앱 컨테이너 env `DB_PASSWORD` 주입 | — |

두 값이 어긋나면 앱이 DB에 붙지 못한다. 실제로 동기화를 강제하는 장치가 없다.

**청구액 검증**은 `ce:GetCostAndUsage` 권한이 없어 CLI로 못 하고 있다. 계획서 전 구간이 공개 요금표 기반 추정치다.

---

## 1. 권한 부여 (관리자 자격증명 필요)

에이전트나 `health-care-prod` 사용자로는 수행할 수 없다. **관리자 권한을 가진 주체가 실행한다.**

### 1.1 현재 부착 상태부터 확인

레포의 정책 파일이 실제와 어긋나 있으므로([infra/iam/README.md](../../infra/iam/README.md)), 무엇이 실제로 붙어 있는지부터 본다.

```bash
aws iam list-attached-user-policies --user-name health-care-prod
aws iam list-user-policies --user-name health-care-prod
```

### 1.2 공백 보완 정책 생성·부착

```bash
ACCOUNT=621770702801

aws iam create-policy \
  --policy-name HealthcareGapPolicy \
  --policy-document file://infra/iam/health-care-gap-policy.json

aws iam attach-user-policy \
  --user-name health-care-prod \
  --policy-arn arn:aws:iam::${ACCOUNT}:policy/HealthcareGapPolicy
```

정책 내용: [`infra/iam/health-care-gap-policy.json`](../../infra/iam/health-care-gap-policy.json)

SSM·KMS·Cost Explorer·IAM 자기조회·dev 롤 정리·RDS 스냅샷·인벤토리 조회를 최소 권한으로 담았다. 와일드카드 `Resource`를 쓴 4개 구문은 AWS가 리소스 수준 권한을 지원하지 않는 액션이거나(`ssm:DescribeParameters`, `ce:*`, `s3:ListAllMyBuckets`, `cloudwatch:ListMetrics`), `kms:ViaService` 조건으로 범위를 제한했다.

### 1.3 Cost Explorer 활성화 확인

`ce:GetCostAndUsage`는 **IAM 권한과 별개로 계정 수준에서 Cost Explorer가 활성화**되어 있어야 한다. 콘솔에서 한 번도 연 적이 없으면 활성화 후 최대 24시간이 지나야 데이터가 조회된다.

Billing 콘솔 → Cost Explorer 진입으로 활성화된다.

### 1.4 부여 확인

```bash
aws iam list-attached-user-policies --user-name health-care-prod   # 자기조회 동작 확인
aws ssm describe-parameters --max-results 1
aws ce get-cost-and-usage --time-period Start=2026-07-01,End=2026-08-01 \
  --granularity MONTHLY --metrics UnblendedCost
```

---

## 2. 청구액 검증

### 2.1 권한 없이 지금 바로 — Billing 콘솔

**Cost Explorer IAM 권한과 무관하게 콘솔에서는 바로 볼 수 있다.** 1단계를 기다릴 필요가 없다.

AWS 콘솔 → **Billing and Cost Management** → **Bills** → 2026년 7월 선택 → 서비스별 요금 확인.

계획서의 추정치와 대조한다.

| 서비스 | 추정 (Phase 1 이전) | 추정 (Phase 1 이후) |
|---|---:|---:|
| EC2 (t3.medium → t3a.medium) | ~$38 | ~$34 |
| RDS db.t3.micro + 20GB gp3 | ~$21 | ~$21 |
| ElastiCache cache.t3.micro | ~$15 | **$0** (제거됨) |
| Route53 + CloudWatch + S3 + ECR | ~$2 | ~$1.6 |
| **합계** | **~$76/월** | **~$57/월** |

> 7월 청구서는 Phase 1 **이전** 상태를 반영한다(ElastiCache 제거는 8월 4일). 절감 효과는 8월 청구서에서 확인된다. 7월분은 "~$76 추정이 맞았는지"를 검증하는 용도다.

### 2.2 권한 부여 후 — CLI

```bash
# 서비스별 월간 비용
aws ce get-cost-and-usage \
  --time-period Start=2026-07-01,End=2026-08-01 \
  --granularity MONTHLY --metrics UnblendedCost \
  --group-by Type=DIMENSION,Key=SERVICE \
  --query 'ResultsByTime[0].Groups[].[Keys[0],Metrics.UnblendedCost.Amount]' \
  --output table

# Phase 1 적용 전후 비교 (일별)
aws ce get-cost-and-usage \
  --time-period Start=2026-08-01,End=2026-08-15 \
  --granularity DAILY --metrics UnblendedCost \
  --query 'ResultsByTime[].[TimePeriod.Start,Total.UnblendedCost.Amount]' \
  --output table
```

### 2.3 결과 반영

확인된 실제 금액으로 [재구조화 계획 §1.3·Phase 1](../exec-plans/PROJECT_RESTRUCTURING_2026-08.md)의 추정치를 교체한다.

---

## 3. DB 비밀번호를 SSM Parameter Store로 이관

**1단계 권한 부여가 선행되어야 한다.**

### 3.1 현재 값을 SSM에 저장

```bash
# 값은 셸 히스토리에 남지 않도록 프롬프트로 입력한다
read -rs -p "DB password: " DBPW && echo

aws ssm put-parameter \
  --name "/healthcare/prod/db/password" \
  --type SecureString \
  --value "$DBPW" \
  --description "RDS master password for healthcare-prod-postgres" \
  --tags Key=Project,Value=healthcare Key=Environment,Value=prod Key=ManagedBy,Value=manual

unset DBPW
```

> `--type SecureString`은 기본 KMS 키(`alias/aws/ssm`)로 암호화한다. 별도 CMK가 필요하면 `--key-id`를 지정한다.

### 3.2 Terraform이 SSM에서 읽도록 변경

`infra/terraform/aws/database.tf`:

```diff
+data "aws_ssm_parameter" "db_password" {
+  name            = "/healthcare/prod/db/password"
+  with_decryption = true
+}
+
 resource "aws_db_instance" "postgres" {
   identifier = "${var.project_name}-${var.environment}-postgres"
 
   engine = "postgres"
   engine_version = "17"
   instance_class = var.rds_instance_class
 
   db_name  = var.db_name
   username = var.db_username
-  password = var.db_password
+  password = data.aws_ssm_parameter.db_password.value
```

`infra/terraform/aws/variables.tf`:

```diff
-variable "db_password" {
-  description = "RDS 마스터 비밀번호"
-  type        = string
-  sensitive   = true
-}
```

`infra/terraform/aws/terraform.tfvars`:

```diff
-db_password        = "..."
```

`terraform.tfvars.example`도 같이 정리하고, 값이 SSM에 있다는 주석을 남긴다.

### 3.3 적용

```bash
cd infra/terraform/aws
terraform plan     # 비밀번호가 동일하면 "No changes" 여야 한다
terraform apply
```

**`plan`에 RDS 변경이 뜨면 멈춘다.** SSM에 넣은 값이 실제 비밀번호와 다르다는 뜻이고, 그대로 apply하면 비밀번호가 바뀌어 앱이 DB에 붙지 못한다.

### 3.4 GitHub Secret 정리

앱은 `PROD_DB_PASSWORD` GitHub Secret에서 값을 받는다. 두 저장소를 유지할지 결정한다.

- **유지**: 지금과 동일. 수동 동기화 필요 — 어긋날 여지가 남는다
- **SSM 단일화**: 배포 워크플로우가 SSM에서 읽어 주입. GitHub Actions IAM 사용자에게 해당 파라미터 `ssm:GetParameter` 권한 추가 필요

후자가 낫지만 워크플로우 변경과 IAM 추가 부여가 따르므로 별도 판단한다.

---

## 4. 남는 한계 — 정직하게

**이 이관으로 비밀번호가 완전히 사라지지는 않는다.**

`data.aws_ssm_parameter`로 읽은 값은 **Terraform state에 평문으로 저장된다.** state는 Phase 2에서 S3로 옮겨 AES256 암호화·버저닝·퍼블릭 전면 차단이 걸려 있으므로 노트북 평문보다는 낫지만, state를 읽을 수 있는 사람은 비밀번호도 읽을 수 있다.

### 근본 해결책

RDS가 비밀번호를 직접 관리하게 하면 Terraform state에 값이 남지 않는다.

```hcl
resource "aws_db_instance" "postgres" {
  manage_master_user_password = true
  # password 속성 자체를 제거
}
```

AWS가 Secrets Manager에 시크릿을 만들고 자동 회전까지 처리한다.

**다만 이번 범위에 넣지 않은 이유가 있다.**

- 기존 인스턴스에 적용하면 **비밀번호가 즉시 회전**된다. 앱이 쓰는 GitHub Secret이 낡아져 **DB 연결이 끊긴다.**
- 앱이 Secrets Manager에서 읽도록 바꾸거나, 배포 워크플로우가 회전된 값을 가져오도록 고쳐야 한다.
- 활성 사용자 약 50명이 있는 운영 DB라 무중단 절차를 따로 설계해야 한다.

3단계(SSM 이관)로 "로컬 평문 제거"라는 당면 목표를 달성하고, `manage_master_user_password` 전환은 앱 측 변경과 함께 별도 과제로 다룬다.

---

## 5. 체크리스트

- [ ] 1.1 현재 부착 정책 확인
- [ ] 1.2 `HealthcareGapPolicy` 생성·부착 (**관리자**)
- [ ] 1.3 Cost Explorer 활성화 확인
- [ ] 1.4 권한 부여 검증
- [ ] 2.1 Billing 콘솔에서 7월 청구액 확인 — **1단계와 무관하게 지금 가능**
- [ ] 2.3 계획서 추정치를 실제값으로 교체
- [ ] 3.1 SSM SecureString에 비밀번호 저장
- [ ] 3.2 Terraform이 SSM을 읽도록 변경
- [ ] 3.3 `plan`이 **No changes**임을 확인 후 apply
- [ ] 3.4 GitHub Secret 단일화 여부 결정
- [ ] 4 `manage_master_user_password` 전환을 별도 이슈로 등록
