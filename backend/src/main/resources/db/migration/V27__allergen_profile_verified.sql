-- Strict allergy mode needs a positive signal that the food's allergen profile
-- was reviewed as a complete set. Positive allergen tags alone are not enough
-- to prove another restricted allergen is absent.
ALTER TABLE food_allergen_tags
    ADD COLUMN allergen_profile_verified BOOLEAN NOT NULL DEFAULT FALSE;

-- Backfill only the most conservative single-ingredient seed rows. Composite
-- foods such as breads, noodles, ice cream, tofu, and prepared beef dishes stay
-- unverified until a complete label/recipe review is loaded.
UPDATE food_allergen_tags fat
SET allergen_profile_verified = TRUE
FROM food_catalog fc
WHERE fat.food_catalog_id = fc.id
  AND fat.confidence_level = 'DIRECT_VERIFIED'
  AND fat.source = 'MFDS_CLASS'
  AND fc.name IN (
      'Egg (Whole)',
      'Hard-boiled Egg',
      'Quail Egg',
      'Milk (Whole)',
      'Milk (Skim)',
      'Mackerel',
      'Crab Meat',
      'Shrimp',
      'Clam',
      'Oyster',
      'Mussel',
      'Squid',
      'Pine Nut (Jat)',
      'Pork Tenderloin',
      'Pork Belly (Raw)',
      'Chicken Breast',
      'Chicken Thigh',
      'Chicken Wing',
      'Beef (Sirloin)',
      'Walnut'
  );
