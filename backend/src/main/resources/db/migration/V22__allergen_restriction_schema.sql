-- 식단 제한 조건 (알러지·기피 식품)
CREATE TABLE diet_restrictions (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id           BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    restriction_type  VARCHAR(10)  NOT NULL CHECK (restriction_type IN ('ALLERGY', 'AVOID')),
    target_type       VARCHAR(20)  NOT NULL CHECK (target_type IN ('FOOD', 'CATEGORY', 'KEYWORD', 'ALLERGEN_TAG')),
    food_catalog_id   BIGINT       REFERENCES food_catalog(id),
    category          VARCHAR(30),
    keyword           VARCHAR(100),
    allergen_tag      VARCHAR(30),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at        TIMESTAMPTZ
);

CREATE INDEX idx_diet_restrictions_user_id      ON diet_restrictions (user_id);
CREATE INDEX idx_diet_restrictions_user_active  ON diet_restrictions (user_id) WHERE deleted_at IS NULL;

-- 식품-알러젠 태그 매핑 (알러지 회피 판정의 근거)
-- confidence_level 은 "이 알러젠이 없다"는 회피 주장의 신뢰도가 아니라,
-- 해당 식품에 대한 알러젠 검토 수준을 나타낸다.
-- 레코드가 존재하면 해당 알러젠이 식품에 포함되어 있음을 의미한다.
CREATE TABLE food_allergen_tags (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    food_catalog_id BIGINT       NOT NULL REFERENCES food_catalog(id) ON DELETE CASCADE,
    allergen_tag    VARCHAR(30)  NOT NULL,
    confidence_level VARCHAR(20) NOT NULL CHECK (confidence_level IN ('DIRECT_VERIFIED', 'LABEL_DERIVED', 'RECIPE_DERIVED', 'UNKNOWN')),
    source          VARCHAR(30)  NOT NULL CHECK (source IN ('MFDS_CLASS', 'KHANES_RECIPE', 'FOODQR', 'OPEN_FOOD_FACTS', 'USER_CUSTOM')),
    reviewed_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (food_catalog_id, allergen_tag)
);

CREATE INDEX idx_food_allergen_tags_food_id     ON food_allergen_tags (food_catalog_id);
CREATE INDEX idx_food_allergen_tags_tag         ON food_allergen_tags (allergen_tag);
