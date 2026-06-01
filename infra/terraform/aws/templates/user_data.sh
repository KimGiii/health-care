#!/bin/bash
set -euo pipefail

# ── Docker ────────────────────────────────────────────────────────────────────
apt-get update -y
apt-get install -y ca-certificates curl gnupg

install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
  | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
chmod a+r /etc/apt/keyrings/docker.gpg

echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
  https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  > /etc/apt/sources.list.d/docker.list

apt-get update -y
apt-get install -y docker-ce docker-ce-cli containerd.io
systemctl enable docker
systemctl start docker
usermod -aG docker ubuntu

# ── AWS CLI v2 ────────────────────────────────────────────────────────────────
apt-get install -y unzip
curl -fsSL "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o /tmp/awscliv2.zip
unzip -q /tmp/awscliv2.zip -d /tmp
/tmp/aws/install
rm -rf /tmp/aws /tmp/awscliv2.zip

# ── nginx ─────────────────────────────────────────────────────────────────────
#
# 초기 설치 시점에는 DNS/도메인이 아직 연결되지 않았을 수 있으므로 80번 포트
# 기본 설정만 깔아둔다. Certbot은 NS 변경 후 운영자가 수동으로 실행한다:
#
#   sudo certbot --nginx -d ${api_domain} \
#     --non-interactive --agree-tos -m <admin-email>
#
# Certbot이 성공하면 자동으로 443 서버 블록과 HTTP→HTTPS 리다이렉트가 추가된다.

apt-get install -y nginx certbot python3-certbot-nginx

cat > /etc/nginx/sites-available/healthcare <<NGINX
server {
    listen 80;
    server_name ${api_domain} _;

    # Let's Encrypt HTTP-01 challenge 경로
    location ^~ /.well-known/acme-challenge/ {
        root /var/www/html;
    }

    location /s3/ {
        proxy_pass              http://127.0.0.1:4566/;
        proxy_buffering         off;
        proxy_request_buffering off;
        client_max_body_size    25m;
    }

    location /actuator/health {
        proxy_pass http://127.0.0.1:8081;
    }

    # Grafana (모니터링 대시보드) — 경로 기반. 컨테이너는 127.0.0.1:3000에만 바인딩.
    # serve_from_sub_path=true이므로 proxy_pass 끝에 슬래시를 두지 않는다(접두사 보존).
    # 슬래시를 붙이면 /grafana/ 접두사가 제거돼 무한 리다이렉트(ERR_TOO_MANY_REDIRECTS) 발생.
    location /grafana/ {
        proxy_pass         http://127.0.0.1:3000;
        proxy_set_header   Host              \$host;
        proxy_set_header   X-Real-IP         \$remote_addr;
        proxy_set_header   X-Forwarded-For   \$proxy_add_x_forwarded_for;
        proxy_set_header   X-Forwarded-Proto \$scheme;
        # Grafana Live (WebSocket)
        proxy_set_header   Upgrade           \$http_upgrade;
        proxy_set_header   Connection        "upgrade";
    }

    location / {
        proxy_pass         http://127.0.0.1:8081;
        proxy_http_version 1.1;
        proxy_set_header   Host              \$host;
        proxy_set_header   X-Real-IP         \$remote_addr;
        proxy_set_header   X-Forwarded-For   \$proxy_add_x_forwarded_for;
        proxy_set_header   X-Forwarded-Proto \$scheme;
        proxy_read_timeout 60s;
    }
}
NGINX

ln -sf /etc/nginx/sites-available/healthcare /etc/nginx/sites-enabled/healthcare
rm -f /etc/nginx/sites-enabled/default
mkdir -p /var/www/html
nginx -t && systemctl reload nginx

# Certbot 자동 갱신 — 시스템 timer는 우분투 패키지에 기본 등록되어 있음
systemctl enable --now certbot.timer || true

# ── 앱 런타임 디렉토리 (FCM 자격증명, blue-green 상태 파일) ─────────────────
mkdir -p /etc/healthcare
chown ubuntu:ubuntu /etc/healthcare
echo "8080" > /etc/healthcare/active_port

# ── ECR 로그인 cron (12시간마다 토큰 갱신) ───────────────────────────────────
cat > /etc/cron.d/ecr-login <<CRON
0 */12 * * * root aws ecr get-login-password --region ${aws_region} \
  | docker login --username AWS --password-stdin ${ecr_registry} >> /var/log/ecr-login.log 2>&1
CRON

# ── CloudWatch Agent ──────────────────────────────────────────────────────────
wget -q https://s3.amazonaws.com/amazoncloudwatch-agent/ubuntu/amd64/latest/amazon-cloudwatch-agent.deb \
  -O /tmp/amazon-cloudwatch-agent.deb
dpkg -i /tmp/amazon-cloudwatch-agent.deb
rm /tmp/amazon-cloudwatch-agent.deb

cat > /opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json <<'CWA'
{
  "logs": {
    "logs_collected": {
      "files": {
        "collect_list": [
          {
            "file_path": "/var/log/syslog",
            "log_group_name": "/${project_name}/${environment}/syslog"
          },
          {
            "file_path": "/var/log/nginx/error.log",
            "log_group_name": "/${project_name}/${environment}/nginx-error"
          }
        ]
      }
    }
  },
  "metrics": {
    "metrics_collected": {
      "mem": { "measurement": ["mem_used_percent"] },
      "disk": { "measurement": ["disk_used_percent"], "resources": ["/"] }
    }
  }
}
CWA

/opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl \
  -a fetch-config -m ec2 \
  -c file:/opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json -s
