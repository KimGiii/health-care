-- 활성 중복 정리: 부분 유니크 인덱스 생성 전, 같은 (user_id, restriction_type, 대상)으로
-- 활성(soft-delete 안 된) 행이 여럿이면 가장 최근(id 최대) 1건만 남기고 나머지를 soft-delete 한다.
-- 인덱스가 살아있는(deleted_at IS NULL) 행만 보므로, 정리하지 않으면 기존 데이터에서 인덱스 생성이 실패한다.
-- 각 블록의 조건은 아래 부분 유니크 인덱스의 WHERE 절과 1:1로 일치한다.

WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (
        PARTITION BY user_id, restriction_type, food_catalog_id ORDER BY id DESC
    ) AS rn
    FROM diet_restrictions
    WHERE deleted_at IS NULL AND target_type = 'FOOD' AND food_catalog_id IS NOT NULL
)
UPDATE diet_restrictions dr
SET deleted_at = NOW(), updated_at = NOW()
FROM ranked
WHERE dr.id = ranked.id AND ranked.rn > 1;

WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (
        PARTITION BY user_id, restriction_type, category ORDER BY id DESC
    ) AS rn
    FROM diet_restrictions
    WHERE deleted_at IS NULL AND target_type = 'CATEGORY' AND category IS NOT NULL
)
UPDATE diet_restrictions dr
SET deleted_at = NOW(), updated_at = NOW()
FROM ranked
WHERE dr.id = ranked.id AND ranked.rn > 1;

WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (
        PARTITION BY user_id, restriction_type, keyword ORDER BY id DESC
    ) AS rn
    FROM diet_restrictions
    WHERE deleted_at IS NULL AND target_type = 'KEYWORD' AND keyword IS NOT NULL
)
UPDATE diet_restrictions dr
SET deleted_at = NOW(), updated_at = NOW()
FROM ranked
WHERE dr.id = ranked.id AND ranked.rn > 1;

WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (
        PARTITION BY user_id, restriction_type, allergen_tag ORDER BY id DESC
    ) AS rn
    FROM diet_restrictions
    WHERE deleted_at IS NULL AND target_type = 'ALLERGEN_TAG' AND allergen_tag IS NOT NULL
)
UPDATE diet_restrictions dr
SET deleted_at = NOW(), updated_at = NOW()
FROM ranked
WHERE dr.id = ranked.id AND ranked.rn > 1;

CREATE UNIQUE INDEX uq_diet_restrictions_active_food
    ON diet_restrictions (user_id, restriction_type, food_catalog_id)
    WHERE deleted_at IS NULL
      AND target_type = 'FOOD'
      AND food_catalog_id IS NOT NULL;

CREATE UNIQUE INDEX uq_diet_restrictions_active_category
    ON diet_restrictions (user_id, restriction_type, category)
    WHERE deleted_at IS NULL
      AND target_type = 'CATEGORY'
      AND category IS NOT NULL;

CREATE UNIQUE INDEX uq_diet_restrictions_active_keyword
    ON diet_restrictions (user_id, restriction_type, keyword)
    WHERE deleted_at IS NULL
      AND target_type = 'KEYWORD'
      AND keyword IS NOT NULL;

CREATE UNIQUE INDEX uq_diet_restrictions_active_allergen_tag
    ON diet_restrictions (user_id, restriction_type, allergen_tag)
    WHERE deleted_at IS NULL
      AND target_type = 'ALLERGEN_TAG'
      AND allergen_tag IS NOT NULL;
