variable "aws_region" {
  description = "AWS 리전"
  type        = string
  default     = "ap-northeast-2"
}

variable "ec2_key_name" {
  description = "EC2 SSH 키 페어 이름 (prod와 동일 키 사용 가능)"
  type        = string
}

variable "allowed_ssh_cidrs" {
  description = "SSH 허용 CIDR (본인 IP)"
  type        = list(string)
  default     = []
}

variable "root_domain" {
  description = "서비스 루트 도메인 (예: gainsy.site)"
  type        = string
}

variable "route53_zone_id" {
  description = "prod Terraform이 관리하는 Route53 Hosted Zone ID"
  type        = string
}

variable "ecr_registry" {
  description = "ECR 레지스트리 URL (예: 621770702801.dkr.ecr.ap-northeast-2.amazonaws.com)"
  type        = string
}
