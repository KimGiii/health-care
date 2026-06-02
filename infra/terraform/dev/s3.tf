resource "aws_s3_bucket" "dev_photos" {
  bucket = "healthcare-dev-progress-photos-${data.aws_caller_identity.current.account_id}"

  tags = merge(local.common_tags, {
    Name = "healthcare-dev-progress-photos"
  })
}

resource "aws_s3_bucket_server_side_encryption_configuration" "dev_photos" {
  bucket = aws_s3_bucket.dev_photos.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "dev_photos" {
  bucket = aws_s3_bucket.dev_photos.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_cors_configuration" "dev_photos" {
  bucket = aws_s3_bucket.dev_photos.id

  cors_rule {
    allowed_headers = ["*"]
    allowed_methods = ["GET", "HEAD", "PUT"]
    allowed_origins = ["*"]
    expose_headers  = ["ETag"]
    max_age_seconds = 3000
  }
}
