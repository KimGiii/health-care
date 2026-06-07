output "dev_public_ip" {
  description = "dev EC2 Elastic IP"
  value       = aws_eip.dev.public_ip
}

output "dev_api_domain" {
  description = "dev API 도메인"
  value       = "https://dev.api.${var.root_domain}"
}

output "ssh_command" {
  description = "SSH 접속 명령"
  value       = "ssh ubuntu@${aws_eip.dev.public_ip}"
}

output "dev_s3_bucket" {
  description = "dev S3 버킷 이름 (GitHub Actions DEV_S3_BUCKET 시크릿에 설정)"
  value       = aws_s3_bucket.dev_photos.bucket
}
