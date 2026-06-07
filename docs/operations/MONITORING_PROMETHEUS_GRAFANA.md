# 모니터링 구축 가이드 — Prometheus + Grafana

백엔드 메트릭을 Prometheus로 수집하고 Grafana로 시각화·알림하는 스택의 구성·운영·트러블슈팅을 정리한다.
코드/IaC 변경은 이미 커밋되어 있으며, 운영자가 수행할 수동 절차와 함정을 함께 기록한다.

## 개요

- **수집**: Spring Boot Actuator + `micrometer-registry-prometheus` → `/actuator/prometheus`
- **저장/스크랩**: Prometheus (보존 7d / 300MB)
- **시각화·알림**: Grafana (대시보드 자동 프로비저닝 + Slack 알림)
- **배포 범위**: 로컬(docker-compose) + 프로덕션(EC2, 앱과 동일 인스턴스, blue-green과 독립)

## 변경된 파일 (참고)

**백엔드 (로컬·프로덕션 공통 아티팩트)**
- `backend/build.gradle.kts` — `micrometer-registry-prometheus` 추가
- `backend/src/main/resources/application.yml` — actuator `prometheus` 노출, `application` 공통 태그, `http.server.requests` 히스토그램 활성화
- `backend/src/main/java/com/healthcare/common/config/SecurityConfig.java` — `/actuator/prometheus` 인증 면제
- `domain/auth/service/AuthService.java` — 회원가입·로그인(result별) Counter
- `domain/diet/service/DietLogService.java` — 식단 기록 Counter
- `domain/diet/ai/service/AiNutritionEstimationService.java` — AI 분석 Timer

**로컬**
- `backend/docker-compose.yml` — prometheus·grafana 서비스 + 볼륨
- `backend/.env.example` — `GRAFANA_ADMIN_PASSWORD`
- `backend/monitoring/**` — prometheus.yml, grafana 데이터소스·대시보드·알림 규칙 프로비저닝

**프로덕션**
- `infra/monitoring/docker-compose.monitoring.yml` — 메모리 제한·보존 단축 독립 스택
- `infra/monitoring/prometheus.prod.yml` — blue-green 8080/8081 스크랩
- `infra/monitoring/grafana/alerting/{contactpoints,policies}.yml` — Slack 채널
- `infra/terraform/aws/templates/user_data.sh` — Nginx `/grafana/` 라우트
- `infra/terraform/aws/variables.tf` — `ec2_instance_type` 기본값 `t3.medium`

## 메트릭

**자동 (Micrometer)**
- JVM: `jvm_memory_used_bytes`, `jvm_gc_pause_seconds_*`
- HTTP: `http_server_requests_seconds_*` (히스토그램 버킷 포함 → p95/p99)
- DB/Redis: `hikaricp_connections_*`, `lettuce_*`

**비즈니스 (커스텀, 0으로 사전 등록)**
- `healthcare_auth_register_total`
- `healthcare_auth_login_total{result="success|fail"}`
- `healthcare_diet_log_created_total`
- `healthcare_diet_ai_analysis_seconds_*`

## 알림 규칙 (`backend/monitoring/grafana/provisioning/alerting/rules.yml`)

| uid | 조건 | NoData |
|---|---|---|
| hc-api-5xx-ratio | 5xx 비율 > 5% (5분) | OK(무시) |
| hc-api-latency-p99 | p99 > 2s (5분) | OK(무시) |
| hc-jvm-heap | 힙 사용률 > 90% (5분) | OK(무시) |
| hc-hikari-exhaustion | active/max > 90% (5분) | OK(무시) |
| hc-instance-down | `sum(up)==0` (1분) | 기본(발동) |

> NoData=OK인 규칙은 배포 중·무트래픽 시 조용하다. 실제 다운은 hc-instance-down이 잡는다.

---

## 로컬 실행

```bash
cd backend
docker compose up -d postgres redis prometheus grafana
JWT_SECRET=... SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

- Prometheus: http://localhost:9090 (타깃 `host.docker.internal:8080`)
- Grafana: http://localhost:3000 (admin / `GRAFANA_ADMIN_PASSWORD`, 기본 `admin`)

---

## 프로덕션 부트스트랩 (EC2, 1회)

### 1. 레포 클론 + 소유자 설정

```bash
sudo git clone https://github.com/KimGiii/Gainsy.git /opt/health-care
# git pull / docker compose가 ubuntu로 동작하도록 소유자 변경 (root 소유면 dubious ownership/permission denied)
sudo chown -R ubuntu:ubuntu /opt/health-care
```

### 2. 시크릿 파일 작성

```bash
sudo tee /etc/healthcare/monitoring.env >/dev/null <<'ENV'
GRAFANA_ADMIN_PASSWORD=<강력한_비밀번호>
SLACK_WEBHOOK_URL=https://hooks.slack.com/services/...   # 없으면 빈 값 → 알림 미발송
ENV
# docker compose(ubuntu)가 --env-file을 읽도록 ubuntu 소유 + 600
sudo chown ubuntu:ubuntu /etc/healthcare/monitoring.env
sudo chmod 600 /etc/healthcare/monitoring.env
```

### 3. 스택 기동

```bash
cd /opt/health-care/infra/monitoring
docker compose -f docker-compose.monitoring.yml --env-file /etc/healthcare/monitoring.env up -d
```

### 4. Nginx `/grafana/` 라우트 (기존 인스턴스는 수동)

`user_data.sh`에 포함돼 있으나 신규 부팅 때만 실행되므로, 기존 인스턴스는
`/etc/nginx/sites-available/healthcare`의 443 서버 블록 `location /` 위에 추가:

```nginx
location /grafana/ {
    proxy_pass         http://127.0.0.1:3000;   # ⚠️ 끝 슬래시 금지 (아래 트러블슈팅 참고)
    proxy_set_header   Host              $host;
    proxy_set_header   X-Real-IP         $remote_addr;
    proxy_set_header   X-Forwarded-For   $proxy_add_x_forwarded_for;
    proxy_set_header   X-Forwarded-Proto $scheme;
    proxy_set_header   Upgrade           $http_upgrade;
    proxy_set_header   Connection        "upgrade";
}
```

```bash
sudo nginx -t && sudo nginx -s reload
```

### 접속

- Grafana: https://api.gainsy.site/grafana/ (admin / monitoring.env 값)
- Prometheus: 외부 비노출(127.0.0.1:9090). 필요 시 SSH 터널.

### 설정 갱신 (대시보드·규칙 변경 후)

```bash
cd /opt/health-care && git pull
docker restart healthcare-grafana
```

---

## 트러블슈팅 (실제 겪은 이슈)

### `permission denied` — monitoring.env
`sudo tee`로 만들면 root 소유 600이라 docker compose(ubuntu)가 못 읽음.
→ `sudo chown ubuntu:ubuntu /etc/healthcare/monitoring.env`

### Grafana 504 / 느림 / `SQLITE_BUSY`
`grafana:latest`가 v13을 받아오며 apiserver·k8s 스토리지·bleve 인덱싱 등 무거운 서브시스템으로
t3.small에서 thrashing(OOM 아님, RestartCount=0이지만 응답 지연).
→ `grafana/grafana:11.6.3`으로 핀 + `mem_limit` 상향. (compose에 반영됨)

### 메모리 부족 (t3.small 2GB)
앱 1400m + Prometheus + Grafana를 같은 박스에 얹으면 빠듯.
→ `terraform.tfvars`의 `ec2_instance_type = "t3.medium"` (4GB)로 상향 후 `terraform apply`.
타입 변경은 in-place(스톱→수정→스타트, EBS/EIP 보존), 다운타임 2~5분, 컨테이너 자동 복구.
> `variables.tf` default만 바꿔도 `terraform.tfvars`가 오버라이드하므로 tfvars를 함께 수정해야 함.

### `ERR_TOO_MANY_REDIRECTS` — /grafana/
Nginx `proxy_pass`가 끝 슬래시(`...:3000/`)면 `/grafana/` 접두사를 제거하는데,
Grafana는 `serve_from_sub_path=true`라 접두사를 기대 → 무한 리다이렉트.
→ `proxy_pass http://127.0.0.1:3000;` (끝 슬래시 제거).

### Prometheus 타깃 8080 down (정상)
blue-green 비활성 포트는 원래 꺼져 있음. 알림은 `sum(up)==0`(양쪽 다)일 때만 발동하므로 무해.

### 지연 p95/p99 패널 `No data`
Spring Boot는 기본적으로 히스토그램 버킷(`_bucket`)을 안 내보냄.
→ `application.yml`에 `management.metrics.distribution.percentiles-histogram.http.server.requests: true`.

### 5xx 비율 패널 `No data`
5xx가 0건이면 쿼리 결과가 빈 벡터.
→ 쿼리에 `or vector(0)` 추가해 0%로 표시.
