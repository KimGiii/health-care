# REST API Design
## Personal Health Tracking App — Korean Market

**Version:** 1.0
**Date:** April 9, 2026
**Author:** System Architect
**Status:** Draft for Engineering Review
**Base URL:** `https://api.healthcare.app`

---

## Table of Contents

1. [Global Conventions](#1-global-conventions)
2. [Auth Endpoints](#2-auth-endpoints)
3. [User Profile Endpoints](#3-user-profile-endpoints)
4. [Exercise Endpoints](#4-exercise-endpoints)
5. [Diet Endpoints](#5-diet-endpoints)
6. [Measurement Endpoints](#6-measurement-endpoints)
7. [Goal Endpoints](#7-goal-endpoints)

---

## 1. Global Conventions

### 1.1 API Versioning

All endpoints are prefixed with `/api/v1/`. URI path versioning is used over header-based versioning for the following reasons:

- **Discoverability:** The version is visible in browser address bars, server logs, and API documentation without requiring knowledge of custom headers.
- **Proxy and CDN compatibility:** Load balancers and CDN rules can route by path prefix without inspecting headers.
- **Explicit client contracts:** When a breaking change requires `/api/v2/`, existing `/api/v1/` clients continue to function without modification until a deprecation sunset date is communicated. Header versioning creates implicit coupling that is harder to sunset.
- **Simplicity at MVP scale:** For a single-team product, path versioning is the lowest-overhead versioning strategy to implement and reason about.

The `v1` prefix will remain unchanged as long as response shapes are backward-compatible. Breaking changes (field removal, type changes, behavioral changes) will increment to `/api/v2/` with a minimum 6-month parallel operation period.

### 1.2 Global Error Response Envelope

All error responses use the following JSON structure. HTTP status codes carry semantic meaning; the `code` field provides machine-readable error classification for client-side handling.

```json
{
  "success": false,
  "code": "RESOURCE_NOT_FOUND",
  "message": "Exercise session not found.",
  "fieldErrors": [
    {
      "field": "weight_kg",
      "message": "Weight must be greater than 0."
    }
  ],
  "timestamp": "2026-04-09T10:30:00Z",
  "path": "/api/v1/exercise/sessions/999"
}
```

**Fields:**
- `success` (boolean): always `false` for error responses
- `code` (string): machine-readable error code (see table below)
- `message` (string): human-readable description (English; Korean translation on client)
- `fieldErrors` (array, optional): present only for validation errors (HTTP 400); each entry identifies the failing field and the reason
- `timestamp` (string): ISO 8601 UTC
- `path` (string): the request path that generated the error

**Standard Error Codes:**

| HTTP Status | Code | When Used |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Request body fails @Valid constraints |
| 400 | `INVALID_PARAMETER` | Query param format invalid |
| 401 | `UNAUTHORIZED` | Missing or invalid JWT |
| 401 | `TOKEN_EXPIRED` | JWT access token has expired |
| 401 | `REFRESH_TOKEN_INVALID` | Refresh token not found, expired, or revoked |
| 403 | `FORBIDDEN` | Authenticated but not authorized for this resource |
| 404 | `RESOURCE_NOT_FOUND` | Entity does not exist or belongs to another user |
| 409 | `DUPLICATE_RESOURCE` | Unique constraint violation (e.g., duplicate meal slot) |
| 422 | `BUSINESS_RULE_VIOLATION` | Request is syntactically valid but violates a domain rule |
| 429 | `RATE_LIMIT_EXCEEDED` | Too many requests (see rate limiting section) |
| 500 | `INTERNAL_ERROR` | Unexpected server error |
| 502 | `EXTERNAL_API_ERROR` | Upstream food API (USDA/OFF) is unavailable |
| 503 | `SERVICE_UNAVAILABLE` | Application temporarily unavailable |

### 1.3 Success Response Envelope

All successful responses are wrapped in a consistent envelope:

```json
{
  "success": true,
  "data": { ... },
  "message": "Session created successfully."
}
```

For paginated responses, `data` is replaced by a `page` object:

```json
{
  "success": true,
  "page": {
    "content": [ ... ],
    "pageNumber": 0,
    "pageSize": 20,
    "totalElements": 47,
    "totalPages": 3,
    "isFirst": true,
    "isLast": false
  }
}
```

### 1.4 Pagination Strategy

Offset-based pagination is used throughout the API. This strategy is appropriate for this use case:

- **Simplicity:** Offset/limit is universally understood by API consumers and straightforward to implement.
- **User-facing access patterns:** Users browse history pages sequentially; random deep-page access is rare, so the known offset performance degradation at large offsets is not a practical concern at this data volume (research report section 4.1: ~2,000–15,000 rows per user over 5 years).
- **No real-time feed requirement:** This app has no live-updating feed where cursor-based pagination would be necessary to avoid duplicates.

**Query parameters:**
- `page` (integer, default `0`): zero-indexed page number
- `size` (integer, default `20`, max `100`): items per page
- `sort` (string, default varies by endpoint): field name and direction (e.g., `sort=logged_at,desc`)

### 1.5 Rate Limiting

All endpoints are subject to rate limiting enforced at the API Gateway / Spring layer. Rate limit state is stored in Redis.

**Default limits:**
- Authenticated requests: 100 requests per minute per user
- Authentication endpoints (register, login): 10 requests per minute per IP
- Food search: 60 requests per minute per user (external API cost consideration)
- Photo upload: 10 requests per minute per user

**Rate limit headers** (included on every response):

```
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 87
X-RateLimit-Reset: 1712657460
X-RateLimit-Policy: 100;w=60
```

- `X-RateLimit-Limit`: Maximum requests allowed in the window
- `X-RateLimit-Remaining`: Requests remaining in the current window
- `X-RateLimit-Reset`: Unix timestamp when the window resets
- `X-RateLimit-Policy`: Structured policy descriptor (count; w=window_seconds)

When the limit is exceeded, HTTP `429 Too Many Requests` is returned with a `Retry-After` header (seconds until the window resets).

### 1.6 Authentication

All endpoints except `POST /api/v1/auth/register`, `POST /api/v1/auth/login`, and `POST /api/v1/auth/token/refresh` require a valid JWT Bearer token.

```
Authorization: Bearer <access_token>
```

Access tokens expire after 24 hours. The client should use the refresh token endpoint to obtain a new access token before expiry. The token's `exp` claim contains the Unix timestamp of expiry.

---

## 2. Auth Endpoints

### POST /api/v1/auth/register

**Auth required:** No
**Description:** Creates a new user account. Validates email uniqueness. Does not require consent acknowledgment in the request body — consent is captured in a subsequent dedicated consent flow (PIPA compliance, PRD section 4 Flow 1 steps 3–4), which updates the `general_consent_at` and `health_consent_at` fields via PATCH /api/v1/users/me.

**Request Body:**
```json
{
  "email": "minjun@example.com",
  "password": "SecurePassword123!",
  "displayName": "Minjun Kim",
  "sex": "MALE",
  "dateOfBirth": "1997-03-15",
  "heightCm": 176.0,
  "weightKg": 82.0,
  "activityLevel": "MODERATELY_ACTIVE",
  "goalType": "BODY_RECOMPOSITION",
  "locale": "ko-KR",
  "timezone": "Asia/Seoul"
}
```

**Response: 201 Created**
```json
{
  "success": true,
  "data": {
    "userId": 1024,
    "email": "minjun@example.com",
    "displayName": "Minjun Kim",
    "accessToken": "eyJhbGci...",
    "refreshToken": "dGhpcyBp...",
    "expiresIn": 86400,
    "calorieTarget": 2350,
    "proteinTargetG": 162,
    "carbTargetG": 235,
    "fatTargetG": 78
  },
  "message": "Account created successfully."
}
```

**Key Error Codes:**
- `409 DUPLICATE_RESOURCE` — email already registered
- `400 VALIDATION_ERROR` — password too weak, invalid date format, height/weight out of range

---

### POST /api/v1/auth/login

**Auth required:** No
**Description:** Authenticates a user with email and password. Returns a new access/refresh token pair. Invalidates no existing tokens — multiple device sessions are supported.

**Request Body:**
```json
{
  "email": "minjun@example.com",
  "password": "SecurePassword123!"
}
```

**Response: 200 OK**
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGci...",
    "refreshToken": "dGhpcyBp...",
    "expiresIn": 86400,
    "userId": 1024,
    "displayName": "Minjun Kim"
  }
}
```

**Key Error Codes:**
- `401 UNAUTHORIZED` — email not found or password incorrect (same message to prevent email enumeration)
- `400 VALIDATION_ERROR` — missing fields

---

### POST /api/v1/auth/token/refresh

**Auth required:** No (uses refresh token)
**Description:** Issues a new access token and rotated refresh token. The previous refresh token is immediately invalidated (refresh token rotation). This limits replay window to near-zero if a refresh token is intercepted.

**Request Body:**
```json
{
  "refreshToken": "dGhpcyBp..."
}
```

**Response: 200 OK**
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGci...",
    "refreshToken": "bmV3UmVm...",
    "expiresIn": 86400
  }
}
```

**Key Error Codes:**
- `401 REFRESH_TOKEN_INVALID` — token not found, expired, or already rotated (possible replay attack)

---

### POST /api/v1/auth/logout

**Auth required:** Yes
**Description:** Revokes the provided refresh token. The access token remains technically valid until its 24-hour expiry, but the client should discard it immediately. For logout-all-devices, call with `allDevices: true`.

**Request Body:**
```json
{
  "refreshToken": "dGhpcyBp...",
  "allDevices": false
}
```

**Response: 200 OK**
```json
{
  "success": true,
  "message": "Logged out successfully."
}
```

---

## 3. User Profile Endpoints

### GET /api/v1/users/me

**Auth required:** Yes
**Description:** Returns the authenticated user's full profile including calculated targets and consent status.

**Response: 200 OK**
```json
{
  "success": true,
  "data": {
    "userId": 1024,
    "email": "minjun@example.com",
    "displayName": "Minjun Kim",
    "sex": "MALE",
    "dateOfBirth": "1997-03-15",
    "heightCm": 176.0,
    "weightKg": 82.0,
    "activityLevel": "MODERATELY_ACTIVE",
    "locale": "ko-KR",
    "timezone": "Asia/Seoul",
    "targets": {
      "calorieTarget": 2350,
      "proteinTargetG": 162,
      "carbTargetG": 235,
      "fatTargetG": 78,
      "waterTargetMl": 2000
    },
    "consent": {
      "generalConsentAt": "2026-04-01T09:00:00Z",
      "healthConsentAt": "2026-04-01T09:01:00Z"
    },
    "createdAt": "2026-04-01T09:00:00Z"
  }
}
```

---

### PATCH /api/v1/users/me

**Auth required:** Yes
**Description:** Updates one or more user profile fields. All fields are optional — only provided fields are updated. When `weightKg`, `activityLevel`, or any goal-affecting field is updated, calorie and macro targets are recalculated automatically. Also used to record PIPA consent events (consent timestamps are write-once; cannot be set to null via this endpoint).

**Request Body (all fields optional):**
```json
{
  "displayName": "Min Kim",
  "heightCm": 176.5,
  "weightKg": 80.5,
  "activityLevel": "VERY_ACTIVE",
  "locale": "en-US",
  "timezone": "Asia/Seoul",
  "fcmToken": "fcm-device-token-string",
  "generalConsentAt": "2026-04-01T09:00:00Z",
  "healthConsentAt": "2026-04-01T09:01:00Z"
}
```

**Response: 200 OK**
```json
{
  "success": true,
  "data": {
    "userId": 1024,
    "displayName": "Min Kim",
    "weightKg": 80.5,
    "targets": {
      "calorieTarget": 2460,
      "proteinTargetG": 161,
      "carbTargetG": 246,
      "fatTargetG": 82
    }
  },
  "message": "Profile updated. Calorie target recalculated."
}
```

**Key Error Codes:**
- `400 VALIDATION_ERROR` — height/weight out of physiologically plausible range
- `422 BUSINESS_RULE_VIOLATION` — attempt to clear a consent timestamp that has already been set

---

### DELETE /api/v1/users/me

**Auth required:** Yes
**Description:** Initiates account deletion. Sets `deleted_at` immediately (account becomes inaccessible). Queues hard-delete and backup purge for 30 days later. Revokes all refresh tokens. Sends confirmation email. Requires password confirmation to prevent accidental deletion.

**Request Body:**
```json
{
  "password": "SecurePassword123!",
  "confirmPhrase": "DELETE MY ACCOUNT"
}
```

**Response: 200 OK**
```json
{
  "success": true,
  "message": "Account deletion initiated. Your account will be permanently deleted on 2026-05-09. A confirmation email has been sent.",
  "data": {
    "deletionScheduledAt": "2026-05-09T00:00:00Z"
  }
}
```

**Key Error Codes:**
- `401 UNAUTHORIZED` — password incorrect
- `400 VALIDATION_ERROR` — confirm phrase does not match

---

## 4. Exercise Endpoints

### POST /api/v1/exercise/sessions

**Auth required:** Yes
**Description:** Creates a new exercise session with all its sets. Sets are bulk-inserted atomically with the session. Personal record detection runs synchronously; PR notifications are dispatched asynchronously after the response is returned.

**Request Body:**
```json
{
  "sessionDate": "2026-04-09",
  "startedAt": "2026-04-09T19:00:00+09:00",
  "endedAt": "2026-04-09T20:05:00+09:00",
  "notes": "Felt strong today. Increased bench weight.",
  "sets": [
    {
      "exerciseCatalogId": 42,
      "setNumber": 1,
      "setType": "WEIGHTED",
      "weightKg": 80.0,
      "reps": 8,
      "restSeconds": 90
    },
    {
      "exerciseCatalogId": 42,
      "setNumber": 2,
      "setType": "WEIGHTED",
      "weightKg": 82.5,
      "reps": 6,
      "restSeconds": 120
    }
  ]
}
```

**Response: 201 Created**
```json
{
  "success": true,
  "data": {
    "sessionId": 5821,
    "sessionDate": "2026-04-09",
    "durationMinutes": 65,
    "totalVolumeKg": 1340.0,
    "caloriesBurned": 312.0,
    "calorieEstimateMethod": "MET",
    "newPersonalRecords": [
      {
        "exerciseName": "Bench Press",
        "exerciseNameKo": "벤치 프레스",
        "weightKg": 82.5,
        "reps": 6
      }
    ],
    "setCount": 2
  },
  "message": "Session saved. New personal record on Bench Press!"
}
```

**Key Error Codes:**
- `404 RESOURCE_NOT_FOUND` — `exerciseCatalogId` does not exist or is another user's private custom exercise
- `400 VALIDATION_ERROR` — set type / column constraint mismatch (e.g., WEIGHTED set missing `weightKg`)

---

### GET /api/v1/exercise/sessions

**Auth required:** Yes
**Description:** Returns a paginated list of the authenticated user's exercise sessions, ordered by date descending.

**Query Parameters:**
- `page` (default 0), `size` (default 20)
- `from` (date, optional): filter sessions from this date inclusive (ISO 8601: `2026-03-01`)
- `to` (date, optional): filter sessions to this date inclusive
- `sort` (default `session_date,desc`)

**Response: 200 OK**
```json
{
  "success": true,
  "page": {
    "content": [
      {
        "sessionId": 5821,
        "sessionDate": "2026-04-09",
        "durationMinutes": 65,
        "totalVolumeKg": 1340.0,
        "caloriesBurned": 312.0,
        "calorieEstimateMethod": "MET",
        "setCount": 12,
        "notes": "Felt strong today."
      }
    ],
    "pageNumber": 0,
    "pageSize": 20,
    "totalElements": 47,
    "totalPages": 3,
    "isFirst": true,
    "isLast": false
  }
}
```

---

### GET /api/v1/exercise/sessions/{id}

**Auth required:** Yes
**Description:** Returns full detail for a single session including all sets and exercise catalog metadata.

**Path Parameter:** `id` — session ID (BIGINT)

**Response: 200 OK**
```json
{
  "success": true,
  "data": {
    "sessionId": 5821,
    "sessionDate": "2026-04-09",
    "startedAt": "2026-04-09T19:00:00+09:00",
    "endedAt": "2026-04-09T20:05:00+09:00",
    "durationMinutes": 65,
    "totalVolumeKg": 1340.0,
    "caloriesBurned": 312.0,
    "calorieEstimateMethod": "MET",
    "notes": "Felt strong today.",
    "sets": [
      {
        "setId": 10201,
        "exerciseCatalogId": 42,
        "exerciseName": "Bench Press",
        "exerciseNameKo": "벤치 프레스",
        "muscleGroup": "CHEST",
        "setNumber": 1,
        "setType": "WEIGHTED",
        "weightKg": 80.0,
        "reps": 8,
        "restSeconds": 90,
        "isPersonalRecord": false
      },
      {
        "setId": 10202,
        "exerciseCatalogId": 42,
        "exerciseName": "Bench Press",
        "exerciseNameKo": "벤치 프레스",
        "muscleGroup": "CHEST",
        "setNumber": 2,
        "setType": "WEIGHTED",
        "weightKg": 82.5,
        "reps": 6,
        "restSeconds": 120,
        "isPersonalRecord": true
      }
    ]
  }
}
```

**Key Error Codes:**
- `404 RESOURCE_NOT_FOUND` — session does not exist or belongs to another user

---

### PATCH /api/v1/exercise/sessions/{id}

**Auth required:** Yes
**Description:** Updates session-level fields (notes, dates, duration). Does not update sets — set modifications require deleting and recreating the session, or adding/removing individual sets (a future v2 enhancement). All fields optional.

**Request Body:**
```json
{
  "notes": "Updated notes.",
  "sessionDate": "2026-04-09"
}
```

**Response: 200 OK** — returns the updated session summary (same shape as GET /{id}).

---

### DELETE /api/v1/exercise/sessions/{id}

**Auth required:** Yes
**Description:** Soft-deletes a session and all its sets. Evicts the daily exercise summary cache for the session's date.

**Response: 200 OK**
```json
{
  "success": true,
  "message": "Session deleted."
}
```

---

### GET /api/v1/exercise/summary/daily

**Auth required:** Yes
**Description:** Returns exercise summary for a specific date. Result is cached in Redis with the key `daily_exercise_summary:{userId}:{date}`. Cache is evicted on any write to `exercise_sessions` for that date.

**Query Parameters:**
- `date` (required): ISO 8601 date string (e.g., `2026-04-09`)

**Response: 200 OK**
```json
{
  "success": true,
  "data": {
    "date": "2026-04-09",
    "sessionCount": 1,
    "totalDurationMinutes": 65,
    "totalVolumeKg": 1340.0,
    "totalCaloriesBurned": 312.0,
    "calorieEstimateDisclaimer": "~estimate ±15%",
    "muscleGroupsTrainedToday": ["CHEST", "TRICEPS", "SHOULDERS"],
    "newPersonalRecordsToday": 1
  }
}
```

---

### GET /api/v1/exercise/summary/weekly

**Auth required:** Yes
**Description:** Returns exercise summary aggregated over a 7-day window. Used for the weekly review screen (PRD section 4, Flow 4).

**Query Parameters:**
- `weekStart` (optional, default: most recent Monday): ISO 8601 date

**Response: 200 OK**
```json
{
  "success": true,
  "data": {
    "weekStart": "2026-04-06",
    "weekEnd": "2026-04-12",
    "sessionCount": 4,
    "totalDurationMinutes": 245,
    "totalVolumeKg": 4820.0,
    "totalCaloriesBurned": 1180.0,
    "newPersonalRecordsThisWeek": 2,
    "muscleGroupVolume": [
      { "muscleGroup": "CHEST", "sets": 12 },
      { "muscleGroup": "BACK", "sets": 10 },
      { "muscleGroup": "LEGS", "sets": 8 }
    ],
    "sessionDates": ["2026-04-07", "2026-04-08", "2026-04-09", "2026-04-11"]
  }
}
```

---

### GET /api/v1/exercise/catalog

**Auth required:** Yes
**Description:** Returns the exercise catalog. Includes global exercises and the authenticated user's custom exercises. Supports search by name, filter by muscle group and exercise type.

**Query Parameters:**
- `page` (default 0), `size` (default 50)
- `q` (string, optional): name search (min 2 chars)
- `muscleGroup` (string, optional): filter by muscle group enum value
- `exerciseType` (string, optional): STRENGTH / CARDIO / BODYWEIGHT / FLEXIBILITY / SPORTS
- `customOnly` (boolean, default false): return only this user's custom exercises

**Response: 200 OK**
```json
{
  "success": true,
  "page": {
    "content": [
      {
        "catalogId": 42,
        "name": "Bench Press",
        "nameKo": "벤치 프레스",
        "muscleGroup": "CHEST",
        "exerciseType": "STRENGTH",
        "metValue": 3.5,
        "isCustom": false
      }
    ],
    "pageNumber": 0,
    "pageSize": 50,
    "totalElements": 83
  }
}
```

**POST /api/v1/exercise/catalog** — Create a custom exercise.

**Request Body:**
```json
{
  "name": "Cable Face Pull",
  "nameKo": "케이블 페이스 풀",
  "muscleGroup": "SHOULDERS",
  "exerciseType": "STRENGTH",
  "metValue": 3.5
}
```

**Response: 201 Created** — returns the created catalog item.

---

## 5. Diet Endpoints

### POST /api/v1/diet/meals

**Auth required:** Yes
**Description:** Creates a new meal for a specific meal slot on a date. Returns `409` if a meal already exists for that user/date/slot combination (enforced by unique index). The meal starts empty — items are added via the items sub-resource.

**Request Body:**
```json
{
  "mealDate": "2026-04-09",
  "mealSlot": "BREAKFAST",
  "notes": "Pre-workout meal"
}
```

**Response: 201 Created**
```json
{
  "success": true,
  "data": {
    "mealId": 3310,
    "mealDate": "2026-04-09",
    "mealSlot": "BREAKFAST",
    "notes": "Pre-workout meal",
    "items": [],
    "mealTotals": {
      "caloriesKcal": 0,
      "proteinG": 0,
      "carbG": 0,
      "fatG": 0,
      "fiberG": 0
    }
  }
}
```

**Key Error Codes:**
- `409 DUPLICATE_RESOURCE` — meal for this slot already exists on this date

---

### GET /api/v1/diet/meals

**Auth required:** Yes
**Description:** Returns all meals for a given date (typically the home screen diet view). Each meal includes its items and totals.

**Query Parameters:**
- `date` (required): ISO 8601 date (e.g., `2026-04-09`)

**Response: 200 OK**
```json
{
  "success": true,
  "data": {
    "date": "2026-04-09",
    "meals": [
      {
        "mealId": 3310,
        "mealSlot": "BREAKFAST",
        "notes": "Pre-workout meal",
        "items": [
          {
            "itemId": 9001,
            "foodName": "Chicken Breast, Grilled",
            "foodNameKo": "닭가슴살 구이",
            "source": "USDA",
            "servingQty": 150.0,
            "servingUnit": "g",
            "caloriesKcal": 248.0,
            "proteinG": 46.5,
            "carbG": 0.0,
            "fatG": 5.4,
            "fiberG": 0.0,
            "sodiumMg": 74.0
          }
        ],
        "mealTotals": {
          "caloriesKcal": 248.0,
          "proteinG": 46.5,
          "carbG": 0.0,
          "fatG": 5.4,
          "fiberG": 0.0
        }
      }
    ]
  }
}
```

---

### PATCH /api/v1/diet/meals/{id}

**Auth required:** Yes
**Description:** Updates meal-level fields (notes only at meal level; items managed separately).

**Request Body:**
```json
{
  "notes": "Updated notes."
}
```

**Response: 200 OK** — returns updated meal with items.

---

### DELETE /api/v1/diet/meals/{id}

**Auth required:** Yes
**Description:** Soft-deletes a meal and all its items. Evicts the daily diet summary cache for the meal's date.

**Response: 200 OK**
```json
{
  "success": true,
  "message": "Meal deleted."
}
```

---

### POST /api/v1/diet/meals/{id}/items

**Auth required:** Yes
**Description:** Adds a food item to a meal. Macros are denormalized from the food catalog entry at the time of addition — the recorded macros are immutable regardless of future food catalog updates.

**Path Parameter:** `id` — meal ID

**Request Body:**
```json
{
  "foodCatalogId": 1042,
  "servingQty": 150.0,
  "servingUnit": "g"
}
```

**Response: 201 Created**
```json
{
  "success": true,
  "data": {
    "itemId": 9001,
    "foodCatalogId": 1042,
    "foodName": "Chicken Breast, Grilled",
    "foodNameKo": "닭가슴살 구이",
    "servingQty": 150.0,
    "servingUnit": "g",
    "caloriesKcal": 248.0,
    "proteinG": 46.5,
    "carbG": 0.0,
    "fatG": 5.4,
    "fiberG": 0.0,
    "sodiumMg": 74.0
  },
  "message": "Item added to meal."
}
```

---

### DELETE /api/v1/diet/meals/{id}/items/{itemId}

**Auth required:** Yes
**Description:** Removes a specific item from a meal (soft-delete).

**Response: 200 OK**
```json
{
  "success": true,
  "message": "Item removed from meal."
}
```

---

### GET /api/v1/diet/summary/daily

**Auth required:** Yes
**Description:** Returns total macros for a date, aggregated across all meals and all items. Cached in Redis as `daily_macro_summary:{userId}:{date}`. Cache evicted on any meal item write for that date.

**Query Parameters:**
- `date` (required): ISO 8601 date

**Response: 200 OK**
```json
{
  "success": true,
  "data": {
    "date": "2026-04-09",
    "totalCaloriesKcal": 1840.0,
    "totalProteinG": 162.0,
    "totalCarbG": 188.0,
    "totalFatG": 52.0,
    "totalFiberG": 18.5,
    "totalSodiumMg": 1420.0,
    "totalWaterMl": 1500,
    "targets": {
      "calorieTarget": 2350,
      "proteinTargetG": 162,
      "carbTargetG": 235,
      "fatTargetG": 78,
      "waterTargetMl": 2000
    },
    "remaining": {
      "caloriesKcal": 510.0,
      "proteinG": 0.0,
      "carbG": 47.0,
      "fatG": 26.0
    },
    "adherencePct": 78.3
  }
}
```

---

### GET /api/v1/diet/summary/weekly

**Auth required:** Yes
**Description:** Returns per-day macro totals for a 7-day window plus week averages. Used for the weekly review screen.

**Query Parameters:**
- `weekStart` (optional, default: most recent Monday): ISO 8601 date

**Response: 200 OK**
```json
{
  "success": true,
  "data": {
    "weekStart": "2026-04-06",
    "weekEnd": "2026-04-12",
    "daysLogged": 5,
    "averageDailyCalories": 2180.0,
    "averageDailyProteinG": 155.0,
    "averageDailyCarbG": 220.0,
    "averageDailyFatG": 65.0,
    "targetAdherenceDays": 4,
    "dailyBreakdown": [
      {
        "date": "2026-04-06",
        "caloriesKcal": 2310.0,
        "proteinG": 160.0,
        "carbG": 230.0,
        "fatG": 68.0,
        "onTarget": true
      }
    ]
  }
}
```

---

### GET /api/v1/diet/food/search

**Auth required:** Yes
**Description:** Searches the food catalog. Cache-first strategy: local PostgreSQL cache is checked first (TTL 30 days, per research section 4.2). On cache miss, queries USDA FoodData Central then Open Food Facts. For barcode lookups, Open Food Facts is queried first (stronger Korean barcode coverage, research section 4.2). Recent and frequent foods for the authenticated user are boosted to the top of results when no `q` parameter is provided.

**Query Parameters:**
- `q` (string, optional): search term (min 2 chars; omit for recent/frequent foods list)
- `barcode` (string, optional): EAN-13 or UPC-A barcode; mutually exclusive with `q`
- `source` (string, optional): filter by `USDA` / `OFF` / `USER`
- `page` (default 0), `size` (default 20)

**Response: 200 OK**
```json
{
  "success": true,
  "page": {
    "content": [
      {
        "foodCatalogId": 1042,
        "name": "Chicken Breast, Grilled",
        "nameKo": "닭가슴살 구이",
        "brand": null,
        "source": "USDA",
        "barcode": null,
        "servingSizeG": 85.0,
        "servingDescription": "3 oz",
        "caloriesKcal": 140.0,
        "proteinG": 26.1,
        "carbG": 0.0,
        "fatG": 3.1,
        "fiberG": 0.0
      }
    ],
    "pageNumber": 0,
    "pageSize": 20,
    "totalElements": 42
  }
}
```

**Key Error Codes:**
- `400 VALIDATION_ERROR` — `q` is shorter than 2 characters; `barcode` and `q` both provided
- `502 EXTERNAL_API_ERROR` — USDA/OFF API unavailable and item not in local cache; includes message "Food search is limited to cached results while our food database is temporarily unavailable."

---

### POST /api/v1/diet/photo-analyses/initiate

**Auth required:** Yes
**Description:** Creates a meal-photo analysis draft and returns a presigned upload URL. Meal photos use a dedicated storage prefix separate from progress photos.

**Request Body:**
```json
{
  "fileName": "meal.jpg",
  "contentType": "image/jpeg",
  "fileSizeBytes": 482193,
  "capturedAt": "2026-04-21T12:30:00+09:00"
}
```

**Response: 201 Created**
```json
{
  "success": true,
  "data": {
    "analysisId": 9001,
    "storageKey": "meal-photos/42/550e8400-e29b-41d4-a716-446655440000.jpg",
    "uploadUrl": "https://...signed-put-url...",
    "previewUrl": "https://...signed-get-url...",
    "expiresAt": "2026-04-21T03:45:00Z"
  },
  "message": "식단 사진 업로드 준비가 완료되었습니다."
}
```

### POST /api/v1/diet/photo-analyses/{id}/analyze

**Auth required:** Yes
**Description:** Runs AI analysis and returns a normalized draft. This endpoint does not persist the final meal log. Items with low confidence remain editable and are marked with `needsReview=true`.

**Request Body:**
```json
{
  "mealType": "LUNCH"
}
```

**Response: 200 OK**
```json
{
  "success": true,
  "data": {
    "analysisId": 9001,
    "status": "ANALYZED",
    "provider": "openai",
    "analysisVersion": "gpt-4.1-mini",
    "previewUrl": "https://...signed-get-url...",
    "capturedAt": "2026-04-21T12:30:00+09:00",
    "needsReview": true,
    "analysisWarnings": [
      "국물과 소스는 실제보다 낮게 추정될 수 있습니다."
    ],
    "detectedItems": [
      {
        "analysisItemId": 1,
        "label": "제육볶음",
        "matchedFoodCatalogId": 501,
        "estimatedServingG": 180.0,
        "calories": 423.0,
        "proteinG": 24.0,
        "carbsG": 18.0,
        "fatG": 28.0,
        "confidence": 0.71,
        "needsReview": true,
        "unknownOrUncertain": "양념, 설탕, 사용된 기름 양은 사진만으로 확정하기 어렵습니다."
      }
    ]
  },
  "message": "식단 사진 분석 초안이 생성되었습니다."
}
```

### POST /api/v1/diet/photo-analyses/{id}/confirm

**Auth required:** Yes
**Description:** Persists the edited AI draft as a regular diet log. When an item cannot be matched to an existing catalog row, the server creates a user-owned custom food before saving the final log.

**Request Body:**
```json
{
  "logDate": "2026-04-21",
  "mealType": "LUNCH",
  "notes": "회사 근처 식당 점심",
  "items": [
    {
      "analysisItemId": 1,
      "label": "제육볶음",
      "matchedFoodCatalogId": 501,
      "estimatedServingG": 180.0,
      "calories": 423.0,
      "proteinG": 24.0,
      "carbsG": 18.0,
      "fatG": 28.0,
      "notes": "밥은 따로 추가"
    }
  ]
}
```

**Response: 201 Created**
```json
{
  "success": true,
  "data": {
    "analysisId": 9001,
    "status": "CONFIRMED",
    "dietLog": {
      "dietLogId": 321,
      "logDate": "2026-04-21",
      "mealType": "LUNCH",
      "entryCount": 1,
      "totalCalories": 423.0,
      "totalProteinG": 24.0,
      "totalCarbsG": 18.0,
      "totalFatG": 28.0
    }
  },
  "message": "식단 사진 분석 결과가 식단 기록으로 저장되었습니다."
}
```

**Key Error Codes:**
- `400 VALIDATION_ERROR` — unsupported image type, oversized upload, invalid confirmation payload
- `404 NOT_FOUND` — analysis record does not belong to the authenticated user
- `422 BUSINESS_RULE_VIOLATION` — analysis already confirmed

---

## 6. Measurement Endpoints

### POST /api/v1/measurements

**Auth required:** Yes
**Description:** Logs a body measurement entry. All measurement fields are optional — the user can log just weight, just waist+hip, or any combination. WHR, BMI, and US Navy body fat estimate are computed server-side if sufficient inputs are present. A minimum of one measurement field must be present.

**Request Body:**
```json
{
  "loggedAt": "2026-04-09T07:30:00+09:00",
  "weightKg": 81.5,
  "waistCm": 84.0,
  "hipCm": 97.0,
  "armCm": 36.5,
  "thighCm": 58.0,
  "neckCm": 38.0,
  "bodyFatPct": 18.5,
  "bodyFatSource": "INBODY",
  "notes": "Post-gym InBody measurement."
}
```

**Response: 201 Created**
```json
{
  "success": true,
  "data": {
    "measurementId": 2201,
    "loggedAt": "2026-04-09T07:30:00+09:00",
    "weightKg": 81.5,
    "waistCm": 84.0,
    "hipCm": 97.0,
    "armCm": 36.5,
    "thighCm": 58.0,
    "neckCm": 38.0,
    "bodyFatPct": 18.5,
    "bodyFatSource": "INBODY",
    "derived": {
      "bmi": 26.3,
      "bmiClassification": "OVERWEIGHT",
      "bmiNote": "WHO Asian threshold: overweight ≥23. BMI does not distinguish muscle from fat.",
      "whr": 0.866,
      "whrRisk": "HIGH",
      "navyBodyFatEstimatePct": 17.9
    }
  },
  "message": "Measurements logged."
}
```

**Key Error Codes:**
- `400 VALIDATION_ERROR` — all measurement fields null; height not present in profile (required for BMI)
- `422 BUSINESS_RULE_VIOLATION` — `bodyFatPct` outside physiologically plausible range (< 2% or > 65%)

---

### GET /api/v1/measurements/history

**Auth required:** Yes
**Description:** Returns paginated measurement history, ordered by logged date descending. Supports date range filtering for chart data.

**Query Parameters:**
- `page` (default 0), `size` (default 30)
- `from` (date, optional), `to` (date, optional)
- `fields` (comma-separated, optional): restrict response to specific fields (e.g., `weight_kg,body_fat_pct`) for lightweight chart data requests

**Response: 200 OK**
```json
{
  "success": true,
  "page": {
    "content": [
      {
        "measurementId": 2201,
        "loggedAt": "2026-04-09T07:30:00+09:00",
        "weightKg": 81.5,
        "waistCm": 84.0,
        "hipCm": 97.0,
        "armCm": 36.5,
        "thighCm": 58.0,
        "bodyFatPct": 18.5,
        "bmi": 26.3,
        "whr": 0.866,
        "whrRisk": "HIGH"
      }
    ],
    "pageNumber": 0,
    "pageSize": 30,
    "totalElements": 14
  }
}
```

---

### POST /api/v1/measurements/photos

**Auth required:** Yes
**Description:** Uploads a progress photo. The request uses `multipart/form-data`. EXIF stripping is performed server-side before the file is written to S3. Three thumbnail sizes are generated asynchronously. The response is returned immediately with a pending thumbnail status — the client polls or uses the original resolution until thumbnails are ready.

**Request (multipart/form-data):**
- `file` (binary): JPEG or PNG, max 20 MB
- `capturedAt` (string): ISO 8601 timestamp
- `photoType` (string): FRONT / BACK / SIDE_LEFT / SIDE_RIGHT / DETAIL
- `bodyWeightKg` (decimal, optional): weight at time of photo
- `waistCm` (decimal, optional): waist measurement at time of photo
- `notes` (string, optional)

**Response: 201 Created**
```json
{
  "success": true,
  "data": {
    "photoId": 801,
    "capturedAt": "2026-04-09T07:45:00+09:00",
    "photoType": "FRONT",
    "exifStripped": true,
    "isPrivate": true,
    "thumbnailStatus": "PROCESSING",
    "signedUrls": {
      "original": "https://s3.ap-northeast-2.amazonaws.com/healthcare-photos-prod/...(signed, TTL 15 min)",
      "thumbnail400": null
    },
    "bodyWeightKg": 81.5,
    "waistCm": 84.0
  },
  "message": "Photo uploaded. Thumbnails are being generated."
}
```

**Key Error Codes:**
- `400 VALIDATION_ERROR` — file is not JPEG or PNG; file exceeds 20 MB
- `422 BUSINESS_RULE_VIOLATION` — EXIF stripping failed (server will retry; photo is not stored if stripping fails)

---

### GET /api/v1/measurements/photos

**Auth required:** Yes
**Description:** Returns photo metadata with fresh signed URLs (15-minute TTL). Signed URLs are generated on each request — not cached — to ensure they are always valid when the client needs them.

**Query Parameters:**
- `photoType` (optional): filter by pose type
- `page` (default 0), `size` (default 20)
- `from` (date, optional), `to` (date, optional)

**Response: 200 OK**
```json
{
  "success": true,
  "page": {
    "content": [
      {
        "photoId": 801,
        "capturedAt": "2026-04-09T07:45:00+09:00",
        "photoType": "FRONT",
        "isBaseline": false,
        "exifStripped": true,
        "signedUrls": {
          "thumbnail150": "https://s3....(signed)",
          "thumbnail400": "https://s3....(signed)",
          "original": "https://s3....(signed)"
        },
        "bodyWeightKg": 81.5,
        "waistCm": 84.0,
        "notes": null
      }
    ],
    "pageNumber": 0,
    "pageSize": 20,
    "totalElements": 8
  }
}
```

---

## 7. Goal Endpoints

### POST /api/v1/goals

**Auth required:** Yes
**Description:** Creates a new goal. Any existing ACTIVE goal is automatically transitioned to ABANDONED status before the new goal is activated. Goal creation triggers recalculation of the user's calorie and macro targets based on the goal type and weekly rate (PRD Module D, user story D-1).

**Request Body:**
```json
{
  "goalType": "BODY_RECOMPOSITION",
  "targetValue": 15.0,
  "targetUnit": "pct",
  "targetDate": "2026-10-01",
  "startValue": 18.5,
  "weeklyRateTarget": -0.25
}
```

**Response: 201 Created**
```json
{
  "success": true,
  "data": {
    "goalId": 412,
    "goalType": "BODY_RECOMPOSITION",
    "targetValue": 15.0,
    "targetUnit": "pct",
    "targetDate": "2026-10-01",
    "startValue": 18.5,
    "startDate": "2026-04-09",
    "status": "ACTIVE",
    "weeklyRateTarget": -0.25,
    "impliedWeeksToGoal": 14,
    "targets": {
      "calorieTarget": 2250,
      "proteinTargetG": 163,
      "carbTargetG": 219,
      "fatTargetG": 75
    },
    "realityCheck": {
      "weeklyRateKg": -0.25,
      "isWithinHealthyRange": true,
      "message": "This requires reducing body fat by 0.25% per week — within the recommended range."
    }
  },
  "message": "Goal created. Your calorie and macro targets have been updated."
}
```

**Key Error Codes:**
- `422 BUSINESS_RULE_VIOLATION` — target date in the past; implied weekly rate exceeds physiological limits (>1 kg/week weight loss, >0.5 kg/week muscle gain) — warning returned but not blocked

---

### GET /api/v1/goals

**Auth required:** Yes
**Description:** Returns all goals for the authenticated user (active and historical). Used for goal history view (PRD user story D-7).

**Query Parameters:**
- `status` (optional): ACTIVE / COMPLETED / ABANDONED (omit for all)
- `page` (default 0), `size` (default 20)

**Response: 200 OK**
```json
{
  "success": true,
  "page": {
    "content": [
      {
        "goalId": 412,
        "goalType": "BODY_RECOMPOSITION",
        "targetValue": 15.0,
        "targetUnit": "pct",
        "targetDate": "2026-10-01",
        "startDate": "2026-04-09",
        "status": "ACTIVE",
        "percentComplete": 12.5
      }
    ],
    "pageNumber": 0,
    "pageSize": 20,
    "totalElements": 3
  }
}
```

---

### GET /api/v1/goals/{id}

**Auth required:** Yes
**Description:** Returns full goal details including targets.

**Response: 200 OK** — same shape as the goal object in POST /api/v1/goals response.

---

### PATCH /api/v1/goals/{id}

**Auth required:** Yes
**Description:** Updates a goal's target value, target date, or notes. Re-evaluates the implied weekly rate and provides an updated reality check. Cannot change `goalType` on an existing goal — create a new goal instead.

**Request Body:**
```json
{
  "targetDate": "2026-12-01",
  "targetValue": 14.0,
  "weeklyRateTarget": -0.2
}
```

**Response: 200 OK** — returns updated goal with recalculated targets.

---

### DELETE /api/v1/goals/{id}

**Auth required:** Yes
**Description:** Sets goal status to ABANDONED. The goal remains in history (never physically deleted in this flow). Only callable on ACTIVE goals — completed goals cannot be un-completed.

**Response: 200 OK**
```json
{
  "success": true,
  "message": "Goal abandoned. It remains in your goal history."
}
```

**Key Error Codes:**
- `422 BUSINESS_RULE_VIOLATION` — goal is already COMPLETED or ABANDONED

---

### GET /api/v1/goals/{id}/progress

**Auth required:** Yes
**Description:** Returns the goal progress data used to render the projected trend line chart (PRD visualization D2). Computes the actual trend from logged measurements and the linear projection to the target date. Checkpoints are created on demand if a Sunday checkpoint is missing.

**Response: 200 OK**
```json
{
  "success": true,
  "data": {
    "goalId": 412,
    "goalType": "BODY_RECOMPOSITION",
    "targetValue": 15.0,
    "targetUnit": "pct",
    "targetDate": "2026-10-01",
    "startDate": "2026-04-09",
    "startValue": 18.5,
    "currentValue": 17.8,
    "percentComplete": 20.0,
    "daysRemaining": 175,
    "projectedCompletionDate": "2026-09-28",
    "isOnTrack": true,
    "trackingStatus": "ON_TRACK",
    "trackingColor": "GREEN",
    "checkpoints": [
      {
        "checkpointDate": "2026-04-12",
        "actualValue": 18.2,
        "projectedValue": 18.3,
        "isOnTrack": true
      },
      {
        "checkpointDate": "2026-04-19",
        "actualValue": 17.8,
        "projectedValue": 18.1,
        "isOnTrack": true
      }
    ]
  }
}
```

**Key Error Codes:**
- `404 RESOURCE_NOT_FOUND` — goal does not exist or belongs to another user
- `422 BUSINESS_RULE_VIOLATION` — goal has no logged measurement data; progress cannot be computed

---

## Appendix A: Water Logging

Water logging is handled via a lightweight endpoint rather than the full meal structure.

### POST /api/v1/diet/water

**Auth required:** Yes

**Request Body:**
```json
{
  "amountMl": 250,
  "loggedAt": "2026-04-09T10:15:00+09:00"
}
```

**Response: 201 Created**
```json
{
  "success": true,
  "data": {
    "logId": 7701,
    "amountMl": 250,
    "dailyTotalMl": 1500,
    "targetMl": 2000,
    "remainingMl": 500
  }
}
```

---

## Appendix B: Custom Food Creation

### POST /api/v1/diet/food

**Auth required:** Yes
**Description:** Creates a user-owned custom food catalog entry. Required for home-cooked meals not in any database (PRD Module B, user story B-9).

**Request Body:**
```json
{
  "name": "Chicken Breast Salad (Home Recipe)",
  "nameKo": "닭가슴살 샐러드 (집밥)",
  "servingSizeG": 250.0,
  "servingDescription": "1 bowl",
  "caloriesKcal": 310.0,
  "proteinG": 42.0,
  "carbG": 12.0,
  "fatG": 9.0,
  "fiberG": 4.5,
  "sodiumMg": 480.0
}
```

**Response: 201 Created** — returns the created food catalog entry with `source: "USER"`.

---

*End of REST API Design v1.0*
