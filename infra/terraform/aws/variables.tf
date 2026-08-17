variable "aws_region" {
  description = "AWS 리전"
  type        = string
  default     = "ap-northeast-2"
}

variable "environment" {
  description = "배포 환경 (dev | prod)"
  type        = string
  default     = "dev"

  validation {
    condition     = contains(["dev", "prod"], var.environment)
    error_message = "environment는 dev 또는 prod 이어야 합니다."
  }
}

variable "project_name" {
  description = "프로젝트 이름"
  type        = string
  default     = "healthcare"
}

# ── 네트워크 ──────────────────────────────────────────────────────────────────

variable "allowed_ssh_cidrs" {
  description = "SSH 접근을 허용할 CIDR 목록 (본인 IP만 입력)"
  type        = list(string)
  default     = []
}

# ── EC2 ───────────────────────────────────────────────────────────────────────

variable "ec2_instance_type" {
  description = "EC2 인스턴스 타입"
  type        = string
  # 모니터링 스택(Prometheus+Grafana)을 앱과 같은 박스에 얹으면서 t3.small(2GB) 메모리가
  # 빠듯해 t3.medium(4GB)으로 상향. 타입 변경은 in-place(스톱→수정→스타트)로 EBS/EIP 보존.
  #
  # 4GB는 유지하되 AMD 변형으로 전환해 단가만 낮춘다 (#111). blue-green 배포 중
  # 앱 컨테이너 2개(각 1400m) + Prometheus(250m) + Grafana(350m)가 동시에 떠서
  # 피크가 3.8GB에 달하므로 메모리를 더 줄일 수는 없다.
  default = "t3a.medium"
}

variable "ec2_key_name" {
  description = "EC2 SSH 키 페어 이름 (AWS 콘솔에서 미리 생성)"
  type        = string
}

# ── RDS ───────────────────────────────────────────────────────────────────────

variable "rds_instance_class" {
  description = "RDS 인스턴스 클래스"
  type        = string
  default     = "db.t3.micro"
}

variable "db_name" {
  description = "데이터베이스 이름"
  type        = string
  default     = "healthcare"
}

variable "db_username" {
  description = "데이터베이스 마스터 사용자명"
  type        = string
  sensitive   = true
}

# db_password 변수는 SSM Parameter Store 이관과 함께 삭제됨 (#118).
# 값은 SecureString `/healthcare/prod/db/password`에 있고 database.tf가 data 소스로 읽는다.
# 최소 16자 검증은 변수와 함께 사라졌다 — 파라미터를 갱신할 때 직접 지켜야 한다.

# ElastiCache 변수는 클러스터 제거와 함께 삭제됨 (#111).

# ── S3 ────────────────────────────────────────────────────────────────────────

variable "bucket_name" {
  description = "진행 사진 저장용 S3 버킷 이름"
  type        = string
}

variable "cors_allowed_origins" {
  description = "Presigned URL 업로드를 허용할 Origin 목록"
  type        = list(string)
  default     = ["*"]
}

# ── DNS ───────────────────────────────────────────────────────────────────────

variable "root_domain" {
  description = "서비스 루트 도메인 (예: gainsy.site)"
  type        = string
}
