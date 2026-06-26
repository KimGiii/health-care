variable "aws_region" {
  description = "AWS 리전"
  type        = string
  default     = "ap-northeast-2"
}

variable "project_name" {
  description = "프로젝트 이름 (리소스 접두어)"
  type        = string
  default     = "healthcare"
}

variable "instance_class" {
  description = "리허설 RDS 인스턴스 클래스 (G2 결정: db.t3.medium). micro 는 스로틀 자명이라 스킵."
  type        = string
  default     = "db.t3.medium"
}

variable "engine_version" {
  description = "PostgreSQL 엔진 버전 (prod 패리티). 로컬 PG16 과 달리 17 로 맞춰 엔진 갭도 닫는다."
  type        = string
  default     = "17.7"
}

variable "db_name" {
  description = "DB 이름 (prod 와 동일하게 두어 Flyway/스키마 경로 동일)"
  type        = string
  default     = "healthcare"
}

variable "db_username" {
  description = "마스터 사용자명"
  type        = string
  sensitive   = true
}

variable "db_password" {
  description = "마스터 비밀번호 (최소 16자)"
  type        = string
  sensitive   = true

  validation {
    condition     = length(var.db_password) >= 16
    error_message = "db_password 는 최소 16자여야 합니다."
  }
}

variable "allowed_cidrs" {
  description = "RDS 5432 인바운드 허용 CIDR. 로더(백엔드)의 공인 IP /32 만 넣는다. 0.0.0.0/0 금지(fail-closed)."
  type        = list(string)

  validation {
    condition     = length(var.allowed_cidrs) > 0 && !contains(var.allowed_cidrs, "0.0.0.0/0")
    error_message = "allowed_cidrs 는 최소 1개여야 하고 0.0.0.0/0 을 포함할 수 없습니다(로더 IP /32 권장)."
  }
}

variable "publicly_accessible" {
  description = "RDS 퍼블릭 접근 허용. 로더가 외부(로컬/사무실)에서 직접 접속할 때 true. SG 로 CIDR 한정됨."
  type        = bool
  default     = true
}
