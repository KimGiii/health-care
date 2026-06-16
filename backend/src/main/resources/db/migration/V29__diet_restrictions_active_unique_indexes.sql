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
