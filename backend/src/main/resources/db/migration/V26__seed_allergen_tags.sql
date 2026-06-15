-- V26: 식품 알러젠 태그 시드 데이터
-- 단일 재료 식품에 식약처 분류 기반(DIRECT_VERIFIED)으로 알러젠 태그를 부여한다.
-- ON CONFLICT DO NOTHING으로 중복 실행에 안전하다.

-- ─── 달걀 (EGG) ──────────────────────────────────────────────────────────
INSERT INTO food_allergen_tags (food_catalog_id, allergen_tag, confidence_level, source, reviewed_at)
SELECT id, 'EGG', 'DIRECT_VERIFIED', 'MFDS_CLASS', NOW()
FROM food_catalog WHERE name IN ('Egg (Whole)', 'Hard-boiled Egg', 'Quail Egg')
ON CONFLICT (food_catalog_id, allergen_tag) DO NOTHING;

-- ─── 우유 (MILK) ─────────────────────────────────────────────────────────
INSERT INTO food_allergen_tags (food_catalog_id, allergen_tag, confidence_level, source, reviewed_at)
SELECT id, 'MILK', 'DIRECT_VERIFIED', 'MFDS_CLASS', NOW()
FROM food_catalog WHERE name IN (
    'Milk (Whole)', 'Milk (Skim)',
    'Plain Yogurt',
    'Mozzarella Cheese', 'Cream Cheese', 'Processed Cheese Slice',
    'Condensed Milk (Sweetened)', 'Parmesan Cheese', 'Ricotta Cheese',
    'Kefir', 'Whipping Cream', 'Brie Cheese', 'Heavy Cream', 'Vanilla Ice Cream',
    'Banana Milk', 'Strawberry Milk'
)
ON CONFLICT (food_catalog_id, allergen_tag) DO NOTHING;

-- ─── 메밀 (BUCKWHEAT) ────────────────────────────────────────────────────
INSERT INTO food_allergen_tags (food_catalog_id, allergen_tag, confidence_level, source, reviewed_at)
SELECT id, 'BUCKWHEAT', 'DIRECT_VERIFIED', 'MFDS_CLASS', NOW()
FROM food_catalog WHERE name = 'Buckwheat'
ON CONFLICT (food_catalog_id, allergen_tag) DO NOTHING;

-- ─── 밀 (WHEAT) ──────────────────────────────────────────────────────────
INSERT INTO food_allergen_tags (food_catalog_id, allergen_tag, confidence_level, source, reviewed_at)
SELECT id, 'WHEAT', 'DIRECT_VERIFIED', 'MFDS_CLASS', NOW()
FROM food_catalog WHERE name IN (
    'Pasta (Dried)', 'Thin Noodles (Somen)', 'Udon (Dried)',
    'Bread Roll (Dinner Roll)', 'Croissant', 'Bagel', 'Pita Bread', 'Flatbread (Tortilla)',
    'Donut (Plain)', 'Muffin (Blueberry)', 'Waffle', 'Pancake',
    'Whole Wheat Cracker', 'Ramen Noodle (Cooked)', 'Instant Cup Noodle'
)
ON CONFLICT (food_catalog_id, allergen_tag) DO NOTHING;

-- 밀 포함 식품에 GLUTEN 태그도 부여 (합성 식이 제한 태그)
INSERT INTO food_allergen_tags (food_catalog_id, allergen_tag, confidence_level, source, reviewed_at)
SELECT id, 'GLUTEN', 'LABEL_DERIVED', 'MFDS_CLASS', NOW()
FROM food_catalog WHERE name IN (
    'Pasta (Dried)', 'Thin Noodles (Somen)', 'Udon (Dried)',
    'Bread Roll (Dinner Roll)', 'Croissant', 'Bagel', 'Pita Bread', 'Flatbread (Tortilla)',
    'Donut (Plain)', 'Muffin (Blueberry)', 'Waffle', 'Pancake',
    'Whole Wheat Cracker', 'Ramen Noodle (Cooked)', 'Instant Cup Noodle',
    'Buckwheat'
)
ON CONFLICT (food_catalog_id, allergen_tag) DO NOTHING;

-- ─── 땅콩 (PEANUT) ───────────────────────────────────────────────────────
INSERT INTO food_allergen_tags (food_catalog_id, allergen_tag, confidence_level, source, reviewed_at)
SELECT id, 'PEANUT', 'DIRECT_VERIFIED', 'MFDS_CLASS', NOW()
FROM food_catalog WHERE name = 'Peanut Butter'
ON CONFLICT (food_catalog_id, allergen_tag) DO NOTHING;

-- ─── 대두 (SOY) ──────────────────────────────────────────────────────────
INSERT INTO food_allergen_tags (food_catalog_id, allergen_tag, confidence_level, source, reviewed_at)
SELECT id, 'SOY', 'DIRECT_VERIFIED', 'MFDS_CLASS', NOW()
FROM food_catalog WHERE name IN ('Soft Tofu (Sundubu)', 'Soy Milk (Plain)')
ON CONFLICT (food_catalog_id, allergen_tag) DO NOTHING;

-- ─── 고등어 (MACKEREL) ───────────────────────────────────────────────────
INSERT INTO food_allergen_tags (food_catalog_id, allergen_tag, confidence_level, source, reviewed_at)
SELECT id, 'MACKEREL', 'DIRECT_VERIFIED', 'MFDS_CLASS', NOW()
FROM food_catalog WHERE name = 'Mackerel'
ON CONFLICT (food_catalog_id, allergen_tag) DO NOTHING;

-- ─── 게 (CRAB) ───────────────────────────────────────────────────────────
INSERT INTO food_allergen_tags (food_catalog_id, allergen_tag, confidence_level, source, reviewed_at)
SELECT id, 'CRAB', 'DIRECT_VERIFIED', 'MFDS_CLASS', NOW()
FROM food_catalog WHERE name = 'Crab Meat'
ON CONFLICT (food_catalog_id, allergen_tag) DO NOTHING;

-- ─── 새우 (SHRIMP) ───────────────────────────────────────────────────────
INSERT INTO food_allergen_tags (food_catalog_id, allergen_tag, confidence_level, source, reviewed_at)
SELECT id, 'SHRIMP', 'DIRECT_VERIFIED', 'MFDS_CLASS', NOW()
FROM food_catalog WHERE name = 'Shrimp'
ON CONFLICT (food_catalog_id, allergen_tag) DO NOTHING;

-- ─── 조개류 (SHELLFISH) ──────────────────────────────────────────────────
INSERT INTO food_allergen_tags (food_catalog_id, allergen_tag, confidence_level, source, reviewed_at)
SELECT id, 'SHELLFISH', 'DIRECT_VERIFIED', 'MFDS_CLASS', NOW()
FROM food_catalog WHERE name IN ('Clam', 'Oyster', 'Mussel', 'Crab Meat')
ON CONFLICT (food_catalog_id, allergen_tag) DO NOTHING;

-- ─── 오징어 (SQUID) ──────────────────────────────────────────────────────
INSERT INTO food_allergen_tags (food_catalog_id, allergen_tag, confidence_level, source, reviewed_at)
SELECT id, 'SQUID', 'DIRECT_VERIFIED', 'MFDS_CLASS', NOW()
FROM food_catalog WHERE name = 'Squid'
ON CONFLICT (food_catalog_id, allergen_tag) DO NOTHING;

-- ─── 잣 (PINE_NUT) ───────────────────────────────────────────────────────
INSERT INTO food_allergen_tags (food_catalog_id, allergen_tag, confidence_level, source, reviewed_at)
SELECT id, 'PINE_NUT', 'DIRECT_VERIFIED', 'MFDS_CLASS', NOW()
FROM food_catalog WHERE name = 'Pine Nut (Jat)'
ON CONFLICT (food_catalog_id, allergen_tag) DO NOTHING;

-- ─── 돼지고기 (PORK) ─────────────────────────────────────────────────────
INSERT INTO food_allergen_tags (food_catalog_id, allergen_tag, confidence_level, source, reviewed_at)
SELECT id, 'PORK', 'DIRECT_VERIFIED', 'MFDS_CLASS', NOW()
FROM food_catalog WHERE name IN (
    'Pork Tenderloin', 'Pork Belly (Raw)', 'Sausage (Pork)', 'Bacon (Pork)'
)
ON CONFLICT (food_catalog_id, allergen_tag) DO NOTHING;

-- ─── 닭고기 (CHICKEN) ────────────────────────────────────────────────────
INSERT INTO food_allergen_tags (food_catalog_id, allergen_tag, confidence_level, source, reviewed_at)
SELECT id, 'CHICKEN', 'DIRECT_VERIFIED', 'MFDS_CLASS', NOW()
FROM food_catalog WHERE name IN ('Chicken Breast', 'Chicken Thigh', 'Chicken Wing')
ON CONFLICT (food_catalog_id, allergen_tag) DO NOTHING;

-- ─── 쇠고기 (BEEF) ───────────────────────────────────────────────────────
INSERT INTO food_allergen_tags (food_catalog_id, allergen_tag, confidence_level, source, reviewed_at)
SELECT id, 'BEEF', 'DIRECT_VERIFIED', 'MFDS_CLASS', NOW()
FROM food_catalog WHERE name IN ('Beef (Sirloin)', 'Bulgogi Beef', 'Galbi (Short Rib)')
ON CONFLICT (food_catalog_id, allergen_tag) DO NOTHING;

-- ─── 호두 (WALNUT) ───────────────────────────────────────────────────────
INSERT INTO food_allergen_tags (food_catalog_id, allergen_tag, confidence_level, source, reviewed_at)
SELECT id, 'WALNUT', 'DIRECT_VERIFIED', 'MFDS_CLASS', NOW()
FROM food_catalog WHERE name = 'Walnut'
ON CONFLICT (food_catalog_id, allergen_tag) DO NOTHING;
