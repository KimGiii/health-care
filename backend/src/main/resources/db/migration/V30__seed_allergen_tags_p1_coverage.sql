-- V30: P1 allergen seed coverage 보강
-- V25 추천 allowlist에서 누락된 TOMATO, SOY, WHEAT/GLUTEN 태그를 보강하고,
-- PEACH 선택 태그 노출에 맞춰 seed 태그를 추가한다.

-- ─── 토마토 (TOMATO) ──────────────────────────────────────────────────────
INSERT INTO food_allergen_tags (food_catalog_id, allergen_tag, confidence_level, source, reviewed_at)
SELECT id, 'TOMATO', 'DIRECT_VERIFIED', 'MFDS_CLASS', NOW()
FROM food_catalog WHERE name IN ('Tomato', 'Cherry Tomato', 'Tomato Juice')
ON CONFLICT (food_catalog_id, allergen_tag) DO NOTHING;

-- ─── 대두 (SOY) 추천 후보 보강 ─────────────────────────────────────────────
INSERT INTO food_allergen_tags (food_catalog_id, allergen_tag, confidence_level, source, reviewed_at)
SELECT id, 'SOY', 'DIRECT_VERIFIED', 'MFDS_CLASS', NOW()
FROM food_catalog WHERE name IN ('Tofu', 'Soybean Sprout')
ON CONFLICT (food_catalog_id, allergen_tag) DO NOTHING;

-- ─── 밀 (WHEAT) 추천 후보 보강 ─────────────────────────────────────────────
INSERT INTO food_allergen_tags (food_catalog_id, allergen_tag, confidence_level, source, reviewed_at)
SELECT id, 'WHEAT', 'DIRECT_VERIFIED', 'MFDS_CLASS', NOW()
FROM food_catalog WHERE name = 'Whole Wheat Bread'
ON CONFLICT (food_catalog_id, allergen_tag) DO NOTHING;

-- ─── 글루텐 (GLUTEN) 합성 식이 제한 태그 보강 ───────────────────────────────
INSERT INTO food_allergen_tags (food_catalog_id, allergen_tag, confidence_level, source, reviewed_at)
SELECT id, 'GLUTEN', 'LABEL_DERIVED', 'MFDS_CLASS', NOW()
FROM food_catalog WHERE name IN (
    'Whole Wheat Bread',
    'Barley',
    'Oatmeal',
    'Barley Tea (Boricha)'
)
ON CONFLICT (food_catalog_id, allergen_tag) DO NOTHING;

-- Buckwheat는 메밀 의무표시 태그로 관리한다. 내부 GLUTEN 정책은 밀·호밀·보리·귀리 기준이다.
DELETE FROM food_allergen_tags fat
USING food_catalog fc
WHERE fat.food_catalog_id = fc.id
  AND fc.name = 'Buckwheat'
  AND fat.allergen_tag = 'GLUTEN'
  AND fat.confidence_level = 'LABEL_DERIVED'
  AND fat.source = 'MFDS_CLASS';

-- ─── 복숭아 (PEACH) ────────────────────────────────────────────────────────
INSERT INTO food_allergen_tags (food_catalog_id, allergen_tag, confidence_level, source, reviewed_at)
SELECT id, 'PEACH', 'DIRECT_VERIFIED', 'MFDS_CLASS', NOW()
FROM food_catalog WHERE name = 'Peach'
ON CONFLICT (food_catalog_id, allergen_tag) DO NOTHING;

-- 단일 원재료로 완결 프로필 검토가 가능한 보강 seed만 Strict 통과 근거로 표시한다.
-- 두부, 통밀빵, 토마토주스처럼 제조/가공 공정에서 다른 알러젠 가능성을 배제하기 어려운 항목은 보수적으로 제외한다.
UPDATE food_allergen_tags fat
SET allergen_profile_verified = TRUE
FROM food_catalog fc
WHERE fat.food_catalog_id = fc.id
  AND fat.confidence_level IN ('DIRECT_VERIFIED', 'LABEL_DERIVED')
  AND fat.source = 'MFDS_CLASS'
  AND fc.name IN (
      'Tomato',
      'Cherry Tomato',
      'Soybean Sprout',
      'Barley',
      'Oatmeal',
      'Peach'
  );
