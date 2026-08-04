data "aws_ami" "ubuntu" {
  most_recent = true
  owners      = ["099720109477"] # Canonical

  filter {
    name   = "name"
    values = ["ubuntu/images/hvm-ssd-gp3/ubuntu-noble-24.04-amd64-server-*"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

# ── IAM Role (EC2 → ECR, S3, CloudWatch) ─────────────────────────────────────

resource "aws_iam_role" "ec2" {
  name = "${var.project_name}-${var.environment}-ec2-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })

  tags = local.common_tags
}

resource "aws_iam_role_policy_attachment" "ec2_ecr" {
  role       = aws_iam_role.ec2.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
}

resource "aws_iam_role_policy_attachment" "ec2_cloudwatch" {
  role       = aws_iam_role.ec2.name
  policy_arn = "arn:aws:iam::aws:policy/CloudWatchAgentServerPolicy"
}

resource "aws_iam_role_policy_attachment" "ec2_s3" {
  role       = aws_iam_role.ec2.name
  policy_arn = aws_iam_policy.progress_photo_bucket_access.arn
}

resource "aws_iam_instance_profile" "ec2" {
  name = "${var.project_name}-${var.environment}-ec2-profile"
  role = aws_iam_role.ec2.name
}

# ── EC2 Instance ──────────────────────────────────────────────────────────────

resource "aws_instance" "app" {
  ami                    = data.aws_ami.ubuntu.id
  instance_type          = var.ec2_instance_type
  subnet_id              = aws_subnet.public[0].id
  vpc_security_group_ids = [aws_security_group.ec2.id]
  iam_instance_profile   = aws_iam_instance_profile.ec2.name
  key_name               = var.ec2_key_name

  root_block_device {
    volume_type           = "gp3"
    volume_size           = 20
    delete_on_termination = true
  }

  user_data = base64encode(templatefile("${path.module}/templates/user_data.sh", {
    aws_region   = var.aws_region
    ecr_registry = "${data.aws_caller_identity.current.account_id}.dkr.ecr.${var.aws_region}.amazonaws.com"
    project_name = var.project_name
    environment  = var.environment
    api_domain   = "api.${var.root_domain}"
  }))

  tags = merge(local.common_tags, {
    Name = "${var.project_name}-${var.environment}-app"
  })

  lifecycle {
    # data.aws_ami.ubuntu는 most_recent = true라 새 Ubuntu 이미지가 나올 때마다 id가 바뀌고,
    # ami는 ForceNew 속성이라 terraform이 인스턴스를 교체하려 한다. 이 박스에는 certbot이
    # 발급한 TLS 인증서, 배포 스크립트가 수정한 nginx 설정, Prometheus/Grafana 도커 볼륨처럼
    # user_data로 재현되지 않는 상태가 있어 교체하면 유실된다.
    #
    # 이미지를 갱신할 때는 이 항목을 일시적으로 빼고 계획을 확인한 뒤, 위 상태를 옮길 준비가
    # 된 상태에서 의도적으로 교체한다. (#111)
    ignore_changes = [ami]
  }
}

data "aws_caller_identity" "current" {}

resource "aws_eip" "app" {
  instance = aws_instance.app.id
  domain   = "vpc"

  tags = merge(local.common_tags, {
    Name = "${var.project_name}-${var.environment}-eip"
  })
}

# ── CloudWatch 로그 그룹 ──────────────────────────────────────────────────────

resource "aws_cloudwatch_log_group" "app" {
  name              = "/${var.project_name}/${var.environment}/app"
  retention_in_days = 30
}

# ── CloudWatch 알람 ───────────────────────────────────────────────────────────
#
# 제거됨 (#111). 기존 알람 4개(ec2_cpu_high, rds_cpu_high, rds_storage_low,
# rds_connections_high)는 alarm_actions가 비어 있어 임계치를 넘어도 아무에게도
# 알리지 않았다. 실제 알림 경로는 Grafana의 Slack contact point이며
# (infra/monitoring/grafana/alerting/), 알람 정의는 그쪽에서 유지한다.
#
# CloudWatch 알람을 다시 도입한다면 SNS 토픽과 alarm_actions를 함께 정의해야 한다.
