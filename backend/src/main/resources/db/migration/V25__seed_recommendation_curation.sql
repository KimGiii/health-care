-- V25: seed 추천 큐레이션 보정
-- V23에서 기존 seed가 추천 가능 상태로 백필됐을 수 있으므로,
-- 전체 seed를 검색 전용으로 낮춘 뒤 명시 검수 목록만 추천 후보로 승격한다.

UPDATE food_catalog
SET recommendation_status = 'SEARCH_ONLY',
    recommendation_reason = NULL
WHERE source = 'SEED';

WITH seed_allowlist(name_ko, category) AS (
    VALUES
        -- GRAIN: 8
        ('현미밥', 'GRAIN'),
        ('통밀빵', 'GRAIN'),
        ('오트밀', 'GRAIN'),
        ('고구마', 'GRAIN'),
        ('옥수수', 'GRAIN'),
        ('감자', 'GRAIN'),
        ('보리', 'GRAIN'),
        ('메밀', 'GRAIN'),

        -- PROTEIN_SOURCE: 10
        ('닭가슴살', 'PROTEIN_SOURCE'),
        ('돼지고기(안심)', 'PROTEIN_SOURCE'),
        ('연어', 'PROTEIN_SOURCE'),
        ('참치(캔)', 'PROTEIN_SOURCE'),
        ('달걀', 'PROTEIN_SOURCE'),
        ('두부', 'PROTEIN_SOURCE'),
        ('광어', 'PROTEIN_SOURCE'),
        ('오징어', 'PROTEIN_SOURCE'),
        ('새우', 'PROTEIN_SOURCE'),
        ('순두부', 'PROTEIN_SOURCE'),

        -- VEGETABLE: 10
        ('브로콜리', 'VEGETABLE'),
        ('시금치', 'VEGETABLE'),
        ('양배추', 'VEGETABLE'),
        ('당근', 'VEGETABLE'),
        ('오이', 'VEGETABLE'),
        ('토마토', 'VEGETABLE'),
        ('깻잎', 'VEGETABLE'),
        ('양파', 'VEGETABLE'),
        ('배추', 'VEGETABLE'),
        ('콩나물', 'VEGETABLE'),

        -- FRUIT: 6
        ('바나나', 'FRUIT'),
        ('사과', 'FRUIT'),
        ('오렌지', 'FRUIT'),
        ('블루베리', 'FRUIT'),
        ('딸기', 'FRUIT'),
        ('키위', 'FRUIT'),

        -- DAIRY: 4
        ('우유(저지방)', 'DAIRY'),
        ('플레인 요거트', 'DAIRY'),
        ('리코타 치즈', 'DAIRY'),
        ('케피어', 'DAIRY'),

        -- 보조 후보: 4
        ('아보카도', 'FAT'),
        ('아몬드', 'FAT'),
        ('두유(무가당)', 'BEVERAGE'),
        ('김치', 'OTHER')
)
UPDATE food_catalog fc
SET recommendation_status = 'RECOMMENDABLE',
    recommendation_reason = NULL
FROM seed_allowlist allowlist
WHERE fc.source = 'SEED'
  AND fc.name_ko = allowlist.name_ko
  AND fc.category = allowlist.category;
