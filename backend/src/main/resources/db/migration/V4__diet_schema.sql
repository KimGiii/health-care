-- ============================================================
-- V3: Diet Domain — food_catalog, diet_logs, food_entries
-- ============================================================

-- 식품 카탈로그: 글로벌(시드) + 사용자 커스텀 식품
CREATE TABLE food_catalog (
    id                  BIGINT          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name                VARCHAR(150)    NOT NULL,
    name_ko             VARCHAR(150),
    category            VARCHAR(30)     NOT NULL CHECK (category IN (
                            'GRAIN', 'PROTEIN_SOURCE', 'VEGETABLE', 'FRUIT',
                            'DAIRY', 'FAT', 'BEVERAGE', 'PROCESSED', 'OTHER'
                        )),
    calories_per_100g   DOUBLE PRECISION NOT NULL CHECK (calories_per_100g >= 0),
    protein_per_100g    DOUBLE PRECISION,
    carbs_per_100g      DOUBLE PRECISION,
    fat_per_100g        DOUBLE PRECISION,
    is_custom           BOOLEAN         NOT NULL DEFAULT FALSE,
    created_by_user_id  BIGINT          REFERENCES users(id) ON DELETE SET NULL,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ
);

CREATE INDEX idx_food_catalog_category
    ON food_catalog (category)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_food_catalog_custom
    ON food_catalog (created_by_user_id)
    WHERE is_custom = TRUE AND deleted_at IS NULL;

-- ─────────────────────────────────────────────────────────────────────────────
-- 글로벌 식품 시드 데이터 (50개)
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO food_catalog (name, name_ko, category, calories_per_100g, protein_per_100g, carbs_per_100g, fat_per_100g, is_custom) VALUES
-- GRAIN
('White Rice',            '흰쌀밥',          'GRAIN',          130.0, 2.4,  28.7,  0.3,  FALSE),
('Brown Rice',            '현미밥',          'GRAIN',          111.0, 2.6,  23.0,  0.9,  FALSE),
('White Bread',           '식빵',            'GRAIN',          265.0, 9.0,  49.0,  3.2,  FALSE),
('Whole Wheat Bread',     '통밀빵',          'GRAIN',          247.0, 13.0, 41.0,  4.2,  FALSE),
('Oatmeal',               '오트밀',          'GRAIN',          389.0, 17.0, 66.0,  7.0,  FALSE),
('Sweet Potato',          '고구마',          'GRAIN',          86.0,  1.6,  20.0,  0.1,  FALSE),
('Corn',                  '옥수수',          'GRAIN',          86.0,  3.3,  19.0,  1.4,  FALSE),
-- PROTEIN_SOURCE
('Chicken Breast',        '닭가슴살',        'PROTEIN_SOURCE', 165.0, 31.0, 0.0,   3.6,  FALSE),
('Beef (Sirloin)',         '소고기(등심)',     'PROTEIN_SOURCE', 271.0, 26.0, 0.0,   18.0, FALSE),
('Pork Tenderloin',       '돼지고기(안심)',   'PROTEIN_SOURCE', 143.0, 21.0, 0.0,   6.0,  FALSE),
('Salmon',                '연어',            'PROTEIN_SOURCE', 208.0, 20.0, 0.0,   13.0, FALSE),
('Tuna (Canned)',         '참치(캔)',         'PROTEIN_SOURCE', 116.0, 26.0, 0.0,   1.0,  FALSE),
('Egg (Whole)',            '달걀',            'PROTEIN_SOURCE', 143.0, 13.0, 1.1,   9.5,  FALSE),
('Tofu',                  '두부',            'PROTEIN_SOURCE', 76.0,  8.0,  1.9,   4.2,  FALSE),
('Greek Yogurt',          '그릭 요거트',      'PROTEIN_SOURCE', 97.0,  9.0,  3.6,   5.0,  FALSE),
('Cottage Cheese',        '코티지 치즈',      'PROTEIN_SOURCE', 98.0,  11.0, 3.4,   4.3,  FALSE),
-- VEGETABLE
('Broccoli',              '브로콜리',        'VEGETABLE',      34.0,  2.8,  7.0,   0.4,  FALSE),
('Spinach',               '시금치',          'VEGETABLE',      23.0,  2.9,  3.6,   0.4,  FALSE),
('Cabbage',               '양배추',          'VEGETABLE',      25.0,  1.3,  5.8,   0.1,  FALSE),
('Carrot',                '당근',            'VEGETABLE',      41.0,  0.9,  10.0,  0.2,  FALSE),
('Cucumber',              '오이',            'VEGETABLE',      15.0,  0.7,  3.6,   0.1,  FALSE),
('Tomato',                '토마토',          'VEGETABLE',      18.0,  0.9,  3.9,   0.2,  FALSE),
-- FRUIT
('Banana',                '바나나',          'FRUIT',          89.0,  1.1,  23.0,  0.3,  FALSE),
('Apple',                 '사과',            'FRUIT',          52.0,  0.3,  14.0,  0.2,  FALSE),
('Orange',                '오렌지',          'FRUIT',          47.0,  0.9,  12.0,  0.1,  FALSE),
('Blueberry',             '블루베리',        'FRUIT',          57.0,  0.7,  14.0,  0.3,  FALSE),
('Strawberry',            '딸기',            'FRUIT',          32.0,  0.7,  7.7,   0.3,  FALSE),
-- DAIRY
('Milk (Whole)',           '우유(전지)',       'DAIRY',          61.0,  3.2,  4.8,   3.3,  FALSE),
('Milk (Skim)',            '우유(저지방)',     'DAIRY',          35.0,  3.4,  5.0,   0.1,  FALSE),
('Cheddar Cheese',        '체다 치즈',       'DAIRY',          403.0, 25.0, 1.3,   33.0, FALSE),
('Butter',                '버터',            'DAIRY',          717.0, 0.9,  0.1,   81.0, FALSE),
-- FAT
('Olive Oil',             '올리브 오일',      'FAT',            884.0, 0.0,  0.0,   100.0, FALSE),
('Avocado',               '아보카도',        'FAT',            160.0, 2.0,  9.0,   15.0, FALSE),
('Almond',                '아몬드',          'FAT',            579.0, 21.0, 22.0,  50.0, FALSE),
('Walnut',                '호두',            'FAT',            654.0, 15.0, 14.0,  65.0, FALSE),
('Peanut Butter',         '땅콩버터',        'FAT',            588.0, 25.0, 20.0,  50.0, FALSE),
-- BEVERAGE
('Coffee (Black)',         '블랙 커피',       'BEVERAGE',       2.0,   0.3,  0.0,   0.0,  FALSE),
('Orange Juice',          '오렌지 주스',      'BEVERAGE',       45.0,  0.7,  10.0,  0.2,  FALSE),
('Sports Drink',          '스포츠 음료',      'BEVERAGE',       26.0,  0.0,  6.4,   0.0,  FALSE),
('Protein Shake',         '프로틴 쉐이크',    'BEVERAGE',       120.0, 24.0, 5.0,   2.0,  FALSE),
-- PROCESSED
('White Sugar',           '설탕',            'PROCESSED',      387.0, 0.0,  100.0, 0.0,  FALSE),
('Ketchup',               '케첩',            'PROCESSED',      101.0, 1.7,  25.0,  0.1,  FALSE),
('Soy Sauce',             '간장',            'PROCESSED',      53.0,  8.1,  4.9,   0.1,  FALSE),
('Mayonnaise',            '마요네즈',        'PROCESSED',      680.0, 1.0,  0.6,   75.0, FALSE),
-- OTHER
('Protein Bar',           '단백질 바',       'OTHER',          350.0, 20.0, 40.0,  10.0, FALSE),
('Mixed Nuts',            '믹스넛',          'OTHER',          607.0, 17.0, 21.0,  54.0, FALSE),
('Dark Chocolate (70%)',   '다크 초콜릿(70%)', 'OTHER',          598.0, 7.8,  45.9,  42.6, FALSE),
('Kimchi',                '김치',            'OTHER',          15.0,  1.1,  2.4,   0.5,  FALSE),
('Ramen (Dry)',            '라면(건면)',       'OTHER',          436.0, 10.0, 65.0,  16.0, FALSE),
('Rice Cake',             '떡',              'OTHER',          221.0, 4.0,  49.0,  0.6,  FALSE);

-- ─────────────────────────────────────────────────────────────────────────────
-- 식사 기록: 한 끼 식사를 나타냄
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE diet_logs (
    id              BIGINT          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    log_date        DATE            NOT NULL,
    meal_type       VARCHAR(15)     NOT NULL CHECK (meal_type IN ('BREAKFAST', 'LUNCH', 'DINNER', 'SNACK')),
    total_calories  DOUBLE PRECISION,
    total_protein_g DOUBLE PRECISION,
    total_carbs_g   DOUBLE PRECISION,
    total_fat_g     DOUBLE PRECISION,
    notes           TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ
);

CREATE INDEX idx_diet_logs_user_date
    ON diet_logs (user_id, log_date DESC)
    WHERE deleted_at IS NULL;

-- ─────────────────────────────────────────────────────────────────────────────
-- 식품 항목: 식사 기록 내 개별 식품 (cascade delete)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE food_entries (
    id              BIGINT          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    diet_log_id     BIGINT          NOT NULL REFERENCES diet_logs(id) ON DELETE CASCADE,
    food_catalog_id BIGINT          NOT NULL REFERENCES food_catalog(id),
    serving_g       DOUBLE PRECISION NOT NULL CHECK (serving_g > 0),
    calories        DOUBLE PRECISION NOT NULL,
    protein_g       DOUBLE PRECISION,
    carbs_g         DOUBLE PRECISION,
    fat_g           DOUBLE PRECISION,
    notes           VARCHAR(255),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_food_entries_diet_log ON food_entries (diet_log_id);
