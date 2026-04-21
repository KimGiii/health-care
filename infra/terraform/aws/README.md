# AWS Terraform

## 목적

- 진행 사진 업로드에 사용하는 S3 버킷과 백엔드 접근용 IAM 정책을 Terraform으로 관리한다.

## 생성 리소스

- private S3 bucket
- bucket versioning
- AES256 server-side encryption
- public access block
- presigned upload/download 대응 CORS
- 백엔드 연결용 IAM policy

## 사용 예시

```bash
cd infra/terraform/aws
terraform init
terraform plan \
  -var="bucket_name=healthcare-photos-dev" \
  -var="environment=dev" \
  -var='cors_allowed_origins=["http://localhost:3000","http://localhost:8080"]'
terraform apply \
  -var="bucket_name=healthcare-photos-dev" \
  -var="environment=dev" \
  -var='cors_allowed_origins=["http://localhost:3000","http://localhost:8080"]'
```

## 백엔드 연동 값

- `app.s3.bucket`: `progress_photo_bucket_name` output 사용
- `app.s3.region`: `progress_photo_bucket_region` output 사용
- EC2 또는 컨테이너 역할에는 `progress_photo_bucket_access_policy_arn` 정책을 연결

## Terraform 실행 IAM 권한

- Terraform을 실행하는 IAM 사용자 또는 역할은 별도 권한이 필요하다.
- 현재 Terraform 코드가 실제로 요구하는 권한은 아래 파일 기준으로 관리한다.
  - 실행 정책 예시: [policies/terraform-executor-policy.json](/Users/kingloo/IdeaProjects/Project/health-care/infra/terraform/aws/policies/terraform-executor-policy.json)
  - 템플릿: [policies/terraform-executor-policy-template.json](/Users/kingloo/IdeaProjects/Project/health-care/infra/terraform/aws/policies/terraform-executor-policy-template.json)

### 포함된 권한 범위

- S3 버킷 생성/삭제
- 버킷 태그 조회/설정/삭제
- 버전 관리 조회/설정
- 암호화 조회/설정
- public access block 조회/설정/삭제
- CORS 조회/설정/삭제
- 객체 조회/업로드/삭제
- Terraform이 생성하는 애플리케이션용 IAM policy 생성/조회/버전 관리/삭제

### 현재 리소스 이름 기준

- 버킷
  - `healthcare-photos-dev`
  - `healthcare-photos-prod`
- IAM 정책
  - `healthcare-dev-progress-photo-bucket-access`
  - `healthcare-prod-progress-photo-bucket-access`

### 콘솔 적용 순서

1. AWS Console에서 `IAM > Users` 또는 `IAM > Roles` 로 이동
2. Terraform 실행 주체 선택
3. `Add permissions` 또는 inline policy 추가
4. `terraform-executor-policy.json` 내용을 붙여넣어 저장
5. 다시 `terraform apply` 실행

### 커스텀 환경을 쓰는 경우

- 버킷 이름이나 `project_name`, `environment`를 바꿨다면 템플릿 파일 기준으로 ARN도 같이 수정해야 한다.
- 최소 권한 유지가 목적이면 wildcard 대신 실제 버킷 이름과 정책 이름만 정확히 넣는 것을 권장한다.

## 로컬 개발

- 로컬은 LocalStack endpoint를 `app.s3.endpoint`에 주입해 사용할 수 있다.
- 실제 AWS 버킷 생성과 권한 관리는 Terraform 기준으로 유지한다.
