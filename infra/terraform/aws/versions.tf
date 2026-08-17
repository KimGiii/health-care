terraform {
  # use_lockfile(S3 네이티브 잠금)는 1.10에서 도입됐다.
  required_version = ">= 1.10.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # state를 S3로 원격화한다 (#116).
  #
  # 이전에는 state가 로컬 파일에만 있었다. .gitignore 처리되어 원격 백업도 잠금도 없어,
  # 노트북을 잃으면 인프라를 관리할 수 없는 상태였다.
  #
  # 잠금은 DynamoDB 대신 S3 네이티브 잠금(use_lockfile)을 쓴다. 현재 실행 주체에
  # dynamodb 권한이 없고, DynamoDB 기반 잠금은 Terraform에서 폐기 예정이다.
  #
  # 버킷은 순환 의존(자기 state를 담는 버킷을 자기가 관리) 때문에 Terraform으로 관리하지
  # 않는다. 생성·설정 절차는 infra/terraform/README.md 참고.
  backend "s3" {
    bucket       = "healthcare-terraform-state-621770702801"
    key          = "prod/terraform.tfstate"
    region       = "ap-northeast-2"
    encrypt      = true
    use_lockfile = true
  }
}
