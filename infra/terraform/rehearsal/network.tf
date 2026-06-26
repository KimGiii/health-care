# ── 전용 격리 VPC (prod 와 분리) ────────────────────────────────────────────────

locals {
  azs            = ["${var.aws_region}a", "${var.aws_region}c"]
  public_subnets = ["10.9.1.0/24", "10.9.2.0/24"]
}

resource "aws_vpc" "main" {
  cidr_block           = "10.9.0.0/16"
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = merge(local.common_tags, { Name = "${local.name}-vpc" })
}

resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id
  tags   = merge(local.common_tags, { Name = "${local.name}-igw" })
}

# RDS 서브넷 그룹은 ≥2 AZ 필요. 로더가 공인 IP 로 접근하도록 public 서브넷에 둔다.
resource "aws_subnet" "public" {
  count                   = length(local.azs)
  vpc_id                  = aws_vpc.main.id
  cidr_block              = local.public_subnets[count.index]
  availability_zone       = local.azs[count.index]
  map_public_ip_on_launch = true

  tags = merge(local.common_tags, { Name = "${local.name}-public-${local.azs[count.index]}" })
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }

  tags = merge(local.common_tags, { Name = "${local.name}-rt-public" })
}

resource "aws_route_table_association" "public" {
  count          = length(aws_subnet.public)
  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

# ── RDS 보안그룹: 5432 인바운드를 로더 CIDR 로만 한정 ─────────────────────────────
resource "aws_security_group" "rds" {
  name        = "${local.name}-sg-rds"
  description = "G2 rehearsal RDS - PostgreSQL from loader only"
  vpc_id      = aws_vpc.main.id

  ingress {
    description = "PostgreSQL from loader"
    from_port   = 5432
    to_port     = 5432
    protocol    = "tcp"
    cidr_blocks = var.allowed_cidrs # 필수: 로더(백엔드)의 공인 IP /32. 0.0.0.0/0 금지.
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(local.common_tags, { Name = "${local.name}-sg-rds" })
}
