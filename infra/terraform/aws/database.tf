# ── RDS PostgreSQL ────────────────────────────────────────────────────────────

# 마스터 비밀번호는 SSM Parameter Store(SecureString)에서 읽는다 (#118).
# 이전에는 terraform.tfvars에 평문으로 있었다.
#
# 파라미터는 Terraform으로 관리하지 않는다. 값을 리소스로 정의하려면 그 값이 코드나
# 변수로 다시 들어와야 해서 평문 제거라는 목적이 무너진다. 최초 저장은 수동으로 하고
# (docs/operations/SECRETS_MIGRATION_AND_BILLING_VERIFICATION.md §3.1) 여기서는 읽기만 한다.
#
# 한계: 여기서 읽은 값은 Terraform state에 평문으로 남는다. state가 S3(AES256·버저닝·
# 퍼블릭 차단)에 있어 로컬 평문보다는 낫지만 완전한 제거는 아니다. 근본 해결책인
# manage_master_user_password는 기존 인스턴스에 적용 시 비밀번호가 즉시 회전해 앱의
# GitHub Secret이 낡아지고 DB 연결이 끊기므로, 앱 측 변경과 함께 별도로 다룬다.
data "aws_ssm_parameter" "db_password" {
  name            = "/healthcare/prod/db/password"
  with_decryption = true
}

resource "aws_db_subnet_group" "postgres" {
  name       = "${var.project_name}-${var.environment}-db-subnet"
  subnet_ids = aws_subnet.private[*].id

  tags = merge(local.common_tags, { Name = "${var.project_name}-${var.environment}-db-subnet" })
}

resource "aws_db_instance" "postgres" {
  identifier = "${var.project_name}-${var.environment}-postgres"

  engine = "postgres"
  # 메이저 버전만 고정한다. auto_minor_version_upgrade가 켜져 있어 AWS가 마이너를
  # 올리는데(17.7 → 17.9 실제 발생), 마이너까지 고정하면 terraform이 매번 다운그레이드를
  # 계획한다. RDS는 다운그레이드를 지원하지 않으므로 apply가 실패한다. (#111)
  engine_version = "17"
  instance_class = var.rds_instance_class

  allocated_storage     = 20
  max_allocated_storage = 100
  storage_type          = "gp3"
  storage_encrypted     = true

  db_name  = var.db_name
  username = var.db_username
  password = data.aws_ssm_parameter.db_password.value

  db_subnet_group_name   = aws_db_subnet_group.postgres.name
  vpc_security_group_ids = [aws_security_group.rds.id]

  backup_retention_period = 7
  backup_window           = "03:00-04:00"
  maintenance_window      = "Mon:04:00-Mon:05:00"

  deletion_protection       = true
  skip_final_snapshot       = false
  final_snapshot_identifier = "${var.project_name}-${var.environment}-final-snapshot"

  performance_insights_enabled = var.environment == "prod"

  tags = merge(local.common_tags, { Name = "${var.project_name}-${var.environment}-postgres" })
}

# ── ElastiCache Redis ─────────────────────────────────────────────────────────
#
# 제거됨 (#111). 애플리케이션이 단일 인스턴스로 운영되고 캐시에 담기던 값이 모두
# 재계산 가능한 파생 데이터뿐이어서 전용 클러스터를 유지할 이유가 없었다.
# 캐시는 애플리케이션 내부 Caffeine으로 대체한다 — backend CacheConfig 참고.
