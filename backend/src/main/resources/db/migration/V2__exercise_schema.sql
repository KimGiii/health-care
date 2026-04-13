-- ============================================================
-- V2: Exercise Domain — exercise_catalog, exercise_sessions, exercise_sets
-- ============================================================

-- 운동 카탈로그: 글로벌(50+ 시드) + 사용자 커스텀 운동
CREATE TABLE exercise_catalog (
    id                  BIGINT          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name                VARCHAR(150)    NOT NULL,
    name_ko             VARCHAR(150),
    muscle_group        VARCHAR(50)     NOT NULL CHECK (muscle_group IN (
                            'CHEST', 'BACK', 'SHOULDERS', 'BICEPS', 'TRICEPS',
                            'FOREARMS', 'CORE', 'QUADRICEPS', 'HAMSTRINGS',
                            'GLUTES', 'CALVES', 'FULL_BODY', 'CARDIO', 'OTHER'
                        )),
    exercise_type       VARCHAR(20)     NOT NULL CHECK (exercise_type IN (
                            'STRENGTH', 'CARDIO', 'BODYWEIGHT', 'FLEXIBILITY', 'SPORTS'
                        )),
    met_value           DOUBLE PRECISION,
    is_custom           BOOLEAN         NOT NULL DEFAULT FALSE,
    created_by_user_id  BIGINT          REFERENCES users(id) ON DELETE SET NULL,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ
);

CREATE INDEX idx_exercise_catalog_type
    ON exercise_catalog (exercise_type, muscle_group)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_exercise_catalog_custom
    ON exercise_catalog (created_by_user_id)
    WHERE is_custom = TRUE AND deleted_at IS NULL;

-- ─────────────────────────────────────────────────────────────────────────────
-- 글로벌 운동 시드 데이터 (50개)
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO exercise_catalog (name, name_ko, muscle_group, exercise_type, met_value, is_custom) VALUES
-- CHEST
('Bench Press',           '벤치 프레스',        'CHEST',      'STRENGTH',   5.0,  FALSE),
('Incline Bench Press',   '인클라인 벤치 프레스', 'CHEST',      'STRENGTH',   5.0,  FALSE),
('Decline Bench Press',   '디클라인 벤치 프레스', 'CHEST',      'STRENGTH',   5.0,  FALSE),
('Push Up',               '푸시업',             'CHEST',      'BODYWEIGHT', 3.8,  FALSE),
('Cable Flye',            '케이블 플라이',        'CHEST',      'STRENGTH',   4.0,  FALSE),
-- BACK
('Pull Up',               '풀업',               'BACK',       'BODYWEIGHT', 8.0,  FALSE),
('Deadlift',              '데드리프트',           'BACK',       'STRENGTH',   6.0,  FALSE),
('Barbell Row',           '바벨 로우',           'BACK',       'STRENGTH',   5.5,  FALSE),
('Lat Pulldown',          '랫 풀다운',           'BACK',       'STRENGTH',   4.5,  FALSE),
('Seated Cable Row',      '시티드 케이블 로우',   'BACK',       'STRENGTH',   4.5,  FALSE),
-- SHOULDERS
('Overhead Press',        '오버헤드 프레스',      'SHOULDERS',  'STRENGTH',   5.0,  FALSE),
('Lateral Raise',         '레터럴 레이즈',        'SHOULDERS',  'STRENGTH',   3.5,  FALSE),
('Front Raise',           '프론트 레이즈',        'SHOULDERS',  'STRENGTH',   3.5,  FALSE),
('Face Pull',             '페이스 풀',           'SHOULDERS',  'STRENGTH',   4.0,  FALSE),
-- BICEPS
('Barbell Curl',          '바벨 컬',             'BICEPS',     'STRENGTH',   3.5,  FALSE),
('Dumbbell Curl',         '덤벨 컬',             'BICEPS',     'STRENGTH',   3.5,  FALSE),
('Hammer Curl',           '해머 컬',             'BICEPS',     'STRENGTH',   3.5,  FALSE),
-- TRICEPS
('Tricep Pushdown',       '트라이셉 푸시다운',    'TRICEPS',    'STRENGTH',   4.0,  FALSE),
('Overhead Tricep Ext',   '오버헤드 트라이셉 익스텐션', 'TRICEPS', 'STRENGTH', 4.0, FALSE),
('Dips',                  '딥스',               'TRICEPS',    'BODYWEIGHT', 5.0,  FALSE),
-- CORE
('Plank',                 '플랭크',              'CORE',       'BODYWEIGHT', 3.0,  FALSE),
('Crunch',                '크런치',              'CORE',       'BODYWEIGHT', 3.0,  FALSE),
('Leg Raise',             '레그 레이즈',          'CORE',       'BODYWEIGHT', 3.5,  FALSE),
('Ab Wheel Rollout',      '복근 롤아웃',          'CORE',       'BODYWEIGHT', 4.5,  FALSE),
-- QUADRICEPS
('Squat',                 '스쿼트',              'QUADRICEPS', 'STRENGTH',   6.0,  FALSE),
('Leg Press',             '레그 프레스',          'QUADRICEPS', 'STRENGTH',   5.0,  FALSE),
('Leg Extension',         '레그 익스텐션',        'QUADRICEPS', 'STRENGTH',   3.5,  FALSE),
('Lunge',                 '런지',               'QUADRICEPS', 'BODYWEIGHT', 4.0,  FALSE),
('Bulgarian Split Squat', '불가리안 스플릿 스쿼트', 'QUADRICEPS', 'STRENGTH', 5.5,  FALSE),
-- HAMSTRINGS
('Romanian Deadlift',     '루마니안 데드리프트',  'HAMSTRINGS', 'STRENGTH',   5.5,  FALSE),
('Leg Curl',              '레그 컬',             'HAMSTRINGS', 'STRENGTH',   3.5,  FALSE),
('Good Morning',          '굿모닝',              'HAMSTRINGS', 'STRENGTH',   4.0,  FALSE),
-- GLUTES
('Hip Thrust',            '힙 스러스트',          'GLUTES',     'STRENGTH',   5.0,  FALSE),
('Glute Bridge',          '글루트 브리지',        'GLUTES',     'BODYWEIGHT', 3.5,  FALSE),
('Cable Kickback',        '케이블 킥백',          'GLUTES',     'STRENGTH',   3.5,  FALSE),
-- CALVES
('Calf Raise',            '카프 레이즈',          'CALVES',     'STRENGTH',   3.0,  FALSE),
('Seated Calf Raise',     '시티드 카프 레이즈',   'CALVES',     'STRENGTH',   3.0,  FALSE),
-- FULL_BODY
('Clean and Jerk',        '클린 앤 저크',         'FULL_BODY',  'STRENGTH',   9.0,  FALSE),
('Kettlebell Swing',      '케틀벨 스윙',          'FULL_BODY',  'STRENGTH',   9.8,  FALSE),
('Burpee',                '버피',               'FULL_BODY',  'BODYWEIGHT', 8.0,  FALSE),
('Mountain Climber',      '마운틴 클라이머',      'FULL_BODY',  'BODYWEIGHT', 8.0,  FALSE),
-- CARDIO
('Running (Outdoor)',     '야외 달리기',          'CARDIO',     'CARDIO',     9.8,  FALSE),
('Running (Treadmill)',   '트레드밀 달리기',      'CARDIO',     'CARDIO',     9.0,  FALSE),
('Cycling (Stationary)',  '고정식 자전거',        'CARDIO',     'CARDIO',     7.0,  FALSE),
('Rowing Machine',        '로잉 머신',           'CARDIO',     'CARDIO',     7.0,  FALSE),
('Jump Rope',             '줄넘기',              'CARDIO',     'CARDIO',    11.0,  FALSE),
('Elliptical',            '일립티컬',            'CARDIO',     'CARDIO',     5.0,  FALSE),
('Swimming',              '수영',               'CARDIO',     'CARDIO',     9.8,  FALSE),
-- FLEXIBILITY
('Static Stretching',     '스태틱 스트레칭',      'OTHER',      'FLEXIBILITY', 2.3, FALSE),
('Yoga',                  '요가',               'OTHER',      'FLEXIBILITY', 3.3,  FALSE),
('Foam Rolling',          '폼롤링',              'OTHER',      'FLEXIBILITY', 2.0,  FALSE);

-- ─────────────────────────────────────────────────────────────────────────────
-- 운동 세션: 하나의 운동 세션(방문)을 나타냄
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE exercise_sessions (
    id                          BIGINT          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id                     BIGINT          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    session_date                DATE            NOT NULL,
    started_at                  TIMESTAMPTZ,
    ended_at                    TIMESTAMPTZ,
    duration_minutes            INTEGER         CHECK (duration_minutes > 0),
    total_volume_kg             DOUBLE PRECISION,
    calories_burned             DOUBLE PRECISION,
    calorie_estimate_method     VARCHAR(20)     CHECK (calorie_estimate_method IN ('MET', 'KEYTEL', 'MANUAL', 'NONE')),
    notes                       TEXT,
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at                  TIMESTAMPTZ
);

CREATE INDEX idx_exercise_sessions_user_date
    ON exercise_sessions (user_id, session_date)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_exercise_sessions_user_created
    ON exercise_sessions (user_id, created_at DESC)
    WHERE deleted_at IS NULL;

-- ─────────────────────────────────────────────────────────────────────────────
-- 운동 세트: 세션 내 개별 세트 (nullable 컬럼 다형성 전략)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE exercise_sets (
    id                      BIGINT          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    session_id              BIGINT          NOT NULL REFERENCES exercise_sessions(id) ON DELETE CASCADE,
    exercise_catalog_id     BIGINT          NOT NULL REFERENCES exercise_catalog(id),
    set_number              SMALLINT        NOT NULL CHECK (set_number > 0),
    set_type                VARCHAR(15)     NOT NULL CHECK (set_type IN ('WEIGHTED', 'CARDIO', 'BODYWEIGHT')),
    -- WEIGHTED / BODYWEIGHT
    weight_kg               DOUBLE PRECISION,
    reps                    SMALLINT,
    -- CARDIO
    duration_seconds        INTEGER,
    distance_m              DOUBLE PRECISION,
    -- 공통 선택 필드
    rest_seconds            SMALLINT,
    is_personal_record      BOOLEAN         NOT NULL DEFAULT FALSE,
    notes                   VARCHAR(255),
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    -- 타입별 필수 컬럼 강제
    CONSTRAINT chk_weighted_cols    CHECK (set_type != 'WEIGHTED'   OR (weight_kg IS NOT NULL AND reps IS NOT NULL)),
    CONSTRAINT chk_cardio_cols      CHECK (set_type != 'CARDIO'     OR (duration_seconds IS NOT NULL)),
    CONSTRAINT chk_bodyweight_cols  CHECK (set_type != 'BODYWEIGHT' OR (reps IS NOT NULL))
);

CREATE INDEX idx_exercise_sets_session      ON exercise_sets (session_id);
CREATE INDEX idx_exercise_sets_catalog_pr   ON exercise_sets (exercise_catalog_id, weight_kg DESC, reps DESC);
