# ── G2 리허설 RDS: db.t3.medium / PG17 / ephemeral-safe ─────────────────────────

resource "aws_db_subnet_group" "postgres" {
  name       = "${local.name}-db-subnet"
  subnet_ids = aws_subnet.public[*].id

  tags = merge(local.common_tags, { Name = "${local.name}-db-subnet" })
}

resource "aws_db_instance" "postgres" {
  identifier = local.name

  engine         = "postgres"
  engine_version = var.engine_version # prod 패리티: 17.7
  instance_class = var.instance_class # 리허설 클래스: db.t3.medium (micro 스킵 — 스로틀 자명)

  # prod 와 동일하게 시작(20GB)하고 오토스케일 한도도 동일(100GB)로 두어
  # 적재 중 스토리지 증가폭·오토스케일(비가역) 발생 여부를 실측한다.
  allocated_storage     = 20
  max_allocated_storage = 100
  storage_type          = "gp3"
  storage_encrypted     = true

  db_name  = var.db_name # prod 와 동일(healthcare) → Flyway/스키마 동일 경로
  username = var.db_username
  password = var.db_password

  db_subnet_group_name   = aws_db_subnet_group.postgres.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  publicly_accessible    = var.publicly_accessible # 로더가 외부에서 접근(SG 로 CIDR 한정)

  # ── ephemeral-safe: 반드시 destroy 가능해야 한다 ──
  deletion_protection     = false
  skip_final_snapshot     = true
  backup_retention_period = 0 # 리허설은 백업 불필요
  apply_immediately       = true

  # ── 용량 측정용 ──
  performance_insights_enabled          = true
  performance_insights_retention_period = 7 # 무료 구간

  tags = merge(local.common_tags, { Name = local.name })
}
