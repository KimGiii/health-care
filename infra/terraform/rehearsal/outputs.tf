output "db_endpoint" {
  description = "RDS 엔드포인트 (host:port)"
  value       = aws_db_instance.postgres.endpoint
}

output "db_address" {
  description = "RDS 호스트"
  value       = aws_db_instance.postgres.address
}

output "db_port" {
  description = "RDS 포트"
  value       = aws_db_instance.postgres.port
}

output "db_name" {
  description = "DB 이름"
  value       = aws_db_instance.postgres.db_name
}

output "jdbc_url" {
  description = "백엔드 DB_URL 로 그대로 사용. DB_USERNAME/DB_PASSWORD 는 tfvars 값과 동일하게 환경변수로 주입."
  value       = "jdbc:postgresql://${aws_db_instance.postgres.endpoint}/${aws_db_instance.postgres.db_name}"
}

output "teardown_reminder" {
  description = "측정 종료 후 반드시 실행"
  value       = "측정·기록 완료 후 즉시: terraform destroy"
}
