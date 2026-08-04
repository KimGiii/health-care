# Terraform

## 스택 구성

| 디렉터리 | 용도 | 수명 | state 키 |
|---|---|---|---|
| [`aws/`](aws/) | 실제 운영 인프라 (VPC·EC2·RDS·S3·ECR·Route53) | 상시 | `prod/terraform.tfstate` |
| [`dev/`](dev/) | 개발 서버 | **일회성** — 필요할 때 apply, 끝나면 destroy | `dev/terraform.tfstate` |
| [`rehearsal/`](rehearsal/) | 카탈로그 전량 적재 용량 리허설용 RDS | **일회성** — 측정 후 destroy | 로컬 |

> `aws/`라는 이름과 달리 이 스택은 `environment = "prod"`로 태그된 유일한 실환경이다. 이름을 바꾸면 App Store 심사 기록 등 다수의 역사적 문서 링크가 깨져 유지하고 있다.

### dev 스택은 상시 가동하지 않는다

2026-08-04에 destroy했고, **그 상태가 기본값이다.** 개발은 `backend/docker-compose.yml`(PostgreSQL·LocalStack)로 로컬에서 한다. 상시 EC2를 띄울 이유가 없다.

필요할 때만 `terraform apply`하고 작업이 끝나면 `terraform destroy`한다.

> `.github/workflows/deploy-dev.yml`은 destroy된 dev 서버를 대상으로 하므로 현재 동작하지 않는다. dev를 다시 띄운 뒤에만 유효하다.

**2026-08-04 destroy 잔여물:** `iam:DeleteRolePolicy` 권한이 없어 IAM 롤 `healthcare-dev-ec2-role`, 인라인 정책 `healthcare-dev-ec2-s3`, 빈 S3 버킷이 남아 있다. 과금은 없다. 권한 있는 자격증명으로 `cd dev && terraform destroy`를 실행하면 정리된다.

---

## state 백엔드

state는 **S3에 원격 저장**한다. 이전에는 로컬 파일에만 있어 원격 백업도 잠금도 없었다 — 노트북을 잃으면 인프라를 관리할 수 없는 상태였다.

```
버킷:   healthcare-terraform-state-621770702801
리전:   ap-northeast-2
버저닝: Enabled       ← state 손상 시 이전 버전으로 복구
암호화: AES256 (SSE-S3, BucketKey 활성)
퍼블릭: 전면 차단
```

### 잠금은 DynamoDB가 아니라 S3 네이티브

```hcl
backend "s3" {
  bucket       = "healthcare-terraform-state-621770702801"
  key          = "prod/terraform.tfstate"
  region       = "ap-northeast-2"
  encrypt      = true
  use_lockfile = true   # ← S3 네이티브 잠금
}
```

`use_lockfile`은 Terraform 1.10에서 도입됐다. DynamoDB 테이블이 필요 없고, DynamoDB 기반 잠금은 Terraform에서 폐기 예정이다. 현재 실행 주체에 `dynamodb:*` 권한이 없다는 제약과도 맞는다.

**필요 Terraform 버전: 1.10.0 이상.**

### 버킷을 Terraform으로 관리하지 않는 이유

자기 state를 담는 버킷을 자기가 관리하면 순환 의존이 생긴다(버킷을 destroy하면 state가 사라지고, state가 없으면 버킷을 관리할 수 없다). 부트스트랩 리소스로 취급해 수동 생성한다.

재생성이 필요하면:

```bash
B=healthcare-terraform-state-621770702801
aws s3api create-bucket --bucket $B --region ap-northeast-2 \
  --create-bucket-configuration LocationConstraint=ap-northeast-2
aws s3api put-bucket-versioning --bucket $B \
  --versioning-configuration Status=Enabled
aws s3api put-bucket-encryption --bucket $B \
  --server-side-encryption-configuration \
  '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"},"BucketKeyEnabled":true}]}'
aws s3api put-public-access-block --bucket $B \
  --public-access-block-configuration \
  BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true
```

### state 복구

버저닝이 켜져 있으므로 손상된 state는 이전 버전으로 되돌린다.

```bash
aws s3api list-object-versions \
  --bucket healthcare-terraform-state-621770702801 \
  --prefix prod/terraform.tfstate \
  --query 'Versions[].[VersionId,LastModified]' --output table

aws s3api get-object \
  --bucket healthcare-terraform-state-621770702801 \
  --key prod/terraform.tfstate \
  --version-id <VERSION_ID> restored.tfstate
```

---

## 모듈화를 하지 않은 이유

재구조화 계획 초안은 `modules/` + `envs/` 구조로 재편하는 것을 제안했으나 **보류했다.**

- 실환경이 `aws/` 하나뿐이다. 모듈을 소비할 두 번째 대상이 없다.
- `dev/`는 일회성으로 재정의되어 상시 존재하지 않는다. 중복 유지 비용이 낮다.
- 디렉터리 이름을 바꾸면 App Store 심사 기록 등 다수의 역사적 문서 링크가 깨진다.

두 번째 상시 환경(staging 등)이 실제로 필요해질 때 다시 판단한다. 그전까지는 추측성 추상화다.

---

## 실행

```bash
cd infra/terraform/aws     # 또는 dev, rehearsal
terraform init             # 최초 1회 (백엔드 설정 변경 시에도)
terraform plan
terraform apply
```

`terraform.tfvars`는 git 추적 대상이 아니다. `terraform.tfvars.example`을 복사해 채운다.

> **주의:** `aws/terraform.tfvars`에 DB 비밀번호가 평문으로 있다. SSM Parameter Store 또는 Secrets Manager로 이관해야 하나, 현재 실행 주체에 `ssm:*`·`secretsmanager:*` 권한이 없어 보류 중이다. [IAM 권한 정비](../iam/README.md) 참고.

---

## 관련 문서

- [IAM 정책](../iam/README.md) — 실행 권한 정의와 현재 공백
- [AWS 스택 상세](aws/README.md)
- [재구조화 계획](../../docs/exec-plans/PROJECT_RESTRUCTURING_2026-08.md)
