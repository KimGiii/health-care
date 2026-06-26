# ── G2 전량 적재 용량 리허설용 일회성(ephemeral) 스택 ───────────────────────────
#
# 목적: prod 본 적재 전, 식품 카탈로그 전량 적재(615K행 + ~130만 ServingOption +
#       인덱스 빌드)를 prod 와 동일 엔진(PG17)·운영 적재창 클래스(db.t3.medium)에서
#       1회 리허설해 용량·소요·정합성을 측정한다.
#
# ⚠️ 이 스택은 **쓰고 버린다**. 측정이 끝나면 반드시 `terraform destroy` 한다.
#    - deletion_protection = false / skip_final_snapshot = true / backup_retention = 0
#    - prod 와 **분리된 전용 VPC·SG** 를 쓴다(prod 네트워크/state 미접촉).
#
# 절차: docs/operations/FOOD_CATALOG_BULK_LOAD_RDS_REHEARSAL.md

provider "aws" {
  region = var.aws_region
}

locals {
  name = "${var.project_name}-g2-rehearsal"

  common_tags = {
    Project      = var.project_name
    Purpose      = "g2-bulk-load-rehearsal"
    Ephemeral    = "true"
    ManagedBy    = "Terraform"
    AutoTeardown = "destroy-after-measurement"
  }
}
