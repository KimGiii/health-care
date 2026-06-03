-- 소셜로그인(Apple, Google) 지원
-- 소셜 전용 가입 사용자는 password_hash가 없으므로 NULL 허용으로 변경
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;

-- 외부 ID 공급자(Apple, Google) 연결 정보
-- 한 user_id가 LOCAL(users.password_hash) + Apple + Google 다중 연결 가능
CREATE TABLE user_identities (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider VARCHAR(20) NOT NULL,
    provider_subject VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_user_identities_provider_subject UNIQUE (provider, provider_subject)
);

CREATE INDEX idx_user_identities_user_id ON user_identities(user_id);
