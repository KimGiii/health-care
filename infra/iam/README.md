# IAM 정책

## 파일 구성

| 파일 | 용도 | 부여 대상 |
|------|------|----------|
| `terraform-deploy-policy.json` | Terraform `plan` / `apply` 전체 권한 | 개발자 IAM 사용자 |
| `github-actions-policy.json` | ECR 이미지 push 전용 (최소 권한) | GitHub Actions IAM 사용자 |

---

## 적용 방법

### 1. Terraform 배포용 정책

```bash
# 정책 생성
aws iam create-policy \
  --policy-name HealthcareTerraformDeploy \
  --policy-document file://infra/iam/terraform-deploy-policy.json

# IAM 사용자에 연결
aws iam attach-user-policy \
  --user-name YOUR_IAM_USER \
  --policy-arn arn:aws:iam::$(aws sts get-caller-identity --query Account --output text):policy/HealthcareTerraformDeploy
```

### 2. GitHub Actions 배포용 정책

```bash
# 전용 IAM 사용자 생성
aws iam create-user --user-name healthcare-github-actions

# 정책 생성 및 연결
aws iam create-policy \
  --policy-name HealthcareGithubActions \
  --policy-document file://infra/iam/github-actions-policy.json

aws iam attach-user-policy \
  --user-name healthcare-github-actions \
  --policy-arn arn:aws:iam::$(aws sts get-caller-identity --query Account --output text):policy/HealthcareGithubActions

# 액세스 키 발급 → GitHub Secrets 등록
aws iam create-access-key --user-name healthcare-github-actions
```

발급된 `AccessKeyId` → `AWS_ACCESS_KEY_ID`  
발급된 `SecretAccessKey` → `AWS_SECRET_ACCESS_KEY`

---

## 권한 분리 원칙

```
개발자 (Terraform 운영자)
└── HealthcareTerraformDeploy
    ├── VPC / 서브넷 / 보안그룹
    ├── EC2 / EIP / 키페어
    ├── RDS / ElastiCache
    ├── ECR 리포지토리 관리
    ├── S3 버킷 관리
    ├── IAM 역할·정책 (EC2 인스턴스 역할 한정)
    ├── CloudWatch 알람·로그
    └── Terraform 원격 상태 (S3 + DynamoDB)

GitHub Actions
└── HealthcareGithubActions
    ├── ecr:GetAuthorizationToken  (전체 리소스)
    └── ECR push/pull              (healthcare-api 리포지토리만)
```

---

## ⚠️ 이 디렉터리의 정책 파일은 실제 부착 상태와 다르다

2026-08-04 Phase 1 작업 중 확인된 사실이다. **정책 파일을 실제 권한의 근거로 삼지 말 것.**

| 파일 | 어긋난 방향 | 근거 |
|---|---|---|
| `policies/terraform-executor-policy.json`<br>(`../terraform/aws/policies/`) | **실제보다 좁다** | S3·IAM 액션만 정의하는데 EC2·RDS·VPC·ElastiCache·Route53 apply가 실제로 성공한다 |
| `health-care-dev-policy.json` | **실제보다 넓다** | `iam:DeleteRolePolicy`를 올바른 범위(`role/healthcare-dev-*`)로 정의하지만 사용자에게 **부착되지 않았다** |

`health-care-prod` 사용자는 `iam:ListAttachedUserPolicies` 권한조차 없어 **자기 정책을 조회할 수 없다.** 권한 진단 자체가 불가능한 상태다.

### 현재 확인된 권한 공백

Phase 1과 V22→V41 릴리스를 진행하며 실제로 막힌 액션이다.

| 액션 | 막힌 작업 | 우회 |
|---|---|---|
| `iam:DeleteRolePolicy` | dev 스택 IAM 롤 destroy | 미완 — 리소스 잔존(과금 없음) |
| `rds:CreateDBSnapshot`<br>`rds:DescribeDBSnapshots` | 릴리스 전 수동 스냅샷 | PITR(자동 백업 7일)로 대체 |
| `ce:GetCostAndUsage` | 실제 AWS 청구액 확인 | 공개 요금표 기반 추정으로 진행 |
| `cloudwatch:ListMetrics` | 메모리 메트릭 존재 확인 | 컨테이너 메모리 설정으로 역산 |
| `s3:ListBucketVersions`<br>`ecr:ListImages` | destroy 전 잔여 객체 확인 | `s3 ls`로 우회 |
| `s3:ListAllMyBuckets` | 버킷 목록 조회 | 버킷 이름 직접 지정 |
| `dynamodb:*` | Terraform state 잠금 테이블 | **S3 네이티브 잠금(`use_lockfile`)으로 해소** |
| `ssm:*` · `secretsmanager:*` | DB 비밀번호 시크릿 이관 | **보류 중 — 평문 tfvars 유지** |
| `iam:ListAttachedUserPolicies` | 자기 정책 조회 | 없음 |

### 정합화 절차

정책 부착·수정은 **관리자 자격증명이 필요하다.** 아래는 관리자가 실행한다.

```bash
# 1. 현재 부착 상태 확인 — 무엇이 실제로 붙어 있는지부터 파악
aws iam list-attached-user-policies --user-name health-care-prod
aws iam list-user-policies --user-name health-care-prod

# 2. 실제 부착된 정책 문서를 내려받아 이 디렉터리의 파일과 대조
aws iam get-policy-version \
  --policy-arn <위에서 확인한 ARN> \
  --version-id <DefaultVersionId> \
  --query 'PolicyVersion.Document'

# 3. 위 공백 표의 액션을 추가하고, 파일을 실제 상태에 맞춰 갱신
```

**정합화가 끝나기 전까지는 시크릿 이관(SSM/Secrets Manager)과 청구액 검증을 진행할 수 없다.**

관련: [재구조화 계획 Phase 2](../../docs/exec-plans/PROJECT_RESTRUCTURING_2026-08.md)
