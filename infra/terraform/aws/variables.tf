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
  default     = "t3.medium"
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

variable "db_password" {
  description = "데이터베이스 마스터 비밀번호 (최소 16자)"
  type        = string
  sensitive   = true

  validation {
    condition     = length(var.db_password) >= 16
    error_message = "db_password는 최소 16자 이상이어야 합니다."
  }
}

# ── ElastiCache ───────────────────────────────────────────────────────────────

variable "redis_node_type" {
  description = "ElastiCache 노드 타입"
  type        = string
  default     = "cache.t3.micro"
}

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
