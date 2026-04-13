# Architecture Design Document
## Personal Health Tracking App — Korean Market

**Version:** 1.0
**Date:** April 9, 2026
**Author:** System Architect
**Status:** Draft for Engineering Review

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
│  │  Security  │  │  Exercise  │  │    Diet     │  │  Measurement     │  │
│  │  (JWT)     │  │  Domain    │  │  Domain     │  │  Domain          │  │
│  └────────────┘  └────────────┘  └─────────────┘  └──────────────────┘  │
│                                                                            │
│  ┌────────────┐  ┌────────────┐  ┌─────────────┐  ┌──────────────────┐  │
│  │  Goal      │  │    S3      │  │    FCM      │  │  Nutrition       │  │
│  │  Domain    │  │  Infra     │  │  Infra      │  │  Infra           │  │
│  └────────────┘  └────────────┘  └─────────────┘  └──────────────────┘  │
└──────┬────────────────┬─────────────────┬──────────────────┬─────────────┘
       │                │                 │                  │
       ▼                ▼                 ▼                  ▼
┌────────────┐  ┌──────────────┐  ┌───────────┐  ┌──────────────────────┐
│ PostgreSQL  │  │    Redis     │  │  AWS S3   │  │  External Food APIs  │
│ (RDS)      │  │ (ElastiCache)│  │           │  │                      │
│            │  │              │  │  Progress │  │  ┌──────────────────┐│
│  - users   │  │  - food      │  │  Photos   │  │  │ USDA FoodData    ││
│  - exercise│  │    search    │  │           │  │  │ Central          ││
│  - diet    │  │  - daily     │  │           │  │  └──────────────────┘│
│  - measure │  │    macro     │  │           │  │  ┌──────────────────┐│
│  - goals   │  │    totals    │  │           │  │  │ Open Food Facts  ││
│  - food    │  │  - user      │  │           │  │  │ (Korean barcodes)││
│    catalog │  │    profile   │  │           │  │  └──────────────────┘│
└────────────┘  └──────────────┘  └───────────┘  └──────────────────────┘
                                                           │
                                              ┌────────────▼──────────────┐
                                              │  FCM (Firebase Cloud      │
                                              │  Messaging)               │
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

6. **Redis cache invalidation**: `ExerciseSessionService` evicts the `daily_exercise_summary:{userId}:{date}` cache key so that the next dashboard query reflects the new session. This is done synchronously before returning the response.

7. **HTTP 201 Created** is returned to the client with the `SessionSummaryResponse` body and a `Location` header pointing to `/api/v1/exercise/sessions/{newId}`. The client displays the summary screen immediately (optimistic UI).

---

## 2. Tech Stack Decisions

### 2.1 Java 21 + Spring Boot 3.x

Java 21 is the current LTS release and is required for Spring Boot 3.x's full feature set. Virtual threads (Project Loom, enabled via `spring.threads.virtual.enabled=true`) eliminate the thread-per-request bottleneck for I/O-bound workloads such as external food API calls, without requiring reactive programming paradigms that would increase onboarding complexity for a solo developer. Spring Boot 3.x's native compilation support via GraalVM provides a path to sub-100ms cold starts on Lambda if the deployment model evolves. The Spring ecosystem's conventions — auto-configuration, dependency injection, Spring Data, Spring Security — substantially reduce boilerplate and keep focus on business logic.

### 2.2 Spring Data JPA + Hibernate

Spring Data JPA provides the repository abstraction layer over Hibernate ORM. The research report (section 4.1) confirms that PostgreSQL at personal-app scale (2,000–15,000 rows over 5 years) requires no special ORM tuning; standard Hibernate with connection pooling via HikariCP is more than sufficient. JPA's `@Query` annotations allow raw JPQL or native SQL for complex aggregation queries (weekly volume trends, macro totals) without abandoning the typed entity model. Hibernate's second-level cache is intentionally disabled in favor of explicit Redis caching, which provides observable cache behavior across application restarts.

### 2.3 Spring Security + JWT

Spring Security provides a mature, battle-tested security filter chain. JWT (JSON Web Tokens) are used for stateless authentication: an access token (24-hour expiry, per PRD section 7.5) and a refresh token (30-day expiry, stored server-side as a hash in the `refresh_tokens` table to enable revocation). The stateless access token allows horizontal scaling without shared session state. Refresh token rotation — issuing a new refresh token on every use and invalidating the previous one — limits the damage window if a refresh token is compromised. All sessions are invalidated on password change or account deletion (PRD section 7.5). The `jjwt` library (io.jsonwebtoken) is used for token signing with HMAC-SHA256.

### 2.4 PostgreSQL (Primary Database)

The research report (section 4.1) provides explicit justification: PostgreSQL is the correct and sufficient choice at this scale. Time-series databases (TimescaleDB, InfluxDB) are designed for millions of rows per day; the app generates approximately 2,000–3,000 rows per year per user. PostgreSQL's full SQL JOIN support is essential for the relational schema — food items reference meal items, which reference meals, which reference users. All major tables use a composite index on `(user_id, logged_at)` as recommended in the research report. `TIMESTAMPTZ` is used for all timestamp columns to avoid timezone ambiguity, which is critical for the streak evaluation logic (PRD section 5.4). Soft-delete via `deleted_at TIMESTAMPTZ` is implemented across all user-owned entities.

### 2.5 Redis (Caching Layer)

Redis is used for three explicit cache targets: food search results (TTL 30 days, aligns with the 30-day food catalog TTL from research section 4.2), daily macro totals per user per date (TTL until end of calendar day, evicted on any meal write), and user profile data (TTL 1 hour, evicted on profile update). These three caches directly address the PRD performance targets: food search cached response must be under 300ms (PRD section 7.1). Redis's sub-millisecond read latency makes cached food searches effectively instant. Spring Cache abstraction (`@Cacheable`, `@CacheEvict`) is used to keep cache management co-located with business logic. Redis is not used for session state — JWT statelessness makes this unnecessary.

### 2.6 AWS S3 (Progress Photo Storage)

The research report (section 4.4) is explicit: progress photos must never be stored as BLOBs in the relational database; S3-compatible object storage with signed URLs is the required architecture. AWS S3 in ap-northeast-2 (Seoul) satisfies the PRD's Korean server region requirement (PRD section 7.5) and the PIPA cross-border transfer constraint. EXIF stripping is performed server-side using the `metadata-extractor` and `Apache Commons Imaging` libraries within 5 seconds of upload (PRD section 7.1). Three thumbnail sizes are generated server-side: 150px (grid), 400px (comparison), 800px (full screen). Signed URL TTL is 15 minutes (PRD section 7.5). Server-side encryption is AES-256 (SSE-S3). Cross-region replication to ap-northeast-3 (Osaka) is configured for disaster recovery given the sensitive, personal nature of progress photos.

### 2.7 FCM (Firebase Cloud Messaging)

Firebase Cloud Messaging provides a managed, cross-platform push notification delivery infrastructure for both Android and iOS. The PRD notification strategy (section 5) requires event-triggered, immediate notifications for PRs and milestones — FCM's server-side SDK allows these to be sent from any backend service. FCM handles platform-specific delivery details (APNs for iOS, FCM direct for Android), eliminating the need to maintain two separate notification pipelines. The Firebase Admin SDK for Java is integrated via the `firebase-admin` dependency. Device tokens are stored in the `users.fcm_token` column and refreshed when the mobile client reports a new token.

### 2.8 USDA FoodData Central + Open Food Facts

The research report (section 4.2) explicitly recommends the layered approach: USDA FoodData Central as the primary database (600,000+ verified items, public domain, laboratory-tested nutrient values) and Open Food Facts as the secondary source for barcode scanning and Korean packaged food coverage (150,000+ Korean products, best free option). API responses are cached in the `food_catalog` table with a 30-day TTL and the source's `external_id` for cache invalidation — this reduces external API calls by 80%+ after initial warm-up (research section 4.2). The `NutritionApiOrchestrator` in the infrastructure layer handles the search priority: local cache first, then USDA, then Open Food Facts for barcode lookups.

### 2.9 Gradle

Gradle with Kotlin DSL (`build.gradle.kts`) is used for the build system. Gradle's incremental compilation and build cache make it significantly faster than Maven for iterative development cycles. The `spring-boot` and `spring-dependency-management` plugins handle dependency version alignment. Multi-module builds are supported if the project grows to separate modules for infrastructure concerns.

---

## 3. Full Package Structure

```
com.healthcare
├── common/
│   ├── config/
│   │   ├── RedisConfig.java
│   │   ├── S3Config.java
│   │   ├── SecurityConfig.java
│   │   ├── FcmConfig.java
│   │   ├── AsyncConfig.java
│   │   └── WebMvcConfig.java
│   ├── exception/
│   │   ├── GlobalExceptionHandler.java
│   │   ├── ResourceNotFoundException.java
│   │   ├── DuplicateResourceException.java
│   │   ├── UnauthorizedException.java
│   │   ├── ValidationException.java
│   │   └── ExternalApiException.java
│   ├── response/
│   │   ├── ApiResponse.java            (generic wrapper: success, data, message)
│   │   ├── PageResponse.java           (paginated wrapper: content, page, size, totalElements)
│   │   └── ErrorResponse.java          (code, message, fieldErrors[])
│   └── util/
│       ├── DateUtil.java               (timezone-aware date helpers; streak day evaluation)
│       ├── CalorieCalculator.java      (Mifflin-St Jeor, MET formula, Keytel formula)
│       ├── BodyMetricsCalculator.java  (BMI, WHR, US Navy body fat formula)
│       └── ExifStripper.java          (strips GPS + device metadata from photo bytes)
│
├── domain/
│   │
│   ├── user/
│   │   ├── controller/
│   │   │   └── UserController.java         (GET/PATCH /api/v1/users/me, DELETE /api/v1/users/me)
│   │   ├── service/
│   │   │   ├── UserService.java
│   │   │   └── UserDeletionService.java    (soft-delete, queues hard-delete at Day 30)
│   │   ├── repository/
│   │   │   └── UserRepository.java
│   │   ├── entity/
│   │   │   └── User.java                  (id, email, passwordHash, displayName, sex, dateOfBirth,
│   │   │                                   heightCm, weightKg, activityLevel, fcmToken,
│   │   │                                   calorieTarget, proteinTargetG, carbTargetG, fatTargetG,
│   │   │                                   createdAt, updatedAt, deletedAt)
│   │   └── dto/
│   │       ├── UserProfileResponse.java
│   │       └── UpdateProfileRequest.java
│   │
│   ├── auth/
│   │   ├── controller/
│   │   │   └── AuthController.java         (POST /api/v1/auth/register, /login, /token/refresh, /logout)
│   │   ├── service/
│   │   │   └── AuthService.java            (register, login, refreshToken, logout)
│   │   ├── repository/
│   │   │   └── RefreshTokenRepository.java
│   │   ├── entity/
│   │   │   └── RefreshToken.java           (id, userId, tokenHash, expiresAt, createdAt, revokedAt)
│   │   └── dto/
│   │       ├── RegisterRequest.java
│   │       ├── LoginRequest.java
│   │       ├── TokenResponse.java          (accessToken, refreshToken, expiresIn)
│   │       └── RefreshTokenRequest.java
│   │
│   ├── exercise/
│   │   ├── controller/
│   │   │   ├── ExerciseSessionController.java   (POST/GET /api/v1/exercise/sessions,
│   │   │   │                                     GET/PATCH/DELETE /api/v1/exercise/sessions/{id})
│   │   │   ├── ExerciseSummaryController.java   (GET /api/v1/exercise/summary/daily,
│   │   │   │                                     GET /api/v1/exercise/summary/weekly)
│   │   │   └── ExerciseCatalogController.java   (GET /api/v1/exercise/catalog)
│   │   ├── service/
│   │   │   ├── ExerciseSessionService.java      (create, list, update, delete, PR detection)
│   │   │   ├── ExerciseSummaryService.java      (daily/weekly aggregation, cached)
│   │   │   └── ExerciseCatalogService.java      (search catalog, create custom exercise)
│   │   ├── repository/
│   │   │   ├── ExerciseSessionRepository.java
│   │   │   ├── ExerciseSetRepository.java
│   │   │   └── ExerciseCatalogRepository.java
│   │   ├── entity/
│   │   │   ├── ExerciseSession.java    (id, userId, sessionDate, durationMinutes, notes,
│   │   │   │                            totalVolumeKg, caloriesBurned, calorieEstimateMethod,
│   │   │   │                            createdAt, updatedAt, deletedAt)
│   │   │   ├── ExerciseSet.java        (id, sessionId, exerciseCatalogId, setNumber,
│   │   │   │                            weightKg, reps, durationSeconds, distanceM,
│   │   │   │                            restSeconds, isPersonalRecord, createdAt)
│   │   │   └── ExerciseCatalog.java   (id, name, nameKo, muscleGroup, exerciseType,
│   │   │                               metValue, isCustom, createdByUserId,
│   │   │                               createdAt, updatedAt, deletedAt)
│   │   └── dto/
│   │       ├── CreateSessionRequest.java
│   │       ├── SessionSummaryResponse.java
│   │       ├── ExerciseSetRequest.java
│   │       ├── DailySummaryResponse.java
│   │       ├── WeeklySummaryResponse.java
│   │       └── CatalogItemResponse.java
│   │
│   ├── diet/
│   │   ├── controller/
│   │   │   ├── MealController.java     (POST/GET /api/v1/diet/meals,
│   │   │   │                            PATCH/DELETE /api/v1/diet/meals/{id},
│   │   │   │                            POST/DELETE /api/v1/diet/meals/{id}/items)
│   │   │   ├── DietSummaryController.java  (GET /api/v1/diet/summary/daily,
│   │   │   │                                GET /api/v1/diet/summary/weekly)
│   │   │   └── FoodSearchController.java   (GET /api/v1/diet/food/search)
│   │   ├── service/
│   │   │   ├── MealService.java            (create, read, update, delete meals and items)
│   │   │   ├── DietSummaryService.java     (macro aggregation, cached daily totals)
│   │   │   └── FoodSearchService.java      (cache-first orchestration: Redis → PostgreSQL → USDA/OFF)
│   │   ├── repository/
│   │   │   ├── MealRepository.java
│   │   │   ├── MealItemRepository.java
│   │   │   └── FoodCatalogRepository.java
│   │   ├── entity/
│   │   │   ├── Meal.java           (id, userId, mealDate, mealSlot [BREAKFAST/LUNCH/DINNER/SNACK],
│   │   │   │                        notes, createdAt, updatedAt, deletedAt)
│   │   │   ├── MealItem.java       (id, mealId, foodCatalogId, servingQty, servingUnit,
│   │   │   │                        caloriesKcal, proteinG, carbG, fatG, fiberG,
│   │   │   │                        sodiumMg, sugarG, createdAt, deletedAt)
│   │   │   └── FoodCatalog.java    (id, externalId, source [USDA/OFF/USER], name, nameKo,
│   │   │   │                        barcode, servingSizeG, caloriesKcal, proteinG, carbG,
│   │   │   │                        fatG, fiberG, sugarG, sodiumMg, cholesterolMg,
│   │   │   │                        vitaminAMcg, vitaminCMg, vitaminDMcg, ironMg,
│   │   │   │                        createdByUserId, cachedAt, deletedAt)
│   │   └── dto/
│   │       ├── CreateMealRequest.java
│   │       ├── MealResponse.java
│   │       ├── AddMealItemRequest.java
│   │       ├── MealItemResponse.java
│   │       ├── DailyDietSummaryResponse.java   (totalCalories, proteinG, carbG, fatG, fiberG,
│   │       │                                    waterMl, meals[])
│   │       ├── WeeklyDietSummaryResponse.java
│   │       └── FoodSearchResponse.java
│   │
│   ├── measurement/
│   │   ├── controller/
│   │   │   ├── BodyMeasurementController.java  (POST /api/v1/measurements,
│   │   │   │                                    GET /api/v1/measurements/history)
│   │   │   └── ProgressPhotoController.java    (POST/GET /api/v1/measurements/photos)
│   │   ├── service/
│   │   │   ├── BodyMeasurementService.java     (log, history, WHR calc, US Navy formula)
│   │   │   └── ProgressPhotoService.java       (upload to S3, EXIF strip, signed URL generation)
│   │   ├── repository/
│   │   │   ├── BodyMeasurementRepository.java
│   │   │   └── ProgressPhotoRepository.java
│   │   ├── entity/
│   │   │   ├── BodyMeasurement.java    (id, userId, loggedAt, weightKg, waistCm, hipCm,
│   │   │   │                            armCm, thighCm, calfCm, neckCm, bodyFatPct,
│   │   │   │                            bodyFatSource [MANUAL/SMART_SCALE/NAVY_FORMULA/DEXA],
│   │   │   │                            bmi, whr, whrRisk [LOW/MODERATE/HIGH],
│   │   │   │                            notes, createdAt, deletedAt)
│   │   │   └── ProgressPhoto.java      (id, userId, capturedAt, photoType [FRONT/BACK/SIDE_LEFT/SIDE_RIGHT],
│   │   │   │                            storageKey, thumbnailKey150, thumbnailKey400, thumbnailKey800,
│   │   │   │                            originalWidthPx, originalHeightPx, exifStripped,
│   │   │   │                            bodyWeightKg, bodyFatPct, waistCm,
│   │   │   │                            notes, isPrivate, isBaseline, createdAt, deletedAt)
│   │   └── dto/
│   │       ├── LogMeasurementRequest.java
│   │       ├── MeasurementHistoryResponse.java
│   │       ├── UploadPhotoResponse.java
│   │       └── PhotoComparisonResponse.java
│   │
│   └── goal/
│       ├── controller/
│       │   └── GoalController.java     (POST/GET /api/v1/goals,
│       │                                GET/PATCH/DELETE /api/v1/goals/{id},
│       │                                GET /api/v1/goals/{id}/progress)
│       ├── service/
│       │   ├── GoalService.java         (create, read, update, delete, archive)
│       │   └── GoalProgressService.java (projected trend calculation, checkpoint evaluation)
│       ├── repository/
│       │   ├── GoalRepository.java
│       │   └── GoalCheckpointRepository.java
│       ├── entity/
│       │   ├── Goal.java           (id, userId, goalType, targetValue, targetUnit,
│       │   │                        targetDate, startValue, startDate, status [ACTIVE/COMPLETED/ABANDONED],
│       │   │                        calorieTarget, proteinTargetG, carbTargetG, fatTargetG,
│       │   │                        weeklyRateTarget, createdAt, updatedAt, deletedAt)
│       │   └── GoalCheckpoint.java (id, goalId, checkpointDate, actualValue,
│       │   │                        projectedValue, onTrack, notes, createdAt)
│       └── dto/
│           ├── CreateGoalRequest.java
│           ├── GoalResponse.java
│           ├── GoalProgressResponse.java   (currentValue, targetValue, percentComplete,
│           │                                projectedCompletionDate, isOnTrack, checkpoints[])
│           └── UpdateGoalRequest.java
│
├── infrastructure/
│   ├── s3/
│   │   ├── S3StorageService.java       (upload, generateSignedUrl, delete; 15-min signed URL TTL)
│   │   └── PhotoProcessingService.java (EXIF stripping, thumbnail generation at 3 sizes)
│   ├── fcm/
│   │   ├── FcmNotificationService.java (sendPrNotification, sendMilestoneNotification,
│   │   │                                sendStreakRiskNotification, sendWeeklySummaryNotification)
│   │   └── NotificationTemplates.java  (Korean + English message templates, PRD section 5.1)
│   └── nutrition/
│       ├── NutritionApiOrchestrator.java   (search priority: cache → USDA → OFF; barcode → OFF → USDA)
│       ├── UsdaFoodDataClient.java         (REST client for api.nal.usda.gov)
│       └── OpenFoodFactsClient.java        (REST client for world.openfoodfacts.org)
│
└── security/
    ├── JwtTokenProvider.java       (generate, validate, extract claims; HS256 via jjwt)
    ├── JwtAuthenticationFilter.java (OncePerRequestFilter; reads Bearer token, populates SecurityContext)
    ├── CustomUserDetailsService.java (loads UserDetails from DB by email for Spring Security)
    └── SecurityConstants.java      (token expiry durations, public endpoint paths)
```

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

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    command: redis-server --maxmemory 256mb --maxmemory-policy allkeys-lru

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
      ┌────────▼──────┐      ┌────────▼──────────┐
      │  RDS           │      │  ElastiCache       │
      │  PostgreSQL 16 │      │  Redis 7           │
      │  db.t3.medium  │      │  cache.t3.micro    │
      │  Multi-AZ      │      │  (single AZ, MVP)  │
      │  ap-northeast-2│      │  ap-northeast-2    │
      └───────────────┘      └────────────────────┘

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
  data:
    redis:
      timeout: 2000ms
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
        include: health,info,metrics
  endpoint:
    health:
      show-details: when-authorized

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
  data:
    redis:
      host: localhost
      port: 6379
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
  data:
    redis:
      host: ${REDIS_HOST}
      port: 6379

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
  data:
    redis:
      host: ${REDIS_HOST}
      port: 6379
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

---

*End of Architecture Design Document v1.0*
