output "progress_photo_bucket_name" {
  description = "진행 사진 저장용 S3 버킷 이름"
  value       = aws_s3_bucket.progress_photos.bucket
}

output "progress_photo_bucket_region" {
  description = "진행 사진 저장용 S3 버킷 리전"
  value       = var.aws_region
}

output "progress_photo_bucket_access_policy_arn" {
  description = "애플리케이션 역할 또는 사용자에 연결할 IAM 정책 ARN"
  value       = aws_iam_policy.progress_photo_bucket_access.arn
}
