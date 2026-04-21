provider "aws" {
  region = var.aws_region
}

locals {
  common_tags = {
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "Terraform"
  }
}

resource "aws_s3_bucket" "progress_photos" {
  bucket = var.bucket_name

  tags = merge(local.common_tags, {
    Name = "${var.project_name}-${var.environment}-progress-photos"
  })
}

resource "aws_s3_bucket_versioning" "progress_photos" {
  bucket = aws_s3_bucket.progress_photos.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "progress_photos" {
  bucket = aws_s3_bucket.progress_photos.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "progress_photos" {
  bucket = aws_s3_bucket.progress_photos.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_cors_configuration" "progress_photos" {
  bucket = aws_s3_bucket.progress_photos.id

  cors_rule {
    allowed_headers = ["*"]
    allowed_methods = ["GET", "HEAD", "PUT"]
    allowed_origins = var.cors_allowed_origins
    expose_headers  = ["ETag"]
    max_age_seconds = 3000
  }
}

data "aws_iam_policy_document" "progress_photo_bucket_access" {
  statement {
    sid = "ListBucket"

    actions = [
      "s3:ListBucket"
    ]

    resources = [
      aws_s3_bucket.progress_photos.arn
    ]
  }

  statement {
    sid = "ObjectAccess"

    actions = [
      "s3:GetObject",
      "s3:PutObject",
      "s3:DeleteObject"
    ]

    resources = [
      "${aws_s3_bucket.progress_photos.arn}/*"
    ]
  }
}

resource "aws_iam_policy" "progress_photo_bucket_access" {
  name        = "${var.project_name}-${var.environment}-progress-photo-bucket-access"
  description = "HealthCare 백엔드가 진행 사진 S3 버킷에 접근하기 위한 정책"
  policy      = data.aws_iam_policy_document.progress_photo_bucket_access.json
}
