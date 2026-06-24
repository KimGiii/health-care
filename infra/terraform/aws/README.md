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

## 모니터링 (Prometheus + Grafana)

앱 메트릭은 `/actuator/prometheus`로 노출된다. 모니터링 스택은 앱 배포(blue-green)와
**독립된 long-running 컨테이너**로, EC2에서 1회만 부트스트랩하면 계속 실행된다.

### 사전 준비 (1회)

1. EC2에 레포가 있어야 한다 (compose가 `../../backend/monitoring` 설정을 재사용).
   ```bash
   sudo git clone https://github.com/KimGiii/health-care.git /opt/health-care
   ```
2. 모니터링 환경변수 파일 작성:
   ```bash
   sudo tee /etc/healthcare/monitoring.env >/dev/null <<'ENV'
   GRAFANA_ADMIN_PASSWORD=<강력한_비밀번호>
   SLACK_WEBHOOK_URL=<슬랙_incoming_webhook_URL>   # 없으면 빈 값 → 알림 미발송
   ENV
   # docker compose(ubuntu 사용자)가 --env-file을 읽을 수 있도록 소유자를 ubuntu로.
   # root 소유 + 600이면 "permission denied"로 기동 실패한다.
   sudo chown ubuntu:ubuntu /etc/healthcare/monitoring.env
   sudo chmod 600 /etc/healthcare/monitoring.env
   ```

### 기동

```bash
cd /opt/health-care/infra/monitoring
docker compose -f docker-compose.monitoring.yml --env-file /etc/healthcare/monitoring.env up -d
```

- Grafana: `https://api.gainsy.site/grafana/` (admin / monitoring.env 값)
- Prometheus: 외부 비노출(127.0.0.1:9090). 필요 시 SSH 터널로 접근.
- Nginx `/grafana/` 라우트는 `user_data.sh`에 포함되어 있다. 기존 인스턴스라면
  `/etc/nginx/sites-available/healthcare`에 동일 블록을 추가 후 `sudo nginx -t && sudo nginx -s reload`.

### 메모리 주의 (t3.small, 2GB)

앱 1400m + Prometheus 250m + Grafana 180m + OS로 여유가 빠듯하다. 배포 후 반드시 확인:

```bash
free -m
docker stats --no-stream
```

압박 시 옵션:
1. Prometheus 보존 단축 — compose의 `--storage.tsdb.retention.time`/`size` 축소.
2. 인스턴스 승격 — `variables.tf`의 `ec2_instance_type`을 `t3.medium`으로.
3. 모니터링을 별도 소형 인스턴스로 분리.

### 메트릭/대시보드/알림

- 기본(자동): JVM 힙/GC, HTTP 요청률·지연(`http_server_requests_*`), HikariCP, Redis.
- 비즈니스(커스텀): `healthcare_auth_register_total`, `healthcare_auth_login_total{result}`,
  `healthcare_diet_log_created_total`, `healthcare_diet_ai_analysis_seconds_*`.
- 대시보드/알림 규칙: `backend/monitoring/grafana/` (로컬·프로덕션 공유).
- 알림 채널: `infra/monitoring/grafana/alerting/` (프로덕션 전용 Slack).
