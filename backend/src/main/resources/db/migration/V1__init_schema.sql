-- ============================================================
-- V1: Initial Schema — users, refresh_tokens
-- ============================================================

CREATE TABLE users (
    id                  BIGINT          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email               VARCHAR(255)    NOT NULL,
    password_hash       VARCHAR(255)    NOT NULL,
    display_name        VARCHAR(100)    NOT NULL,
    sex                 VARCHAR(10),
    date_of_birth       DATE,
    height_cm           DOUBLE PRECISION,
    weight_kg           DOUBLE PRECISION,
    activity_level      VARCHAR(20),
    fcm_token           VARCHAR(500),
    calorie_target      INTEGER,
    protein_target_g    INTEGER,
    carb_target_g       INTEGER,
    fat_target_g        INTEGER,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ
);

CREATE UNIQUE INDEX idx_users_email_active
    ON users (email)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_users_deleted_at
    ON users (deleted_at)
    WHERE deleted_at IS NOT NULL;


CREATE TABLE refresh_tokens (
    id          BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users(id),
    token_hash  VARCHAR(255) NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    revoked_at  TIMESTAMPTZ
);

CREATE INDEX idx_refresh_tokens_user_id     ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_token_hash  ON refresh_tokens (token_hash) WHERE revoked_at IS NULL;
