# ── Route 53 Hosted Zone ──────────────────────────────────────────────────────
#
# 도메인 등록처(가비아 등)에서 NS 레코드를 본 호스팅 영역의 네임서버 4개로
# 변경해야 DNS가 실제로 동작한다. NS 값은 `terraform output route53_nameservers`
# 로 확인한다.

resource "aws_route53_zone" "main" {
  name = var.root_domain

  tags = merge(local.common_tags, {
    Name = "${var.project_name}-${var.environment}-zone"
  })
}

# ── API 서브도메인 A 레코드 ─────────────────────────────────────────────────
#
# api.<root_domain> → EC2 Elastic IP

resource "aws_route53_record" "api" {
  zone_id = aws_route53_zone.main.zone_id
  name    = "api.${var.root_domain}"
  type    = "A"
  ttl     = 300
  records = [aws_eip.app.public_ip]
}

# ── (선택) 루트 도메인은 추후 랜딩 페이지가 생기면 별도 레코드를 추가한다.
# dev.api.gainsy.site DNS는 infra/terraform/dev/ 에서 관리한다.
