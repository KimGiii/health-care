-- Phase 2: 추천 자격을 태그 행과 분리된 데이터 계약으로 관리한다.

CREATE TABLE food_allergen_profiles (
    food_catalog_id     BIGINT PRIMARY KEY REFERENCES food_catalog(id) ON DELETE CASCADE,
    confidence_level    VARCHAR(20)  NOT NULL,
    source              VARCHAR(30)  NOT NULL,
    standard_version    VARCHAR(80)  NOT NULL,
    source_reference    VARCHAR(500),
    reviewed_at         TIMESTAMPTZ  NOT NULL,
    valid_until         TIMESTAMPTZ,
    invalidated_at      TIMESTAMPTZ,
    invalidation_reason VARCHAR(255),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_food_allergen_profile_confidence
        CHECK (confidence_level IN ('DIRECT_VERIFIED', 'LABEL_DERIVED')),
    CONSTRAINT chk_food_allergen_profile_source
        CHECK (source IN ('MFDS_CLASS', 'FOODQR', 'BRAND_OFFICIAL', 'OPEN_FOOD_FACTS')),
    CONSTRAINT chk_food_allergen_profile_validity
        CHECK (valid_until IS NULL OR valid_until > reviewed_at),
    CONSTRAINT chk_food_allergen_profile_invalidation
        CHECK (
            (invalidated_at IS NULL AND invalidation_reason IS NULL)
            OR (invalidated_at IS NOT NULL AND invalidation_reason IS NOT NULL)
        )
);

-- 태그 행에 저장된 기존 완결성 신호를 별도 프로필로 이전한다.
-- 이후 신규 검토는 food_allergen_profiles만 기준으로 삼는다.
INSERT INTO food_allergen_profiles (
    food_catalog_id,
    confidence_level,
    source,
    standard_version,
    reviewed_at
)
SELECT DISTINCT ON (food_catalog_id)
    food_catalog_id,
    confidence_level,
    source,
    'legacy-tag-profile-v1',
    COALESCE(reviewed_at, created_at, NOW())
FROM food_allergen_tags
WHERE allergen_profile_verified = TRUE
  AND confidence_level IN ('DIRECT_VERIFIED', 'LABEL_DERIVED')
ORDER BY
    food_catalog_id,
    CASE confidence_level WHEN 'DIRECT_VERIFIED' THEN 0 ELSE 1 END,
    reviewed_at DESC NULLS LAST,
    created_at DESC
ON CONFLICT (food_catalog_id) DO NOTHING;
