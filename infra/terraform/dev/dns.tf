# prod Terraform이 관리하는 Route53 Zone에 dev 서브도메인 레코드를 추가한다.
# zone_id는 var.route53_zone_id로 주입 (terraform.tfvars에 Z03929631UCOQNSN4RL8M 입력).

resource "aws_route53_record" "api_dev" {
  zone_id = var.route53_zone_id
  name    = "dev.api.${var.root_domain}"
  type    = "A"
  ttl     = 300
  records = [aws_eip.dev.public_ip]
}
