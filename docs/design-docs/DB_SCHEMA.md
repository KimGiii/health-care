# Database Schema Design
## Personal Health Tracking App — Korean Market

**Version:** 1.0
**Date:** April 9, 2026
**Author:** System Architect
**Status:** Draft for Engineering Review
**Database:** PostgreSQL 16+

---

## Table of Contents

1. [Design Decisions](#1-design-decisions)
2. [Table Definitions](#2-table-definitions)
3. [ER Diagram](#3-er-diagram)
4. [Index Strategy](#4-index-strategy)
5. [Soft-Delete Strategy](#5-soft-delete-strategy)

---

## 1. Design Decisions

### 1.1 Surrogate Primary Keys: BIGINT Auto-Increment (not UUID)

All tables use `BIGINT GENERATED ALWAYS AS IDENTITY` as the surrogate primary key rather than UUID. This decision is justified by the scale and technical requirements of this application:

**Performance:** At personal-app scale (research report section 4.1 projects 10,000–15,000 rows per user over 5 years), sequential BIGINT PKs produce perfectly ordered B-tree index pages, eliminating the page-split fragmentation that random UUID v4 values cause. For this write volume the difference is academic, but sequential integers remain best practice and avoid a class of problem entirely.

**Storage:** A BIGINT occupies 8 bytes vs. 16 bytes for a UUID. With foreign keys appearing in every child table, BIGINT reduces index size meaningfully at any scale.

**Readability and debuggability:** Integer IDs are human-readable in logs, SQL queries, and API responses, which matters significantly during development and support investigations.

**Sufficient ID space:** `BIGINT` supports values up to 9,223,372,036,854,775,807. Even if the app reaches 10 million users each with 1,000 rows of data per table, that is 10 billion rows — still within BIGINT range with a comfortable margin.

**Why not UUID?** UUIDs are appropriate when IDs must be generated client-side (offline-first architectures) or when IDs must be non-guessable for security reasons. This app generates all IDs server-side on insert, and API endpoints are protected by authentication — the primary arguments for UUID do not apply. The `storage_key` on `progress_photos` (an S3 object key) intentionally uses a UUID-formatted string to prevent enumeration of private photo URLs; that is the appropriate place for UUID-like opacity.

### 1.2 Soft-Delete Strategy

All user-owned data tables include a `deleted_at TIMESTAMPTZ` column (nullable; NULL means not deleted). This implements the soft-delete pattern required for:

- **GDPR Article 17 / PIPA Article 36:** Account deletion must complete within 30 days. Soft-delete on Day 0 (record inaccessible immediately), hard-delete on Day 30 (actual row removal + backup purge). The 30-day window also provides account recovery.
- **Audit and recovery:** Deleted records remain visible to administrative processes for the 30-day window without requiring backup restoration.
- **Referential integrity during deletion window:** Meal items can reference food catalog entries even after soft-deletion of the meal, preventing FK violations during the deletion staging period.

All application queries add `WHERE deleted_at IS NULL` as a default condition. Spring Data JPA `@Where(clause = "deleted_at IS NULL")` is applied at the entity level to enforce this transparently. A scheduled job runs nightly to hard-delete rows where `deleted_at < NOW() - INTERVAL '30 days'`.

### 1.3 Exercise Set Polymorphism: Nullable Columns (not EAV)

The `exercise_sets` table handles three distinct exercise types (weighted strength, cardio, bodyweight) using nullable columns rather than an Entity-Attribute-Value (EAV) pattern or a separate table per type.

**Why nullable columns over EAV:**
- EAV (a generic `attribute_name` / `attribute_value` table) makes every query a pivot operation with multiple JOINs; SQL readability and query performance both suffer significantly.
- At this scale, the nullable column approach has no storage overhead problem — a NULL DECIMAL column costs 1 byte in PostgreSQL's null bitmap, not the full 8 bytes.
- Type-specific columns are immediately queryable with standard SQL and can carry proper type constraints (NOT NULL where required for a given type).
- Adding a new exercise type requires an ALTER TABLE to add columns — a low-cost operation in PostgreSQL at this row volume, and far simpler than evolving an EAV schema.

**Column groups by exercise type:**
- **Weighted strength:** `weight_kg`, `reps` — both populated; `duration_seconds` and `distance_m` are NULL
- **Cardio:** `duration_seconds`, `distance_m` — both populated; `weight_kg` is NULL; `reps` may be NULL
- **Bodyweight:** `reps` — populated; `weight_kg` is NULL (or 0.0 if app tracks bodyweight as load); `duration_seconds` optional

A `set_type` ENUM column ('WEIGHTED', 'CARDIO', 'BODYWEIGHT') makes the intended type explicit and enables CHECK constraints.

---

## 2. Table Definitions

---

### 2.1 `users`

Stores registered user accounts. Core identity and personalization data. Health/biometric fields are derived from onboarding and used for calorie target calculation (Mifflin-St Jeor formula, PRD section 1.2).

```sql
CREATE TABLE users (
    id                  BIGINT          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email               VARCHAR(255)    NOT NULL,
    password_hash       VARCHAR(255)    NOT NULL,
    display_name        VARCHAR(100)    NOT NULL,
    sex                 VARCHAR(10)     NOT NULL CHECK (sex IN ('MALE', 'FEMALE', 'NOT_SPECIFIED')),
    date_of_birth       DATE            NOT NULL,
    height_cm           DECIMAL(5,1)    NOT NULL CHECK (height_cm > 0),
    weight_kg           DECIMAL(5,1)    NOT NULL CHECK (weight_kg > 0),
    activity_level      VARCHAR(20)     NOT NULL CHECK (activity_level IN (
                            'SEDENTARY', 'LIGHTLY_ACTIVE', 'MODERATELY_ACTIVE',
                            'VERY_ACTIVE', 'ATHLETE'
                        )),
    -- Calculated personalized targets (Mifflin-St Jeor + goal adjustment)
    calorie_target      INTEGER         NOT NULL DEFAULT 2000,
    protein_target_g    INTEGER         NOT NULL DEFAULT 150,
    carb_target_g       INTEGER         NOT NULL DEFAULT 200,
    fat_target_g        INTEGER         NOT NULL DEFAULT 65,
    -- Notification
    fcm_token           VARCHAR(512),
    -- Localization
    locale              VARCHAR(10)     NOT NULL DEFAULT 'ko-KR',
    timezone            VARCHAR(64)     NOT NULL DEFAULT 'Asia/Seoul',
    -- PIPA/GDPR consent tracking (consent details in separate consent_audit table)
    general_consent_at  TIMESTAMPTZ,
    health_consent_at   TIMESTAMPTZ,
    -- Soft-delete
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ,
    -- Deletion tracking for hard-delete scheduling
    deletion_requested_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_users_email ON users (email) WHERE deleted_at IS NULL;
CREATE INDEX idx_users_deleted_at ON users (deleted_at) WHERE deleted_at IS NOT NULL;
```

---

### 2.2 `refresh_tokens`

Stores hashed refresh tokens for stateless JWT authentication with server-side revocation capability. Storing the hash (not the token itself) prevents token exposure in the event of a database breach.

```sql
CREATE TABLE refresh_tokens (
    id              BIGINT          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash      VARCHAR(64)     NOT NULL,  -- SHA-256 hex of the raw token
    issued_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ     NOT NULL,
    revoked_at      TIMESTAMPTZ,
    device_info     VARCHAR(255)    -- optional: user-agent snippet for session management UI
);

CREATE UNIQUE INDEX uq_refresh_tokens_hash ON refresh_tokens (token_hash);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens (expires_at);
```

---

### 2.3 `exercise_catalog`

Global exercise library (seeded with 50+ exercises at launch, per PRD Module A) and user-created custom exercises. MET values from the Ainsworth Compendium (research report section 3.1) are stored for calorie estimation.

```sql
CREATE TABLE exercise_catalog (
    id                  BIGINT          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name                VARCHAR(150)    NOT NULL,
    name_ko             VARCHAR(150),   -- Korean name for localized display
    muscle_group        VARCHAR(50)     NOT NULL CHECK (muscle_group IN (
                            'CHEST', 'BACK', 'SHOULDERS', 'BICEPS', 'TRICEPS',
                            'FOREARMS', 'CORE', 'QUADRICEPS', 'HAMSTRINGS',
                            'GLUTES', 'CALVES', 'FULL_BODY', 'CARDIO', 'OTHER'
                        )),
    exercise_type       VARCHAR(20)     NOT NULL CHECK (exercise_type IN (
                            'STRENGTH', 'CARDIO', 'BODYWEIGHT', 'FLEXIBILITY', 'SPORTS'
                        )),
    met_value           DECIMAL(4,1),   -- MET from Ainsworth Compendium; NULL for custom entries without MET assignment
    is_custom           BOOLEAN         NOT NULL DEFAULT FALSE,
    created_by_user_id  BIGINT          REFERENCES users(id) ON DELETE SET NULL,
    -- Soft-delete (applies to custom exercises only; global exercises are never deleted)
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ
);

-- Global exercises visible to all users (created_by_user_id IS NULL)
-- User custom exercises visible only to that user (created_by_user_id IS NOT NULL)
CREATE INDEX idx_exercise_catalog_type ON exercise_catalog (exercise_type, muscle_group) WHERE deleted_at IS NULL;
CREATE INDEX idx_exercise_catalog_custom ON exercise_catalog (created_by_user_id) WHERE is_custom = TRUE AND deleted_at IS NULL;
```

---

### 2.4 `exercise_sessions`

One row per workout session. A session groups all sets performed in a single gym visit or cardio outing. `calorie_estimate_method` records whether MET or Keytel formula was used — required for the honest labeling specified in PRD section 1.4 and user story A-9.

```sql
CREATE TABLE exercise_sessions (
    id                          BIGINT          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id                     BIGINT          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    session_date                DATE            NOT NULL,
    started_at                  TIMESTAMPTZ,
    ended_at                    TIMESTAMPTZ,
    duration_minutes            INTEGER         CHECK (duration_minutes > 0),
    total_volume_kg             DECIMAL(10,2),  -- sum of (weight_kg × reps) across all weighted sets; computed on save
    calories_burned             DECIMAL(7,1),
    calorie_estimate_method     VARCHAR(20)     CHECK (calorie_estimate_method IN ('MET', 'KEYTEL', 'MANUAL', 'NONE')),
    notes                       TEXT,
    -- Soft-delete
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at                  TIMESTAMPTZ
);

-- Primary query pattern: per-user, date-range lookups (daily/weekly summary)
CREATE INDEX idx_exercise_sessions_user_date ON exercise_sessions (user_id, session_date) WHERE deleted_at IS NULL;
CREATE INDEX idx_exercise_sessions_user_created ON exercise_sessions (user_id, created_at DESC) WHERE deleted_at IS NULL;
```

---

### 2.5 `exercise_sets`

Individual sets within a session. Implements the nullable-column polymorphism strategy (section 1.3). The `is_personal_record` flag is set by the service layer at write time when the set exceeds the historical maximum for that exercise/user combination.

```sql
CREATE TABLE exercise_sets (
    id                      BIGINT          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    session_id              BIGINT          NOT NULL REFERENCES exercise_sessions(id) ON DELETE CASCADE,
    exercise_catalog_id     BIGINT          NOT NULL REFERENCES exercise_catalog(id),
    set_number              SMALLINT        NOT NULL CHECK (set_number > 0),
    set_type                VARCHAR(15)     NOT NULL CHECK (set_type IN ('WEIGHTED', 'CARDIO', 'BODYWEIGHT')),
    -- Weighted / Bodyweight columns
    weight_kg               DECIMAL(6,2),   -- NULL for pure cardio sets; 0.0 acceptable for bodyweight-as-load
    reps                    SMALLINT,       -- NULL for time-based cardio sets
    -- Cardio columns
    duration_seconds        INTEGER,        -- NULL for weighted sets
    distance_m              DECIMAL(8,1),   -- NULL for weighted sets
    -- Common optional columns
    rest_seconds            SMALLINT,
    is_personal_record      BOOLEAN         NOT NULL DEFAULT FALSE,
    notes                   VARCHAR(255),
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    -- Enforce type consistency: each type must have its core columns populated
    CONSTRAINT chk_weighted_cols  CHECK (set_type != 'WEIGHTED'  OR (weight_kg IS NOT NULL AND reps IS NOT NULL)),
    CONSTRAINT chk_cardio_cols    CHECK (set_type != 'CARDIO'    OR (duration_seconds IS NOT NULL)),
    CONSTRAINT chk_bodyweight_cols CHECK (set_type != 'BODYWEIGHT' OR (reps IS NOT NULL))
);

CREATE INDEX idx_exercise_sets_session ON exercise_sets (session_id);
-- PR detection query: max weight/reps for a given user+exercise combination
CREATE INDEX idx_exercise_sets_catalog ON exercise_sets (exercise_catalog_id, weight_kg DESC, reps DESC);
```

---

### 2.6 `food_catalog`

Caches responses from USDA FoodData Central and Open Food Facts, and stores user-created custom foods. The research report (section 4.2) specifies a 30-day TTL and `external_id` for cache invalidation. This table is the single food data source for the diet module at query time; the application layer handles cache miss population.

```sql
CREATE TABLE food_catalog (
    id                  BIGINT          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    external_id         VARCHAR(100),   -- USDA fdc_id or OFF barcode; NULL for user-created foods
    source              VARCHAR(10)     NOT NULL CHECK (source IN ('USDA', 'OFF', 'USER')),
    name                VARCHAR(255)    NOT NULL,
    name_ko             VARCHAR(255),   -- Korean name where available (Open Food Facts Korean dataset)
    brand               VARCHAR(150),
    barcode             VARCHAR(30),    -- EAN-13 / UPC-A for barcode scanning
    serving_size_g      DECIMAL(7,2),
    serving_description VARCHAR(100),   -- e.g., "1 cup", "1 slice", "100g"
    -- Macronutrients (per serving)
    calories_kcal       DECIMAL(7,1)    NOT NULL,
    protein_g           DECIMAL(6,2)    NOT NULL DEFAULT 0,
    carb_g              DECIMAL(6,2)    NOT NULL DEFAULT 0,
    fat_g               DECIMAL(6,2)    NOT NULL DEFAULT 0,
    fiber_g             DECIMAL(6,2),
    sugar_g             DECIMAL(6,2),
    -- Key micronutrients (PRD section 2: "8 key micros")
    sodium_mg           DECIMAL(7,1),
    cholesterol_mg      DECIMAL(7,1),
    vitamin_a_mcg       DECIMAL(7,1),
    vitamin_c_mg        DECIMAL(7,1),
    vitamin_d_mcg       DECIMAL(7,1),
    iron_mg             DECIMAL(6,2),
    -- Cache management
    cached_at           TIMESTAMPTZ,    -- NULL for user-created entries; set for API-cached entries
    -- User-created food ownership
    created_by_user_id  BIGINT          REFERENCES users(id) ON DELETE SET NULL,
    -- Soft-delete
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ
);

-- Barcode lookups (primary access pattern for barcode scanner)
CREATE INDEX idx_food_catalog_barcode ON food_catalog (barcode) WHERE barcode IS NOT NULL AND deleted_at IS NULL;
-- External ID for cache invalidation
CREATE UNIQUE INDEX uq_food_catalog_external ON food_catalog (source, external_id) WHERE external_id IS NOT NULL AND deleted_at IS NULL;
-- Full-text search on name (Korean and English)
CREATE INDEX idx_food_catalog_name_fts ON food_catalog USING gin(to_tsvector('simple', coalesce(name, '') || ' ' || coalesce(name_ko, '')));
-- User-created foods lookup
CREATE INDEX idx_food_catalog_user ON food_catalog (created_by_user_id) WHERE source = 'USER' AND deleted_at IS NULL;
-- Cache TTL sweep: find entries older than 30 days
CREATE INDEX idx_food_catalog_cached_at ON food_catalog (cached_at) WHERE cached_at IS NOT NULL;
```

---

### 2.7 `meals`

A meal is a container for one or more food items consumed in a single meal slot on a given date. The four meal slots (BREAKFAST, LUNCH, DINNER, SNACK) match the PRD Module B specification.

```sql
CREATE TABLE meals (
    id          BIGINT          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    meal_date   DATE            NOT NULL,
    meal_slot   VARCHAR(10)     NOT NULL CHECK (meal_slot IN ('BREAKFAST', 'LUNCH', 'DINNER', 'SNACK')),
    notes       TEXT,
    -- Soft-delete
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at  TIMESTAMPTZ
);

-- Primary query: all meals for a user on a date (daily dashboard)
CREATE INDEX idx_meals_user_date ON meals (user_id, meal_date) WHERE deleted_at IS NULL;
-- Uniqueness: one record per meal slot per day per user (application enforces; index supports)
CREATE UNIQUE INDEX uq_meals_user_date_slot ON meals (user_id, meal_date, meal_slot) WHERE deleted_at IS NULL;
```

---

### 2.8 `meal_items`

Each row is one food item added to a meal. Macro values are denormalized at write time from `food_catalog` — this ensures historical diary entries remain accurate even if the source food record is updated or deleted. Denormalization is the correct pattern for diary-type data (immutable log of what was eaten).

```sql
CREATE TABLE meal_items (
    id                  BIGINT          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    meal_id             BIGINT          NOT NULL REFERENCES meals(id) ON DELETE CASCADE,
    food_catalog_id     BIGINT          NOT NULL REFERENCES food_catalog(id),
    serving_qty         DECIMAL(6,2)    NOT NULL CHECK (serving_qty > 0),
    serving_unit        VARCHAR(50)     NOT NULL,  -- e.g., "g", "cup", "slice", "piece"
    -- Denormalized macros at time of logging (serving_qty × food_catalog macro per serving)
    calories_kcal       DECIMAL(7,1)    NOT NULL,
    protein_g           DECIMAL(6,2)    NOT NULL,
    carb_g              DECIMAL(6,2)    NOT NULL,
    fat_g               DECIMAL(6,2)    NOT NULL,
    fiber_g             DECIMAL(6,2),
    sodium_mg           DECIMAL(7,1),
    sugar_g             DECIMAL(6,2),
    -- Soft-delete
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ
);

CREATE INDEX idx_meal_items_meal ON meal_items (meal_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_meal_items_food ON meal_items (food_catalog_id);
```

---

### 2.9 `water_logs`

Separate table for water intake logging. A simple append-only log — each row is one water intake event. The daily total is the sum of `amount_ml` for the user on the date. Kept separate from meals to allow quick-tap logging without creating a meal record.

```sql
CREATE TABLE water_logs (
    id          BIGINT          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    logged_at   TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    log_date    DATE            NOT NULL,   -- denormalized from logged_at for date-range queries
    amount_ml   SMALLINT        NOT NULL DEFAULT 250 CHECK (amount_ml > 0),
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_water_logs_user_date ON water_logs (user_id, log_date);
```

---

### 2.10 `body_measurements`

Fixed-column schema for the 5 MVP measurements (weight, waist, hip, arm, thigh) plus supporting measurements for the US Navy body fat formula (neck). The research report (section 3.3) identifies these as the "Essential 5 for MVP." Derived metrics (BMI, WHR, WHR risk, body fat via Navy formula) are computed by the service layer and stored for query efficiency — recalculating on every read would require the same inputs to be present.

```sql
CREATE TABLE body_measurements (
    id                  BIGINT          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id             BIGINT          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    logged_at           TIMESTAMPTZ     NOT NULL,  -- user-specified time; morning post-toilet is recommended protocol
    log_date            DATE            NOT NULL,  -- denormalized for date-range queries
    -- The 5 MVP measurements (all nullable — user logs whichever they have)
    weight_kg           DECIMAL(5,1),   -- to 0.1 kg precision per research section 3.3
    waist_cm            DECIMAL(5,1),
    hip_cm              DECIMAL(5,1),
    arm_cm              DECIMAL(5,1),   -- flexed; dominant arm; research section 3.3
    thigh_cm            DECIMAL(5,1),   -- mid-thigh, 10 cm above patella
    -- Additional measurements for Navy formula and completeness
    calf_cm             DECIMAL(5,1),
    neck_cm             DECIMAL(5,1),   -- required for US Navy body fat formula (PRD Module C)
    -- Body fat %: manually entered or computed
    body_fat_pct        DECIMAL(4,1),
    body_fat_source     VARCHAR(20)     CHECK (body_fat_source IN (
                            'MANUAL', 'SMART_SCALE', 'NAVY_FORMULA', 'DEXA', 'INBODY'
                        )),
    -- Derived metrics (computed at write time by service layer)
    bmi                 DECIMAL(4,1),
    whr                 DECIMAL(4,3),   -- waist-to-hip ratio; 3 decimal places for precision
    whr_risk            VARCHAR(10)     CHECK (whr_risk IN ('LOW', 'MODERATE', 'HIGH')),
    notes               TEXT,
    -- Soft-delete
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ
);

-- Primary query: per-user, date-ordered history
CREATE INDEX idx_body_measurements_user_date ON body_measurements (user_id, log_date DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_body_measurements_user_logged ON body_measurements (user_id, logged_at DESC) WHERE deleted_at IS NULL;
```

---

### 2.11 `progress_photos`

Metadata for progress photos stored in AWS S3. Photos are never stored in the database as BLOBs (research report section 4.4). The `storage_key` is a UUID-formatted path in S3 to prevent enumeration. `exif_stripped` is a confirmation flag set to TRUE only after the server-side EXIF stripping operation completes successfully.

```sql
CREATE TABLE progress_photos (
    id                  BIGINT          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id             BIGINT          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    captured_at         TIMESTAMPTZ     NOT NULL,  -- time of the photo (may differ from upload time)
    photo_date          DATE            NOT NULL,   -- denormalized for date-range queries
    photo_type          VARCHAR(15)     NOT NULL CHECK (photo_type IN (
                            'FRONT', 'BACK', 'SIDE_LEFT', 'SIDE_RIGHT', 'DETAIL'
                        )),
    -- S3 object keys (UUID-formatted paths; never predictable by client)
    storage_key         VARCHAR(512)    NOT NULL,   -- full resolution
    thumbnail_key_150   VARCHAR(512),              -- 150px width grid thumbnail
    thumbnail_key_400   VARCHAR(512),              -- 400px width comparison view
    thumbnail_key_800   VARCHAR(512),              -- 800px width full screen
    original_width_px   INTEGER,
    original_height_px  INTEGER,
    file_size_bytes     INTEGER,
    -- Privacy and compliance
    exif_stripped       BOOLEAN         NOT NULL DEFAULT FALSE,
    is_private          BOOLEAN         NOT NULL DEFAULT TRUE,  -- default private per PRD 7.5 and GDPR Art. 25
    is_baseline         BOOLEAN         NOT NULL DEFAULT FALSE, -- pinned baseline photo for comparison
    -- Measurement context at time of photo (for overlay in comparison view)
    body_weight_kg      DECIMAL(5,1),
    body_fat_pct        DECIMAL(4,1),
    waist_cm            DECIMAL(5,1),
    notes               TEXT,
    -- Soft-delete
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ
);

-- Photo timeline: per-user, per-type, ordered by date (timeline strip query)
CREATE INDEX idx_progress_photos_user_type_date ON progress_photos (user_id, photo_type, photo_date DESC) WHERE deleted_at IS NULL;
-- Baseline photo lookup
CREATE INDEX idx_progress_photos_baseline ON progress_photos (user_id, photo_type) WHERE is_baseline = TRUE AND deleted_at IS NULL;
```

---

### 2.11A `meal_photo_analyses`

Temporary draft records for AI meal-photo analysis. These rows support an explicit two-step flow: upload and analyze first, then user-confirmed save into the standard diet log tables.

```sql
CREATE TABLE meal_photo_analyses (
    id                  BIGINT          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id             BIGINT          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    storage_key         VARCHAR(512)    NOT NULL,
    content_type        VARCHAR(100)    NOT NULL,
    file_size_bytes     BIGINT          NOT NULL,
    captured_at         TIMESTAMPTZ     NOT NULL,
    status              VARCHAR(20)     NOT NULL CHECK (status IN (
                            'INITIATED', 'ANALYZED', 'FAILED', 'CONFIRMED'
                        )),
    provider            VARCHAR(50),
    analysis_version    VARCHAR(50),
    raw_model_output    TEXT,
    analysis_warnings   TEXT,
    confirmed_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_meal_photo_analyses_storage_key ON meal_photo_analyses (storage_key);
CREATE INDEX idx_meal_photo_analyses_user_created ON meal_photo_analyses (user_id, created_at DESC) WHERE deleted_at IS NULL;
```

### 2.11B `meal_photo_analysis_items`

Normalized item-level output from the AI draft. `matched_food_catalog_id` links a detected item to an existing food row when the server can find a reasonable match.

```sql
CREATE TABLE meal_photo_analysis_items (
    id                        BIGINT          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    analysis_id               BIGINT          NOT NULL REFERENCES meal_photo_analyses(id) ON DELETE CASCADE,
    item_order                INTEGER         NOT NULL,
    label                     VARCHAR(150)    NOT NULL,
    matched_food_catalog_id   BIGINT          REFERENCES food_catalog(id) ON DELETE SET NULL,
    estimated_serving_g       DOUBLE PRECISION NOT NULL,
    calories                  DOUBLE PRECISION,
    protein_g                 DOUBLE PRECISION,
    carbs_g                   DOUBLE PRECISION,
    fat_g                     DOUBLE PRECISION,
    confidence                DOUBLE PRECISION,
    needs_review              BOOLEAN         NOT NULL DEFAULT TRUE,
    unknown_or_uncertain      TEXT,
    created_at                TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_meal_photo_analysis_items_analysis_order ON meal_photo_analysis_items (analysis_id, item_order ASC);
```

---

### 2.12 `goals`

One row per goal per user. Only one goal may have `status = 'ACTIVE'` at a time (enforced by partial unique index). When a new goal is created, the previous active goal is set to `status = 'ABANDONED'` (or 'COMPLETED' if target was reached). Goal history is preserved per PRD Module D user story D-7.

```sql
CREATE TABLE goals (
    id                  BIGINT          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id             BIGINT          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    goal_type           VARCHAR(30)     NOT NULL CHECK (goal_type IN (
                            'WEIGHT_LOSS', 'MUSCLE_GAIN', 'BODY_RECOMPOSITION',
                            'ENDURANCE', 'GENERAL_HEALTH'
                        )),
    target_value        DECIMAL(7,2),   -- e.g., target weight in kg, target body fat %, target endurance time in minutes
    target_unit         VARCHAR(20),    -- 'kg', 'pct', 'minutes', null for GENERAL_HEALTH
    target_date         DATE,
    start_value         DECIMAL(7,2),   -- value at goal creation (for progress %)
    start_date          DATE            NOT NULL DEFAULT CURRENT_DATE,
    status              VARCHAR(15)     NOT NULL DEFAULT 'ACTIVE' CHECK (status IN (
                            'ACTIVE', 'COMPLETED', 'ABANDONED'
                        )),
    -- Auto-set calorie and macro targets derived from goal type (PRD section 3.2 macro guidelines)
    calorie_target      INTEGER,
    protein_target_g    INTEGER,
    carb_target_g       INTEGER,
    fat_target_g        INTEGER,
    weekly_rate_target  DECIMAL(4,2),   -- e.g., -0.5 kg/week for weight loss
    -- Completion metadata
    completed_at        TIMESTAMPTZ,
    abandoned_at        TIMESTAMPTZ,
    -- Soft-delete
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ
);

-- Enforce single active goal per user
CREATE UNIQUE INDEX uq_goals_user_active ON goals (user_id) WHERE status = 'ACTIVE' AND deleted_at IS NULL;
CREATE INDEX idx_goals_user ON goals (user_id, created_at DESC) WHERE deleted_at IS NULL;
```

---

### 2.13 `goal_checkpoints`

Weekly progress snapshots used to plot the actual vs. projected trend line in the goal visualization (PRD section 6, visualization D2). Created automatically by a weekly scheduled job (Sunday morning, aligned with the weekly summary notification). Also created on-demand when the user views goal progress.

```sql
CREATE TABLE goal_checkpoints (
    id                  BIGINT          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    goal_id             BIGINT          NOT NULL REFERENCES goals(id) ON DELETE CASCADE,
    checkpoint_date     DATE            NOT NULL,
    actual_value        DECIMAL(7,2),   -- the measured value on this date (weight, body fat %, etc.)
    projected_value     DECIMAL(7,2),   -- the value predicted by the linear projection from start
    is_on_track         BOOLEAN,        -- actual <= projected for loss goals; actual >= projected for gain goals
    notes               TEXT,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_goal_checkpoints_goal_date ON goal_checkpoints (goal_id, checkpoint_date);
CREATE UNIQUE INDEX uq_goal_checkpoints_weekly ON goal_checkpoints (goal_id, checkpoint_date);
```

---

### 2.14 `consent_audit`

Immutable record of user consent events. Survives account deletion (the row itself is retained with hashed user ID only, per research report section 5.3 and PIPA compliance requirements). Records which version of the privacy policy the user consented to and when.

```sql
CREATE TABLE consent_audit (
    id                  BIGINT          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id             BIGINT          REFERENCES users(id) ON DELETE SET NULL,
    user_id_hash        VARCHAR(64)     NOT NULL,  -- SHA-256 of user_id (persists after account deletion)
    consent_type        VARCHAR(30)     NOT NULL CHECK (consent_type IN (
                            'GENERAL_TERMS', 'HEALTH_DATA_SENSITIVE', 'MARKETING', 'CROSS_BORDER_TRANSFER'
                        )),
    action              VARCHAR(10)     NOT NULL CHECK (action IN ('GRANTED', 'WITHDRAWN')),
    policy_version      VARCHAR(20)     NOT NULL,  -- e.g., '1.0', '1.1'
    consented_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    ip_address_hash     VARCHAR(64),               -- hashed for privacy; for audit trail only
    user_agent          VARCHAR(512)
);

CREATE INDEX idx_consent_audit_user ON consent_audit (user_id) WHERE user_id IS NOT NULL;
CREATE INDEX idx_consent_audit_hash ON consent_audit (user_id_hash);
CREATE INDEX idx_consent_audit_type ON consent_audit (user_id_hash, consent_type, consented_at DESC);
```

---

## 3. ER Diagram

```
users
  │
  ├──────────────────────┬───────────────────────────┬──────────────────────────────┐
  │                      │                           │                              │
  │              refresh_tokens               consent_audit                         │
  │            (user_id → users.id)         (user_id → users.id)                   │
  │                                                                                  │
  ├── exercise_sessions ──────── exercise_sets                                      │
  │   (user_id → users.id)       (session_id → exercise_sessions.id)               │
  │                               (exercise_catalog_id → exercise_catalog.id)       │
  │                                                                                  │
  │                          exercise_catalog                                        │
  │                          (created_by_user_id → users.id, nullable)              │
  │                                                                                  │
  ├── meals ──────────────── meal_items ──── food_catalog                           │
  │   (user_id → users.id)  (meal_id → meals.id)  (created_by_user_id → users.id)  │
  │                         (food_catalog_id → food_catalog.id)                     │
  │                                                                                  │
  ├── water_logs                                                                     │
  │   (user_id → users.id)                                                          │
  │                                                                                  │
  ├── body_measurements                                                              │
  │   (user_id → users.id)                                                          │
  │                                                                                  │
  ├── progress_photos                                                                │
  │   (user_id → users.id)                                                          │
  │                                                                                  │
  └── goals ──────────── goal_checkpoints
      (user_id → users.id) (goal_id → goals.id)


Cardinalities:
  users            (1) ──── (0..N)  refresh_tokens
  users            (1) ──── (0..N)  exercise_sessions
  users            (1) ──── (0..N)  meals
  users            (1) ──── (0..N)  water_logs
  users            (1) ──── (0..N)  body_measurements
  users            (1) ──── (0..N)  progress_photos
  users            (1) ──── (0..N)  goals
  users            (1) ──── (0..N)  exercise_catalog    [custom exercises only]
  users            (1) ──── (0..N)  food_catalog        [user-created foods only]
  users            (1) ──── (0..N)  consent_audit
  exercise_sessions (1) ──── (1..N)  exercise_sets
  exercise_catalog  (1) ──── (0..N)  exercise_sets
  meals            (1) ──── (1..N)  meal_items
  food_catalog     (1) ──── (0..N)  meal_items
  goals            (1) ──── (0..N)  goal_checkpoints
```

---

## 4. Index Strategy

All primary indexes follow the research report recommendation (section 4.1): composite index on `(user_id, <date_column>)` for all major user-owned tables. This supports the dominant query pattern — "fetch this user's records for a date range" — with a single efficient B-tree scan.

| Table | Index | Type | Purpose |
|---|---|---|---|
| users | `(email)` partial | UNIQUE | Login lookup |
| refresh_tokens | `(token_hash)` | UNIQUE | Token validation on refresh |
| refresh_tokens | `(user_id)` | B-tree | Revoke all sessions for user |
| exercise_catalog | `(exercise_type, muscle_group)` | B-tree | Catalog browse/filter |
| exercise_sessions | `(user_id, session_date)` | B-tree | Daily/weekly summary |
| exercise_sets | `(session_id)` | B-tree | Load sets for a session |
| exercise_sets | `(exercise_catalog_id, weight_kg, reps)` | B-tree | PR detection query |
| food_catalog | `(barcode)` | B-tree | Barcode scan lookup |
| food_catalog | `(source, external_id)` | UNIQUE | Cache invalidation |
| food_catalog | `GIN (tsvector name + name_ko)` | GIN | Full-text search |
| meals | `(user_id, meal_date)` | B-tree | Daily diet summary |
| meals | `(user_id, meal_date, meal_slot)` | UNIQUE | One slot per day enforcement |
| meal_items | `(meal_id)` | B-tree | Items for a meal |
| water_logs | `(user_id, log_date)` | B-tree | Daily water total |
| body_measurements | `(user_id, log_date DESC)` | B-tree | Measurement history |
| progress_photos | `(user_id, photo_type, photo_date DESC)` | B-tree | Timeline strip query |
| goals | `(user_id)` | UNIQUE partial | Single active goal |
| goal_checkpoints | `(goal_id, checkpoint_date)` | UNIQUE | Weekly checkpoint |
| consent_audit | `(user_id_hash, consent_type)` | B-tree | Compliance reporting |

---

## 5. Soft-Delete Strategy

All user-owned data tables include `deleted_at TIMESTAMPTZ DEFAULT NULL`. The lifecycle is:

| Phase | Trigger | `deleted_at` State | Data Visibility |
|---|---|---|---|
| Active | Normal operation | NULL | Fully visible to user and application |
| Soft-deleted | User deletes record or requests account deletion | SET to `NOW()` | Invisible to application queries (filtered by `WHERE deleted_at IS NULL`); visible only to admin/deletion jobs |
| Hard-deleted | Nightly job runs 30 days after `deleted_at` | Row removed | Gone from database; backup purge also queued |

**Application layer enforcement:** JPA `@Where(clause = "deleted_at IS NULL")` on each entity class ensures all Spring Data queries automatically exclude soft-deleted rows without requiring developers to remember the condition in each query.

**Hard-delete scheduled job** (`@Scheduled(cron = "0 3 * * * *")` — 3 AM daily):
```sql
-- Hard-delete records past the 30-day retention window
DELETE FROM meal_items     WHERE deleted_at < NOW() - INTERVAL '30 days';
DELETE FROM meals          WHERE deleted_at < NOW() - INTERVAL '30 days';
DELETE FROM exercise_sets  WHERE deleted_at < NOW() - INTERVAL '30 days';
DELETE FROM exercise_sessions WHERE deleted_at < NOW() - INTERVAL '30 days';
DELETE FROM body_measurements WHERE deleted_at < NOW() - INTERVAL '30 days';
DELETE FROM progress_photos WHERE deleted_at < NOW() - INTERVAL '30 days';
-- S3 deletion is handled separately by PhotoCleanupJob
DELETE FROM goals          WHERE deleted_at < NOW() - INTERVAL '30 days';
DELETE FROM users          WHERE deleted_at < NOW() - INTERVAL '30 days';
```

Deletion events are recorded in a `deletion_audit_log` table (not defined above as it contains no personal data — only a hashed user ID and a timestamp — and is retained indefinitely for compliance documentation).

---

*End of Database Schema Design v1.0*
