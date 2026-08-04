# Architecture Design Document
## Personal Health Tracking App — Korean Market

**Version:** 1.1
**Date:** June 18, 2026
**Author:** System Architect
**Status:** Reflects current implementation and documented deployment direction (post-MVP)

> v1.1 변경 요약: 패키지 구조를 실제 구현(`domain/*` 단일 계층, `infrastructure/` 폐지)에 맞춰
> 전면 갱신. 신규 도메인·기능 반영 — 소셜 로그인(Apple/Google OIDC), AI 식단/운동 추정,
> 식단 사진 분석, 알레르기·식이제한 필터, 일일 식단 추천 엔진, 공공 식품(MFDS) 카탈로그
> 임포터, 인사이트/주간 요약, 레이트 리밋·프리미엄 게이팅, Prometheus/Grafana 관측.

---

## Table of Contents

1. [System Overview](#1-system-overview)
2. [Tech Stack Decisions](#2-tech-stack-decisions)
3. [Full Package Structure](#3-full-package-structure)
4. [Deployment Architecture](#4-deployment-architecture)

---

## 1. System Overview

### 1.1 Component Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                            CLIENTS                                       │
│                                                                          │
│   ┌──────────────────────┐          ┌──────────────────────────┐        │
│   │   Mobile App         │          │   Web Browser            │        │
│   │   (iOS / Android)    │          │   (React / future v2)    │        │
│   └──────────┬───────────┘          └──────────────┬───────────┘        │
└──────────────┼──────────────────────────────────────┼────────────────────┘
               │  HTTPS / TLS 1.3                     │
               └──────────────────┬───────────────────┘
                                  │
┌─────────────────────────────────▼────────────────────────────────────────┐
│                        API GATEWAY / LOAD BALANCER                        │
│                    (AWS ALB — ap-northeast-2 Seoul)                        │
└─────────────────────────────────┬────────────────────────────────────────┘
                                  │
┌─────────────────────────────────▼────────────────────────────────────────┐
│                         SPRING BOOT APPLICATION                            │
│                        (EC2 / Docker container)                            │
│                                                                            │
│  ┌────────────┐  ┌────────────┐  ┌─────────────┐  ┌──────────────────┐  │
│  │  Security  │  │  Auth /    │  │  Exercise   │  │  Diet            │  │
│  │  (JWT +    │  │  OAuth     │  │  (+AI est.) │  │  (catalog, AI,   │  │
│  │   OIDC)    │  │ (Apple/Goog)│  │             │  │  allergen, rec.) │  │
│  └────────────┘  └────────────┘  └─────────────┘  └──────────────────┘  │
│                                                                            │
│  ┌────────────┐  ┌────────────┐  ┌─────────────┐  ┌──────────────────┐  │
│  │ BodyMeasure│  │  Goals     │  │  Insights   │  │  Nutrition       │  │
│  │ +ProgPhoto │  │ (+sched.)  │  │ (weekly,    │  │  (targets,       │  │
│  │            │  │            │  │  change)    │  │   calculator)    │  │
│  └────────────┘  └────────────┘  └─────────────┘  └──────────────────┘  │
│                                                                            │
│  common: RateLimitingFilter · Premium/Admin guards · NotificationCenter   │
└──────┬───────────┬───────────┬───────────┬──────────────┬────────────────┘
       │           │           │           │              │
       ▼           ▼           ▼           ▼              ▼
┌──────────┐ ┌──────────┐ ┌─────────┐ ┌──────────┐ ┌────────────────────┐
│PostgreSQL│ │ Caffeine │ │ AWS S3  │ │ External │ │  AI Providers      │
│ (RDS)    │ │(in-proc) │ │         │ │ Food API │ │                    │
│          │ │          │ │ Progress│ │          │ │ ┌────────────────┐ │
│ users    │ │ external │ │ + meal  │ │ ┌──────┐ │ │ │ OpenAI         │ │
│ exercise │ │  food    │ │ photos  │ │ │ MFDS │ │ │ │ (meal photo,   │ │
│ diet     │ │  search  │ │         │ │ │식약처│ │ │ │  nutrition/    │ │
│ food_    │ │ user     │ │         │ │ │ 공공  │ │ │ │  exercise est.)│ │
│  catalog │ │  profile │ │         │ │ │ 식품  │ │ │ └────────────────┘ │
│ allergen │ │          │ │         │ │ │ API  │ │ │ (fallback when    │
│ recommend│ │          │ │         │ │ └──────┘ │ │  not configured)   │
└──────────┘ └──────────┘ └─────────┘ └──────────┘ └────────────────────┘
       │                                                      │
       │                                         ┌────────────▼──────────────┐
       │                                         │  FCM (Firebase Cloud      │
       └─── OAuth JWKS (Apple/Google) ◄──────────┤  Messaging)               │
                                                 │  Push → Mobile Clients    │
                                                 └───────────────────────────┘
```

### 1.2 Data Flow Narrative — "Log Exercise Session" Request

The following describes the complete request lifecycle when a user finishes a strength workout and saves the session:

1. **Client sends POST /api/v1/exercise/sessions** with a JSON payload containing session metadata (date, duration, notes) and an array of exercise sets (exercise_catalog_id, set_number, weight_kg, reps, rest_seconds). The request carries a JWT Bearer token in the Authorization header. TLS 1.3 encrypts the transport.

2. **Spring Security filter chain** intercepts the request at `JwtAuthenticationFilter`. The filter extracts the JWT, validates the signature using the secret key, checks the expiry timestamp, and resolves the `UserDetails` from the token's `sub` claim. If validation succeeds, an `Authentication` object is placed in the `SecurityContextHolder`.

3. **ExerciseController** receives the authenticated request. It delegates to `ExerciseSessionService`, passing the validated `CreateSessionRequest` DTO and the authenticated user's ID extracted from the security context.

4. **ExerciseSessionService** orchestrates the write:
   - Validates that all referenced `exercise_catalog_id` values exist and belong to either the global catalog or the authenticated user's custom exercises.
   - Creates and persists an `ExerciseSession` entity via `ExerciseSessionRepository`.
   - For each set in the payload, creates `ExerciseSet` entities and bulk-inserts them via `ExerciseSetRepository`.
   - Checks for personal record (PR) conditions: queries the historical maximum weight at any rep count for each exercise. If the current set exceeds the stored PR, updates the record and enqueues a PR notification event.
   - Returns a `SessionSummaryResponse` DTO containing the new session ID, computed total volume (sets × reps × weight), and any new PRs flagged.

5. **PR Notification path** (asynchronous, Spring `@Async`): If any new PR is detected, `FcmNotificationService` calls the Firebase Cloud Messaging API with the user's stored FCM device token. The notification is delivered to the mobile client immediately. This is fire-and-forget; failure is logged but does not affect the HTTP response.

6. **HTTP 201 Created** is returned to the client with the `SessionSummaryResponse` body and a `Location` header pointing to `/api/v1/exercise/sessions/{newId}`. The client displays the summary screen immediately (optimistic UI).

---

## 2. Tech Stack Decisions

### 2.1 Java 21 + Spring Boot 3.x

Java 21 is the current LTS release and is required for Spring Boot 3.x's full feature set. Virtual threads (Project Loom, enabled via `spring.threads.virtual.enabled=true`) eliminate the thread-per-request bottleneck for I/O-bound workloads such as external food API calls, without requiring reactive programming paradigms that would increase onboarding complexity for a solo developer. Spring Boot 3.x's native compilation support via GraalVM provides a path to sub-100ms cold starts on Lambda if the deployment model evolves. The Spring ecosystem's conventions — auto-configuration, dependency injection, Spring Data, Spring Security — substantially reduce boilerplate and keep focus on business logic.

### 2.2 Spring Data JPA + Hibernate

Spring Data JPA provides the repository abstraction layer over Hibernate ORM. The research report (section 4.1) confirms that PostgreSQL at personal-app scale (2,000–15,000 rows over 5 years) requires no special ORM tuning; standard Hibernate with connection pooling via HikariCP is more than sufficient. JPA's `@Query` annotations allow raw JPQL or native SQL for complex aggregation queries (weekly volume trends, macro totals) without abandoning the typed entity model. Hibernate's second-level cache is intentionally disabled in favor of an explicit application-level cache (Caffeine), which keeps cache membership and eviction visible in business code rather than hidden in the ORM.

### 2.3 Spring Security + JWT

Spring Security provides a mature, battle-tested security filter chain. JWT (JSON Web Tokens) are used for stateless authentication: an access token (24-hour expiry, per PRD section 7.5) and a refresh token (30-day expiry, stored server-side as a hash in the `refresh_tokens` table to enable revocation). The stateless access token allows horizontal scaling without shared session state. Refresh token rotation — issuing a new refresh token on every use and invalidating the previous one — limits the damage window if a refresh token is compromised. All sessions are invalidated on password change or account deletion (PRD section 7.5). The `jjwt` library (io.jsonwebtoken) is used for token signing with HMAC-SHA256.

### 2.4 PostgreSQL (Primary Database)

The research report (section 4.1) provides explicit justification: PostgreSQL is the correct and sufficient choice at this scale. Time-series databases (TimescaleDB, InfluxDB) are designed for millions of rows per day; the app generates approximately 2,000–3,000 rows per year per user. PostgreSQL's full SQL JOIN support is essential for the relational schema — food items reference meal items, which reference meals, which reference users. All major tables use a composite index on `(user_id, logged_at)` as recommended in the research report. `TIMESTAMPTZ` is used for all timestamp columns to avoid timezone ambiguity, which is critical for the streak evaluation logic (PRD section 5.4). Soft-delete via `deleted_at TIMESTAMPTZ` is implemented across all user-owned entities.

### 2.5 Caffeine (Caching Layer)

Caching uses an in-process Caffeine cache with two explicit targets: admin/enrichment-only external food search results (`external-food-search`, TTL 30 days) and user profile data (`userProfile`, TTL 1 hour, evicted on profile/body-measurement/goal updates). Spring Cache abstraction (`@Cacheable`, `@CacheEvict`) keeps cache management co-located with business logic. The cache holds no session state — JWT statelessness makes this unnecessary, and refresh tokens are persisted in PostgreSQL via the `RefreshToken` entity.

This layer previously ran on a dedicated ElastiCache Redis cluster. It was removed because the application runs as a single instance and every cached value is derived data that can be recomputed from PostgreSQL or the external food API, so a network-attached cache added cost without adding capability. Two consequences follow from the move in-process: the cache is empty after every restart and must warm up again, and each cache carries an explicit maximum entry count (`app.cache.*-max-entries`) to bound heap usage. Should the application ever scale beyond one instance, the cache becomes per-instance and a shared store must be reconsidered.

### 2.6 AWS S3 (Progress Photo Storage)

The research report (section 4.4) is explicit: progress photos must never be stored as BLOBs in the relational database; S3-compatible object storage with signed URLs is the required architecture. AWS S3 in ap-northeast-2 (Seoul) satisfies the PRD's Korean server region requirement (PRD section 7.5) and the PIPA cross-border transfer constraint. EXIF stripping is performed server-side using the `metadata-extractor` and `Apache Commons Imaging` libraries within 5 seconds of upload (PRD section 7.1). Three thumbnail sizes are generated server-side: 150px (grid), 400px (comparison), 800px (full screen). Signed URL TTL is 15 minutes (PRD section 7.5). Server-side encryption is AES-256 (SSE-S3). Cross-region replication to ap-northeast-3 (Osaka) is configured for disaster recovery given the sensitive, personal nature of progress photos.

### 2.7 FCM (Firebase Cloud Messaging)

Firebase Cloud Messaging provides a managed, cross-platform push notification delivery infrastructure for both Android and iOS. The PRD notification strategy (section 5) requires event-triggered, immediate notifications for PRs and milestones — FCM's server-side SDK allows these to be sent from any backend service. FCM handles platform-specific delivery details (APNs for iOS, FCM direct for Android), eliminating the need to maintain two separate notification pipelines. The Firebase Admin SDK for Java is integrated via the `firebase-admin` dependency. Device tokens are stored in the `users.fcm_token` column and refreshed when the mobile client reports a new token.

### 2.8 식품 데이터 — MFDS 공공 식품 API + 카탈로그 임포터

초기 설계(v1.0)는 USDA FoodData Central + Open Food Facts를 가정했으나, 한국 시장 커버리지와
표시명·영양표시기준(영양소 10종) 정합성을 위해 **식약처(MFDS) 공공 식품 영양성분 API**를 1차
소스로 채택했다. 외부 API는 실시간 프록시가 아니라 **배치 임포터**(`domain/diet/external/importer`)로
`food_catalog` 테이블에 적재한다. 표준 식품·가공식품·외식 메뉴 등 소스별 페이지 페처
(`*PageFetcher`)와 페이지 단위 스로틀·체크포인트(`FoodCatalogImportCheckpoint`)로 대용량 적재를
재개 가능하게 처리하고, 표시명 정규화(`FoodDisplayNameNormalizer`)·중복 탐지
(`dedup/FoodCatalogDuplicate*`)·검색 별칭(`food_catalog_search_alias`)으로 검색 품질을 보강한다.
사용자 런타임 검색은 로컬 카탈로그(`FoodCatalogService`)만 사용한다. 공공 API 조회
(`ExternalFoodSearchService`)는 관리자 보강·검수 경로로 제한하며, 사용자 검색 요청의 fallback으로
호출하지 않는다.

### 2.9 AI 추정 — OpenAI (식단 사진 / 영양·운동 추정)

세 가지 AI 보조 기능이 OpenAI를 사용한다: (1) 식단 사진 분석(`domain/diet/mealphoto`) — 사진을
S3에 업로드 후 `OpenAiMealAnalysisProvider`가 음식 항목·추정 영양을 산출하고 사용자가 확정
(confirm)하면 식단 기록으로 전환한다. (2) 자연어 식단 영양 추정(`domain/diet/ai`). (3) 운동 칼로리
추정(`domain/exercise/ai`). 식단 사진 분석은 `MealAnalysisProvider` 인터페이스 뒤에 두며,
OpenAI provider bean을 구성할 수 없을 때 `FallbackMealAnalysisProvider`가 검토용 초안을 제공한다.
실행 중 OpenAI 호출 실패를 자동 fallback하는 계약은 아니다. 자연어 식단·운동 추정은 각 도메인의
전용 서비스를 사용한다. AI 기능은 프리미엄 게이팅 대상이다.

### 2.10 소셜 로그인 — Apple / Google OIDC

이메일/비밀번호 외에 Apple·Google ID 토큰 기반 소셜 로그인을 지원한다(`security/oauth`).
`auth0:jwks-rsa`로 발급자 JWKS를 받아 ID 토큰 서명을 검증하고(`AppleIdTokenVerifier`,
`GoogleIdTokenVerifier`, 공통 `JwksIdTokenVerifier`), 동일 이메일이면 기존 계정에 자동 연결한다.
검증 통과 후에는 자체 JWT(access/refresh)를 발급해 이후 흐름은 기존과 동일하다. 스키마는
`V19__add_social_auth.sql`.

### 2.11 레이트 리밋 · 프리미엄/관리자 게이팅 · 동의 관리

`common/filter/RateLimitingFilter`가 엔드포인트 호출량을 제한한다. 프리미엄 기능(AI 추정·사진
분석 등)은 `PremiumAccessGuard` + `PremiumRequiredException`으로 게이팅하며 사용자 플래그는
`V17__user_premium_flag.sql`. 관리자 전용 카탈로그/알레르기 태그 운영 API는
`AdminOperationGuard`로 보호한다. 약관·개인정보 동의 시각은 `V20__add_user_consent_timestamps.sql`.

### 2.12 알레르기 · 식이제한 · 일일 식단 추천

식약처/브랜드 공식 출처 기반 알레르기 태그(`domain/diet/allergen`, `food_allergen_tags`,
`AllergenConfidenceLevel`·confidence gate)와 사용자 식이제한(`domain/diet/restriction`)을 결합해,
일일 식단 추천 엔진(`domain/diet/recommendation/engine`)이 후보 풀에서 제약을 만족하는 끼니를
구성한다. 추천 큐레이션 값 객체와 주의 판정 정책은
`RecommendationCuration`/`RecommendationCautionPolicy`로 표현하고, 저장 상태의 초기값은
시드(`V25__seed_recommendation_curation.sql`)로 관리한다. 관련 스키마:
`V22__allergen_restriction_schema.sql`, `V23__food_catalog_source_recommendation_fields.sql`,
`V26`~`V31`(알레르기 태그 시드·검증·출처).

### 2.13 Gradle

Gradle with Kotlin DSL (`build.gradle.kts`) is used for the build system. Gradle's incremental compilation and build cache make it significantly faster than Maven for iterative development cycles. The `spring-boot` and `spring-dependency-management` plugins handle dependency version alignment. Multi-module builds are supported if the project grows to separate modules for infrastructure concerns.

---

## 3. Full Package Structure

> 실제 구현은 단일 `domain/*` 계층을 쓰며 v1.0의 `infrastructure/` 패키지는 폐지됐다.
> S3/FCM은 `common/`과 각 도메인 서비스로, 영양 계산은 `domain/nutrition`으로 흡수됐다.
> `measurement`는 `bodymeasurement`(체측 + 진행 사진)로, `goal`은 `goals`로 명칭이 바뀌었다.
> 아래는 클래스 단위가 아닌 패키지·핵심 컴포넌트 수준의 현행 구조다.

```
com.healthcare
├── HealthCareApplication.java
│
├── common/
│   ├── config/        CacheConfig · S3Config · SecurityConfig · AsyncConfig · WebMvcConfig
│   ├── security/      AdminOperationGuard · PremiumAccessGuard
│   ├── filter/        RateLimitingFilter
│   ├── notification/  FcmConfig/FcmService/FcmProperties · NotificationCenterService ·
│   │                  NotificationController · NotificationLog(+Repository) ·
│   │                  WeeklyNotificationScheduler · NotificationService
│   ├── exception/     GlobalExceptionHandler · ResourceNotFound · Duplicate · Unauthorized ·
│   │                  Validation · BusinessRuleViolation · PremiumRequired
│   ├── response/      ApiResponse · ErrorResponse
│   └── web/           PageRequests
│
├── security/                  (인증/인가 인프라 — 도메인 아님)
│   ├── JwtTokenProvider · JwtAuthenticationFilter · CustomUserDetailsService · SecurityConstants
│   ├── CurrentUserId(@) · CurrentUserIdArgumentResolver
│   ├── RestAuthenticationEntryPoint · RestAccessDeniedHandler
│   └── oauth/         OAuthIdTokenVerifier · JwksIdTokenVerifier ·
│                      AppleIdTokenVerifier · GoogleIdTokenVerifier · OAuthUserInfo
│
└── domain/
    ├── user/          controller · service · repository · entity(User) · dto
    │
    ├── auth/          AuthController(register/login/소셜 로그인/refresh/logout) ·
    │                  AuthService · RefreshToken(+Repository) · dto
    │
    ├── exercise/
    │   ├── controller · service · repository · entity · dto   (세션/세트/카탈로그, PR 탐지)
    │   └── ai/        AiExerciseController · AiExerciseEstimationService · dto  (AI 칼로리 추정)
    │
    ├── diet/                                  (가장 큰 도메인 — 식단 핵심)
    │   ├── controller/   DietLogController · FoodCatalogController ·
    │   │                 FoodCatalogAdminController · ExternalFoodAdminController ·
    │   │                 FoodAllergenTagAdminController
    │   ├── service/      FoodCatalogService
    │   ├── usecase/      DietLogUseCases
    │   ├── entity/       DietLog · FoodEntry · FoodCatalog(+Source) ·
    │   │                 DietLogNutritionTotals · RecommendationCuration ·
    │   │                 RecommendationCautionPolicy · RecommendationStatus
    │   ├── identity/     FoodCatalogIdentity
    │   ├── ai/           AiNutritionController · AiNutritionEstimationService · dto
    │   ├── mealphoto/    MealPhotoAnalysisController · MealPhotoAnalysisService ·
    │   │                 MealAnalysisProvider ← OpenAiMealAnalysisProvider /
    │   │                 FallbackMealAnalysisProvider · S3MealPhotoStorageService ·
    │   │                 entity(MealPhotoAnalysis·Item) · repository · dto
    │   ├── allergen/     AllergenTag · AllergenConfidenceLevel/Gate · AllergenDataSource ·
    │   │                 FoodAllergenTag(entity) · FoodAllergenTagAdminOperations · repository · dto
    │   ├── restriction/  DietRestrictionController · DietRestrictionUseCases ·
    │   │                 DietRestriction(entity) · repository · dto
    │   ├── recommendation/  DietRecommendationController · DailyDietRecommendationUseCases ·
    │   │                 engine/DietRecommendationEngine ·
    │   │                 candidate/DietRecommendationCandidate(Pool/s) · dto
    │   ├── admin/        FoodCatalogAdminOperations · FoodCatalogNameOverrideService ·
    │   │                 FoodCatalogNameRenormalizationService
    │   └── external/     공공 식품 API 적재
    │       ├── client/   PublicFoodApiClient(+Impl)
    │       ├── service/  ExternalFoodSearchService · FoodImportService
    │       ├── config/   ExternalApiConfig · ExternalApiProperties
    │       ├── importer/ FoodCatalogIngestService · FoodCatalogPageImporter ·
    │       │             MfdsFoodNutrientDbImporter · StandardProcessedFoodImporter ·
    │       │             StandardDishFoodImporter · BrandMenuCsvImporter ·
    │       │             *PageFetcher · FoodCatalogImportCheckpoint(+Store/Repository) ·
    │       │             FoodDisplayNameNormalizer · 페이지 스로틀/배치 러너
    │       └── dedup/    FoodCatalogDuplicateReportService · DuplicateCandidateReporter · dto
    │
    ├── bodymeasurement/       (v1.0의 measurement — 체측 + 진행 사진 통합)
    │   ├── controller/   BodyMeasurementController · ProgressPhotoController
    │   ├── service/      BodyMeasurementService · ProgressPhotoService ·
    │   │                 ProgressPhotoStorageService ← S3ProgressPhotoStorageService ·
    │   │                 ProgressPhotoImageProcessor (EXIF strip · 썸네일)
    │   └── repository · entity(BodyMeasurement · ProgressPhoto) · dto (사전서명 URL 업로드 흐름)
    │
    ├── goals/                 (v1.0의 goal)
    │   ├── controller · service · repository · entity(Goal · GoalCheckpoint) · dto
    │   └── scheduler/    GoalCheckpointScheduler
    │
    ├── insights/             (신규 — 주간 요약 · 변화 분석)
    │   └── InsightsController · InsightsService · dto(WeeklySummary · ChangeAnalysis)
    │
    └── nutrition/            (신규 — v1.0 infrastructure/nutrition 대체)
        └── NutritionTargetService · NutritionCalculator · dto(NutritionTargets)
```

**iOS — WidgetKit 익스텐션** (`ios/HealthCareWidgets/`): 칼로리/목표 위젯 번들
(`HealthCareWidgetsBundle.swift` 등). App Group으로 본 앱과 데이터를 공유하고 URL 스킴으로
딥링크한다. 상세는 `ios/CONTEXT.md` 참고.

---

## 4. Deployment Architecture

### 4.1 Local Development — Docker Compose

```yaml
# docker-compose.yml (local profile)
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: healthcare_local
      POSTGRES_USER: healthcare
      POSTGRES_PASSWORD: local_password
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data

  localstack:
    image: localstack/localstack:latest
    ports:
      - "4566:4566"
    environment:
      SERVICES: s3
      DEFAULT_REGION: ap-northeast-2
    volumes:
      - localstack_data:/tmp/localstack

volumes:
  postgres_data:
  localstack_data:
```

LocalStack provides a local S3 emulation for progress photo upload/download during development. No Firebase emulator is required — FCM calls are stubbed via a `MockFcmNotificationService` bean activated by the `local` profile.

### 4.2 Production — AWS Architecture (Seoul Region: ap-northeast-2)

```
Internet
    │
    ▼
Route 53 (DNS)
    │
    ▼
AWS Certificate Manager (TLS certificate)
    │
    ▼
Application Load Balancer (ALB)
    │  Target Group: EC2 Auto Scaling Group
    ▼
┌─────────────────────────────────────────────────┐
│  EC2 Auto Scaling Group (t3.medium baseline)     │
│                                                   │
│  ┌─────────────────────────────────────────────┐ │
│  │  Docker container: healthcare-api:latest    │ │
│  │  JVM: Java 21, -Xmx512m                     │ │
│  │  Port 8080                                   │ │
│  └─────────────────────────────────────────────┘ │
└──────────────┬──────────────────────┬────────────┘
               │                      │
      ┌────────▼──────┐
      │  RDS           │
      │  PostgreSQL 17 │
      │  db.t3.micro   │
      │  Single-AZ     │
      │  ap-northeast-2│
      └───────────────┘

캐시는 애플리케이션 프로세스 내부(Caffeine)에 있어 별도 인프라 구성 요소가 없다.

S3 Bucket: healthcare-progress-photos-prod
  - Region: ap-northeast-2 (Seoul)
  - Cross-region replication: ap-northeast-3 (Osaka)
  - Server-side encryption: SSE-S3 (AES-256)
  - Public access: BLOCKED; all access via signed URLs only

FCM: Firebase Cloud Messaging (Google-managed; no regional configuration required)
```

**Scaling thresholds (MVP):**
- Scale out: CPU > 70% for 5 minutes
- Scale in: CPU < 30% for 15 minutes
- Minimum instances: 1 (MVP), 2 (post-launch)

### 4.3 Application Profile Configuration

**src/main/resources/application.yml** (base, shared across all profiles):
```yaml
spring:
  application:
    name: healthcare-api
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: false
        jdbc:
          batch_size: 50
  threads:
    virtual:
      enabled: true

server:
  port: 8080
  compression:
    enabled: true
    mime-types: application/json

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus   # /actuator/prometheus → Prometheus 스크랩
  endpoint:
    health:
      show-details: when-authorized
  metrics:
    distribution:
      percentiles-histogram:
        http.server.requests: true                # p95/p99 지연 히스토그램

app:
  jwt:
    access-token-expiry-hours: 24
    refresh-token-expiry-days: 30
  photo:
    signed-url-ttl-minutes: 15
  cache:
    food-search-ttl-days: 30
    user-profile-ttl-minutes: 60
  notifications:
    max-per-day: 2
```

**src/main/resources/application-local.yml**:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/healthcare_local
    username: healthcare
    password: local_password
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: true

app:
  s3:
    endpoint: http://localhost:4566
    bucket: healthcare-photos-local
    access-key: test
    secret-key: test
    region: ap-northeast-2
  fcm:
    mock: true
  food-api:
    usda-base-url: https://api.nal.usda.gov/fdc/v1
    off-base-url: https://world.openfoodfacts.org

logging:
  level:
    com.healthcare: DEBUG
    org.hibernate.SQL: DEBUG
```

**src/main/resources/application-dev.yml**:
```yaml
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}

app:
  s3:
    bucket: healthcare-photos-dev
    region: ap-northeast-2
  jwt:
    secret: ${JWT_SECRET}

logging:
  level:
    com.healthcare: DEBUG
```

**src/main/resources/application-prod.yml**:
```yaml
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
  jpa:
    hibernate:
      ddl-auto: validate

app:
  s3:
    bucket: healthcare-photos-prod
    region: ap-northeast-2
  jwt:
    secret: ${JWT_SECRET}
  fcm:
    credentials-path: ${FCM_CREDENTIALS_PATH}

logging:
  level:
    com.healthcare: INFO
    root: WARN
```

All secrets (DB credentials, JWT secret, FCM credentials, AWS credentials) are injected as environment variables — never committed to source control. In production, AWS Systems Manager Parameter Store or Secrets Manager provides secret injection.

### 4.4 Observability — Prometheus + Grafana

애플리케이션 메트릭은 Actuator + Micrometer로 `/actuator/prometheus`에 노출되며, Prometheus가
스크랩하고 Grafana가 시각화·알림(Slack)한다. 로컬은 `backend/docker-compose.yml`, 프로덕션은
EC2에서 앱과 동일 인스턴스에 독립 컨테이너로 운영된다(blue-green과 분리). 메모리 여유를 위해
인스턴스는 t3.medium, Grafana는 11.6.3으로 핀한다.

- 자동 메트릭: JVM(힙/GC), HTTP(`http_server_requests` 히스토그램), HikariCP, 캐시(Caffeine)
- 비즈니스 메트릭: `healthcare_auth_*`, `healthcare_diet_log_created_total`, `healthcare_diet_ai_analysis_*`
- 알림: 5xx 비율·p99 지연·힙·HikariCP·인스턴스 다운

구성·운영·트러블슈팅 상세는 **[docs/operations/MONITORING_PROMETHEUS_GRAFANA.md](docs/operations/MONITORING_PROMETHEUS_GRAFANA.md)** 참고.

---

*End of Architecture Design Document v1.1*
