-- Keep Strict-mode profile verification aligned with the v1 decision:
-- only direct or label-derived allergen profiles can be marked complete.
ALTER TABLE food_allergen_tags
    ADD CONSTRAINT chk_food_allergen_profile_verified_confidence
        CHECK (
            allergen_profile_verified = FALSE
            OR confidence_level IN ('DIRECT_VERIFIED', 'LABEL_DERIVED')
        );
