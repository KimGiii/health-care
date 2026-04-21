variable "aws_region" {
  description = "AWS 리전"
  type        = string
  default     = "ap-northeast-2"
}

variable "environment" {
  description = "배포 환경명"
  type        = string
  default     = "dev"
}

variable "project_name" {
  description = "프로젝트 이름"
  type        = string
  default     = "healthcare"
}

variable "bucket_name" {
  description = "진행 사진 저장용 S3 버킷 이름"
  type        = string
}

variable "cors_allowed_origins" {
  description = "Presigned URL 업로드를 허용할 Origin 목록"
  type        = list(string)
  default     = ["*"]
}
