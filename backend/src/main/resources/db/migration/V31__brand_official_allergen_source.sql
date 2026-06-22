-- V31: 브랜드 공식 메뉴 CSV가 라벨 기반 알러젠 태그를 직접 적재할 수 있도록 source 값을 확장한다.
ALTER TABLE food_allergen_tags
    DROP CONSTRAINT IF EXISTS food_allergen_tags_source_check;

ALTER TABLE food_allergen_tags
    ADD CONSTRAINT food_allergen_tags_source_check
        CHECK (source IN (
            'MFDS_CLASS',
            'KHANES_RECIPE',
            'FOODQR',
            'BRAND_OFFICIAL',
            'OPEN_FOOD_FACTS',
            'USER_CUSTOM'
        ));
