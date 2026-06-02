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
