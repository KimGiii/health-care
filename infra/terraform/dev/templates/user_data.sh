#!/bin/bash
set -euo pipefail

# ── 스왑 (t3.micro 1GB 메모리 보완) ─────────────────────────────────────────
fallocate -l 2G /swapfile
chmod 600 /swapfile
mkswap /swapfile
swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab

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
apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
systemctl enable docker
systemctl start docker
usermod -aG docker ubuntu

# ── AWS CLI v2 ────────────────────────────────────────────────────────────────
apt-get install -y unzip
curl -fsSL "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o /tmp/awscliv2.zip
unzip -q /tmp/awscliv2.zip -d /tmp
/tmp/aws/install
rm -rf /tmp/aws /tmp/awscliv2.zip

# ── Nginx + Certbot ───────────────────────────────────────────────────────────
apt-get install -y nginx certbot python3-certbot-nginx

cat > /etc/nginx/sites-available/healthcare-dev <<NGINX
server {
    listen 80;
    server_name ${api_domain};

    location ^~ /.well-known/acme-challenge/ {
        root /var/www/html;
    }

    location / {
        proxy_pass         http://127.0.0.1:8084;
        proxy_http_version 1.1;
        proxy_set_header   Host              \$host;
        proxy_set_header   X-Real-IP         \$remote_addr;
        proxy_set_header   X-Forwarded-For   \$proxy_add_x_forwarded_for;
        proxy_set_header   X-Forwarded-Proto \$scheme;
        proxy_read_timeout 60s;
    }
}
NGINX

ln -sf /etc/nginx/sites-available/healthcare-dev /etc/nginx/sites-enabled/healthcare-dev
rm -f /etc/nginx/sites-enabled/default
mkdir -p /var/www/html
nginx -t && systemctl reload nginx
systemctl enable --now certbot.timer || true

# ── Docker Compose (PostgreSQL + Redis) ──────────────────────────────────────
mkdir -p /opt/healthcare-dev
cat > /opt/healthcare-dev/docker-compose.yml <<'COMPOSE'
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: healthcare_dev
      POSTGRES_USER: healthcare
      POSTGRES_PASSWORD: devHealthCare2026!
    volumes:
      - postgres_data:/var/lib/postgresql/data
    restart: unless-stopped
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U healthcare"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

volumes:
  postgres_data:
COMPOSE

# DB와 Redis만 먼저 기동 (앱은 GitHub Actions 배포 시 기동)
docker compose -f /opt/healthcare-dev/docker-compose.yml up -d postgres redis

# ── ECR 로그인 cron (12시간마다) ──────────────────────────────────────────────
cat > /etc/cron.d/ecr-login-dev <<CRON
0 */12 * * * root aws ecr get-login-password --region ${aws_region} \
  | docker login --username AWS --password-stdin ${ecr_registry} >> /var/log/ecr-login-dev.log 2>&1
CRON

# ── 앱 환경변수 디렉터리 ─────────────────────────────────────────────────────
mkdir -p /etc/healthcare-dev
chown ubuntu:ubuntu /etc/healthcare-dev
echo "Ready. Run certbot and deploy app via GitHub Actions." > /etc/healthcare-dev/status
