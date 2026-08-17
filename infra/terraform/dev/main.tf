terraform {
  # use_lockfile(S3 네이티브 잠금)는 1.10에서 도입됐다.
  required_version = ">= 1.10.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # dev는 일회성 환경이지만 state는 원격에 둔다 (#116).
  #
  # 2026-08-04 destroy 시 iam:DeleteRolePolicy 권한이 없어 IAM 롤·인라인 정책·빈 S3 버킷이
  # 잔존했다. state가 로컬에만 있으면 이 정리를 특정 노트북에서만 할 수 있다. 원격 state는
  # 권한을 가진 사람이 어디서든 destroy를 마무리할 수 있게 한다.
  backend "s3" {
    bucket       = "healthcare-terraform-state-621770702801"
    key          = "dev/terraform.tfstate"
    region       = "ap-northeast-2"
    encrypt      = true
    use_lockfile = true
  }
}

provider "aws" {
  region = var.aws_region
}

locals {
  common_tags = {
    Project     = "healthcare"
    Environment = "dev"
    ManagedBy   = "Terraform"
  }
}
