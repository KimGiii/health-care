-- 소셜 로그인(Apple/Google) 신규 가입 시 약관·개인정보처리방침 동의 시각 저장.
-- 이메일 가입은 기존 흐름 유지(추후 동일 컬럼 사용 가능).
ALTER TABLE users
    ADD COLUMN terms_agreed_at   TIMESTAMP WITH TIME ZONE,
    ADD COLUMN privacy_agreed_at TIMESTAMP WITH TIME ZONE;
