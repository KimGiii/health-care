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

data "aws_caller_identity" "current" {}

# ── IAM Role (EC2 → ECR pull, S3) ────────────────────────────────────────────

resource "aws_iam_role" "dev_ec2" {
  name = "healthcare-dev-ec2-role"

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

resource "aws_iam_role_policy_attachment" "dev_ec2_ecr" {
  role       = aws_iam_role.dev_ec2.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
}

resource "aws_iam_role_policy" "dev_ec2_s3" {
  name = "healthcare-dev-ec2-s3"
  role = aws_iam_role.dev_ec2.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["s3:ListBucket"]
        Resource = [aws_s3_bucket.dev_photos.arn]
      },
      {
        Effect   = "Allow"
        Action   = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"]
        Resource = ["${aws_s3_bucket.dev_photos.arn}/*"]
      }
    ]
  })
}

resource "aws_iam_instance_profile" "dev_ec2" {
  name = "healthcare-dev-ec2-profile"
  role = aws_iam_role.dev_ec2.name
}

# ── Security Group ────────────────────────────────────────────────────────────

resource "aws_security_group" "dev" {
  name        = "healthcare-dev-sg"
  description = "Dev EC2 security group"

  ingress {
    description = "HTTP (Let's Encrypt challenge)"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "HTTPS"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "SSH"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = length(var.allowed_ssh_cidrs) > 0 ? var.allowed_ssh_cidrs : ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(local.common_tags, { Name = "healthcare-dev-sg" })
}

# ── EC2 Instance ──────────────────────────────────────────────────────────────

resource "aws_instance" "dev" {
  ami                    = data.aws_ami.ubuntu.id
  instance_type          = "t3.micro"
  key_name               = var.ec2_key_name
  vpc_security_group_ids = [aws_security_group.dev.id]
  iam_instance_profile   = aws_iam_instance_profile.dev_ec2.name

  root_block_device {
    volume_type           = "gp3"
    volume_size           = 20
    delete_on_termination = true
  }

  user_data = base64encode(templatefile("${path.module}/templates/user_data.sh", {
    ecr_registry = var.ecr_registry
    aws_region   = var.aws_region
    api_domain   = "dev.api.${var.root_domain}"
  }))

  tags = merge(local.common_tags, { Name = "healthcare-dev-app" })
}

resource "aws_eip" "dev" {
  instance = aws_instance.dev.id
  domain   = "vpc"

  tags = merge(local.common_tags, { Name = "healthcare-dev-eip" })
}
