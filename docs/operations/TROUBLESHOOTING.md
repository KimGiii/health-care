# 트러블슈팅 로그

> 작업 중 발생한 문제와 해결 과정을 누적 기록한다.
> 같은 문제 재발 시 빠르게 원인을 좁히고, 재발 방지 조치가 무엇이었는지 추적하기 위함이다.
>
> **기록 규칙**
> - 새 이슈는 문서 상단(최신순)에 추가한다.
> - 각 이슈는 `증상 / 원인 / 해결 / 재발 방지` 4단으로 적는다.
> - 임시방편(workaround)과 근본 원인(root cause)을 구분해서 적는다.

---

## 환경 분리 매트릭스 (Source of Truth)

iOS 빌드 환경과 백엔드 환경의 매핑. **이 표와 어긋나면 버그다.**

| 용도 | iOS Scheme | Build Config | xcconfig | `BASE_URL` | 백엔드 프로파일 | 백엔드 도메인 |
|---|---|---|---|---|---|---|
| 로컬 개발 | `HealthCare` (Run) | Debug | `Debug.xcconfig` | `localhost:8080` (sim) / `$(LOCAL_IP):8080` (device) | `local` | 로컬 |
| dev 테스트 | `HealthCare-Staging` | Staging | `Staging.xcconfig` | `https://dev.api.gainsy.site` | `dev` | `dev.api.gainsy.site` |
| App Store 심사/배포 | `HealthCare` (Archive) | Release | `Release.xcconfig` | `https://api.gainsy.site` | `prod` | `api.gainsy.site` |

### 핵심 원칙

- **`BASE_URL`은 xcconfig에서만 정의한다.** scheme의 `environmentVariables`로 주입하지 않는다 (xcconfig 환경 분리가 무력화됨).
- iOS 런타임 `BASE_URL` 우선순위 (`AppContainer.swift`):
  1. `ProcessInfo.environment["BASE_URL"]` — **Xcode 디버그 Run 세션에서만** 주입됨. Archive 빌드엔 없음.
  2. `Info.plist`의 `API_BASE_URL = $(BASE_URL)` — xcconfig 값
  3. `Constants.API.defaultBaseURL` (prod fallback)
- `HealthCare` scheme은 **액션마다 config가 다르다**: Run=Debug, Archive=Release. 그래서 Run으로는 prod를 볼 수 없고, prod 연결은 Archive할 때만 적용된다.

### 진단 명령 모음

```bash
# 백엔드 헬스체크 (환경별)
curl -s https://dev.api.gainsy.site/actuator/health   # dev
curl -s https://api.gainsy.site/actuator/health       # prod

# 백엔드 활성 프로파일 확인 (서버 SSH 후)
docker logs healthcare-api-dev 2>&1 | grep "profile is active"

# iOS 런타임 baseURL 확인 (Xcode 콘솔)
#   AppContainer.swift의 NSLog 진단 출력:
#   [AppContainer] resolved baseURL=...

# 소셜 로그인 엔드포인트 생존 확인 (더미 토큰 → "유효하지 않은 ID 토큰" 나오면 정상)
curl -i -s -X POST https://dev.api.gainsy.site/api/v1/auth/social-login/apple \
  -H "Content-Type: application/json" -d '{"idToken":"dummy"}'
```

---

## 2026-06-02 — iOS dev 연결 / Apple 로그인 실패 (다중 원인)

증상: `HealthCare-Staging` scheme으로 빌드해도 Apple 로그인 시 빨간 "인증에 실패했습니다." 표시. dev 백엔드 로그에는 해당 요청이 **찍히지 않음**.

세 개의 독립적인 버그가 겹쳐 있었다.

### 이슈 A — dev 서버가 `prod` 프로파일로 기동

- **증상**: dev 서버(`dev.api.gainsy.site`) 백엔드 로그에 `The following 1 profile is active: "prod"`.
- **원인**: `.github/workflows/deploy-dev.yml`에서 dev 컨테이너를 `-e SPRING_PROFILES_ACTIVE=prod`로 실행하고 있었음. `application-prod.yml`이 적용되어 CORS(`api.gainsy.site`)·시크릿·DB 설정이 모두 prod 값을 참조.
- **해결**:
  - `deploy-dev.yml`: `SPRING_PROFILES_ACTIVE=prod` → `dev`.
  - `application-dev.yml`을 prod 수준으로 보강: `forward-headers-strategy: native`, `cors.allowed-origins: https://dev.api.gainsy.site`(누락 시 시작 단계 IllegalStateException), `rate-limit.trust-forwarded-headers: true`, S3 버킷 환경변수화(`${S3_BUCKET}`, 기존 하드코딩 `healthcare-photos-dev`는 실재하지 않는 버킷명이었음), LocalStack 기본 endpoint 제거, JSON 로그 패턴.
- **재발 방지**: 배포 후 `docker logs ... | grep "profile is active"`로 `dev` 확인을 절차화. 환경 매트릭스 문서화(상단 표).
- **커밋**: `ef89736`

### 이슈 B — dev S3 버킷명 불일치

- **증상**: dev 프로파일 적용 시 S3 업로드/조회가 `NoSuchBucket`으로 깨질 위험.
- **원인**: `application-dev.yml`에 `healthcare-photos-dev`가 하드코딩되어 있었으나, 실제 Terraform이 만든 버킷명은 `healthcare-dev-progress-photos-621770702801`. `deploy-dev.yml`이 주입하는 `DEV_S3_BUCKET` secret은 무시되고 있었음.
- **해결**: `application-dev.yml`의 `s3.bucket`을 `${S3_BUCKET}` 환경변수 참조로 변경. 실제 버킷명은 `infra/terraform/dev`의 `terraform output dev_s3_bucket`으로 확인.
- **재발 방지**: 버킷·도메인 등 인프라 식별자는 코드에 하드코딩하지 말고 환경변수/Terraform output을 single source로 사용.
- **커밋**: `ef89736`

### 이슈 C — scheme 환경변수가 xcconfig를 덮어씀 (근본 원인)

- **증상**: Staging scheme인데도 iOS 런타임 `resolved baseURL=https://api.gainsy.site`(prod). 그래서 로그인 요청이 prod로 가고(prod엔 계정 없음 → 401), dev 백엔드 로그엔 흔적이 없었던 것.
- **진단**: `AppContainer.swift`에 NSLog 추가 → `env BASE_URL=https://api.gainsy.site`가 `Info.plist API_BASE_URL=https://dev.api.gainsy.site`를 덮어쓰고 있음을 확인.
- **원인**: `ios/project.yml`(xcodegen source of truth)의 두 scheme(`HealthCare`, `HealthCare-Staging`) `run.environmentVariables`에 `BASE_URL: https://api.gainsy.site`(prod)가 박혀 있었음. 런타임 우선순위상 env가 xcconfig 기반 Info.plist를 덮어써서, 어떤 scheme으로 Run해도 prod로 연결됨.
- **해결**: `project.yml` 두 scheme의 `environmentVariables` 블록 제거 → `xcodegen generate` 재실행. 이후 각 scheme이 xcconfig대로 동작(Debug=localhost, Staging=dev, Release=prod).
- **재발 방지**:
  - scheme env로 `BASE_URL`을 박지 않는다 (주석으로 명시). `.xcodeproj`는 xcodegen이 생성하므로 `project.yml`이 유일한 수정 지점.
  - `AppContainer.swift`에 baseURL 진단 NSLog 상시 유지 → 빌드 환경 오인을 즉시 발견.
- **커밋**: `1a3e93a` (project.yml), `a5b5e0c` (진단 로그)

### 무시해도 되는 노이즈 로그

- `nw_path_necp_check_for_updates Failed to copy updated result (22)` — iOS 네트워크 스택 시스템 로그.
- `Could not create a sandbox extension for '...HealthCare.app'` — 시뮬레이터/디바이스 샌드박스 경고.
- `WebContent[...] Unable to hide query parameters from script (missing data)` — WebKit 내부 로그.
- 백엔드 `JwksIdTokenVerifier ... Invalid compact JWT string ... Found: 0` — **더미 토큰으로 엔드포인트 생존 확인 시** 나오는 정상 거절.

---

## 2026-06-02 — dev 배포 파이프라인 권한/시크릿 정리

증상: `deploy-dev.yml` 워크플로가 AWS 자격증명·IAM 권한 단계에서 연속 실패.

- **원인 & 해결**:
  - **시크릿 충돌** — dev/prod가 같은 `AWS_*` 시크릿을 공유해 서로 덮어씀 → dev 전용 `DEV_AWS_*`로 분리(`4501a36`). environment 시크릿 접근을 위해 deploy job에 `environment: dev` 지정(`8f995be`).
  - **IAM 권한 누락** — EC2 `Describe*` 권한이 하나씩 막혀 배포가 단계마다 멈춤 → 와일드카드 `ec2:Describe*`로 통합(`655ca4b`). 존재하지 않는 액션 `s3:DeleteBucketCORS`가 정책에 있어 IAM 적용 실패 → 제거(`9c3d02b`). security group description의 apostrophe(`'`)가 AWS API에서 거부 → 제거(`fa7a582`).
- **재발 방지**: 환경별 시크릿은 접두사로 분리(`DEV_*`/prod). IAM 정책 작성 시 실제 존재하는 액션명인지 확인(AWS 액션 레퍼런스). description 등 문자열 필드에 특수문자 주의.

## 2026-06-02 — iOS 실기기 cold-start 시 API 호출 실패

증상: 실기기에서 앱 cold-start 직후 LAN 라우팅 미복구로 첫 API 호출들이 무더기 실패.

- **원인**: 탭 전환 시 view가 매번 재생성되어 4개 API가 병렬 호출되며 cold-start 부담 누적. iOS가 LAN 경로를 복구하기 전에 요청이 나가 실패.
- **해결**(`31f69eb`): `MainTabView` tabBinding을 같은 탭 재선택 시에만 reset(불필요한 재생성 제거). `APIClient`에 transient 재시도 2회 + 지수 백오프(250ms→750ms), 단 `URLError.cancelled`(view dismiss 의도)는 재시도 제외. `update-local-ip.sh`로 Mac LAN IP 자동 갱신.
- **재발 방지**: LAN 의존 cold-start는 `NetworkPathWaiter`로 경로 확인 후 요청. 자세한 함정은 메모리 `social_login.md` 참고.

## 2026-06-01 — Grafana 서브패스 + Spring 포트 충돌

증상: nginx 뒤 Grafana 서브패스(`/grafana/`) 접근 시 라우팅 깨짐.

- **원인 & 해결**(`01a3c9f`): nginx `proxy_pass` 포트가 실제 Spring Boot 포트와 어긋남. Spring Boot 포트를 8080→8081로 정리하고 nginx proxy_pass 포트를 맞춤.

## 2026-05-29 — 모니터링 스택(Grafana/Prometheus) 구축 중 장애 4건

dev 모니터링 구축 과정에서 연달아 발생.

- **Grafana 무한 리다이렉트**(`ed8acd6`): `serve_from_sub_path=true`에서 `proxy_pass` URL 끝 슬래시가 `/grafana/` 접두사를 제거 → `ERR_TOO_MANY_REDIRECTS`. **proxy_pass 끝 슬래시 제거**로 접두사 보존.
- **t3.small thrashing 504**(`738890a`): `grafana:latest`가 v13을 받아오며 apiserver·k8s 스토리지 등 무거운 서브시스템 구동 → t3.small에서 `SQLITE_BUSY`·handler timeout(504). **11.6.3 LTS로 핀**, mem_limit 180m→350m, 불필요 기능 비활성화. prometheus도 v3.1.0 핀.
- **지연 패널 No data**(`2505df5`): `http.server.requests` percentiles-histogram 미활성 → p95/p99 버킷 없음. 히스토그램 활성화. 5xx 비율 쿼리에 `or vector(0)` 추가(에러 0건 시 No data 대신 0% 표시).
- **NoData 알림 노이즈**(`aedf4bb`): 배포 중·무트래픽 시 5xx/지연/힙/HikariCP 규칙이 `DatasourceNoData`로 오발. 실제 다운은 `hc-instance-down`이 잡으므로 나머지 4개 규칙은 `noDataState: OK`.
- **재발 방지**: 컨테이너 이미지는 항상 버전 핀(`:latest` 금지). 작은 인스턴스에선 무거운 기능 비활성화. 알림 규칙은 NoData와 실제 장애를 구분.
- 상세: [MONITORING_PROMETHEUS_GRAFANA.md](MONITORING_PROMETHEUS_GRAFANA.md)

## 2026-05-28 — Redis 캐시 장애가 서비스 장애로 전파

증상: prod에서 `QueryTimeoutException: Redis command timed out`이 연속 발생, `@Cacheable` 경유 API가 500으로 실패.

- **원인**: `CachingConfigurer.errorHandler` 미설정 → Redis 타임아웃·연결 실패가 캐시 추상화 밖으로 그대로 전파. 캐시는 성능 최적화일 뿐인데 필수 의존성처럼 동작해 Redis 불안정이 곧 사용자 장애로 직결.
- **해결**(`f77934c`): `RedisConfig`가 `CachingConfigurer` 구현, `CacheErrorHandler` 등록. GET 실패 → 캐시 미스로 처리되어 DB 조회 실행. PUT/EVICT/CLEAR 실패 → 로그만 남기고 무시(TTL 1h로 정리). Redis가 죽어도 DB 경로로 서비스 지속.
- **재발 방지**: 캐시는 항상 graceful degradation. 근본 원인(Redis 응답 지연)은 ElastiCache CPU/메모리·네트워크·`maxmemory-policy` 인프라 점검 별도 필요 — 본 수정은 코드 레벨 완화책.

## 2026-05-19 — App Store 재심사 거절 3건

> Submission `a5176114...`, v1.0(6). 상세: [APPSTORE_REVIEW_REJECTION_2026_05_19.md](../exec-plans/APPSTORE_REVIEW_REJECTION_2026_05_19.md)

- **1.4.1 의학 정보 출처 누락**: BMI 계산식·영양 수치에 출처(citation) 없음 → `MedicalSourcesView` 신설(WHO 등 출처 명시).
- **2.5.1 HealthKit 식별 누락**: Info.plist/project.yml에 `NSHealth*UsageDescription` 키 선언됐으나 실제 `import HealthKit` 사용 0건 → **키 제거**(미사용 권한 선언이 리젝 사유).
- **2.1 ATT 프롬프트 미발견**: `applicationDidBecomeActive`에서 너무 이른 시점 호출 + 푸시 권한과 동시 트리거로 가려짐 → 데이터 수집 직전·맥락 전달 후로 요청 시점 이동.
- **재발 방지**: plist에 미사용 권한 키를 남기지 않는다. 시스템 권한 프롬프트는 적절한 맥락·타이밍에 단독 노출.

## 2026-05-15 — App Store 거절: 운영 백엔드 502 (영구 다운)

> Guideline 2.1(a). Apple 보고: "Error for registration and login." 상세: [APPSTORE_REVIEW_REJECTION_2026_05_15.md](../exec-plans/APPSTORE_REVIEW_REJECTION_2026_05_15.md)

증상: 심사 시점 운영 도메인의 `/actuator/health`, `/auth/register`, `/auth/login` 모두 **502 Bad Gateway**. (코드·BASE_URL·ATS·SSL은 모두 정상으로 검증됨 → 앱 문제 아님)

- **원인**: nginx 뒤 Spring Boot 컨테이너가 죽어 upstream 호출 실패. 게다가 Docker `--restart` 정책 부재 등으로 **한 번 죽으면 자동 회복 불가능한 구조**(영구 502).
- **해결**: 백엔드 컨테이너 복구 + 자동 회복 보강(restart 정책 등). 이후 헬스체크 기반 배포 검증 절차화.
- **재발 방지**: 컨테이너 `--restart unless-stopped`(현재 `deploy-dev.yml`에 적용됨). 배포 워크플로에 헬스체크 게이트. 운영 다운 감지용 `hc-instance-down` 알림(모니터링 스택). **앱 심사 전 운영 헬스체크 필수 확인.**
